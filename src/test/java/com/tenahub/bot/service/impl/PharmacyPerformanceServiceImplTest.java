package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.PharmacyPerformanceReportDTO;
import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.MedicineReservationStatus;
import com.tenahub.bot.entity.MedicineSearchLog;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.entity.PharmacySale;
import com.tenahub.bot.repository.MedicineReservationRepository;
import com.tenahub.bot.repository.MedicineSearchLogRepository;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.repository.PharmacySaleItemRepository;
import com.tenahub.bot.repository.PharmacySaleRepository;
import com.tenahub.bot.util.DemandLabeler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PharmacyPerformanceServiceImplTest {

    @Mock
    private PharmacyRepository pharmacyRepository;
    @Mock
    private MedicineReservationRepository reservationRepository;
    @Mock
    private PharmacyInventoryRepository inventoryRepository;
    @Mock
    private PharmacySaleRepository pharmacySaleRepository;
    @Mock
    private PharmacySaleItemRepository pharmacySaleItemRepository;
    @Mock
    private MedicineSearchLogRepository searchLogRepository;

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
                PharmacyInventory.builder().quantity(0).outOfStock(true).medicineName("a").build(),
                PharmacyInventory.builder().quantity(3).outOfStock(false).lowStockThreshold(10).medicineName("b").build(),
                PharmacyInventory.builder().quantity(40).outOfStock(false).updatedAt(now).medicineName("c").build()
        ));
        when(pharmacySaleRepository.findByPharmacyIdAndCreatedAtBetweenOrderByCreatedAtDesc(eq(3L), any(), any()))
                .thenReturn(List.of());
        when(searchLogRepository.findBySearchedAtBetween(any(), any())).thenReturn(List.of());

        String card = service.buildPerformanceCard(9001L);

        assertTrue(card.contains("Pharmacy Performance"));
        assertTrue(card.contains("Health:"));
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

    @Test
    void getPerformanceReport_emptyPharmacy_neutralHealth() {
        Pharmacy pharmacy = Pharmacy.builder().id(9L).telegramId(55L).build();
        when(pharmacyRepository.findByTelegramId(55L)).thenReturn(Optional.of(pharmacy));
        when(reservationRepository.findByPharmacyIdOrderByCreatedAtDesc(9L)).thenReturn(List.of());
        when(inventoryRepository.findByPharmacyId(9L)).thenReturn(List.of());
        when(pharmacySaleRepository.findByPharmacyIdAndCreatedAtBetweenOrderByCreatedAtDesc(eq(9L), any(), any()))
                .thenReturn(List.of());
        when(searchLogRepository.findBySearchedAtBetween(any(), any())).thenReturn(List.of());

        PharmacyPerformanceReportDTO report = service.getPerformanceReport(55L, "weekly");

        assertEquals("weekly", report.getPeriod());
        assertEquals(50, report.getHealthScore());
        assertEquals("C", report.getHealthGrade());
        assertEquals(0, report.getReservations().getTotal());
        assertEquals(0, report.getSales().getSaleCount());
    }

    @Test
    void getPerformanceReport_includesSalesAndHotDemandLabel() {
        Pharmacy pharmacy = Pharmacy.builder().id(3L).telegramId(9001L).build();
        when(pharmacyRepository.findByTelegramId(9001L)).thenReturn(Optional.of(pharmacy));
        when(reservationRepository.findByPharmacyIdOrderByCreatedAtDesc(3L)).thenReturn(List.of());
        when(inventoryRepository.findByPharmacyId(3L)).thenReturn(List.of(
                PharmacyInventory.builder().medicineName("amoxicillin").quantity(0).outOfStock(true).build()
        ));
        when(pharmacySaleRepository.findByPharmacyIdAndCreatedAtBetweenOrderByCreatedAtDesc(eq(3L), any(), any()))
                .thenReturn(List.of(PharmacySale.builder()
                        .id(1L)
                        .pharmacyId(3L)
                        .totalAmount(new BigDecimal("120.00"))
                        .createdAt(LocalDateTime.now())
                        .build()));
        when(pharmacySaleItemRepository.findByPharmacyIdAndSaleIdIn(eq(3L), any()))
                .thenReturn(List.of());

        MedicineSearchLog log = new MedicineSearchLog();
        log.setMedicineName("amoxicillin");
        log.setUserId(1L);
        log.setSearchedAt(LocalDateTime.now());
        when(searchLogRepository.findBySearchedAtBetween(any(), any()))
                .thenReturn(List.of(log, log, log, log, log, log, log, log));

        PharmacyPerformanceReportDTO report = service.getPerformanceReport(9001L, "daily");

        assertEquals("daily", report.getPeriod());
        assertEquals(1, report.getSales().getSaleCount());
        assertEquals(0, new BigDecimal("120.00").compareTo(report.getSales().getRevenue()));
        assertEquals(1, report.getTopDemand().size());
        assertEquals("HOT", report.getTopDemand().get(0).getDemandLabel());
        assertTrue(report.getHealthScore() >= 0 && report.getHealthScore() <= 100);
    }

    @Test
    void demandLabeler_rules() {
        assertEquals("HOT", DemandLabeler.label(8, true, false));
        assertEquals("RISING", DemandLabeler.label(4, false, false));
        assertEquals("STEADY", DemandLabeler.label(2, false, false));
        assertEquals("COLD", DemandLabeler.label(0, false, false));
    }
}
