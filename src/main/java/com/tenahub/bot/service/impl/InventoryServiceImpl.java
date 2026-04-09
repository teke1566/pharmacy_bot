package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.*;
import com.tenahub.bot.repository.*;
import com.tenahub.bot.service.InventoryService;
import com.tenahub.bot.service.MedicineAvailabilityAlertService;
import com.tenahub.bot.service.PharmacyService;
import com.tenahub.bot.util.MedicineSearchNormalizer;
import com.tenahub.bot.util.TelegramClient;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final PharmacyRepository pharmacyRepository;
    private final PharmacyInventoryRepository inventoryRepository;
    private final InventoryHistoryRepository historyRepository;
    private final LowStockThresholdRepository thresholdRepository;
    private final MedicineSearchLogRepository searchLogRepository;
    private final MedicineReservationRepository reservationRepository;
    private final TelegramClient telegramClient;
    private final MedicineAvailabilityAlertService medicineAvailabilityAlertService;
    private final PharmacyService pharmacyService;
    



   @Override
public void markOutOfStock(Long telegramId, String medicineName) {
    Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId)
            .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

    LocalDateTime now = LocalDateTime.now();
    String normalizedMedicine = MedicineSearchNormalizer.normalizeToEnglishCanonical(medicineName);

    PharmacyInventory item = inventoryRepository
        .findByPharmacyIdAndMedicineNameIgnoreCase(pharmacy.getId(), normalizedMedicine)
            .orElse(
                    PharmacyInventory.builder()
                            .pharmacyId(pharmacy.getId())
                .medicineName(normalizedMedicine)
                            .quantity(0)
                            .outOfStock(true)
                            .lowStockAlertSent(true)
                            .build()
            );

    Integer oldQty = item.getQuantity();
    item.setQuantity(0);
    item.setOutOfStock(true);
    item.setLowStockAlertSent(true);
    item.setUpdatedAt(now);

    inventoryRepository.save(item);

    pharmacy.setLastInventoryUpdate(now);
    pharmacyRepository.save(pharmacy);

    historyRepository.save(
            InventoryHistory.builder()
                    .pharmacyId(pharmacy.getId())
                    .medicineName(item.getMedicineName())
                    .oldQuantity(oldQty)
                    .newQuantity(0)
                    .eventType(InventoryEventType.MARKED_OUT)
                    .createdAt(now)
                    .build()
    );

    telegramClient.sendMessage(
            telegramId,
            "📉 <b>Marked Out of Stock</b>\n\n💊 " + telegramClient.displayMedicine(telegramId, item.getMedicineName())
    );
}
    @Override
    public List<PharmacyInventory> getInventory(Long telegramId) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        return inventoryRepository.findByPharmacyId(pharmacy.getId())
                .stream()
                .sorted(Comparator.comparing(PharmacyInventory::getMedicineName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

        @Override
        public List<PharmacyInventory> getInventoryByPharmacyId(Long pharmacyId) {
        pharmacyRepository.findById(pharmacyId)
            .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        return inventoryRepository.findByPharmacyId(pharmacyId)
            .stream()
            .sorted(Comparator.comparing(PharmacyInventory::getMedicineName, String.CASE_INSENSITIVE_ORDER))
            .toList();
        }

        @Override
        public PharmacyInventory setRequiresPrescription(Long telegramId, Long medicineId, boolean requiresPrescription) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId)
            .orElseThrow(() -> new RuntimeException("Pharmacy not found"));
        return setRequiresPrescriptionForPharmacy(pharmacy.getId(), medicineId, requiresPrescription);
        }

        @Override
        public PharmacyInventory setRequiresPrescriptionForPharmacy(Long pharmacyId, Long medicineId, boolean requiresPrescription) {
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
            .orElseThrow(() -> new RuntimeException("Pharmacy not found"));
        PharmacyInventory item = inventoryRepository.findById(medicineId)
            .orElseThrow(() -> new RuntimeException("Medicine not found with ID: " + medicineId));

        if (!pharmacyId.equals(item.getPharmacyId())) {
            throw new RuntimeException("Selected medicine does not belong to the pharmacy");
        }

        item.setRequiresPrescription(requiresPrescription);
        item.setUpdatedAt(LocalDateTime.now());
        inventoryRepository.save(item);

        pharmacy.setLastInventoryUpdate(LocalDateTime.now());
        pharmacyRepository.save(pharmacy);

        return item;
        }

    @Override
    public byte[] exportInventoryCsv(Long telegramId) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        List<PharmacyInventory> inventory = inventoryRepository.findByPharmacyId(pharmacy.getId());

        StringBuilder csv = new StringBuilder();
        csv.append("medicine_name,quantity,status\n");

        for (PharmacyInventory item : inventory) {
            String status;
            if (item.isOutOfStock() || item.getQuantity() == null || item.getQuantity() <= 0) {
                status = "OUT_OF_STOCK";
            } else if (item.getQuantity() <= getThreshold(pharmacy.getId(), item.getMedicineName())) {
                status = "LOW_STOCK";
            } else {
                status = "IN_STOCK";
            }

            csv.append("\"").append(item.getMedicineName()).append("\",")
                    .append(item.getQuantity() == null ? 0 : item.getQuantity()).append(",")
                    .append(status).append("\n");
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void importInventoryCsv(Long telegramId, String csvContent) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        LocalDateTime now = LocalDateTime.now();

        String[] lines = csvContent.split("\\r?\\n");

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isBlank()) continue;

            String[] parts = line.split(",");
            if (parts.length < 2) continue;

            String medicine = MedicineSearchNormalizer.normalizeToEnglishCanonical(parts[0].replace("\"", "").trim());
            Integer qty = Integer.parseInt(parts[1].trim());

            PharmacyInventory item = inventoryRepository
                    .findByPharmacyIdAndMedicineNameIgnoreCase(pharmacy.getId(), medicine)
                    .orElse(
                            PharmacyInventory.builder()
                                    .pharmacyId(pharmacy.getId())
                                    .medicineName(medicine)
                                    .build()
                    );

            Integer oldQty = item.getQuantity();

            item.setQuantity(qty);
            item.setOutOfStock(qty <= 0);
            item.setUpdatedAt(now);
            inventoryRepository.save(item);

            historyRepository.save(
                    InventoryHistory.builder()
                            .pharmacyId(pharmacy.getId())
                            .medicineName(medicine)
                            .oldQuantity(oldQty)
                            .newQuantity(qty)
                            .eventType(InventoryEventType.CSV_IMPORTED)
                            .createdAt(now)
                            .build()
            );
        }

        pharmacy.setLastInventoryUpdate(now);
        pharmacyRepository.save(pharmacy);
    }

    @Override
    public String buildSummary(Long telegramId, String period) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start;

        switch (period.toLowerCase()) {
            case "daily":
                start = LocalDate.now().atStartOfDay();
                break;
            case "weekly":
                start = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
                break;
            case "monthly":
                start = LocalDate.now().withDayOfMonth(1).atStartOfDay();
                break;
            case "yearly":
                start = LocalDate.now().withDayOfYear(1).atStartOfDay();
                break;
            default:
                throw new RuntimeException("Invalid summary period");
        }

        List<InventoryHistory> history =
                        historyRepository.findByPharmacyIdAndCreatedAtBetween(pharmacy.getId(), start, now);

        List<PharmacyInventory> inventory =
                inventoryRepository.findByPharmacyId(pharmacy.getId());

        long inStock = inventory.stream()
                .filter(i -> !i.isOutOfStock() && i.getQuantity() != null && i.getQuantity() > getThreshold(pharmacy.getId(), i.getMedicineName()))
                .count();

        long lowStock = inventory.stream()
                .filter(i -> !i.isOutOfStock() && i.getQuantity() != null && i.getQuantity() > 0
                        && i.getQuantity() <= getThreshold(pharmacy.getId(), i.getMedicineName()))
                .count();

        long outOfStock = inventory.stream()
                .filter(i -> i.isOutOfStock() || i.getQuantity() == null || i.getQuantity() <= 0)
                .count();

        long updated = history.stream().filter(h -> h.getEventType() == InventoryEventType.STOCK_UPDATED).count();
        long imported = history.stream().filter(h -> h.getEventType() == InventoryEventType.CSV_IMPORTED).count();
        long markedOut = history.stream().filter(h -> h.getEventType() == InventoryEventType.MARKED_OUT).count();

        return "📊 <b>" + capitalize(period) + " Inventory Summary</b>\n\n"
                + "🏥 " + pharmacy.getName() + "\n"
                + "✅ In stock: " + inStock + "\n"
                + "⚠️ Low stock: " + lowStock + "\n"
                + "❌ Out of stock: " + outOfStock + "\n\n"
                + "🔄 Stock updates: " + updated + "\n"
                + "📥 CSV imports: " + imported + "\n"
                + "📉 Marked out: " + markedOut + "\n"
                + "🕒 Since: " + start.toLocalDate();
    }

    @Override
    public String buildLowStockAlert(Long telegramId) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        List<PharmacyInventory> inventory =
                inventoryRepository.findByPharmacyId(pharmacy.getId());

        StringBuilder low = new StringBuilder();
        StringBuilder out = new StringBuilder();

        for (PharmacyInventory item : inventory) {
            int qty = item.getQuantity() == null ? 0 : item.getQuantity();
            int threshold = getThreshold(pharmacy.getId(), item.getMedicineName());

            if (item.isOutOfStock() || qty <= 0) {
                out.append("💊 ").append(telegramClient.displayMedicine(telegramId, item.getMedicineName())).append("\n");
            } else if (qty <= threshold) {
                low.append("💊 ").append(telegramClient.displayMedicine(telegramId, item.getMedicineName())).append(" — ").append(qty).append(" left\n");
            }
        }

        if (low.isEmpty() && out.isEmpty()) {
            return "✅ <b>Low Stock Alert</b>\n\nNo low-stock or out-of-stock medicines right now.";
        }

        StringBuilder sb = new StringBuilder("⚠️ <b>Low Stock Alert</b>\n\n");

        if (!low.isEmpty()) {
            sb.append("⚠️ <b>Low Stock</b>\n").append(low).append("\n");
        }

        if (!out.isEmpty()) {
            sb.append("❌ <b>Out of Stock</b>\n").append(out);
        }

        return sb.toString().trim();
    }

   @Override
public void setLowStockThreshold(Long telegramId, String medicineName, Integer threshold) {
    Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId)
            .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

    String normalizedMedicine = MedicineSearchNormalizer.normalizeToEnglishCanonical(medicineName);

    LowStockThreshold config = thresholdRepository
        .findByPharmacyIdAndMedicineNameIgnoreCase(pharmacy.getId(), normalizedMedicine)
            .orElse(
                    LowStockThreshold.builder()
                            .pharmacyId(pharmacy.getId())
                .medicineName(normalizedMedicine)
                            .build()
            );

    config.setThreshold(threshold);
    thresholdRepository.save(config);

    // also check current inventory and reset alert flag if stock is now safe
    inventoryRepository.findByPharmacyIdAndMedicineNameIgnoreCase(pharmacy.getId(), normalizedMedicine)
            .ifPresent(item -> {
                Integer qty = item.getQuantity() == null ? 0 : item.getQuantity();

                if (qty > threshold) {
                    item.setLowStockAlertSent(false);
                } else if (qty > 0 && qty <= threshold && !Boolean.TRUE.equals(item.getLowStockAlertSent())) {
                    telegramClient.sendLowStockAlert(
                            telegramId,
                            item.getMedicineName(),
                            qty,
                            threshold
                    );
                    item.setLowStockAlertSent(true);
                }

                inventoryRepository.save(item);
            });
}

    @Override
    public String getDemandInsights(Long telegramId) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        LocalDateTime start = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime end = LocalDateTime.now();

        List<MedicineSearchLog> logs = searchLogRepository.findBySearchedAtBetween(start, end);

        List<PharmacyInventory> inventory = inventoryRepository.findByPharmacyId(pharmacy.getId());

        StringBuilder sb = new StringBuilder("📈 <b>Demand Insights</b>\n\n");

        for (PharmacyInventory item : inventory) {
            long searchCount = logs.stream()
                    .filter(l -> l.getMedicineName().equalsIgnoreCase(item.getMedicineName()))
                    .count();

            if (searchCount > 0) {
                sb.append("💊 ").append(item.getMedicineName())
                        .append(" — searched ").append(searchCount).append(" times this month");

                if (item.isOutOfStock() || item.getQuantity() == null || item.getQuantity() <= 0) {
                    sb.append(" ❌ out of stock");
                } else if (item.getQuantity() <= getThreshold(pharmacy.getId(), item.getMedicineName())) {
                    sb.append(" ⚠️ low stock");
                }

                sb.append("\n");
            }
        }

        if (sb.toString().equals("📈 <b>Demand Insights</b>\n\n")) {
            return "📈 <b>Demand Insights</b>\n\nNo demand data yet.";
        }

        return sb.toString().trim();
    }

    @Override
    public String getAdvancedRestockSuggestions(Long telegramId) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekStart = now.minusDays(7);

        List<PharmacyInventory> inventory = inventoryRepository.findByPharmacyId(pharmacy.getId());
        if (inventory.isEmpty()) {
            return "💡 <b>Restock Suggestions</b>\n\nNo inventory items found yet. Add medicines first to get recommendations.";
        }

        Set<String> pharmacyMedicines = inventory.stream()
                .map(PharmacyInventory::getMedicineName)
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.trim().toLowerCase())
                .collect(Collectors.toCollection(HashSet::new));

        Map<String, Integer> weeklySearchCount = new HashMap<>();
        for (MedicineSearchLog log : searchLogRepository.findBySearchedAtBetween(weekStart, now)) {
            if (log.getMedicineName() == null || log.getMedicineName().isBlank()) {
                continue;
            }
            String medicine = log.getMedicineName().trim().toLowerCase();
            if (!pharmacyMedicines.contains(medicine)) {
                continue;
            }
            weeklySearchCount.merge(medicine, 1, Integer::sum);
        }

        Map<String, Integer> weeklyFailureCount = new HashMap<>();
        for (MedicineReservation reservation : reservationRepository.findByPharmacyIdOrderByCreatedAtDesc(pharmacy.getId())) {
            if (reservation.getCreatedAt() == null || reservation.getCreatedAt().isBefore(weekStart)) {
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

            String medicine = reservation.getMedicineName().trim().toLowerCase();
            weeklyFailureCount.merge(medicine, 1, Integer::sum);
        }

        Map<String, Integer> weeklyStockoutCount = new HashMap<>();
        for (InventoryHistory event : historyRepository.findByPharmacyIdAndCreatedAtBetween(pharmacy.getId(), weekStart, now)) {
            if (event.getMedicineName() == null || event.getMedicineName().isBlank()) {
                continue;
            }
            if (event.getEventType() != InventoryEventType.MARKED_OUT) {
                continue;
            }

            String medicine = event.getMedicineName().trim().toLowerCase();
            weeklyStockoutCount.merge(medicine, 1, Integer::sum);
        }

        class Suggestion {
            String medicine;
            int demandScore;
            int searches;
            int failures;
            int stockouts;
            boolean outOfStock;
            boolean lowStock;
            int quantity;
        }

        List<Suggestion> ranked = inventory.stream()
                .map(item -> {
                    String medicine = item.getMedicineName() == null ? "" : item.getMedicineName().trim().toLowerCase();
                    int searches = weeklySearchCount.getOrDefault(medicine, 0);
                    int failures = weeklyFailureCount.getOrDefault(medicine, 0);
                    int stockouts = weeklyStockoutCount.getOrDefault(medicine, 0);
                    int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
                    int threshold = getThreshold(pharmacy.getId(), medicine);
                    boolean outOfStock = item.isOutOfStock() || quantity <= 0;
                    boolean lowStock = !outOfStock && quantity <= threshold;

                    // Weighted weekly demand score across the four signals.
                    int score = (searches * 2) + (failures * 3) + (stockouts * 2);
                    if (outOfStock) {
                        score += 6;
                    } else if (lowStock) {
                        score += 3;
                    }

                    Suggestion suggestion = new Suggestion();
                    suggestion.medicine = medicine;
                    suggestion.demandScore = score;
                    suggestion.searches = searches;
                    suggestion.failures = failures;
                    suggestion.stockouts = stockouts;
                    suggestion.outOfStock = outOfStock;
                    suggestion.lowStock = lowStock;
                    suggestion.quantity = quantity;
                    return suggestion;
                })
                .filter(s -> s.demandScore > 0)
                .sorted((a, b) -> Integer.compare(b.demandScore, a.demandScore))
                .limit(5)
                .collect(Collectors.toList());

        if (ranked.isEmpty()) {
            return "💡 <b>Restock Suggestions</b>\n\nNo strong restock signals this week. Stock levels look stable.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("💡 <b>Restock Suggestions (Advanced)</b>\n\n");
        sb.append("Signals used: demand insights, recent searches, reservation failures, stockout frequency.\n\n");

        for (Suggestion s : ranked) {
            String stockState = s.outOfStock
                    ? "❌ out of stock"
                    : (s.lowStock ? "⚠️ low stock" : "✅ in stock");

            sb.append("⚠️ You may want to restock ")
                    .append(s.medicine)
                    .append(". Searched ")
                    .append(s.searches)
                    .append(" times this week")
                    .append(s.failures > 0 ? ", reservation failures " + s.failures : "")
                    .append(s.stockouts > 0 ? ", stockouts " + s.stockouts : "")
                    .append(".\n")
                    .append("   • status: ")
                    .append(stockState)
                    .append(" | qty: ")
                    .append(s.quantity)
                    .append(" | score: ")
                    .append(s.demandScore)
                    .append("\n\n");
        }

        return sb.toString().trim();
    }

    private int getThreshold(Long pharmacyId, String medicineName) {
        return thresholdRepository.findByPharmacyIdAndMedicineNameIgnoreCase(pharmacyId, medicineName)
                .map(LowStockThreshold::getThreshold)
                .orElse(10);
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) return value;
        return value.substring(0, 1).toUpperCase() + value.substring(1).toLowerCase();
    }
@Override
public void upsertStock(Long telegramId, String medicineName, Integer quantity, BigDecimal price) {
    Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId)
            .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

    LocalDateTime now = LocalDateTime.now();

    String normalizedMedicine = medicineName.toLowerCase().trim();
    normalizedMedicine = MedicineSearchNormalizer.normalizeToEnglishCanonical(normalizedMedicine);

    PharmacyInventory item = inventoryRepository
            .findByPharmacyIdAndMedicineNameIgnoreCase(pharmacy.getId(), normalizedMedicine)
            .orElse(
                    PharmacyInventory.builder()
                            .pharmacyId(pharmacy.getId())
                            .medicineName(normalizedMedicine)
                            .quantity(0)
                            .price(price)
                            .outOfStock(false)
                            .lowStockAlertSent(false)
                            .build()
            );

    Integer oldQty = item.getQuantity();

    item.setQuantity(quantity);
    item.setPrice(price);
    item.setOutOfStock(quantity == null || quantity <= 0);
    item.setUpdatedAt(now);

    int threshold = getThreshold(pharmacy.getId(), item.getMedicineName());
    boolean alertSent = Boolean.TRUE.equals(item.getLowStockAlertSent());

    if (quantity != null && quantity > threshold) {
        item.setLowStockAlertSent(false);
    }

    if (quantity != null && quantity > 0 && quantity <= threshold && !alertSent) {
        telegramClient.sendLowStockAlert(
                telegramId,
                item.getMedicineName(),
                quantity,
                threshold
        );
        item.setLowStockAlertSent(true);
    }

    if (quantity == null || quantity <= 0) {
        item.setLowStockAlertSent(true);
    }

    inventoryRepository.save(item);

    pharmacy.setLastInventoryUpdate(now);
    pharmacyRepository.save(pharmacy);

    historyRepository.save(
            InventoryHistory.builder()
                    .pharmacyId(pharmacy.getId())
                    .medicineName(item.getMedicineName())
                    .oldQuantity(oldQty)
                    .newQuantity(quantity)
                    .eventType(InventoryEventType.STOCK_UPDATED)
                    .createdAt(now)
                    .build()
    );

    // notify alert users when stock is positive
    if (quantity != null && quantity > 0) {
        medicineAvailabilityAlertService.notifyUsersIfAvailable(
                normalizedMedicine,
                pharmacy,
                quantity
        );
    }
}

@Override
public BulkInventoryUpdateResult bulkUpsertFromText(Long telegramId, String bulkText) {
    if (bulkText == null || bulkText.isBlank()) {
        return new BulkInventoryUpdateResult(0, 0, 0, List.of("No lines received."));
    }

    String[] lines = bulkText.split("\\r?\\n");
    int total = 0;
    int updated = 0;
    List<String> errors = new java.util.ArrayList<>();

    for (int i = 0; i < lines.length; i++) {
        String line = lines[i] == null ? "" : lines[i].trim();
        if (line.isBlank()) {
            continue;
        }

        total++;
        int displayLine = i + 1;

        String[] parts = line.split("\\|");
        if (parts.length != 3 && parts.length != 4) {
            errors.add("Line " + displayLine + ": invalid format. Use medicine | quantity | price [| threshold]");
            continue;
        }

        String medicineRaw = parts[0].trim();
        if (medicineRaw.isBlank()) {
            errors.add("Line " + displayLine + ": medicine name is empty");
            continue;
        }

        Integer quantity;
        try {
            quantity = Integer.parseInt(parts[1].trim());
        } catch (Exception e) {
            errors.add("Line " + displayLine + ": invalid quantity");
            continue;
        }

        if (quantity < 0) {
            errors.add("Line " + displayLine + ": quantity must be >= 0");
            continue;
        }

        BigDecimal price;
        try {
            price = new BigDecimal(parts[2].trim());
        } catch (Exception e) {
            errors.add("Line " + displayLine + ": invalid price");
            continue;
        }

        if (price.compareTo(BigDecimal.ZERO) < 0) {
            errors.add("Line " + displayLine + ": price must be >= 0");
            continue;
        }

        Integer threshold = null;
        if (parts.length == 4 && !parts[3].trim().isBlank()) {
            try {
                threshold = Integer.parseInt(parts[3].trim());
            } catch (Exception e) {
                errors.add("Line " + displayLine + ": invalid threshold");
                continue;
            }

            if (threshold < 0) {
                errors.add("Line " + displayLine + ": threshold must be >= 0");
                continue;
            }
        }

        String normalizedMedicine = MedicineSearchNormalizer.normalizeToEnglishCanonical(medicineRaw);
        if (normalizedMedicine == null || normalizedMedicine.isBlank()) {
            errors.add("Line " + displayLine + ": unknown medicine name");
            continue;
        }

        if (!pharmacyService.medicineExistsInCatalog(normalizedMedicine)) {
            errors.add("Line " + displayLine + ": unknown medicine name");
            continue;
        }

        try {
            upsertStock(telegramId, normalizedMedicine, quantity, price);
            if (threshold != null) {
                setLowStockThreshold(telegramId, normalizedMedicine, threshold);
            }
            updated++;
        } catch (Exception e) {
            errors.add("Line " + displayLine + ": " + e.getMessage());
        }
    }

    int failed = Math.max(0, total - updated);
    return new BulkInventoryUpdateResult(total, updated, failed, List.copyOf(errors));
}
 
@Override
public void updatePrice(Long pharmacyChatId, String medicineName, BigDecimal price) {
    if (medicineName == null || medicineName.isBlank()) {
        throw new RuntimeException("Medicine name is required.");
    }

    if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
        throw new RuntimeException("Price must be 0 or greater.");
    }

        String normalizedMedicine = MedicineSearchNormalizer.normalizeToEnglishCanonical(medicineName);

    Pharmacy pharmacy = pharmacyRepository.findByTelegramId(pharmacyChatId)
            .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

    PharmacyInventory inventory = inventoryRepository
            .findByPharmacyIdAndMedicineNameIgnoreCase(pharmacy.getId(), normalizedMedicine)
            .orElseThrow(() -> new RuntimeException("Medicine not found in inventory"));

    inventory.setPrice(price);

    if (inventory.getCurrency() == null || inventory.getCurrency().isBlank()) {
        inventory.setCurrency("ETB");
    }

    inventoryRepository.save(inventory);
}    

@Override
public void initializeInventoryFromMedicines(Long pharmacyId, String medicines) {
    if (medicines == null || medicines.isBlank()) {
        return;
    }

    String normalizedMedicines = MedicineSearchNormalizer.normalizeCommaSeparatedMedicines(medicines);

    List<String> medicineList = Arrays.stream(normalizedMedicines.split(","))
            .map(String::trim)
            .filter(m -> !m.isBlank())
            .map(String::toLowerCase)
            .distinct()
            .toList();

    for (String medicine : medicineList) {
        boolean exists = inventoryRepository.existsByPharmacyIdAndMedicineNameIgnoreCase(pharmacyId, medicine);

        if (!exists) {
            PharmacyInventory item = PharmacyInventory.builder()
                    .pharmacyId(pharmacyId)
                    .medicineName(medicine)
                    .quantity(0)
                    .outOfStock(true)
                    .lowStockThreshold(5)
                    .lowStockAlertSent(false)
                    .price(null)
                    .currency("ETB")
                    .build();

            inventoryRepository.save(item);
        }
    }
}
}