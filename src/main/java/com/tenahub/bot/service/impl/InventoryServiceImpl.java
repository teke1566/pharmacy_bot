package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.InventoryCsvImportResultDTO;
import com.tenahub.bot.dto.MedicineBatchDTO;
import com.tenahub.bot.dto.PharmacyMiniAppAddStockRequest;
import com.tenahub.bot.dto.PharmacyMiniAppInventoryItemDTO;
import com.tenahub.bot.dto.PharmacyMiniAppInventoryPatchRequest;
import com.tenahub.bot.dto.RestockSuggestionDTO;
import com.tenahub.bot.dto.StockMovementDTO;
import com.tenahub.bot.entity.*;
import com.tenahub.bot.repository.*;
import com.tenahub.bot.service.InventoryService;
import com.tenahub.bot.service.MedicineAvailabilityAlertService;
import com.tenahub.bot.service.MedicineCatalogService;
import com.tenahub.bot.service.MedicineLotService;
import com.tenahub.bot.service.PharmacyService;
import com.tenahub.bot.util.DemandLabeler;
import com.tenahub.bot.util.MedicineSearchNormalizer;
import com.tenahub.bot.util.PricingMath;
import com.tenahub.bot.util.TelegramClient;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
    private final MedicineCatalogService medicineCatalogService;
    private final MedicineLotService medicineLotService;
    private final RestockIgnoreRepository restockIgnoreRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    



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
    medicineLotService.zeroAllLots(item, telegramId, "Marked out of stock", StockMovementType.ADJUSTMENT);
    item.setLowStockAlertSent(true);
    item.setUpdatedAt(now);

    attachCatalog(item);
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
        attachCatalog(item);
        inventoryRepository.save(item);
        medicineCatalogService.refreshPrescriptionRequired(item.getCatalogMedicineId());

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
        csv.append("medicine_name,quantity,price,purchase_cost,requires_prescription,batch_number,expiry_date,strength,dosage_form,threshold,status,reason\n");

        for (PharmacyInventory item : inventory) {
            if (item.isArchived()) {
                continue;
            }
            int qty = item.getQuantity() == null ? 0 : item.getQuantity();
            String status;
            if (isExpired(item) || item.isOutOfStock() || qty <= 0) {
                status = isExpired(item) ? "EXPIRED" : "OUT_OF_STOCK";
            } else if (qty <= getThreshold(item)) {
                status = "LOW_STOCK";
            } else {
                status = "IN_STOCK";
            }

            csv.append(csvCell(item.getMedicineName())).append(",")
                    .append(qty).append(",")
                    .append(item.getPrice() == null ? "" : item.getPrice().toPlainString()).append(",")
                    .append(item.getPurchaseCost() == null ? "" : item.getPurchaseCost().toPlainString()).append(",")
                    .append(item.isRequiresPrescription() ? "true" : "false").append(",")
                    .append(csvCell(item.getBatchNumber())).append(",")
                    .append(item.getExpiryDate() == null ? "" : item.getExpiryDate()).append(",")
                    .append(csvCell(item.getStrength())).append(",")
                    .append(csvCell(item.getDosageForm())).append(",")
                    .append(getThreshold(item)).append(",")
                    .append(status).append(",")
                    .append("")
                    .append("\n");
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public InventoryCsvImportResultDTO importInventoryCsv(Long telegramId, String csvContent) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        InventoryCsvImportResultDTO result = InventoryCsvImportResultDTO.builder()
                .success(false)
                .errors(new ArrayList<>())
                .warnings(new ArrayList<>())
                .build();

        String[] lines = csvContent == null ? new String[0] : csvContent.split("\\r?\\n");
        if (lines.length == 0) {
            result.setMessage("CSV is empty");
            result.getErrors().add(InventoryCsvImportResultDTO.RowIssue.builder()
                    .line(0).field("file").message("CSV is empty").build());
            return result;
        }

        int start = 0;
        Map<String, Integer> headerIndex = Map.of();
        if (looksLikeCsvHeader(lines[0])) {
            headerIndex = csvHeaderIndex(lines[0]);
            start = 1;
        }

        record PendingRow(int line, String medicine, Integer qty, BigDecimal price, BigDecimal purchaseCost,
                          String reason, Boolean requiresPrescription, String batch, LocalDate expiry,
                          String strength, String form, Integer threshold, PharmacyInventory existing) {}

        List<PendingRow> pending = new ArrayList<>();
        Set<String> seenMedicines = new HashSet<>();

        for (int i = start; i < lines.length; i++) {
            int lineNo = i + 1;
            String line = lines[i].trim();
            if (line.isBlank()) {
                continue;
            }
            List<String> parts = splitCsvLine(line);
            if (parts.size() < 2) {
                result.getErrors().add(InventoryCsvImportResultDTO.RowIssue.builder()
                        .line(lineNo).field("row").message("Row must include medicine_name and quantity").build());
                continue;
            }

            String medicine = MedicineSearchNormalizer.normalizeToEnglishCanonical(
                    csvValue(parts, headerIndex, "medicine_name", "medicine", 0));
            if (medicine.isBlank()) {
                result.getErrors().add(InventoryCsvImportResultDTO.RowIssue.builder()
                        .line(lineNo).field("medicine_name").message("Medicine name is required").build());
                continue;
            }
            String medicineKey = medicine.toLowerCase(Locale.ROOT);
            if (!seenMedicines.add(medicineKey)) {
                result.getErrors().add(InventoryCsvImportResultDTO.RowIssue.builder()
                        .line(lineNo).medicineName(medicine).field("medicine_name")
                        .message("Duplicate medicine in CSV").build());
                continue;
            }

            Integer qty = null;
            try {
                qty = Integer.parseInt(csvValue(parts, headerIndex, "quantity", "qty", 1));
                if (qty < 0) {
                    throw new IllegalArgumentException("negative");
                }
            } catch (Exception e) {
                result.getErrors().add(InventoryCsvImportResultDTO.RowIssue.builder()
                        .line(lineNo).medicineName(medicine).field("quantity")
                        .message("Quantity must be a non-negative integer").build());
                continue;
            }

            BigDecimal price = null;
            String priceText = csvValue(parts, headerIndex, "price", "selling_price", 2);
            if (priceText != null && !priceText.isBlank()
                    && !priceText.equalsIgnoreCase("OUT_OF_STOCK")
                    && !priceText.equalsIgnoreCase("IN_STOCK")
                    && !priceText.equalsIgnoreCase("LOW_STOCK")
                    && !priceText.equalsIgnoreCase("EXPIRED")) {
                try {
                    price = PricingMath.money(new BigDecimal(priceText));
                    if (price.compareTo(BigDecimal.ZERO) < 0) {
                        throw new IllegalArgumentException("negative");
                    }
                } catch (Exception e) {
                    result.getErrors().add(InventoryCsvImportResultDTO.RowIssue.builder()
                            .line(lineNo).medicineName(medicine).field("price")
                            .message("Invalid selling price").build());
                    continue;
                }
            }

            BigDecimal purchaseCost = null;
            String costText = csvValue(parts, headerIndex, "purchase_cost", "cost", -1);
            if (costText != null && !costText.isBlank()) {
                try {
                    purchaseCost = PricingMath.money(new BigDecimal(costText));
                    if (purchaseCost.compareTo(BigDecimal.ZERO) < 0) {
                        throw new IllegalArgumentException("negative");
                    }
                } catch (Exception e) {
                    result.getErrors().add(InventoryCsvImportResultDTO.RowIssue.builder()
                            .line(lineNo).medicineName(medicine).field("purchase_cost")
                            .message("Invalid purchase cost").build());
                    continue;
                }
            }

            String reason = csvValue(parts, headerIndex, "reason", null, -1);
            PharmacyInventory existing = inventoryRepository
                    .findByPharmacyIdAndMedicineNameIgnoreCase(pharmacy.getId(), medicine)
                    .orElse(null);
            boolean priceChanging = price != null && existing != null && existing.getPrice() != null
                    && price.compareTo(existing.getPrice()) != 0;
            boolean priceCreating = price != null && (existing == null || existing.getPrice() == null);
            if ((priceChanging || priceCreating) && (reason == null || reason.isBlank())) {
                result.getErrors().add(InventoryCsvImportResultDTO.RowIssue.builder()
                        .line(lineNo).medicineName(medicine).field("reason")
                        .message("Reason is required when selling price is set or changed").build());
                continue;
            }
            if (price != null && purchaseCost != null && price.compareTo(purchaseCost) < 0) {
                result.getWarnings().add(InventoryCsvImportResultDTO.RowIssue.builder()
                        .line(lineNo).medicineName(medicine).field("price")
                        .message("Selling price is below purchase cost").build());
            }

            String effectiveDateText = csvValue(parts, headerIndex, "effective_date", null, -1);
            if (effectiveDateText != null && !effectiveDateText.isBlank()) {
                try {
                    LocalDate.parse(effectiveDateText.trim());
                } catch (Exception e) {
                    result.getErrors().add(InventoryCsvImportResultDTO.RowIssue.builder()
                            .line(lineNo).medicineName(medicine).field("effective_date")
                            .message("Invalid effective date (use YYYY-MM-DD)").build());
                    continue;
                }
            }

            Boolean requiresPrescription = null;
            String rx = csvValue(parts, headerIndex, "requires_prescription", "rx", 3);
            if (rx != null && !rx.isBlank()
                    && !rx.equalsIgnoreCase("OUT_OF_STOCK")
                    && !rx.equalsIgnoreCase("IN_STOCK")
                    && !rx.equalsIgnoreCase("LOW_STOCK")) {
                requiresPrescription = "true".equalsIgnoreCase(rx) || "1".equals(rx) || "yes".equalsIgnoreCase(rx);
            }

            String batch = blankToNull(csvValue(parts, headerIndex, "batch_number", "batch", 4));
            LocalDate expiry = null;
            String expiryText = csvValue(parts, headerIndex, "expiry_date", "expiry", 5);
            if (expiryText != null && !expiryText.isBlank()) {
                try {
                    expiry = LocalDate.parse(expiryText.trim());
                } catch (Exception e) {
                    result.getErrors().add(InventoryCsvImportResultDTO.RowIssue.builder()
                            .line(lineNo).medicineName(medicine).field("expiry_date")
                            .message("Invalid expiry date").build());
                    continue;
                }
            }
            String strength = blankToNull(csvValue(parts, headerIndex, "strength", null, 6));
            String form = blankToNull(csvValue(parts, headerIndex, "dosage_form", "form", 7));
            Integer threshold = null;
            String thresholdText = csvValue(parts, headerIndex, "threshold", "low_stock_threshold", 8);
            if (thresholdText != null && !thresholdText.isBlank()) {
                try {
                    threshold = Integer.parseInt(thresholdText);
                    if (threshold < 0) {
                        throw new IllegalArgumentException("negative");
                    }
                } catch (Exception e) {
                    result.getErrors().add(InventoryCsvImportResultDTO.RowIssue.builder()
                            .line(lineNo).medicineName(medicine).field("threshold")
                            .message("Invalid threshold").build());
                    continue;
                }
            }

            pending.add(new PendingRow(lineNo, medicine, qty, price, purchaseCost, reason, requiresPrescription,
                    batch, expiry, strength, form, threshold, existing));
        }

        result.setRowCount(pending.size());
        if (!result.getErrors().isEmpty()) {
            result.setSuccess(false);
            result.setAppliedCount(0);
            result.setMessage("Import blocked — fix validation errors and retry. No rows were applied.");
            return result;
        }

        LocalDateTime now = LocalDateTime.now();
        int applied = 0;
        for (PendingRow row : pending) {
            PharmacyInventory item = row.existing() != null ? row.existing() : PharmacyInventory.builder()
                    .pharmacyId(pharmacy.getId())
                    .medicineName(row.medicine())
                    .build();
            Integer oldQty = item.getQuantity();
            BigDecimal oldPrice = item.getPrice();
            BigDecimal oldCost = item.getPurchaseCost();

            item.setQuantity(row.qty());
            item.setOutOfStock(row.qty() <= 0);
            item.setArchived(false);
            item.setUpdatedAt(now);
            if (row.price() != null) {
                item.setPrice(row.price());
                if (item.getCurrency() == null || item.getCurrency().isBlank()) {
                    item.setCurrency("ETB");
                }
            }
            if (row.purchaseCost() != null) {
                item.setPurchaseCost(row.purchaseCost());
            }
            if (row.requiresPrescription() != null) {
                item.setRequiresPrescription(row.requiresPrescription());
            }
            if (row.batch() != null) {
                item.setBatchNumber(row.batch());
            }
            if (row.expiry() != null) {
                item.setExpiryDate(row.expiry());
            }
            if (row.strength() != null) {
                item.setStrength(row.strength());
            }
            if (row.form() != null) {
                item.setDosageForm(row.form());
            }
            if (row.threshold() != null) {
                item.setLowStockThreshold(row.threshold());
                persistThresholdTable(pharmacy.getId(), row.medicine(), row.threshold());
            }

            attachCatalog(item);
            inventoryRepository.save(item);
            medicineLotService.setSellableQuantity(item, row.qty(), telegramId, "CSV import");
            historyRepository.save(InventoryHistory.builder()
                    .pharmacyId(pharmacy.getId())
                    .medicineName(row.medicine())
                    .oldQuantity(oldQty)
                    .newQuantity(row.qty())
                    .eventType(InventoryEventType.CSV_IMPORTED)
                    .createdAt(now)
                    .build());

            if (row.price() != null && (oldPrice == null || row.price().compareTo(oldPrice) != 0)) {
                priceHistoryRepository.save(PriceHistory.builder()
                        .pharmacyId(pharmacy.getId())
                        .inventoryId(item.getId())
                        .medicineName(row.medicine())
                        .oldSellingPrice(oldPrice)
                        .newSellingPrice(row.price())
                        .oldPurchaseCost(oldCost)
                        .newPurchaseCost(item.getPurchaseCost())
                        .currency(item.getCurrency() == null ? "ETB" : item.getCurrency())
                        .reason(row.reason())
                        .actorTelegramId(telegramId)
                        .actorNameSnapshot("CSV import")
                        .effectiveAt(now)
                        .createdAt(now)
                        .build());
            }
            applied++;
        }

        pharmacy.setLastInventoryUpdate(now);
        pharmacyRepository.save(pharmacy);

        result.setSuccess(true);
        result.setAppliedCount(applied);
        result.setMessage("Import complete — " + applied + " row(s) applied");
        return result;
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
                .filter(i -> !i.isArchived() && !i.isOutOfStock() && i.getQuantity() != null && i.getQuantity() > getThreshold(i))
                .count();

        long lowStock = inventory.stream()
                .filter(i -> !i.isArchived() && !i.isOutOfStock() && i.getQuantity() != null && i.getQuantity() > 0
                        && i.getQuantity() <= getThreshold(i))
                .count();

        long outOfStock = inventory.stream()
                .filter(i -> i.isArchived() ? false : (i.isOutOfStock() || i.getQuantity() == null || i.getQuantity() <= 0))
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
            int threshold = getThreshold(item);

            if (item.isArchived()) {
                continue;
            }

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
    public String buildExpiryAlert(Long telegramId) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        StringBuilder expired = new StringBuilder();
        StringBuilder expiring = new StringBuilder();
        LocalDate today = LocalDate.now();
        LocalDate soon = today.plusDays(90);

        for (PharmacyInventory item : inventoryRepository.findByPharmacyId(pharmacy.getId())) {
            if (item.isArchived() || item.getExpiryDate() == null) {
                continue;
            }
            if (!item.getExpiryDate().isAfter(today)) {
                expired.append("💊 ").append(telegramClient.displayMedicine(telegramId, item.getMedicineName()))
                        .append(" — ").append(item.getExpiryDate()).append("\n");
            } else if (!item.getExpiryDate().isAfter(soon)) {
                expiring.append("💊 ").append(telegramClient.displayMedicine(telegramId, item.getMedicineName()))
                        .append(" — ").append(item.getExpiryDate()).append("\n");
            }
        }

        if (expired.isEmpty() && expiring.isEmpty()) {
            return "✅ <b>Expiry Alert</b>\n\nNo expired or near-expiry medicines right now.";
        }

        StringBuilder sb = new StringBuilder("📅 <b>Expiry Alert</b>\n\n");
        if (!expired.isEmpty()) {
            sb.append("❌ <b>Expired</b>\n").append(expired).append("\n");
        }
        if (!expiring.isEmpty()) {
            sb.append("⚠️ <b>Expires within 90 days</b>\n").append(expiring);
        }
        return sb.toString().trim();
    }

   @Override
public void setLowStockThreshold(Long telegramId, String medicineName, Integer threshold) {
    Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId)
            .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

    if (medicineName == null || medicineName.isBlank()) {
        throw new RuntimeException("medicineName is required");
    }
    if (threshold == null || threshold < 0) {
        throw new RuntimeException("threshold must be 0 or greater");
    }

    String normalizedMedicine = MedicineSearchNormalizer.normalizeToEnglishCanonical(medicineName);
    persistThresholdTable(pharmacy.getId(), normalizedMedicine, threshold);

    // also check current inventory and reset alert flag if stock is now safe
    inventoryRepository.findByPharmacyIdAndMedicineNameIgnoreCase(pharmacy.getId(), normalizedMedicine)
            .ifPresent(item -> {
                item.setLowStockThreshold(threshold);
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
                } else if (item.getQuantity() <= getThreshold(item)) {
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
        List<RestockSuggestionDTO> ranked = listRestockSuggestions(telegramId);
        if (ranked.isEmpty()) {
            Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId).orElse(null);
            boolean hasInventory = pharmacy != null && !inventoryRepository.findByPharmacyId(pharmacy.getId()).isEmpty();
            if (!hasInventory) {
                return "💡 <b>Restock Suggestions</b>\n\nNo inventory items found yet. Add medicines first to get recommendations.";
            }
            return "💡 <b>Restock Suggestions</b>\n\nNo strong restock signals this week. Stock levels look stable.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("💡 <b>Restock Suggestions (Advanced)</b>\n\n");
        sb.append("Signals used: demand insights, recent searches, reservation failures, stockout frequency.\n\n");

        int shown = 0;
        for (RestockSuggestionDTO s : ranked) {
            if (shown >= 5) {
                break;
            }
            sb.append("⚠️ You may want to restock ")
                    .append(s.getMedicineName())
                    .append(". Searched ")
                    .append(s.getWeeklySearches() == null ? 0 : s.getWeeklySearches())
                    .append(" times this week")
                    .append(s.getReservationFailures() != null && s.getReservationFailures() > 0
                            ? ", reservation failures " + s.getReservationFailures() : "")
                    .append(s.getStockouts() != null && s.getStockouts() > 0
                            ? ", stockouts " + s.getStockouts() : "")
                    .append(".\n")
                    .append("   • status: ")
                    .append(s.getStatus())
                    .append(" | qty: ")
                    .append(s.getCurrentStock() == null ? 0 : s.getCurrentStock())
                    .append(" | score: ")
                    .append(s.getScore() == null ? 0 : s.getScore())
                    .append(" | recommended: ")
                    .append(s.getRecommendedQuantity() == null ? 0 : s.getRecommendedQuantity())
                    .append("\n\n");
            shown++;
        }

        return sb.toString().trim();
    }

    @Override
    public List<RestockSuggestionDTO> listRestockSuggestions(Long telegramId) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekStart = now.minusDays(7);

        List<PharmacyInventory> inventory = inventoryRepository.findByPharmacyId(pharmacy.getId());
        if (inventory.isEmpty()) {
            return List.of();
        }

        Set<String> ignored = restockIgnoreRepository
                .findByPharmacyIdAndIgnoredAtAfter(pharmacy.getId(), now.minusDays(14))
                .stream()
                .map(RestockIgnore::getMedicineName)
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(HashSet::new));

        Set<String> pharmacyMedicines = inventory.stream()
                .map(PharmacyInventory::getMedicineName)
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(HashSet::new));

        Map<String, Integer> weeklySearchCount = new HashMap<>();
        for (MedicineSearchLog log : searchLogRepository.findBySearchedAtBetween(weekStart, now)) {
            if (log.getMedicineName() == null || log.getMedicineName().isBlank()) {
                continue;
            }
            String medicine = log.getMedicineName().trim().toLowerCase(Locale.ROOT);
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

            String medicine = reservation.getMedicineName().trim().toLowerCase(Locale.ROOT);
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

            String medicine = event.getMedicineName().trim().toLowerCase(Locale.ROOT);
            weeklyStockoutCount.merge(medicine, 1, Integer::sum);
        }

        return inventory.stream()
                .map(item -> {
                    String displayName = item.getMedicineName() == null ? "" : item.getMedicineName().trim();
                    String medicine = displayName.toLowerCase(Locale.ROOT);
                    int searches = weeklySearchCount.getOrDefault(medicine, 0);
                    int failures = weeklyFailureCount.getOrDefault(medicine, 0);
                    int stockouts = weeklyStockoutCount.getOrDefault(medicine, 0);
                    int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
                    int threshold = getThreshold(item);
                    boolean outOfStock = item.isOutOfStock() || quantity <= 0;
                    boolean lowStock = !outOfStock && quantity <= threshold;

                    int score = (searches * 2) + (failures * 3) + (stockouts * 2);
                    if (outOfStock) {
                        score += 6;
                    } else if (lowStock) {
                        score += 3;
                    }

                    int recommended = Math.max(quantity + searches, failures * 2);
                    if (recommended <= quantity) {
                        recommended = Math.max(threshold, quantity + (outOfStock || lowStock ? 1 : 0));
                    }

                    String status = outOfStock ? "❌ out of stock" : (lowStock ? "⚠️ low stock" : "✅ in stock");
                    String priority = score >= 100 ? "Critical" : score >= 20 ? "High" : score >= 5 ? "Medium" : "Low";
                    String reason = "Searched " + searches + " times this week"
                            + (failures > 0 ? ", reservation failures " + failures : "")
                            + (stockouts > 0 ? ", stockouts " + stockouts : "");

                    return RestockSuggestionDTO.builder()
                            .medicineName(displayName)
                            .currentStock(quantity)
                            .recommendedQuantity(recommended)
                            .weeklySearches(searches)
                            .reservationFailures(failures)
                            .stockouts(stockouts)
                            .score(score)
                            .priority(priority)
                            .status(status)
                            .reason(reason)
                            .demand(reason)
                            .demandLabel(DemandLabeler.label(searches, outOfStock, lowStock))
                            .build();
                })
                .filter(s -> s.getScore() != null && s.getScore() > 0)
                .filter(s -> s.getMedicineName() != null && !ignored.contains(s.getMedicineName().trim().toLowerCase(Locale.ROOT)))
                .sorted((a, b) -> Integer.compare(b.getScore() == null ? 0 : b.getScore(), a.getScore() == null ? 0 : a.getScore()))
                .limit(20)
                .collect(Collectors.toList());
    }

    @Override
    public void ignoreRestockSuggestion(Long telegramId, String medicineName) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));
        if (medicineName == null || medicineName.isBlank()) {
            throw new RuntimeException("medicineName is required");
        }
        String canonical = MedicineSearchNormalizer.normalizeToEnglishCanonical(medicineName);
        LocalDateTime now = LocalDateTime.now();
        RestockIgnore existing = restockIgnoreRepository
                .findByPharmacyIdAndMedicineNameIgnoreCase(pharmacy.getId(), canonical)
                .orElse(null);
        if (existing == null) {
            restockIgnoreRepository.save(RestockIgnore.builder()
                    .pharmacyId(pharmacy.getId())
                    .medicineName(canonical)
                    .ignoredAt(now)
                    .build());
            return;
        }
        existing.setIgnoredAt(now);
        restockIgnoreRepository.save(existing);
    }

    private int getThreshold(PharmacyInventory item) {
        if (item != null && item.getLowStockThreshold() != null) {
            return item.getLowStockThreshold();
        }
        if (item == null) {
            return 10;
        }
        return getThresholdFromTable(item.getPharmacyId(), item.getMedicineName());
    }

    private int getThresholdFromTable(Long pharmacyId, String medicineName) {
        return thresholdRepository.findByPharmacyIdAndMedicineNameIgnoreCase(pharmacyId, medicineName)
                .map(LowStockThreshold::getThreshold)
                .orElse(10);
    }

    private void persistThresholdTable(Long pharmacyId, String medicineName, Integer threshold) {
        LowStockThreshold config = thresholdRepository
                .findByPharmacyIdAndMedicineNameIgnoreCase(pharmacyId, medicineName)
                .orElse(LowStockThreshold.builder()
                        .pharmacyId(pharmacyId)
                        .medicineName(medicineName)
                        .build());
        config.setThreshold(threshold);
        thresholdRepository.save(config);
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

    item.setPrice(price);
    item.setUpdatedAt(now);

    int threshold = getThreshold(item);
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

    attachCatalog(item);
    inventoryRepository.save(item);
    medicineLotService.setSellableQuantity(item, quantity == null ? 0 : quantity, telegramId, "Stock updated");

    pharmacy.setLastInventoryUpdate(now);
    pharmacyRepository.save(pharmacy);

    historyRepository.save(
            InventoryHistory.builder()
                    .pharmacyId(pharmacy.getId())
                    .medicineName(item.getMedicineName())
                    .oldQuantity(oldQty)
                    .newQuantity(item.getQuantity())
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

            attachCatalog(item);
            inventoryRepository.save(item);
        }
    }
}

    // ── Pharmacy Mini App Inventory ──────────────────────────────────────

    @Override
    public List<PharmacyMiniAppInventoryItemDTO> getPharmacyMiniAppInventory(Long pharmacyTelegramId) {
        return getPharmacyMiniAppInventory(pharmacyTelegramId, null, null, null, false);
    }

    @Override
    public List<PharmacyMiniAppInventoryItemDTO> getPharmacyMiniAppInventory(
            Long pharmacyTelegramId, String search, String stockStatus, String expiryStatus, Boolean includeArchived) {
        Pharmacy pharmacy = resolvePharmacy(pharmacyTelegramId);
        String needle = search == null ? "" : search.trim().toLowerCase();
        String stock = stockStatus == null ? "all" : stockStatus.trim().toLowerCase();
        String expiry = expiryStatus == null ? "all" : expiryStatus.trim().toLowerCase();
        boolean archived = Boolean.TRUE.equals(includeArchived);

        List<PharmacyInventory> items = new ArrayList<>(inventoryRepository.findByPharmacyId(pharmacy.getId()));
        items.sort(Comparator.comparing(PharmacyInventory::getMedicineName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        Map<Long, Integer> lotCounts = medicineLotService.lotCountsByPharmacy(pharmacy.getId());
        return items.stream()
                .filter(item -> archived || !item.isArchived())
                .filter(item -> needle.isBlank() || (item.getMedicineName() != null
                        && item.getMedicineName().toLowerCase().contains(needle)))
                .filter(item -> matchesStockStatus(item, stock))
                .filter(item -> matchesExpiryStatus(item, expiry))
                .map(item -> toMiniAppDTO(item, lotCounts.getOrDefault(item.getId(), 0)))
                .toList();
    }

    @Override
    public PharmacyMiniAppInventoryItemDTO updateStockFromMiniApp(Long pharmacyTelegramId, Long itemId, Integer quantity) {
        Pharmacy pharmacy = resolvePharmacy(pharmacyTelegramId);
        PharmacyInventory item = resolveOwnedItem(pharmacy, itemId);

        if (quantity == null || quantity < 0) {
            throw new RuntimeException("quantity must be 0 or greater");
        }
        Integer oldQty = item.getQuantity();
        medicineLotService.setSellableQuantity(item, quantity, pharmacyTelegramId, "Quantity set from Mini App");
        recordHistory(pharmacy.getId(), item.getMedicineName(), oldQty, item.getQuantity(), InventoryEventType.MINIAPP_UPDATED);
        return toMiniAppDTO(item);
    }

    @Override
    public PharmacyMiniAppInventoryItemDTO updatePriceFromMiniApp(Long pharmacyTelegramId, Long itemId, BigDecimal price) {
        Pharmacy pharmacy = resolvePharmacy(pharmacyTelegramId);
        PharmacyInventory item = resolveOwnedItem(pharmacy, itemId);

        item.setPrice(price);
        if (item.getCurrency() == null || item.getCurrency().isBlank()) {
            item.setCurrency("ETB");
        }
        item.setUpdatedAt(LocalDateTime.now());

        inventoryRepository.save(item);
        recordHistory(pharmacy.getId(), item.getMedicineName(), item.getQuantity(), item.getQuantity(), InventoryEventType.MINIAPP_UPDATED);
        return toMiniAppDTO(item);
    }

    @Override
    public PharmacyMiniAppInventoryItemDTO togglePrescriptionFromMiniApp(Long pharmacyTelegramId, Long itemId, boolean requiresPrescription) {
        Pharmacy pharmacy = resolvePharmacy(pharmacyTelegramId);
        PharmacyInventory item = resolveOwnedItem(pharmacy, itemId);

        item.setRequiresPrescription(requiresPrescription);
        item.setUpdatedAt(LocalDateTime.now());

        attachCatalog(item);
        inventoryRepository.save(item);
        medicineCatalogService.refreshPrescriptionRequired(item.getCatalogMedicineId());
        return toMiniAppDTO(item);
    }

    @Override
    public PharmacyMiniAppInventoryItemDTO toggleAvailabilityFromMiniApp(Long pharmacyTelegramId, Long itemId, boolean available) {
        Pharmacy pharmacy = resolvePharmacy(pharmacyTelegramId);
        PharmacyInventory item = resolveOwnedItem(pharmacy, itemId);

        Integer oldQty = item.getQuantity();
        if (!available) {
            medicineLotService.zeroAllLots(item, pharmacyTelegramId, "Marked out of stock", StockMovementType.ADJUSTMENT);
        } else {
            item.setOutOfStock(false);
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                medicineLotService.receive(item, 1, item.getBatchNumber(), item.getExpiryDate(), item.getPrice(),
                        pharmacyTelegramId, StockMovementType.ADJUSTMENT, "Marked in stock", null);
            } else {
                item.setUpdatedAt(LocalDateTime.now());
                inventoryRepository.save(item);
            }
        }

        recordHistory(pharmacy.getId(), item.getMedicineName(), oldQty, item.getQuantity(),
                available ? InventoryEventType.STOCK_UPDATED : InventoryEventType.MARKED_OUT);
        return toMiniAppDTO(item);
    }

    private void attachCatalog(PharmacyInventory item) {
        medicineCatalogService.attachCatalogMedicine(item);
    }

    private Pharmacy resolvePharmacy(Long pharmacyTelegramId) {
        if (pharmacyTelegramId == null || pharmacyTelegramId <= 0) {
            throw new RuntimeException("pharmacyTelegramId is required");
        }
        return pharmacyRepository.findByTelegramId(pharmacyTelegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));
    }

    @Override
    public PharmacyMiniAppInventoryItemDTO addStockFromMiniApp(Long pharmacyTelegramId, PharmacyMiniAppAddStockRequest request) {
        Pharmacy pharmacy = resolvePharmacy(pharmacyTelegramId);
        if (request == null) {
            throw new RuntimeException("Request body is required");
        }

        String medicineName = request.getMedicineName();
        if (medicineName == null || medicineName.isBlank()) {
            throw new RuntimeException("medicineName is required");
        }

        Integer incomingQty = request.getQuantity();
        if (incomingQty == null || incomingQty < 1) {
            throw new RuntimeException("quantity must be at least 1");
        }
        if (request.getPrice() == null) {
            throw new RuntimeException("price is required");
        }

        LocalDate expiryDate = request.getExpiryDate();
        if (expiryDate != null && expiryDate.isBefore(LocalDate.now())) {
            throw new RuntimeException("Expiry date must be today or in the future");
        }

        String normalized = MedicineSearchNormalizer.normalizeToEnglishCanonical(medicineName);
        PharmacyInventory item = inventoryRepository
                .findByPharmacyIdAndMedicineNameIgnoreCase(pharmacy.getId(), normalized)
                .orElse(null);
        boolean existing = item != null;
        if (!existing) {
            item = PharmacyInventory.builder()
                    .pharmacyId(pharmacy.getId())
                    .medicineName(normalized)
                    .quantity(0)
                    .outOfStock(false)
                    .lowStockAlertSent(false)
                    .build();
        }

        Integer oldQty = item.getQuantity() == null ? 0 : item.getQuantity();
        item.setPrice(request.getPrice());
        if (item.getCurrency() == null || item.getCurrency().isBlank()) {
            item.setCurrency("ETB");
        }
        if (request.getLowStockThreshold() != null) {
            item.setLowStockThreshold(request.getLowStockThreshold());
            persistThresholdTable(pharmacy.getId(), normalized, request.getLowStockThreshold());
        }
        if (request.getRequiresPrescription() != null) {
            item.setRequiresPrescription(request.getRequiresPrescription());
        }
        String strength = blankToNull(request.getStrength());
        if (strength != null) {
            item.setStrength(strength);
        }
        String dosageForm = blankToNull(request.getDosageForm());
        if (dosageForm != null) {
            item.setDosageForm(dosageForm);
        }
        item.setUpdatedAt(LocalDateTime.now());

        attachCatalog(item);
        inventoryRepository.save(item);
        medicineLotService.receive(
                item,
                incomingQty,
                blankToNull(request.getBatchNumber()),
                expiryDate,
                request.getPrice(),
                pharmacyTelegramId,
                StockMovementType.RECEIVED,
                "Stock received",
                null);
        recordHistory(pharmacy.getId(), item.getMedicineName(), oldQty, item.getQuantity(), InventoryEventType.MINIAPP_UPDATED);
        if (item.getQuantity() != null && item.getQuantity() > 0) {
            medicineAvailabilityAlertService.notifyUsersIfAvailable(normalized, pharmacy, item.getQuantity());
        }
        return toMiniAppDTO(item);
    }

    @Override
    public PharmacyMiniAppInventoryItemDTO patchInventoryFromMiniApp(
            Long pharmacyTelegramId, Long itemId, PharmacyMiniAppInventoryPatchRequest request) {
        Pharmacy pharmacy = resolvePharmacy(pharmacyTelegramId);
        PharmacyInventory item = resolveOwnedItem(pharmacy, itemId);
        if (request == null) {
            throw new RuntimeException("Request body is required");
        }

        if (request.getPrice() != null) {
            item.setPrice(request.getPrice());
            if (item.getCurrency() == null || item.getCurrency().isBlank()) {
                item.setCurrency("ETB");
            }
        }
        if (request.getRequiresPrescription() != null) {
            item.setRequiresPrescription(request.getRequiresPrescription());
            attachCatalog(item);
            medicineCatalogService.refreshPrescriptionRequired(item.getCatalogMedicineId());
        }
        if (request.getBatchNumber() != null || request.isClearExpiry() || request.getExpiryDate() != null) {
            medicineLotService.updateLotMetadata(
                    item,
                    request.getBatchNumber(),
                    request.getExpiryDate(),
                    request.isClearExpiry());
        }
        if (request.getStrength() != null) {
            item.setStrength(blankToNull(request.getStrength()));
        }
        if (request.getDosageForm() != null) {
            item.setDosageForm(blankToNull(request.getDosageForm()));
        }
        if (request.getLowStockThreshold() != null) {
            if (request.getLowStockThreshold() < 0) {
                throw new RuntimeException("threshold must be 0 or greater");
            }
            item.setLowStockThreshold(request.getLowStockThreshold());
            persistThresholdTable(pharmacy.getId(), item.getMedicineName(), request.getLowStockThreshold());
        }
        if (request.getArchived() != null) {
            item.setArchived(request.getArchived());
        }

        Integer oldQty = item.getQuantity();
        if (request.getQuantity() != null) {
            String reason = request.getReason() == null ? "" : request.getReason().trim();
            if (reason.isEmpty()) {
                throw new RuntimeException("Reason is required when changing quantity");
            }
            medicineLotService.setSellableQuantity(item, request.getQuantity(), pharmacyTelegramId, reason);
        }
        if (request.getAvailable() != null) {
            if (!request.getAvailable()) {
                medicineLotService.zeroAllLots(item, pharmacyTelegramId,
                        request.getReason() == null || request.getReason().isBlank() ? "Marked out of stock" : request.getReason(),
                        StockMovementType.ADJUSTMENT);
            } else {
                item.setOutOfStock(false);
                if (item.getQuantity() == null || item.getQuantity() <= 0) {
                    medicineLotService.receive(item, 1, item.getBatchNumber(), item.getExpiryDate(), item.getPrice(),
                            pharmacyTelegramId, StockMovementType.ADJUSTMENT, "Marked in stock", null);
                }
            }
        }
        item.setUpdatedAt(LocalDateTime.now());
        inventoryRepository.save(item);
        if (request.getQuantity() != null || request.getAvailable() != null) {
            recordHistory(pharmacy.getId(), item.getMedicineName(), oldQty, item.getQuantity(),
                    Boolean.FALSE.equals(request.getAvailable())
                            ? InventoryEventType.MARKED_OUT
                            : InventoryEventType.MINIAPP_UPDATED);
        }
        return toMiniAppDTO(item);
    }

    @Override
    public List<MedicineBatchDTO> listInventoryBatches(Long pharmacyTelegramId, Long itemId) {
        Pharmacy pharmacy = resolvePharmacy(pharmacyTelegramId);
        PharmacyInventory item = resolveOwnedItem(pharmacy, itemId);
        medicineLotService.ensureBackfillAndSync(item);
        return medicineLotService.listBatches(pharmacy.getId(), item.getId());
    }

    @Override
    public List<StockMovementDTO> listInventoryMovements(Long pharmacyTelegramId, Long itemId) {
        Pharmacy pharmacy = resolvePharmacy(pharmacyTelegramId);
        PharmacyInventory item = resolveOwnedItem(pharmacy, itemId);
        return medicineLotService.listMovements(pharmacy.getId(), item.getId());
    }

    @Override
    public List<MedicineBatchDTO> listExpiryBatches(Long pharmacyTelegramId, String bucket) {
        Pharmacy pharmacy = resolvePharmacy(pharmacyTelegramId);
        return medicineLotService.listExpiry(pharmacy.getId(), bucket);
    }

    @Override
    public PharmacyMiniAppInventoryItemDTO adjustInventoryFromMiniApp(
            Long pharmacyTelegramId,
            Long itemId,
            Integer quantityChange,
            String reason,
            String movementType,
            String batchNumber,
            LocalDate expiryDate) {
        Pharmacy pharmacy = resolvePharmacy(pharmacyTelegramId);
        PharmacyInventory item = resolveOwnedItem(pharmacy, itemId);
        if (quantityChange == null || quantityChange == 0) {
            throw new RuntimeException("quantityChange is required and must not be 0");
        }
        if (reason == null || reason.isBlank()) {
            throw new RuntimeException("Reason is required when adjusting inventory");
        }
        StockMovementType type = parseAdjustmentType(movementType);
        Integer oldQty = item.getQuantity();
        if (quantityChange > 0) {
            medicineLotService.receive(item, quantityChange, blankToNull(batchNumber), expiryDate, item.getPrice(),
                    pharmacyTelegramId, type, reason.trim(), null);
        } else {
            medicineLotService.deductFefo(item, -quantityChange, type, pharmacyTelegramId, reason.trim(), null);
        }
        recordHistory(pharmacy.getId(), item.getMedicineName(), oldQty, item.getQuantity(), InventoryEventType.MINIAPP_UPDATED);
        return toMiniAppDTO(item);
    }

    private StockMovementType parseAdjustmentType(String movementType) {
        if (movementType == null || movementType.isBlank()) {
            return StockMovementType.ADJUSTMENT;
        }
        try {
            StockMovementType type = StockMovementType.valueOf(movementType.trim().toUpperCase(Locale.ROOT));
            if (type == StockMovementType.ADJUSTMENT
                    || type == StockMovementType.RETURNED
                    || type == StockMovementType.EXPIRED
                    || type == StockMovementType.DAMAGED
                    || type == StockMovementType.RECEIVED) {
                return type;
            }
            throw new RuntimeException("Unsupported adjustment type: " + movementType);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unsupported adjustment type: " + movementType);
        }
    }

    @Override
    public void archiveInventoryItem(Long pharmacyTelegramId, Long itemId) {
        Pharmacy pharmacy = resolvePharmacy(pharmacyTelegramId);
        PharmacyInventory item = resolveOwnedItem(pharmacy, itemId);
        item.setArchived(true);
        item.setOutOfStock(true);
        item.setUpdatedAt(LocalDateTime.now());
        inventoryRepository.save(item);
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isExpired(PharmacyInventory item) {
        return isExpired(item, LocalDate.now());
    }

    private boolean isExpired(PharmacyInventory item, LocalDate today) {
        return item.getExpiryDate() != null && item.getExpiryDate().isBefore(today);
    }

    private boolean matchesStockStatus(PharmacyInventory item, String stockStatus) {
        if (stockStatus == null || stockStatus.isBlank() || "ALL".equalsIgnoreCase(stockStatus.trim())) {
            return true;
        }
        int qty = item.getQuantity() == null ? 0 : item.getQuantity();
        int threshold = getThreshold(item);
        boolean expired = isExpired(item);
        boolean inStock = !item.isOutOfStock() && qty > 0 && !expired;
        String normalized = stockStatus.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "IN", "IN_STOCK" -> inStock;
            case "OUT", "OUT_OF_STOCK" -> !inStock;
            case "LOW", "LOW_STOCK" -> inStock && qty <= threshold;
            default -> true;
        };
    }

    private boolean matchesExpiryStatus(PharmacyInventory item, String expiryStatus) {
        return matchesExpiryStatus(item, expiryStatus, LocalDate.now());
    }

    private boolean matchesExpiryStatus(PharmacyInventory item, String expiryStatus, LocalDate today) {
        if (expiryStatus == null || expiryStatus.isBlank() || "ALL".equalsIgnoreCase(expiryStatus.trim())) {
            return true;
        }
        String normalized = expiryStatus.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        LocalDate expiry = item.getExpiryDate();
        return switch (normalized) {
            case "EXPIRED" -> expiry != null && expiry.isBefore(today);
            case "EXPIRING", "NEAR_EXPIRY", "EXPIRING_SOON" ->
                    expiry != null && !expiry.isBefore(today) && !expiry.isAfter(today.plusDays(90));
            case "OK" -> expiry == null || expiry.isAfter(today.plusDays(90));
            default -> true;
        };
    }

    private String csvCell(Object value) {
        String raw = value == null ? "" : String.valueOf(value);
        if (raw.contains(",") || raw.contains("\"") || raw.contains("\n")) {
            return "\"" + raw.replace("\"", "\"\"") + "\"";
        }
        return raw;
    }

    private List<String> splitCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    private boolean looksLikeCsvHeader(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        List<String> cells = splitCsvLine(line);
        String first = cells.isEmpty() ? "" : cells.get(0).trim().toLowerCase(Locale.ROOT);
        return first.equals("name") || first.equals("medicine_name") || first.equals("medicine");
    }

    private Map<String, Integer> csvHeaderIndex(String line) {
        List<String> cells = splitCsvLine(line);
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < cells.size(); i++) {
            String cell = cells.get(i) == null ? "" : cells.get(i).trim().toLowerCase(Locale.ROOT);
            if (!cell.isEmpty()) {
                index.put(cell, i);
            }
        }
        return index;
    }

    private String csvValue(List<String> parts, Map<String, Integer> headerIndex, String primary, String alt, int fallback) {
        Integer idx = null;
        if (headerIndex != null && !headerIndex.isEmpty()) {
            idx = headerIndex.get(primary);
            if (idx == null && alt != null) {
                idx = headerIndex.get(alt);
            }
            if (idx == null) {
                idx = fallback;
            }
        } else {
            idx = fallback;
        }
        if (idx == null || idx < 0 || idx >= parts.size()) {
            return "";
        }
        String value = parts.get(idx);
        return value == null ? "" : value.trim();
    }

    private PharmacyInventory resolveOwnedItem(Pharmacy pharmacy, Long itemId) {
        if (itemId == null) {
            throw new RuntimeException("itemId is required");
        }
        PharmacyInventory item = inventoryRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Inventory item not found"));
        if (!item.getPharmacyId().equals(pharmacy.getId())) {
            throw new RuntimeException("This inventory item does not belong to your pharmacy.");
        }
        return item;
    }

    private void recordHistory(Long pharmacyId, String medicineName, Integer oldQty, Integer newQty, InventoryEventType eventType) {
        historyRepository.save(
                InventoryHistory.builder()
                        .pharmacyId(pharmacyId)
                        .medicineName(medicineName)
                        .oldQuantity(oldQty)
                        .newQuantity(newQty)
                        .eventType(eventType)
                        .createdAt(LocalDateTime.now())
                        .build()
        );
    }

    private PharmacyMiniAppInventoryItemDTO toMiniAppDTO(PharmacyInventory item) {
        int lotCount = item.getId() == null ? 0 : (int) Math.max(0, medicineLotService.lotCountsByPharmacy(item.getPharmacyId())
                .getOrDefault(item.getId(), 0));
        return toMiniAppDTO(item, lotCount);
    }

    private PharmacyMiniAppInventoryItemDTO toMiniAppDTO(PharmacyInventory item, int lotCount) {
        int qty = item.getQuantity() == null ? 0 : item.getQuantity();
        int threshold = getThreshold(item);
        boolean expired = isExpired(item, LocalDate.now());
        boolean inStock = !item.isOutOfStock() && qty > 0 && !expired;
        boolean lowStock = inStock && qty <= threshold;
        boolean expiringSoon = item.getExpiryDate() != null
                && !expired
                && !item.getExpiryDate().isAfter(LocalDate.now().plusDays(90));

        return PharmacyMiniAppInventoryItemDTO.builder()
                .itemId(item.getId())
                .medicineId(item.getId())
                .medicineName(item.getMedicineName())
                .stockQuantity(qty)
                .price(item.getPrice())
                .currency(item.getCurrency())
                .requiresPrescription(item.isRequiresPrescription())
                .inStock(inStock)
                .outOfStock(item.isOutOfStock() || qty <= 0 || expired)
                .lowStock(lowStock)
                .lowStockThreshold(threshold)
                .batchNumber(item.getBatchNumber())
                .expiryDate(item.getExpiryDate())
                .strength(item.getStrength())
                .dosageForm(item.getDosageForm())
                .archived(item.isArchived())
                .expired(expired)
                .expiringSoon(expiringSoon)
                .lotCount(lotCount)
                .lastUpdatedAt(item.getUpdatedAt())
                .build();
    }
}