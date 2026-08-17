package com.tenahub.bot.service;

import com.tenahub.bot.dto.MedicineBatchDTO;
import com.tenahub.bot.dto.StockMovementDTO;
import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.entity.StockMovementType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface MedicineLotService {

    void ensureBackfillAndSync(PharmacyInventory sku);

    int sellableQuantity(PharmacyInventory sku);

    boolean hasExpiredStock(PharmacyInventory sku);

    void receive(PharmacyInventory sku,
                 int quantity,
                 String batchNumber,
                 LocalDate expiryDate,
                 BigDecimal sellingPrice,
                 Long actorTelegramId,
                 StockMovementType type,
                 String reason,
                 Long reservationId);

    void receiveWithCost(PharmacyInventory sku,
                         int quantity,
                         String batchNumber,
                         LocalDate expiryDate,
                         BigDecimal sellingPrice,
                         BigDecimal purchasePrice,
                         String supplier,
                         Long supplierId,
                         Long actorTelegramId,
                         StockMovementType type,
                         String reason,
                         Long reservationId);

    void deductFefo(PharmacyInventory sku,
                    int quantity,
                    StockMovementType type,
                    Long actorTelegramId,
                    String reason,
                    Long reservationId);

    void holdForReservation(MedicineReservation reservation);

    void releaseHeldForReservation(MedicineReservation reservation);

    void fulfillReservation(MedicineReservation reservation, Long actorTelegramId);

    void setSellableQuantity(PharmacyInventory sku, int targetQuantity, Long actorTelegramId, String reason);

    void zeroAllLots(PharmacyInventory sku, Long actorTelegramId, String reason, StockMovementType type);

    void updateLotMetadata(PharmacyInventory sku, String batchNumber, LocalDate expiryDate, boolean clearExpiry);

    List<MedicineBatchDTO> listBatches(Long pharmacyId, Long inventoryId);

    List<StockMovementDTO> listMovements(Long pharmacyId, Long inventoryId);

    List<MedicineBatchDTO> listExpiry(Long pharmacyId, String bucket);

    Map<Long, Integer> lotCountsByPharmacy(Long pharmacyId);
}
