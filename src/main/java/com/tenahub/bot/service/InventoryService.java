package com.tenahub.bot.service;

import com.tenahub.bot.entity.PharmacyInventory;

import java.math.BigDecimal;
import java.util.List;

public interface InventoryService {

  record BulkInventoryUpdateResult(int totalLines,
                   int updatedCount,
                   int failedCount,
                   List<String> errors) {}


    void markOutOfStock(Long telegramId, String medicineName);

    List<PharmacyInventory> getInventory(Long telegramId);
    List<PharmacyInventory> getInventoryByPharmacyId(Long pharmacyId);
    byte[] exportInventoryCsv(Long telegramId);
     String buildSummary(Long telegramId, String period);

    String buildLowStockAlert(Long telegramId);
      void setLowStockThreshold(Long telegramId, String medicineName, Integer threshold);

    String getDemandInsights(Long telegramId);
    String getAdvancedRestockSuggestions(Long telegramId);
    void importInventoryCsv(Long telegramId, String csvContent);
    void updatePrice(Long pharmacyChatId, String medicineName, BigDecimal price);
    void upsertStock(Long chatId, String medicineName, Integer quantity, BigDecimal price);
    PharmacyInventory setRequiresPrescription(Long telegramId, Long medicineId, boolean requiresPrescription);
    PharmacyInventory setRequiresPrescriptionForPharmacy(Long pharmacyId, Long medicineId, boolean requiresPrescription);
    BulkInventoryUpdateResult bulkUpsertFromText(Long chatId, String bulkText);
    void initializeInventoryFromMedicines(Long pharmacyId, String medicines);

}