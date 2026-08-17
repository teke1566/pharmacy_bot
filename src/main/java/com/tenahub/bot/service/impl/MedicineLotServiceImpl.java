package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.MedicineBatchDTO;
import com.tenahub.bot.dto.StockMovementDTO;
import com.tenahub.bot.entity.MedicineBatch;
import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.entity.StockMovement;
import com.tenahub.bot.entity.StockMovementType;
import com.tenahub.bot.repository.MedicineBatchRepository;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.repository.StockMovementRepository;
import com.tenahub.bot.service.MedicineLotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MedicineLotServiceImpl implements MedicineLotService {

    private final PharmacyInventoryRepository inventoryRepository;
    private final MedicineBatchRepository batchRepository;
    private final StockMovementRepository movementRepository;
    private final PharmacyRepository pharmacyRepository;

    @Override
    @Transactional
    public void ensureBackfillAndSync(PharmacyInventory sku) {
        if (sku == null || sku.getId() == null) {
            return;
        }
        List<MedicineBatch> lots = batchRepository.findByInventoryId(sku.getId());
        int skuQty = sku.getQuantity() == null ? 0 : sku.getQuantity();
        if (lots.isEmpty() && skuQty > 0) {
            LocalDateTime now = LocalDateTime.now();
            MedicineBatch lot = MedicineBatch.builder()
                    .pharmacyId(sku.getPharmacyId())
                    .inventoryId(sku.getId())
                    .medicineName(sku.getMedicineName())
                    .batchNumber(blankToNull(sku.getBatchNumber()))
                    .expiryDate(sku.getExpiryDate())
                    .quantity(skuQty)
                    .sellingPrice(sku.getPrice())
                    .receivedAt(sku.getUpdatedAt() == null ? now : sku.getUpdatedAt())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            batchRepository.save(lot);
        }
        syncSkuFromLots(sku);
    }

    @Override
    public int sellableQuantity(PharmacyInventory sku) {
        if (sku == null || sku.getId() == null) {
            return sku == null || sku.getQuantity() == null ? 0 : sku.getQuantity();
        }
        LocalDate today = LocalDate.now();
        return batchRepository.findByInventoryId(sku.getId()).stream()
                .filter(lot -> isSellable(lot, today))
                .mapToInt(lot -> lot.getQuantity() == null ? 0 : lot.getQuantity())
                .sum();
    }

    @Override
    public boolean hasExpiredStock(PharmacyInventory sku) {
        if (sku == null || sku.getId() == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        return batchRepository.findByInventoryId(sku.getId()).stream()
                .anyMatch(lot -> (lot.getQuantity() == null ? 0 : lot.getQuantity()) > 0
                        && lot.getExpiryDate() != null
                        && lot.getExpiryDate().isBefore(today));
    }

    @Override
    @Transactional
    public void receive(PharmacyInventory sku,
                        int quantity,
                        String batchNumber,
                        LocalDate expiryDate,
                        BigDecimal sellingPrice,
                        Long actorTelegramId,
                        StockMovementType type,
                        String reason,
                        Long reservationId) {
        receiveWithCost(sku, quantity, batchNumber, expiryDate, sellingPrice, null, null, null,
                actorTelegramId, type, reason, reservationId);
    }

    @Override
    @Transactional
    public void receiveWithCost(PharmacyInventory sku,
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
                                Long reservationId) {
        if (quantity < 1) {
            throw new RuntimeException("quantity must be at least 1");
        }
        if (expiryDate != null && expiryDate.isBefore(LocalDate.now()) && type == StockMovementType.RECEIVED) {
            throw new RuntimeException("Expiry date must be today or in the future");
        }
        persistSku(sku);
        ensureBackfillAndSync(sku);

        String normalizedBatch = blankToNull(batchNumber);
        MedicineBatch lot = batchRepository.findByInventoryId(sku.getId()).stream()
                .filter(existing -> sameLotKey(existing, normalizedBatch, expiryDate))
                .findFirst()
                .orElse(null);

        LocalDateTime now = LocalDateTime.now();
        int skuBefore = sku.getQuantity() == null ? 0 : sku.getQuantity();
        int batchBefore = 0;
        if (lot == null) {
            lot = MedicineBatch.builder()
                    .pharmacyId(sku.getPharmacyId())
                    .inventoryId(sku.getId())
                    .medicineName(sku.getMedicineName())
                    .batchNumber(normalizedBatch)
                    .expiryDate(expiryDate)
                    .quantity(0)
                    .sellingPrice(sellingPrice != null ? sellingPrice : sku.getPrice())
                    .purchasePrice(purchasePrice)
                    .supplier(supplier)
                    .supplierId(supplierId)
                    .receivedAt(now)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
        } else {
            batchBefore = lot.getQuantity() == null ? 0 : lot.getQuantity();
        }
        int batchAfter = batchBefore + quantity;
        lot.setQuantity(batchAfter);
        if (sellingPrice != null) {
            lot.setSellingPrice(sellingPrice);
        }
        if (purchasePrice != null) {
            lot.setPurchasePrice(purchasePrice);
        }
        if (supplier != null && !supplier.isBlank()) {
            lot.setSupplier(supplier);
        }
        if (supplierId != null) {
            lot.setSupplierId(supplierId);
        }
        lot.setUpdatedAt(now);
        batchRepository.save(lot);
        syncSkuFromLots(sku);
        recordMovement(sku, lot, type, quantity, skuBefore, sku.getQuantity(), batchBefore, batchAfter,
                actorTelegramId, reason, reservationId);
    }

    @Override
    @Transactional
    public void deductFefo(PharmacyInventory sku,
                           int quantity,
                           StockMovementType type,
                           Long actorTelegramId,
                           String reason,
                           Long reservationId) {
        if (quantity < 1) {
            throw new RuntimeException("quantity must be at least 1");
        }
        persistSku(sku);
        ensureBackfillAndSync(sku);

        LocalDate today = LocalDate.now();
        List<MedicineBatch> sellable = fefoSellableLots(sku, today);
        int available = sellable.stream().mapToInt(lot -> lot.getQuantity() == null ? 0 : lot.getQuantity()).sum();
        if (available <= 0) {
            if (hasExpiredStock(sku)) {
                throw new RuntimeException("Cannot dispense expired medicine.");
            }
            throw new RuntimeException("Medicine is currently out of stock.");
        }
        if (quantity > available) {
            throw new RuntimeException("Requested quantity exceeds available stock.");
        }

        int remaining = quantity;
        int skuBefore = sku.getQuantity() == null ? 0 : sku.getQuantity();
        LocalDateTime now = LocalDateTime.now();
        for (MedicineBatch lot : sellable) {
            if (remaining <= 0) {
                break;
            }
            int lotQty = lot.getQuantity() == null ? 0 : lot.getQuantity();
            if (lotQty <= 0) {
                continue;
            }
            int take = Math.min(lotQty, remaining);
            int batchAfter = lotQty - take;
            lot.setQuantity(batchAfter);
            lot.setUpdatedAt(now);
            batchRepository.save(lot);
            remaining -= take;
            int skuAfterPreview = skuBefore - (quantity - remaining);
            recordMovement(sku, lot, type, -take, skuBefore, skuAfterPreview, lotQty, batchAfter,
                    actorTelegramId, reason, reservationId);
        }
        if (remaining > 0) {
            throw new RuntimeException("Requested quantity exceeds available stock.");
        }
        syncSkuFromLots(sku);
    }

    @Override
    @Transactional
    public void holdForReservation(MedicineReservation reservation) {
        PharmacyInventory sku = requireSku(reservation);
        Long actorId = pharmacyActor(reservation.getPharmacyId());
        deductFefo(sku, requiredQty(reservation), StockMovementType.HELD, actorId, "Reservation hold", reservation.getId());
        reservation.setInventoryHeld(true);
    }

    @Override
    @Transactional
    public void releaseHeldForReservation(MedicineReservation reservation) {
        if (reservation == null || !reservation.isInventoryHeld()) {
            return;
        }
        PharmacyInventory sku = inventoryRepository
                .findByPharmacyIdAndMedicineNameIgnoreCase(reservation.getPharmacyId(), reservation.getMedicineName())
                .orElse(null);
        if (sku == null) {
            reservation.setInventoryHeld(false);
            return;
        }
        persistSku(sku);
        ensureBackfillAndSync(sku);

        List<StockMovement> held = reservation.getId() == null
                ? List.of()
                : movementRepository.findByReservationIdAndMovementType(reservation.getId(), StockMovementType.HELD);
        Long actorId = pharmacyActor(reservation.getPharmacyId());
        if (held.isEmpty()) {
            int releaseQty = requiredQty(reservation);
            if (releaseQty > 0) {
                receive(sku, releaseQty, sku.getBatchNumber(), sku.getExpiryDate(), sku.getPrice(),
                        actorId, StockMovementType.RELEASED, "Reservation released", reservation.getId());
            }
        } else {
            for (StockMovement movement : held) {
                int restore = Math.abs(movement.getQuantityChange() == null ? 0 : movement.getQuantityChange());
                if (restore <= 0) {
                    continue;
                }
                MedicineBatch lot = movement.getBatchId() == null ? null : batchRepository.findById(movement.getBatchId()).orElse(null);
                if (lot == null) {
                    receive(sku, restore, sku.getBatchNumber(), sku.getExpiryDate(), sku.getPrice(),
                            actorId, StockMovementType.RELEASED, "Reservation released", reservation.getId());
                    continue;
                }
                int skuBefore = sku.getQuantity() == null ? 0 : sku.getQuantity();
                int batchBefore = lot.getQuantity() == null ? 0 : lot.getQuantity();
                lot.setQuantity(batchBefore + restore);
                lot.setUpdatedAt(LocalDateTime.now());
                batchRepository.save(lot);
                syncSkuFromLots(sku);
                recordMovement(sku, lot, StockMovementType.RELEASED, restore, skuBefore, sku.getQuantity(),
                        batchBefore, lot.getQuantity(), actorId, "Reservation released", reservation.getId());
            }
        }
        reservation.setInventoryHeld(false);
    }

    @Override
    @Transactional
    public void fulfillReservation(MedicineReservation reservation, Long actorTelegramId) {
        PharmacyInventory sku = requireSku(reservation);
        Long actorId = actorTelegramId != null ? actorTelegramId : pharmacyActor(reservation.getPharmacyId());
        if (reservation.isInventoryHeld()) {
            List<StockMovement> held = reservation.getId() == null
                    ? List.of()
                    : movementRepository.findByReservationIdAndMovementType(reservation.getId(), StockMovementType.HELD);
            int skuQty = sku.getQuantity() == null ? 0 : sku.getQuantity();
            if (held.isEmpty()) {
                recordMovement(sku, null, StockMovementType.DISPENSED, -requiredQty(reservation),
                        skuQty, skuQty, null, null, actorId, "Reservation fulfilled", reservation.getId());
            } else {
                for (StockMovement movement : held) {
                    MedicineBatch lot = movement.getBatchId() == null ? null : batchRepository.findById(movement.getBatchId()).orElse(null);
                    int change = movement.getQuantityChange() == null ? 0 : movement.getQuantityChange();
                    recordMovement(sku, lot, StockMovementType.DISPENSED, change,
                            skuQty, skuQty,
                            movement.getBatchQuantityAfter(), movement.getBatchQuantityAfter(),
                            actorId, "Reservation fulfilled", reservation.getId());
                }
            }
            reservation.setInventoryHeld(false);
            return;
        }
        deductFefo(sku, requiredQty(reservation), StockMovementType.DISPENSED, actorId, "Reservation fulfilled", reservation.getId());
    }

    @Override
    @Transactional
    public void setSellableQuantity(PharmacyInventory sku, int targetQuantity, Long actorTelegramId, String reason) {
        if (targetQuantity < 0) {
            throw new RuntimeException("quantity must be 0 or greater");
        }
        persistSku(sku);
        ensureBackfillAndSync(sku);
        if (targetQuantity == 0) {
            zeroAllLots(sku, actorTelegramId, reason, StockMovementType.ADJUSTMENT);
            return;
        }
        int current = sellableQuantity(sku);
        int delta = targetQuantity - current;
        if (delta > 0) {
            receive(sku, delta, sku.getBatchNumber(), sku.getExpiryDate(), sku.getPrice(),
                    actorTelegramId, StockMovementType.ADJUSTMENT, reason, null);
        } else if (delta < 0) {
            deductFefo(sku, -delta, StockMovementType.ADJUSTMENT, actorTelegramId, reason, null);
        }
    }

    @Override
    @Transactional
    public void zeroAllLots(PharmacyInventory sku, Long actorTelegramId, String reason, StockMovementType type) {
        persistSku(sku);
        ensureBackfillAndSync(sku);
        LocalDateTime now = LocalDateTime.now();
        int skuBefore = sku.getQuantity() == null ? 0 : sku.getQuantity();
        for (MedicineBatch lot : batchRepository.findByInventoryId(sku.getId())) {
            int lotQty = lot.getQuantity() == null ? 0 : lot.getQuantity();
            if (lotQty <= 0) {
                continue;
            }
            lot.setQuantity(0);
            lot.setUpdatedAt(now);
            batchRepository.save(lot);
            recordMovement(sku, lot, type, -lotQty, skuBefore, 0, lotQty, 0, actorTelegramId, reason, null);
        }
        syncSkuFromLots(sku);
    }

    @Override
    @Transactional
    public void updateLotMetadata(PharmacyInventory sku, String batchNumber, LocalDate expiryDate, boolean clearExpiry) {
        persistSku(sku);
        ensureBackfillAndSync(sku);
        List<MedicineBatch> lots = batchRepository.findByInventoryId(sku.getId());
        MedicineBatch lot = fefoSellableLots(sku, LocalDate.now()).stream().findFirst()
                .orElse(lots.stream().findFirst().orElse(null));
        if (lot == null) {
            if (batchNumber != null) {
                sku.setBatchNumber(blankToNull(batchNumber));
            }
            if (clearExpiry) {
                sku.setExpiryDate(null);
            } else if (expiryDate != null) {
                sku.setExpiryDate(expiryDate);
            }
            inventoryRepository.save(sku);
            return;
        }
        if (batchNumber != null) {
            lot.setBatchNumber(blankToNull(batchNumber));
        }
        if (clearExpiry) {
            lot.setExpiryDate(null);
        } else if (expiryDate != null) {
            lot.setExpiryDate(expiryDate);
        }
        lot.setUpdatedAt(LocalDateTime.now());
        batchRepository.save(lot);
        syncSkuFromLots(sku);
    }

    @Override
    public List<MedicineBatchDTO> listBatches(Long pharmacyId, Long inventoryId) {
        return batchRepository.findByPharmacyIdAndInventoryId(pharmacyId, inventoryId).stream()
                .sorted(fefoComparator())
                .map(this::toBatchDto)
                .toList();
    }

    @Override
    public List<StockMovementDTO> listMovements(Long pharmacyId, Long inventoryId) {
        PharmacyInventory sku = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new RuntimeException("Inventory item not found"));
        if (!pharmacyId.equals(sku.getPharmacyId())) {
            throw new RuntimeException("Inventory item does not belong to this pharmacy");
        }
        Map<Long, String> batchNumbers = batchRepository.findByInventoryId(inventoryId).stream()
                .filter(lot -> lot.getId() != null)
                .collect(Collectors.toMap(MedicineBatch::getId, lot -> lot.getBatchNumber() == null ? "" : lot.getBatchNumber(), (a, b) -> a));
        return movementRepository.findByInventoryIdOrderByCreatedAtDescIdDesc(inventoryId).stream()
                .limit(200)
                .map(movement -> StockMovementDTO.builder()
                        .movementId(movement.getId())
                        .itemId(movement.getInventoryId())
                        .batchId(movement.getBatchId())
                        .batchNumber(movement.getBatchId() == null ? null : batchNumbers.get(movement.getBatchId()))
                        .medicineName(movement.getMedicineName())
                        .movementType(movement.getMovementType() == null ? null : movement.getMovementType().name())
                        .quantityChange(movement.getQuantityChange())
                        .quantityBefore(movement.getQuantityBefore())
                        .quantityAfter(movement.getQuantityAfter())
                        .actorTelegramId(movement.getActorTelegramId())
                        .reason(movement.getReason())
                        .reservationId(movement.getReservationId())
                        .createdAt(movement.getCreatedAt())
                        .build())
                .toList();
    }

    @Override
    public List<MedicineBatchDTO> listExpiry(Long pharmacyId, String bucket) {
        String normalized = bucket == null ? "90" : bucket.trim().toUpperCase().replace('-', '_');
        LocalDate today = LocalDate.now();
        return batchRepository.findByPharmacyId(pharmacyId).stream()
                .filter(lot -> (lot.getQuantity() == null ? 0 : lot.getQuantity()) > 0)
                .filter(lot -> matchesExpiryBucket(lot, normalized, today))
                .sorted(fefoComparator())
                .map(this::toBatchDto)
                .toList();
    }

    @Override
    public Map<Long, Integer> lotCountsByPharmacy(Long pharmacyId) {
        return batchRepository.findByPharmacyId(pharmacyId).stream()
                .collect(Collectors.groupingBy(MedicineBatch::getInventoryId,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
    }

    private void syncSkuFromLots(PharmacyInventory sku) {
        LocalDate today = LocalDate.now();
        List<MedicineBatch> lots = batchRepository.findByInventoryId(sku.getId());
        int sellable = lots.stream()
                .filter(lot -> isSellable(lot, today))
                .mapToInt(lot -> lot.getQuantity() == null ? 0 : lot.getQuantity())
                .sum();
        sku.setQuantity(sellable);
        sku.setOutOfStock(sellable <= 0);
        MedicineBatch nearest = lots.stream()
                .filter(lot -> isSellable(lot, today))
                .min(fefoComparator())
                .orElse(lots.stream()
                        .filter(lot -> (lot.getQuantity() == null ? 0 : lot.getQuantity()) > 0)
                        .min(fefoComparator())
                        .orElse(null));
        if (nearest != null) {
            sku.setBatchNumber(nearest.getBatchNumber());
            sku.setExpiryDate(nearest.getExpiryDate());
        }
        sku.setUpdatedAt(LocalDateTime.now());
        inventoryRepository.save(sku);
    }

    private List<MedicineBatch> fefoSellableLots(PharmacyInventory sku, LocalDate today) {
        List<MedicineBatch> lots = new ArrayList<>(batchRepository.findByInventoryId(sku.getId()));
        lots.removeIf(lot -> !isSellable(lot, today));
        lots.sort(fefoComparator());
        return lots;
    }

    private Comparator<MedicineBatch> fefoComparator() {
        return Comparator
                .comparing((MedicineBatch lot) -> lot.getExpiryDate() == null)
                .thenComparing(MedicineBatch::getExpiryDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(MedicineBatch::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private boolean isSellable(MedicineBatch lot, LocalDate today) {
        int qty = lot.getQuantity() == null ? 0 : lot.getQuantity();
        if (qty <= 0) {
            return false;
        }
        return lot.getExpiryDate() == null || !lot.getExpiryDate().isBefore(today);
    }

    private boolean matchesExpiryBucket(MedicineBatch lot, String bucket, LocalDate today) {
        LocalDate expiry = lot.getExpiryDate();
        if ("MISSING".equals(bucket) || "NONE".equals(bucket) || "NO_EXPIRY".equals(bucket)) {
            return expiry == null;
        }
        if (expiry == null) {
            return false;
        }
        long days = ChronoUnit.DAYS.between(today, expiry);
        return switch (bucket) {
            case "30", "D30", "WITHIN_30" -> days >= 0 && days <= 30;
            case "60", "D60", "WITHIN_60" -> days >= 31 && days <= 60;
            case "90", "D90", "WITHIN_90" -> days >= 61 && days <= 90;
            case "EXPIRED" -> days < 0;
            default -> days >= 0 && days <= 90;
        };
    }

    private MedicineBatchDTO toBatchDto(MedicineBatch lot) {
        LocalDate today = LocalDate.now();
        Long daysRemaining = lot.getExpiryDate() == null ? null : ChronoUnit.DAYS.between(today, lot.getExpiryDate());
        boolean expired = lot.getExpiryDate() != null && lot.getExpiryDate().isBefore(today);
        boolean expiringSoon = daysRemaining != null && !expired && daysRemaining <= 90;
        String warning = expired ? "EXPIRED"
                : lot.getExpiryDate() == null ? "MISSING"
                : daysRemaining <= 30 ? "EXPIRES_30"
                : daysRemaining <= 60 ? "EXPIRES_60"
                : daysRemaining <= 90 ? "EXPIRES_90"
                : "OK";
        return MedicineBatchDTO.builder()
                .batchId(lot.getId())
                .itemId(lot.getInventoryId())
                .medicineName(lot.getMedicineName())
                .batchNumber(lot.getBatchNumber())
                .quantity(lot.getQuantity())
                .expiryDate(lot.getExpiryDate())
                .daysRemaining(daysRemaining)
                .supplier(lot.getSupplier())
                .purchasePrice(lot.getPurchasePrice())
                .sellingPrice(lot.getSellingPrice())
                .receivedAt(lot.getReceivedAt())
                .expired(expired)
                .expiringSoon(expiringSoon)
                .warning(warning)
                .build();
    }

    private void recordMovement(PharmacyInventory sku,
                                MedicineBatch lot,
                                StockMovementType type,
                                int quantityChange,
                                Integer skuBefore,
                                Integer skuAfter,
                                Integer batchBefore,
                                Integer batchAfter,
                                Long actorTelegramId,
                                String reason,
                                Long reservationId) {
        movementRepository.save(StockMovement.builder()
                .pharmacyId(sku.getPharmacyId())
                .inventoryId(sku.getId())
                .batchId(lot == null ? null : lot.getId())
                .medicineName(sku.getMedicineName())
                .movementType(type)
                .quantityChange(quantityChange)
                .quantityBefore(skuBefore)
                .quantityAfter(skuAfter)
                .batchQuantityBefore(batchBefore)
                .batchQuantityAfter(batchAfter)
                .actorTelegramId(actorTelegramId)
                .reason(reason)
                .reservationId(reservationId)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private PharmacyInventory requireSku(MedicineReservation reservation) {
        return inventoryRepository
                .findByPharmacyIdAndMedicineNameIgnoreCase(reservation.getPharmacyId(), reservation.getMedicineName())
                .orElseThrow(() -> new RuntimeException("Medicine inventory not found"));
    }

    private int requiredQty(MedicineReservation reservation) {
        return reservation.getRequestedQuantity() == null ? 0 : reservation.getRequestedQuantity();
    }

    private Long pharmacyActor(Long pharmacyId) {
        return pharmacyRepository.findById(pharmacyId).map(Pharmacy::getTelegramId).orElse(null);
    }

    private void persistSku(PharmacyInventory sku) {
        if (sku.getId() == null) {
            inventoryRepository.save(sku);
        }
    }

    private boolean sameLotKey(MedicineBatch lot, String batchNumber, LocalDate expiryDate) {
        return Objects.equals(blankToNull(lot.getBatchNumber()), batchNumber)
                && Objects.equals(lot.getExpiryDate(), expiryDate);
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
