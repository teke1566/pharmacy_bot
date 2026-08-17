package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.MedicineReservationStatus;
import com.tenahub.bot.entity.PharmacyRegistration;
import com.tenahub.bot.repository.MedicineReservationRepository;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyRegistrationRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.MedicineLotService;
import com.tenahub.bot.service.PharmacySalesService;
import com.tenahub.bot.service.ReservationStatusHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceImplTest {

    @Mock
    private PharmacyRegistrationRepository registrationRepository;
    @Mock
    private PharmacyRepository pharmacyRepository;
    @Mock
    private MedicineReservationRepository reservationRepository;
    @Mock
    private PharmacyInventoryRepository pharmacyInventoryRepository;
    @Mock
    private MedicineLotService medicineLotService;
    @Mock
    private PharmacySalesService pharmacySalesService;
    @Mock
    private ReservationStatusHistoryService reservationStatusHistoryService;

    @InjectMocks
    private AdminServiceImpl service;

    @Test
    void viewPendingRegistrations_returnsEmptyMessage() {
        when(registrationRepository.findByStatusOrderByIdDesc("PENDING")).thenReturn(List.of());

        assertEquals("🆕 No pending registrations.", service.viewPendingRegistrations());
    }

    @Test
    void viewPendingRegistrations_includesRegistrationFields() {
        PharmacyRegistration registration = PharmacyRegistration.builder()
                .id(12L)
                .name("City Rx")
                .city("Addis")
                .area("Bole")
                .phone("0911")
                .medicines("paracetamol")
                .openTime("08:00")
                .closeTime("20:00")
                .telegramId(77L)
                .build();
        when(registrationRepository.findByStatusOrderByIdDesc("PENDING")).thenReturn(List.of(registration));

        String card = service.viewPendingRegistrations();

        assertTrue(card.contains("Pending Registrations"));
        assertTrue(card.contains("City Rx"));
        assertTrue(card.contains("77"));
        assertTrue(card.contains("Not uploaded"));
    }

    @Test
    void adminCancelReservation_throwsForTerminalState() {
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(
                MedicineReservation.builder().id(5L).status(MedicineReservationStatus.FULFILLED).build()));

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.adminCancelReservation(5L));
        assertTrue(error.getMessage().contains("terminal state"));
    }

    @Test
    void adminCancelReservation_cancelsActiveReservation() {
        MedicineReservation reservation = MedicineReservation.builder()
                .id(5L)
                .status(MedicineReservationStatus.PENDING)
                .inventoryHeld(false)
                .build();
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(reservation));

        service.adminCancelReservation(5L);

        ArgumentCaptor<MedicineReservation> captor = ArgumentCaptor.forClass(MedicineReservation.class);
        verify(reservationRepository).save(captor.capture());
        assertEquals(MedicineReservationStatus.CANCELLED, captor.getValue().getStatus());
        assertEquals("ADMIN_FORCED_CANCEL", captor.getValue().getNote());
    }

    @Test
    void adminFulfillReservation_recordsSale() {
        MedicineReservation reservation = MedicineReservation.builder()
                .id(9L)
                .status(MedicineReservationStatus.APPROVED)
                .requestedQuantity(2)
                .build();
        when(reservationRepository.findById(9L)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(MedicineReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.adminFulfillReservation(9L);

        ArgumentCaptor<MedicineReservation> captor = ArgumentCaptor.forClass(MedicineReservation.class);
        verify(reservationRepository).save(captor.capture());
        assertEquals(MedicineReservationStatus.FULFILLED, captor.getValue().getStatus());
        verify(pharmacySalesService).recordFromReservation(captor.getValue(), null);
    }
}
