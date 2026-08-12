package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.MedicineReservationStatus;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.repository.MedicineReservationRepository;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.PharmacyPerformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PharmacyPerformanceServiceImpl implements PharmacyPerformanceService {

    private final PharmacyRepository pharmacyRepository;
    private final MedicineReservationRepository reservationRepository;
    private final PharmacyInventoryRepository inventoryRepository;

    @Override
    public String buildPerformanceCard(Long pharmacyTelegramId) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(pharmacyTelegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        List<MedicineReservation> reservations = reservationRepository.findByPharmacyIdOrderByCreatedAtDesc(pharmacy.getId());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime sevenDaysStart = now.minusDays(7);

        WindowStats today = computeWindowStats(reservations, todayStart, now);
        WindowStats week = computeWindowStats(reservations, sevenDaysStart, now);

        List<PharmacyInventory> inventory = inventoryRepository.findByPharmacyId(pharmacy.getId());
        long outOfStock = inventory.stream()
                .filter(i -> i.isOutOfStock() || i.getQuantity() == null || i.getQuantity() <= 0)
                .count();
        long lowStock = inventory.stream()
                .filter(i -> !i.isOutOfStock() && i.getQuantity() != null && i.getQuantity() > 0)
                .filter(i -> i.getQuantity() <= thresholdFor(i))
                .count();
        long updatedToday = inventory.stream()
                .filter(i -> i.getUpdatedAt() != null && !i.getUpdatedAt().isBefore(todayStart))
                .count();

        return "📊 <b>Pharmacy Performance</b>\n\n"
                + "<b>Today</b>\n"
                + formatWindow(today)
                + "\n\n"
                + "<b>Last 7 Days</b>\n"
                + formatWindow(week)
                + "\n\n"
                + "📦 Low Stock Items: " + lowStock + "\n"
                + "❌ Out of Stock Items: " + outOfStock + "\n"
                + "🔄 Medicines Updated Today: " + updatedToday;
    }

    private WindowStats computeWindowStats(List<MedicineReservation> reservations, LocalDateTime start, LocalDateTime end) {
        List<MedicineReservation> inWindow = reservations.stream()
                .filter(r -> r.getCreatedAt() != null)
                .filter(r -> !r.getCreatedAt().isBefore(start) && !r.getCreatedAt().isAfter(end))
                .toList();

        int total = inWindow.size();
        int approved = (int) inWindow.stream()
                .filter(r -> r.getStatus() == MedicineReservationStatus.APPROVED
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

    private String formatWindow(WindowStats stats) {
        return "• Requests: " + stats.total + "\n"
                + "• Approved: " + stats.approved + "\n"
                + "• Fulfilled: " + stats.fulfilled + "\n"
                + "• Rejected: " + stats.rejected + "\n"
                + "• Expired: " + stats.expired + "\n"
                + "• Cancelled: " + stats.cancelled + "\n"
                + "• Approval Rate: " + oneDecimal(stats.approvalRate) + "%\n"
                + "• Fulfillment Rate: " + oneDecimal(stats.fulfillmentRate) + "%\n"
                + "• Avg Response Time: " + Math.round(stats.avgResponseMinutes) + " min";
    }

    private String oneDecimal(double value) {
        return String.format(java.util.Locale.US, "%.1f", value);
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
}
