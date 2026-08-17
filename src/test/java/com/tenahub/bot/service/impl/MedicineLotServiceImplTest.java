package com.tenahub.bot.service.impl;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class MedicineLotServiceImplTest {

    @Mock
    private PharmacyInventoryRepository inventoryRepository;
    @Mock
    private MedicineBatchRepository batchRepository;
    @Mock
    private StockMovementRepository movementRepository;
    @Mock
    private PharmacyRepository pharmacyRepository;

    private MedicineLotServiceImpl service;
    private final List<MedicineBatch> lots = new ArrayList<>();
    private final List<StockMovement> movements = new ArrayList<>();
    private final AtomicLong lotIds = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        service = new MedicineLotServiceImpl(inventoryRepository, batchRepository, movementRepository, pharmacyRepository);
        lots.clear();
        movements.clear();
        when(batchRepository.findByInventoryId(any())).thenAnswer(invocation -> new ArrayList<>(lots));
        when(batchRepository.save(any(MedicineBatch.class))).thenAnswer(invocation -> {
            MedicineBatch lot = invocation.getArgument(0);
            if (lot.getId() == null) {
                lot.setId(lotIds.getAndIncrement());
                lots.add(lot);
            } else {
                lots.removeIf(existing -> existing.getId().equals(lot.getId()));
                lots.add(lot);
            }
            return lot;
        });
        when(batchRepository.findById(any())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return lots.stream().filter(lot -> lot.getId().equals(id)).findFirst();
        });
        when(movementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            movements.add(movement);
            return movement;
        });
        when(inventoryRepository.save(any(PharmacyInventory.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void deductFefo_picksEarliestValidExpiryFirst() {
        PharmacyInventory sku = sku(10);
        lots.add(lot(1L, "LATE", LocalDate.now().plusDays(90), 6));
        lots.add(lot(2L, "EARLY", LocalDate.now().plusDays(10), 4));

        service.deductFefo(sku, 5, StockMovementType.DISPENSED, 11L, "Pickup", 99L);

        assertEquals(0, lotById(2L).getQuantity());
        assertEquals(5, lotById(1L).getQuantity());
        assertEquals(5, sku.getQuantity());
        assertTrue(movements.stream().anyMatch(m -> m.getMovementType() == StockMovementType.DISPENSED));
        assertTrue(movements.stream().allMatch(m -> m.getReason() != null));
    }

    @Test
    void deductFefo_skipsExpiredLotsAndThrowsWhenOnlyExpiredRemain() {
        PharmacyInventory sku = sku(8);
        lots.add(lot(1L, "EXP", LocalDate.now().minusDays(1), 8));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.deductFefo(sku, 1, StockMovementType.DISPENSED, 11L, "Pickup", null));
        assertEquals("Cannot dispense expired medicine.", error.getMessage());
        assertEquals(8, lotById(1L).getQuantity());
    }

    @Test
    void deductFefo_doesNotGoNegative() {
        PharmacyInventory sku = sku(2);
        lots.add(lot(1L, "A", LocalDate.now().plusDays(20), 2));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.deductFefo(sku, 5, StockMovementType.DISPENSED, 11L, "Pickup", null));
        assertEquals("Requested quantity exceeds available stock.", error.getMessage());
        assertEquals(2, lotById(1L).getQuantity());
    }

    @Test
    void receive_createsMovementAndIncrementsMatchingLot() {
        PharmacyInventory sku = sku(0);
        service.receive(sku, 12, "INS-1", LocalDate.now().plusDays(40), new BigDecimal("50"),
                11L, StockMovementType.RECEIVED, "Stock received", null);
        service.receive(sku, 3, "INS-1", LocalDate.now().plusDays(40), new BigDecimal("50"),
                11L, StockMovementType.RECEIVED, "Stock received", null);

        assertEquals(1, lots.size());
        assertEquals(15, lots.get(0).getQuantity());
        assertEquals(15, sku.getQuantity());
        assertEquals(2, movements.size());
        assertEquals(StockMovementType.RECEIVED, movements.get(0).getMovementType());
    }

    @Test
    void holdAndRelease_restoresTheSameLot() {
        PharmacyInventory sku = sku(10);
        lots.add(lot(3L, "A", LocalDate.now().plusDays(5), 10));
        when(inventoryRepository.findByPharmacyIdAndMedicineNameIgnoreCase(1L, "insulin"))
                .thenReturn(Optional.of(sku));
        when(pharmacyRepository.findById(1L)).thenReturn(Optional.of(Pharmacy.builder().id(1L).telegramId(11L).build()));
        when(movementRepository.findByReservationIdAndMovementType(44L, StockMovementType.HELD))
                .thenAnswer(invocation -> movements.stream()
                        .filter(m -> m.getMovementType() == StockMovementType.HELD)
                        .toList());

        MedicineReservation reservation = MedicineReservation.builder()
                .id(44L)
                .pharmacyId(1L)
                .medicineName("insulin")
                .requestedQuantity(4)
                .inventoryHeld(false)
                .build();

        service.holdForReservation(reservation);
        assertTrue(reservation.isInventoryHeld());
        assertEquals(6, sku.getQuantity());

        service.releaseHeldForReservation(reservation);
        assertEquals(10, sku.getQuantity());
        assertTrue(movements.stream().anyMatch(m -> m.getMovementType() == StockMovementType.RELEASED));
    }

    @Test
    void listExpiry_filtersThirtyDayBucket() {
        PharmacyInventory sku = sku(5);
        lots.add(lot(1L, "SOON", LocalDate.now().plusDays(12), 2));
        lots.add(lot(2L, "LATER", LocalDate.now().plusDays(70), 3));
        when(batchRepository.findByPharmacyId(1L)).thenReturn(lots);

        var soon = service.listExpiry(1L, "30");
        assertEquals(1, soon.size());
        assertEquals("SOON", soon.get(0).getBatchNumber());
        assertTrue(soon.get(0).getDaysRemaining() <= 30);
    }

    @Test
    void listMovements_rejectsOtherPharmacy() {
        when(inventoryRepository.findById(100L)).thenReturn(Optional.of(
                PharmacyInventory.builder().id(100L).pharmacyId(9L).medicineName("x").build()));

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.listMovements(1L, 100L));
        assertTrue(error.getMessage().contains("does not belong"));
    }

    private PharmacyInventory sku(int qty) {
        return PharmacyInventory.builder()
                .id(100L)
                .pharmacyId(1L)
                .medicineName("insulin")
                .quantity(qty)
                .outOfStock(qty <= 0)
                .price(new BigDecimal("10"))
                .build();
    }

    private MedicineBatch lot(Long id, String batch, LocalDate expiry, int qty) {
        return MedicineBatch.builder()
                .id(id)
                .pharmacyId(1L)
                .inventoryId(100L)
                .medicineName("insulin")
                .batchNumber(batch)
                .expiryDate(expiry)
                .quantity(qty)
                .build();
    }

    private MedicineBatch lotById(Long id) {
        return lots.stream().filter(lot -> lot.getId().equals(id)).findFirst().orElseThrow();
    }
}
