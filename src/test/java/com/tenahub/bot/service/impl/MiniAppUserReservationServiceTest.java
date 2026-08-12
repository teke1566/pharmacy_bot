package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.MiniAppReservationConfirmRequestDTO;
import com.tenahub.bot.dto.MiniAppReservationCreateRequestDTO;
import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.MedicineReservationStatus;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.repository.MedicineReservationRepository;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.MiniAppAuthException;
import com.tenahub.bot.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MiniAppUserReservationServiceTest {

    @Mock
    private MedicineReservationRepository medicineReservationRepository;
    @Mock
    private PharmacyRepository pharmacyRepository;
    @Mock
    private PharmacyInventoryRepository pharmacyInventoryRepository;
    @Mock
    private ReservationService reservationService;

    private MiniAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MiniAppServiceImpl();
        ReflectionTestUtils.setField(service, "medicineReservationRepository", medicineReservationRepository);
        ReflectionTestUtils.setField(service, "pharmacyRepository", pharmacyRepository);
        ReflectionTestUtils.setField(service, "pharmacyInventoryRepository", pharmacyInventoryRepository);
        ReflectionTestUtils.setField(service, "reservationService", reservationService);
    }

    @Test
    void getActiveReservations_returnsPendingAndApprovedAndSkipsHistoryStatuses() {
        Long userId = 11L;
        MedicineReservation pending = reservation(1L, userId, MedicineReservationStatus.PENDING);
        MedicineReservation approved = reservation(2L, userId, MedicineReservationStatus.READY_FOR_PICKUP);
        when(medicineReservationRepository.findByUserIdAndStatusInOrderByCreatedAtDesc(eq(userId), anyList()))
                .thenReturn(List.of(pending, approved));
        when(pharmacyRepository.findById(5L)).thenReturn(Optional.of(Pharmacy.builder().id(5L).name("City").build()));
        when(pharmacyInventoryRepository.findByPharmacyIdAndMedicineNameIgnoreCase(5L, "paracetamol"))
                .thenReturn(Optional.empty());

        var cards = service.getActiveReservations(userId);

        assertEquals(2, cards.size());
        assertTrue(cards.stream().anyMatch(card -> Long.valueOf(1L).equals(card.getReservationId())));
        assertTrue(cards.stream().anyMatch(card -> Long.valueOf(2L).equals(card.getReservationId())));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MedicineReservationStatus>> statuses = ArgumentCaptor.forClass(List.class);
        verify(medicineReservationRepository).findByUserIdAndStatusInOrderByCreatedAtDesc(eq(userId), statuses.capture());
        assertTrue(statuses.getValue().contains(MedicineReservationStatus.PENDING));
        assertTrue(statuses.getValue().contains(MedicineReservationStatus.APPROVED));
        assertTrue(statuses.getValue().contains(MedicineReservationStatus.READY_FOR_PICKUP));
        assertTrue(!statuses.getValue().contains(MedicineReservationStatus.FULFILLED));
        assertTrue(!statuses.getValue().contains(MedicineReservationStatus.CANCELLED));
    }

    @Test
    void cancelReservation_delegatesToReservationService() {
        var response = service.cancelReservation(15L, 11L);

        assertTrue(response.isSuccess());
        verify(reservationService).cancelReservationByUser(11L, 15L);
    }

    @Test
    void confirmReservation_requiresInitData() {
        MiniAppReservationConfirmRequestDTO request = MiniAppReservationConfirmRequestDTO.builder()
                .pharmacyId(1L)
                .medicineId(2L)
                .quantity(1)
                .phone("+251911000000")
                .telegramUserId(11L)
                .build();

        MiniAppAuthException error = assertThrows(MiniAppAuthException.class, () -> service.confirmReservation(request));
        assertEquals("Telegram initData is required", error.getMessage());
    }

    @Test
    void createReservation_requiresInitData() {
        MiniAppReservationCreateRequestDTO request = MiniAppReservationCreateRequestDTO.builder()
                .userId(11L)
                .pharmacyId(1L)
                .medicineName("paracetamol")
                .quantity(1)
                .build();

        MiniAppAuthException error = assertThrows(MiniAppAuthException.class, () -> service.createReservation(request));
        assertEquals("Telegram initData is required", error.getMessage());
    }

    private MedicineReservation reservation(Long id, Long userId, MedicineReservationStatus status) {
        return MedicineReservation.builder()
                .id(id)
                .userId(userId)
                .pharmacyId(5L)
                .medicineName("paracetamol")
                .requestedQuantity(1)
                .status(status)
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .build();
    }
}
