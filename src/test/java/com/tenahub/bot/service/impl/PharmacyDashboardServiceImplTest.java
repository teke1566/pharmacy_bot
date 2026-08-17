package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.MedicineBatchDTO;
import com.tenahub.bot.dto.PharmacyDashboardDTO;
import com.tenahub.bot.dto.PharmacyMiniAppInventoryItemDTO;
import com.tenahub.bot.dto.PharmacySalesSummaryDTO;
import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.MedicineReservationStatus;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PrescriptionReviewStatus;
import com.tenahub.bot.repository.MedicineReservationRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.InventoryService;
import com.tenahub.bot.service.PharmacyNotificationService;
import com.tenahub.bot.service.PharmacySalesService;
import com.tenahub.bot.service.ReservationService;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PharmacyDashboardServiceImplTest {

    @Mock
    private PharmacyRepository pharmacyRepository;
    @Mock
    private MedicineReservationRepository reservationRepository;
    @Mock
    private ReservationService reservationService;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private PharmacySalesService pharmacySalesService;
    @Mock
    private PharmacyNotificationService pharmacyNotificationService;

    @InjectMocks
    private PharmacyDashboardServiceImpl service;

    @Test
    void getDashboard_aggregatesCountsFromExistingServices() {
        Pharmacy pharmacy = Pharmacy.builder().id(3L).telegramId(9001L).build();
        when(pharmacyRepository.findByTelegramId(9001L)).thenReturn(Optional.of(pharmacy));

        when(reservationService.getPendingReservations(9001L)).thenReturn(List.of(
                MedicineReservation.builder().id(1L).build(),
                MedicineReservation.builder().id(2L).build()
        ));
        when(reservationService.getPrescriptionReservations(9001L)).thenReturn(List.of(
                MedicineReservation.builder().id(10L).prescriptionReviewStatus(PrescriptionReviewStatus.PENDING_REVIEW).build(),
                MedicineReservation.builder().id(11L).prescriptionReviewStatus(PrescriptionReviewStatus.NEEDS_CLARIFICATION).build(),
                MedicineReservation.builder().id(12L).prescriptionReviewStatus(PrescriptionReviewStatus.APPROVED).build()
        ));

        LocalDateTime today = LocalDateTime.now();
        when(reservationRepository.findByPharmacyIdOrderByCreatedAtDesc(3L)).thenReturn(List.of(
                MedicineReservation.builder()
                        .status(MedicineReservationStatus.FULFILLED)
                        .fulfilledAt(today.minusHours(2))
                        .build(),
                MedicineReservation.builder()
                        .status(MedicineReservationStatus.FULFILLED)
                        .fulfilledAt(today.minusDays(2))
                        .build(),
                MedicineReservation.builder()
                        .status(MedicineReservationStatus.PENDING)
                        .build()
        ));

        when(pharmacySalesService.summary(9001L, "daily")).thenReturn(PharmacySalesSummaryDTO.builder()
                .revenue(new BigDecimal("1250.50"))
                .saleCount(4)
                .build());

        when(inventoryService.getPharmacyMiniAppInventory(9001L)).thenReturn(List.of(
                PharmacyMiniAppInventoryItemDTO.builder().inStock(true).outOfStock(false).lowStock(false).build(),
                PharmacyMiniAppInventoryItemDTO.builder().inStock(true).outOfStock(false).lowStock(true).build(),
                PharmacyMiniAppInventoryItemDTO.builder().inStock(false).outOfStock(true).lowStock(false).build()
        ));
        when(inventoryService.listExpiryBatches(9001L, "30")).thenReturn(List.of(
                MedicineBatchDTO.builder().batchId(1L).build(),
                MedicineBatchDTO.builder().batchId(2L).build()
        ));
        when(pharmacyNotificationService.unreadCount(9001L)).thenReturn(7L);

        PharmacyDashboardDTO dto = service.getDashboard(9001L);

        assertEquals(2, dto.getPendingPrescriptions());
        assertEquals(2, dto.getPendingReservations());
        assertEquals(1, dto.getFulfilledToday());
        assertEquals(new BigDecimal("1250.50"), dto.getTodayRevenue());
        assertEquals(4, dto.getTodaySaleCount());
        assertEquals(3, dto.getTotalItems());
        assertEquals(2, dto.getInStock());
        assertEquals(1, dto.getLowStock());
        assertEquals(1, dto.getOutOfStock());
        assertEquals(2, dto.getExpiringSoon());
        assertEquals(7L, dto.getUnreadNotifications());
    }

    @Test
    void getDashboard_throwsWhenPharmacyMissing() {
        when(pharmacyRepository.findByTelegramId(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.getDashboard(1L));
    }
}
