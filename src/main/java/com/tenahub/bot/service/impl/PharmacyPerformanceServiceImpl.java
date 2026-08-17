package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.PharmacyHealthFactorDTO;
import com.tenahub.bot.dto.PharmacyPerformanceDemandItemDTO;
import com.tenahub.bot.dto.PharmacyPerformanceInventorySnapshotDTO;
import com.tenahub.bot.dto.PharmacyPerformanceReportDTO;
import com.tenahub.bot.dto.PharmacyPerformanceSalesSnapshotDTO;
import com.tenahub.bot.dto.PharmacyPerformanceWindowDTO;
import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.MedicineReservationStatus;
import com.tenahub.bot.entity.MedicineSearchLog;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.entity.PharmacySale;
import com.tenahub.bot.entity.PharmacySaleItem;
import com.tenahub.bot.repository.MedicineReservationRepository;
import com.tenahub.bot.repository.MedicineSearchLogRepository;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.repository.PharmacySaleItemRepository;
import com.tenahub.bot.repository.PharmacySaleRepository;
import com.tenahub.bot.service.PharmacyPerformanceService;
import com.tenahub.bot.util.DemandLabeler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PharmacyPerformanceServiceImpl implements PharmacyPerformanceService {

    private final PharmacyRepository pharmacyRepository;
    private final MedicineReservationRepository reservationRepository;
    private final PharmacyInventoryRepository inventoryRepository;
    private final PharmacySaleRepository pharmacySaleRepository;
    private final PharmacySaleItemRepository pharmacySaleItemRepository;
    private final MedicineSearchLogRepository searchLogRepository;

    @Override
    public String buildPerformanceCard(Long pharmacyTelegramId) {
        PharmacyPerformanceReportDTO today = getPerformanceReport(pharmacyTelegramId, "daily");
        PharmacyPerformanceReportDTO week = getPerformanceReport(pharmacyTelegramId, "weekly");

        return "📊 <b>Pharmacy Performance</b>\n\n"
                + "<b>Health: " + week.getHealthScore() + " (" + week.getHealthGrade() + ")</b>\n\n"
                + "<b>Today</b>\n"
                + formatWindow(today.getReservations())
                + "\n\n"
                + "<b>Last 7 Days</b>\n"
                + formatWindow(week.getReservations())
                + "\n\n"
                + "📦 Low Stock Items: " + (week.getInventory() == null ? 0 : week.getInventory().getLowStock()) + "\n"
                + "❌ Out of Stock Items: " + (week.getInventory() == null ? 0 : week.getInventory().getOutOfStock()) + "\n"
                + "🔄 Medicines Updated Today: "
                + (today.getInventory() == null ? 0 : today.getInventory().getUpdatedInPeriod());
    }

    @Override
    public PharmacyPerformanceReportDTO getPerformanceReport(Long pharmacyTelegramId, String period) {
        Pharmacy pharmacy = resolvePharmacy(pharmacyTelegramId);
        String resolvedPeriod = normalizePeriod(period);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = periodStart(resolvedPeriod, now);

        List<MedicineReservation> reservations = reservationRepository.findByPharmacyIdOrderByCreatedAtDesc(pharmacy.getId());
        List<PharmacyInventory> inventory = inventoryRepository.findByPharmacyId(pharmacy.getId());
        Map<String, Integer> searchCounts = aggregateSearchCounts(inventory, from, now);

        PharmacyPerformanceWindowDTO window = toWindowDto(computeWindowStats(reservations, from, now));
        PharmacyPerformanceInventorySnapshotDTO inventorySnapshot = computeInventorySnapshot(inventory, from);
        PharmacyPerformanceSalesSnapshotDTO sales = computeSalesSnapshot(pharmacy.getId(), from, now);
        List<PharmacyPerformanceDemandItemDTO> topDemand = buildDemandItems(inventory, searchCounts, 10);
        int criticalRestock = countCriticalRestock(inventory, searchCounts, reservations, from);

        HealthResult health = computeHealth(window, inventorySnapshot, sales, topDemand, inventory.isEmpty());

        return PharmacyPerformanceReportDTO.builder()
                .period(resolvedPeriod)
                .from(from)
                .to(now)
                .healthScore(health.score())
                .healthGrade(health.grade())
                .healthFactors(health.factors())
                .reservations(window)
                .inventory(inventorySnapshot)
                .sales(sales)
                .topDemand(topDemand)
                .criticalRestockCount(criticalRestock)
                .build();
    }

    @Override
    public List<PharmacyPerformanceDemandItemDTO> listDemandItems(Long pharmacyTelegramId, String period) {
        Pharmacy pharmacy = resolvePharmacy(pharmacyTelegramId);
        String resolvedPeriod = normalizePeriod(period);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = periodStart(resolvedPeriod, now);
        List<PharmacyInventory> inventory = inventoryRepository.findByPharmacyId(pharmacy.getId());
        Map<String, Integer> searchCounts = aggregateSearchCounts(inventory, from, now);
        return buildDemandItems(inventory, searchCounts, 50);
    }

    private Map<String, Integer> aggregateSearchCounts(List<PharmacyInventory> inventory,
                                                       LocalDateTime from,
                                                       LocalDateTime to) {
        Map<String, PharmacyInventory> byName = inventoryIndex(inventory);
        Map<String, Integer> searchCounts = new HashMap<>();
        for (MedicineSearchLog log : searchLogRepository.findBySearchedAtBetween(from, to)) {
            if (log.getMedicineName() == null || log.getMedicineName().isBlank()) {
                continue;
            }
            String key = log.getMedicineName().trim().toLowerCase(Locale.ROOT);
            if (!byName.isEmpty() && !byName.containsKey(key)) {
                continue;
            }
            searchCounts.merge(key, 1, Integer::sum);
        }
        return searchCounts;
    }

    private Map<String, PharmacyInventory> inventoryIndex(List<PharmacyInventory> inventory) {
        return inventory.stream()
                .filter(i -> i.getMedicineName() != null && !i.getMedicineName().isBlank())
                .collect(Collectors.toMap(
                        i -> i.getMedicineName().trim().toLowerCase(Locale.ROOT),
                        i -> i,
                        (a, b) -> a));
    }

    private int countCriticalRestock(List<PharmacyInventory> inventory,
                                     Map<String, Integer> searchCounts,
                                     List<MedicineReservation> reservations,
                                     LocalDateTime from) {
        Map<String, Integer> failures = new HashMap<>();
        for (MedicineReservation reservation : reservations) {
            if (reservation.getCreatedAt() == null || reservation.getCreatedAt().isBefore(from)) {
                continue;
            }
            if (reservation.getMedicineName() == null || reservation.getMedicineName().isBlank()) {
                continue;
            }
            if (reservation.getStatus() != MedicineReservationStatus.REJECTED
                    && reservation.getStatus() != MedicineReservationStatus.CANCELLED
                    && reservation.getStatus() != MedicineReservationStatus.EXPIRED) {
                continue;
            }
            failures.merge(reservation.getMedicineName().trim().toLowerCase(Locale.ROOT), 1, Integer::sum);
        }

        int critical = 0;
        for (PharmacyInventory item : inventory) {
            if (item.getMedicineName() == null || item.getMedicineName().isBlank()) {
                continue;
            }
            String key = item.getMedicineName().trim().toLowerCase(Locale.ROOT);
            int searches = searchCounts.getOrDefault(key, 0);
            int fail = failures.getOrDefault(key, 0);
            int qty = item.getQuantity() == null ? 0 : item.getQuantity();
            boolean oos = item.isOutOfStock() || qty <= 0;
            boolean low = !oos && qty <= thresholdFor(item);
            int score = (searches * 2) + (fail * 3);
            if (oos) {
                score += 6;
            } else if (low) {
                score += 3;
            }
            if (score >= 100) {
                critical++;
            }
        }
        return critical;
    }

    private List<PharmacyPerformanceDemandItemDTO> buildDemandItems(List<PharmacyInventory> inventory,
                                                                   Map<String, Integer> searchCounts,
                                                                   int limit) {
        Map<String, PharmacyInventory> byName = inventoryIndex(inventory);
        List<Map.Entry<String, Integer>> ranked = searchCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .toList();

        List<PharmacyPerformanceDemandItemDTO> items = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : ranked) {
            PharmacyInventory inv = byName.get(entry.getKey());
            int qty = inv == null || inv.getQuantity() == null ? 0 : inv.getQuantity();
            boolean oos = inv == null || inv.isOutOfStock() || qty <= 0;
            boolean low = !oos && inv != null && qty <= thresholdFor(inv);
            String displayName = inv != null && inv.getMedicineName() != null
                    ? inv.getMedicineName()
                    : entry.getKey();
            items.add(PharmacyPerformanceDemandItemDTO.builder()
                    .medicineName(displayName)
                    .searchCount(entry.getValue())
                    .stockQuantity(qty)
                    .outOfStock(oos)
                    .lowStock(low)
                    .demandLabel(DemandLabeler.label(entry.getValue(), oos, low))
                    .build());
        }
        return items;
    }

    private PharmacyPerformanceSalesSnapshotDTO computeSalesSnapshot(Long pharmacyId, LocalDateTime from, LocalDateTime to) {
        List<PharmacySale> sales = pharmacySaleRepository
                .findByPharmacyIdAndCreatedAtBetweenOrderByCreatedAtDesc(pharmacyId, from, to);
        BigDecimal revenue = BigDecimal.ZERO;
        for (PharmacySale sale : sales) {
            if (sale.getTotalAmount() != null) {
                revenue = revenue.add(sale.getTotalAmount());
            }
        }
        int units = 0;
        if (!sales.isEmpty()) {
            List<Long> saleIds = sales.stream().map(PharmacySale::getId).filter(Objects::nonNull).toList();
            if (!saleIds.isEmpty()) {
                List<PharmacySaleItem> items = pharmacySaleItemRepository.findByPharmacyIdAndSaleIdIn(pharmacyId, saleIds);
                for (PharmacySaleItem item : items) {
                    units += item.getQuantity() == null ? 0 : item.getQuantity();
                }
            }
        }
        return PharmacyPerformanceSalesSnapshotDTO.builder()
                .revenue(revenue)
                .saleCount(sales.size())
                .unitsSold(units)
                .build();
    }

    private PharmacyPerformanceInventorySnapshotDTO computeInventorySnapshot(List<PharmacyInventory> inventory,
                                                                             LocalDateTime from) {
        int total = inventory.size();
        int out = 0;
        int low = 0;
        int in = 0;
        int updated = 0;
        for (PharmacyInventory item : inventory) {
            int qty = item.getQuantity() == null ? 0 : item.getQuantity();
            boolean oos = item.isOutOfStock() || qty <= 0;
            if (oos) {
                out++;
            } else if (qty <= thresholdFor(item)) {
                low++;
                in++;
            } else {
                in++;
            }
            if (item.getUpdatedAt() != null && !item.getUpdatedAt().isBefore(from)) {
                updated++;
            }
        }
        return PharmacyPerformanceInventorySnapshotDTO.builder()
                .totalItems(total)
                .inStock(in)
                .lowStock(low)
                .outOfStock(out)
                .updatedInPeriod(updated)
                .build();
    }

    private HealthResult computeHealth(PharmacyPerformanceWindowDTO window,
                                       PharmacyPerformanceInventorySnapshotDTO inventory,
                                       PharmacyPerformanceSalesSnapshotDTO sales,
                                       List<PharmacyPerformanceDemandItemDTO> topDemand,
                                       boolean emptyInventory) {
        int totalRes = window.getTotal() == null ? 0 : window.getTotal();
        int saleCount = sales.getSaleCount() == null ? 0 : sales.getSaleCount();
        if (emptyInventory && totalRes == 0 && saleCount == 0) {
            List<PharmacyHealthFactorDTO> factors = List.of(
                    factor("Fulfillment", 50, 30, "Insufficient activity"),
                    factor("Responsiveness", 50, 20, "Insufficient activity"),
                    factor("Stock availability", 50, 25, "Insufficient activity"),
                    factor("Demand coverage", 50, 15, "Insufficient activity"),
                    factor("Sales activity", 50, 10, "Insufficient activity")
            );
            return new HealthResult(50, "C", factors);
        }

        double fulfillmentRate = window.getFulfillmentRate() == null ? 0 : window.getFulfillmentRate();
        int approved = window.getApproved() == null ? 0 : window.getApproved();
        int fulfillmentScore = approved == 0 ? 50 : (int) Math.round(Math.min(100, fulfillmentRate));

        double avgMins = window.getAvgResponseMinutes() == null ? 0 : window.getAvgResponseMinutes();
        int responseScore;
        String responseNote;
        if (approved == 0 && totalRes == 0) {
            responseScore = 50;
            responseNote = "No reservations in period";
        } else if (avgMins <= 0 && approved == 0) {
            responseScore = 50;
            responseNote = "No responses yet";
        } else if (avgMins <= 15) {
            responseScore = 100;
            responseNote = Math.round(avgMins) + " min avg";
        } else if (avgMins >= 120) {
            responseScore = 0;
            responseNote = Math.round(avgMins) + " min avg";
        } else {
            responseScore = (int) Math.round(100.0 * (120.0 - avgMins) / (120.0 - 15.0));
            responseNote = Math.round(avgMins) + " min avg";
        }

        int totalItems = inventory.getTotalItems() == null ? 0 : inventory.getTotalItems();
        int out = inventory.getOutOfStock() == null ? 0 : inventory.getOutOfStock();
        int low = inventory.getLowStock() == null ? 0 : inventory.getLowStock();
        int stockScore;
        String stockNote;
        if (totalItems == 0) {
            stockScore = 50;
            stockNote = "No inventory";
        } else {
            double availableShare = (totalItems - out) * 100.0 / totalItems;
            double lowPenalty = (low * 100.0 / totalItems) * 0.25;
            stockScore = (int) Math.round(Math.max(0, Math.min(100, availableShare - lowPenalty)));
            stockNote = out + " OOS, " + low + " low";
        }

        int demandScore;
        String demandNote;
        if (topDemand.isEmpty()) {
            demandScore = 50;
            demandNote = "No demand signals";
        } else {
            long covered = topDemand.stream()
                    .filter(d -> !Boolean.TRUE.equals(d.getOutOfStock()))
                    .count();
            demandScore = (int) Math.round(covered * 100.0 / topDemand.size());
            demandNote = covered + "/" + topDemand.size() + " top searched in stock";
        }

        int salesScore;
        String salesNote;
        if (saleCount == 0 && totalRes == 0) {
            salesScore = 50;
            salesNote = "No sales or reservations";
        } else if (saleCount > 0) {
            salesScore = Math.min(100, 60 + saleCount * 5);
            salesNote = saleCount + " sale(s)";
        } else {
            salesScore = 40;
            salesNote = "Reservations but no sales yet";
        }

        List<PharmacyHealthFactorDTO> factors = List.of(
                factor("Fulfillment", fulfillmentScore, 30, oneDecimal(fulfillmentRate) + "% fulfillment"),
                factor("Responsiveness", responseScore, 20, responseNote),
                factor("Stock availability", stockScore, 25, stockNote),
                factor("Demand coverage", demandScore, 15, demandNote),
                factor("Sales activity", salesScore, 10, salesNote)
        );

        double weighted = (fulfillmentScore * 30
                + responseScore * 20
                + stockScore * 25
                + demandScore * 15
                + salesScore * 10) / 100.0;
        int score = (int) Math.round(Math.max(0, Math.min(100, weighted)));
        return new HealthResult(score, gradeFor(score), factors);
    }

    private static PharmacyHealthFactorDTO factor(String name, int score, int weight, String note) {
        return PharmacyHealthFactorDTO.builder()
                .name(name)
                .score(score)
                .weight(weight)
                .note(note)
                .build();
    }

    private static String gradeFor(int score) {
        if (score >= 85) return "A";
        if (score >= 70) return "B";
        if (score >= 55) return "C";
        if (score >= 40) return "D";
        return "F";
    }

    private Pharmacy resolvePharmacy(Long pharmacyTelegramId) {
        if (pharmacyTelegramId == null || pharmacyTelegramId <= 0) {
            throw new RuntimeException("pharmacyTelegramId is required");
        }
        return pharmacyRepository.findByTelegramId(pharmacyTelegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));
    }

    private String normalizePeriod(String period) {
        if (period == null || period.isBlank()) {
            return "weekly";
        }
        return switch (period.trim().toLowerCase(Locale.ROOT)) {
            case "daily", "today", "day" -> "daily";
            case "monthly", "month" -> "monthly";
            default -> "weekly";
        };
    }

    private LocalDateTime periodStart(String period, LocalDateTime now) {
        return switch (period) {
            case "daily" -> now.toLocalDate().atStartOfDay();
            case "monthly" -> now.minusDays(30);
            default -> now.minusDays(7);
        };
    }

    private WindowStats computeWindowStats(List<MedicineReservation> reservations, LocalDateTime start, LocalDateTime end) {
        List<MedicineReservation> inWindow = reservations.stream()
                .filter(r -> r.getCreatedAt() != null)
                .filter(r -> !r.getCreatedAt().isBefore(start) && !r.getCreatedAt().isAfter(end))
                .toList();

        int total = inWindow.size();
        int approved = (int) inWindow.stream()
                .filter(r -> r.getStatus() == MedicineReservationStatus.APPROVED
                        || r.getStatus() == MedicineReservationStatus.READY_FOR_PICKUP
                        || r.getStatus() == MedicineReservationStatus.FULFILLED
                        || r.getApprovedAt() != null)
                .count();
        int fulfilled = (int) inWindow.stream()
                .filter(r -> r.getStatus() == MedicineReservationStatus.FULFILLED || r.getFulfilledAt() != null)
                .count();
        int rejected = (int) inWindow.stream()
                .filter(r -> r.getStatus() == MedicineReservationStatus.REJECTED)
                .count();
        int expired = (int) inWindow.stream()
                .filter(r -> r.getStatus() == MedicineReservationStatus.EXPIRED)
                .count();
        int cancelled = (int) inWindow.stream()
                .filter(r -> r.getStatus() == MedicineReservationStatus.CANCELLED)
                .count();

        List<Long> responseMins = inWindow.stream()
                .map(this::responseMinutes)
                .filter(v -> v != null && v >= 0)
                .toList();

        double avgResponse = responseMins.isEmpty()
                ? 0.0
                : responseMins.stream().mapToLong(Long::longValue).average().orElse(0.0);

        double approvalRate = total == 0 ? 0.0 : (approved * 100.0) / total;
        double fulfillmentRate = approved == 0 ? 0.0 : (fulfilled * 100.0) / approved;

        return new WindowStats(total, approved, fulfilled, rejected, expired, cancelled, approvalRate, fulfillmentRate, avgResponse);
    }

    private PharmacyPerformanceWindowDTO toWindowDto(WindowStats stats) {
        return PharmacyPerformanceWindowDTO.builder()
                .total(stats.total())
                .approved(stats.approved())
                .fulfilled(stats.fulfilled())
                .rejected(stats.rejected())
                .expired(stats.expired())
                .cancelled(stats.cancelled())
                .approvalRate(round1(stats.approvalRate()))
                .fulfillmentRate(round1(stats.fulfillmentRate()))
                .avgResponseMinutes(round1(stats.avgResponseMinutes()))
                .build();
    }

    private Long responseMinutes(MedicineReservation reservation) {
        if (reservation == null || reservation.getCreatedAt() == null) {
            return null;
        }

        LocalDateTime actionAt = null;
        if (reservation.getApprovedAt() != null) {
            actionAt = reservation.getApprovedAt();
        } else if (reservation.getFulfilledAt() != null) {
            actionAt = reservation.getFulfilledAt();
        } else if (reservation.getStatus() == MedicineReservationStatus.EXPIRED && reservation.getExpiresAt() != null) {
            actionAt = reservation.getExpiresAt();
        }

        if (actionAt == null) {
            return null;
        }

        return Math.max(0, Duration.between(reservation.getCreatedAt(), actionAt).toMinutes());
    }

    private int thresholdFor(PharmacyInventory inventory) {
        if (inventory == null || inventory.getLowStockThreshold() == null || inventory.getLowStockThreshold() <= 0) {
            return 10;
        }
        return inventory.getLowStockThreshold();
    }

    private String formatWindow(PharmacyPerformanceWindowDTO stats) {
        if (stats == null) {
            return "• Requests: 0";
        }
        return "• Requests: " + stats.getTotal() + "\n"
                + "• Approved: " + stats.getApproved() + "\n"
                + "• Fulfilled: " + stats.getFulfilled() + "\n"
                + "• Rejected: " + stats.getRejected() + "\n"
                + "• Expired: " + stats.getExpired() + "\n"
                + "• Cancelled: " + stats.getCancelled() + "\n"
                + "• Approval Rate: " + oneDecimal(stats.getApprovalRate() == null ? 0 : stats.getApprovalRate()) + "%\n"
                + "• Fulfillment Rate: " + oneDecimal(stats.getFulfillmentRate() == null ? 0 : stats.getFulfillmentRate()) + "%\n"
                + "• Avg Response Time: "
                + Math.round(stats.getAvgResponseMinutes() == null ? 0 : stats.getAvgResponseMinutes()) + " min";
    }

    private String oneDecimal(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record WindowStats(int total,
                               int approved,
                               int fulfilled,
                               int rejected,
                               int expired,
                               int cancelled,
                               double approvalRate,
                               double fulfillmentRate,
                               double avgResponseMinutes) {
    }

    private record HealthResult(int score, String grade, List<PharmacyHealthFactorDTO> factors) {
    }
}
