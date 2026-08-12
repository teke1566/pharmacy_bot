package com.tenahub.bot.controller;

import com.tenahub.bot.dto.PharmacyMiniAppReservationDTO;
import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.MedicineReservationStatus;
import com.tenahub.bot.service.MiniAppActorResolver;
import com.tenahub.bot.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PharmacyMiniAppReservationsControllerTest {

    @Mock
    private ReservationService reservationService;
    @Mock
    private MiniAppActorResolver miniAppActorResolver;

    private PharmacyMiniAppReservationsController controller;

    @BeforeEach
    void setUp() {
        controller = new PharmacyMiniAppReservationsController(reservationService, miniAppActorResolver);
        when(miniAppActorResolver.requirePharmacyTelegramId(9001L, null)).thenReturn(9001L);
    }

    @Test
    void getAllReservations_pendingUsesPendingQuery() {
        when(reservationService.getPendingReservations(9001L)).thenReturn(List.of(
                MedicineReservation.builder().id(1L).status(MedicineReservationStatus.PENDING).build()));

        ResponseEntity<?> response = controller.getAllReservations(9001L, null, "pending");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(reservationService).getPendingReservations(9001L);
    }

    @Test
    void approveReservation_usesResolverIdAndOwnership() {
        MedicineReservation approved = MedicineReservation.builder()
                .id(4L)
                .status(MedicineReservationStatus.READY_FOR_PICKUP)
                .build();
        when(reservationService.approveReservationAndNotify(4L)).thenReturn(approved);

        ResponseEntity<?> response = controller.approveReservation(4L, 9001L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(reservationService).assertPharmacyOwnsReservation(4L, 9001L);
        verify(reservationService).approveReservationAndNotify(4L);
        PharmacyMiniAppReservationDTO body = (PharmacyMiniAppReservationDTO) response.getBody();
        assertEquals(4L, body.getReservationId());
    }

    @Test
    void rejectReservation_usesResolverId() {
        MedicineReservation rejected = MedicineReservation.builder()
                .id(5L)
                .status(MedicineReservationStatus.REJECTED)
                .build();
        when(reservationService.rejectReservation(5L, "no stock")).thenReturn(rejected);

        ResponseEntity<?> response = controller.rejectReservation(5L, 9001L, null, Map.of("reason", "no stock"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(reservationService).assertPharmacyOwnsReservation(5L, 9001L);
        verify(reservationService).rejectReservation(5L, "no stock");
    }

    @Test
    void cancelReservation_usesResolverId() {
        MedicineReservation cancelled = MedicineReservation.builder()
                .id(6L)
                .status(MedicineReservationStatus.CANCELLED)
                .build();
        when(reservationService.cancelReservationByPharmacy(6L, 9001L)).thenReturn(cancelled);

        ResponseEntity<?> response = controller.cancelReservation(6L, 9001L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(reservationService).cancelReservationByPharmacy(6L, 9001L);
    }
}
