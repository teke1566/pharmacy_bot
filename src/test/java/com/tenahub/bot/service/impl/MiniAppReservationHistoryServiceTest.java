package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.MedicineReservationStatus;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.repository.MedicineReservationRepository;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyRepository;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MiniAppReservationHistoryServiceTest {

    @Mock
    private MedicineReservationRepository medicineReservationRepository;
    @Mock
    private PharmacyRepository pharmacyRepository;
    @Mock
    private PharmacyInventoryRepository pharmacyInventoryRepository;

    private MiniAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MiniAppServiceImpl();
        ReflectionTestUtils.setField(service, "medicineReservationRepository", medicineReservationRepository);
        ReflectionTestUtils.setField(service, "pharmacyRepository", pharmacyRepository);
        ReflectionTestUtils.setField(service, "pharmacyInventoryRepository", pharmacyInventoryRepository);
    }

    @Test
    void getReservationHistory_excludesHiddenRows() {
        Long userId = 11L;
        MedicineReservation visible = historyReservation(21L, userId, MedicineReservationStatus.FULFILLED, null);
        MedicineReservation hidden = historyReservation(22L, userId, MedicineReservationStatus.CANCELLED, LocalDateTime.now());
        when(medicineReservationRepository.findByUserIdAndStatusInOrderByCreatedAtDesc(eq(userId), anyList()))
                .thenReturn(List.of(visible, hidden));
        when(pharmacyRepository.findById(5L)).thenReturn(Optional.of(Pharmacy.builder().id(5L).name("City").build()));
        when(pharmacyInventoryRepository.findByPharmacyIdAndMedicineNameIgnoreCase(5L, "paracetamol"))
                .thenReturn(Optional.empty());

        var cards = service.getReservationHistory(userId);

        assertEquals(1, cards.size());
        assertEquals(21L, cards.get(0).getReservationId());
    }

    @Test
    void hideReservationFromHistory_setsHiddenFromUserAt() {
        Long userId = 11L;
        MedicineReservation reservation = historyReservation(31L, userId, MedicineReservationStatus.EXPIRED, null);
        when(medicineReservationRepository.findById(31L)).thenReturn(Optional.of(reservation));
        when(medicineReservationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.hideReservationFromHistory(31L, userId);

        assertTrue(response.isSuccess());
        assertNotNull(reservation.getHiddenFromUserAt());
        verify(medicineReservationRepository).saveAll(List.of(reservation));
    }

    @Test
    void hideReservationFromHistory_rejectsOtherUsersReservation() {
        MedicineReservation reservation = historyReservation(32L, 99L, MedicineReservationStatus.FULFILLED, null);
        when(medicineReservationRepository.findById(32L)).thenReturn(Optional.of(reservation));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.hideReservationFromHistory(32L, 11L));

        assertEquals("Reservation does not belong to this user", ex.getMessage());
        assertNull(reservation.getHiddenFromUserAt());
        verify(medicineReservationRepository, never()).saveAll(anyList());
    }

    @Test
    void hideReservationFromHistory_rejectsActiveReservation() {
        MedicineReservation reservation = historyReservation(33L, 11L, MedicineReservationStatus.PENDING, null);
        when(medicineReservationRepository.findById(33L)).thenReturn(Optional.of(reservation));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.hideReservationFromHistory(33L, 11L));

        assertEquals("Only completed reservations can be removed from history", ex.getMessage());
        verify(medicineReservationRepository, never()).saveAll(anyList());
    }

    @Test
    void hideReservationFromHistory_hidesWholeGroup() {
        Long userId = 11L;
        MedicineReservation first = historyReservation(41L, userId, MedicineReservationStatus.FULFILLED, null);
        first.setReservationGroupId("group-1");
        MedicineReservation second = historyReservation(42L, userId, MedicineReservationStatus.FULFILLED, null);
        second.setReservationGroupId("group-1");
        when(medicineReservationRepository.findById(41L)).thenReturn(Optional.of(first));
        when(medicineReservationRepository.findByReservationGroupId("group-1")).thenReturn(List.of(first, second));
        when(medicineReservationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        service.hideReservationFromHistory(41L, userId);

        assertNotNull(first.getHiddenFromUserAt());
        assertNotNull(second.getHiddenFromUserAt());
    }

    @Test
    void clearReservationHistory_hidesOnlyHistoryStatusesForUser() {
        Long userId = 11L;
        MedicineReservation fulfilled = historyReservation(51L, userId, MedicineReservationStatus.FULFILLED, null);
        MedicineReservation cancelled = historyReservation(52L, userId, MedicineReservationStatus.CANCELLED, null);
        when(medicineReservationRepository.findByUserIdAndStatusIn(eq(userId), anyList()))
                .thenReturn(List.of(fulfilled, cancelled));
        when(medicineReservationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.clearReservationHistory(userId);

        assertTrue(response.isSuccess());
        assertNotNull(fulfilled.getHiddenFromUserAt());
        assertNotNull(cancelled.getHiddenFromUserAt());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MedicineReservationStatus>> statusesCaptor = ArgumentCaptor.forClass(List.class);
        verify(medicineReservationRepository).findByUserIdAndStatusIn(eq(userId), statusesCaptor.capture());
        List<MedicineReservationStatus> statuses = statusesCaptor.getValue();
        assertTrue(statuses.contains(MedicineReservationStatus.FULFILLED));
        assertTrue(statuses.contains(MedicineReservationStatus.EXPIRED));
        assertTrue(statuses.contains(MedicineReservationStatus.REJECTED));
        assertTrue(statuses.contains(MedicineReservationStatus.CANCELLED));
        assertTrue(!statuses.contains(MedicineReservationStatus.PENDING));
        assertTrue(!statuses.contains(MedicineReservationStatus.APPROVED));
        assertTrue(!statuses.contains(MedicineReservationStatus.READY_FOR_PICKUP));
    }

    private MedicineReservation historyReservation(Long id,
                                                   Long userId,
                                                   MedicineReservationStatus status,
                                                   LocalDateTime hiddenFromUserAt) {
        return MedicineReservation.builder()
                .id(id)
                .userId(userId)
                .pharmacyId(5L)
                .medicineName("paracetamol")
                .requestedQuantity(1)
                .status(status)
                .createdAt(LocalDateTime.now().minusHours(2))
                .hiddenFromUserAt(hiddenFromUserAt)
                .build();
    }
}
