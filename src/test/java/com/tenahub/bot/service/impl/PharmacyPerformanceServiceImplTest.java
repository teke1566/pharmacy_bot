package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.MedicineReservationStatus;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.repository.MedicineReservationRepository;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PharmacyPerformanceServiceImplTest {

    @Mock
    private PharmacyRepository pharmacyRepository;
    @Mock
    private MedicineReservationRepository reservationRepository;
    @Mock
    private PharmacyInventoryRepository inventoryRepository;

    @InjectMocks
    private PharmacyPerformanceServiceImpl service;

    @Test
    void buildPerformanceCard_includesWindowAndStockCounts() {
        Pharmacy pharmacy = Pharmacy.builder().id(3L).telegramId(9001L).build();
        when(pharmacyRepository.findByTelegramId(9001L)).thenReturn(Optional.of(pharmacy));

        LocalDateTime now = LocalDateTime.now();
        MedicineReservation approved = MedicineReservation.builder()
                .status(MedicineReservationStatus.APPROVED)
                .createdAt(now.minusMinutes(20))
                .approvedAt(now.minusMinutes(10))
                .build();
        when(reservationRepository.findByPharmacyIdOrderByCreatedAtDesc(3L)).thenReturn(List.of(approved));
        when(inventoryRepository.findByPharmacyId(3L)).thenReturn(List.of(
                PharmacyInventory.builder().quantity(0).outOfStock(true).build(),
                PharmacyInventory.builder().quantity(3).outOfStock(false).lowStockThreshold(10).build(),
                PharmacyInventory.builder().quantity(40).outOfStock(false).updatedAt(now).build()
        ));

        String card = service.buildPerformanceCard(9001L);

        assertTrue(card.contains("Pharmacy Performance"));
        assertTrue(card.contains("Requests: 1"));
        assertTrue(card.contains("Approved: 1"));
        assertTrue(card.contains("Out of Stock Items: 1"));
        assertTrue(card.contains("Low Stock Items: 1"));
        assertTrue(card.contains("Medicines Updated Today: 1"));
    }

    @Test
    void buildPerformanceCard_throwsWhenPharmacyMissing() {
        when(pharmacyRepository.findByTelegramId(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.buildPerformanceCard(1L));
    }
}
