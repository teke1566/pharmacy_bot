package com.tenahub.bot.service;

import com.tenahub.bot.entity.PharmacyInventory;

import java.math.BigDecimal;
import java.util.List;

public interface InventoryService {


    void markOutOfStock(Long telegramId, String medicineName);

    List<PharmacyInventory> getInventory(Long telegramId);
    byte[] exportInventoryCsv(Long telegramId);
     String buildSummary(Long telegramId, String period);

    String buildLowStockAlert(Long telegramId);
      void setLowStockThreshold(Long telegramId, String medicineName, Integer threshold);

    String getDemandInsights(Long telegramId);
    void importInventoryCsv(Long telegramId, String csvContent);
    void updatePrice(Long pharmacyChatId, String medicineName, BigDecimal price);
    void upsertStock(Long chatId, String medicineName, Integer quantity, BigDecimal price);
    void initializeInventoryFromMedicines(Long pharmacyId, String medicines);

}