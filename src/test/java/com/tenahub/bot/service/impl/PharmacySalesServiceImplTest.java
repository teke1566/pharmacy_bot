package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.entity.PharmacySale;
import com.tenahub.bot.entity.PharmacySaleItem;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.repository.PharmacySaleItemRepository;
import com.tenahub.bot.repository.PharmacySaleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PharmacySalesServiceImplTest {

    @Mock
    private PharmacyRepository pharmacyRepository;
    @Mock
    private PharmacyInventoryRepository inventoryRepository;
    @Mock
    private PharmacySaleRepository saleRepository;
    @Mock
    private PharmacySaleItemRepository saleItemRepository;

    private PharmacySalesServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PharmacySalesServiceImpl(
                pharmacyRepository, inventoryRepository, saleRepository, saleItemRepository);
    }

    @Test
    void recordFromReservation_isIdempotent() {
        MedicineReservation reservation = MedicineReservation.builder()
                .id(44L)
                .pharmacyId(3L)
                .medicineName("insulin")
                .requestedQuantity(2)
                .customerName("Abel")
                .build();
        when(saleRepository.findByReservationId(44L))
                .thenReturn(Optional.of(PharmacySale.builder().id(1L).reservationId(44L).build()));

        service.recordFromReservation(reservation, 9001L);

        verify(saleRepository, never()).save(any());
        verify(saleItemRepository, never()).save(any());
    }

    @Test
    void recordFromReservation_writesSaleAndLine() {
        MedicineReservation reservation = MedicineReservation.builder()
                .id(44L)
                .pharmacyId(3L)
                .medicineName("insulin")
                .requestedQuantity(2)
                .customerName("Abel")
                .build();
        when(saleRepository.findByReservationId(44L)).thenReturn(Optional.empty());
        when(inventoryRepository.findByPharmacyIdAndMedicineNameIgnoreCase(3L, "insulin"))
                .thenReturn(Optional.of(PharmacyInventory.builder()
                        .pharmacyId(3L)
                        .medicineName("insulin")
                        .price(new BigDecimal("10.00"))
                        .currency("ETB")
                        .build()));
        when(saleRepository.save(any(PharmacySale.class))).thenAnswer(invocation -> {
            PharmacySale sale = invocation.getArgument(0);
            sale.setId(88L);
            return sale;
        });

        service.recordFromReservation(reservation, 9001L);

        ArgumentCaptor<PharmacySale> saleCaptor = ArgumentCaptor.forClass(PharmacySale.class);
        verify(saleRepository).save(saleCaptor.capture());
        assertEquals(44L, saleCaptor.getValue().getReservationId());
        assertEquals(new BigDecimal("20.00"), saleCaptor.getValue().getTotalAmount());

        ArgumentCaptor<PharmacySaleItem> itemCaptor = ArgumentCaptor.forClass(PharmacySaleItem.class);
        verify(saleItemRepository).save(itemCaptor.capture());
        assertEquals("insulin", itemCaptor.getValue().getMedicineName());
        assertEquals(2, itemCaptor.getValue().getQuantity());
        assertEquals(88L, itemCaptor.getValue().getSaleId());
    }

    @Test
    void summary_usesOwnPharmacyOnly() {
        when(pharmacyRepository.findByTelegramId(9001L))
                .thenReturn(Optional.of(Pharmacy.builder().id(3L).telegramId(9001L).build()));
        when(saleRepository.findByPharmacyIdAndCreatedAtBetweenOrderByCreatedAtDesc(any(), any(), any()))
                .thenReturn(java.util.List.of());

        var summary = service.summary(9001L, "daily");

        assertEquals("daily", summary.getPeriod());
        assertEquals(0, summary.getSaleCount());
        verify(saleRepository).findByPharmacyIdAndCreatedAtBetweenOrderByCreatedAtDesc(any(), any(), any());
    }
}
