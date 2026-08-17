package com.tenahub.bot.controller;

import com.tenahub.bot.dto.PharmacyMiniAppReservationDTO;
import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.MedicineReservationStatus;
import com.tenahub.bot.service.MiniAppActorResolver;
import com.tenahub.bot.service.ReservationService;
import com.tenahub.bot.service.ReservationStatusHistoryService;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PharmacyMiniAppReservationsControllerTest {

    @Mock
    private ReservationService reservationService;
    @Mock
    private MiniAppActorResolver miniAppActorResolver;
    @Mock
    private com.tenahub.bot.repository.ReservationPrescriptionFileRepository prescriptionFileRepository;
    @Mock
    private ReservationStatusHistoryService reservationStatusHistoryService;
    @Mock
    private com.tenahub.bot.service.PharmacyAuthorizationService pharmacyAuthorizationService;

    private PharmacyMiniAppReservationsController controller;

    @BeforeEach
    void setUp() {
        controller = new PharmacyMiniAppReservationsController(
                reservationService, miniAppActorResolver, prescriptionFileRepository,
                reservationStatusHistoryService, pharmacyAuthorizationService);
        lenient().when(miniAppActorResolver.requirePharmacyTelegramId(9001L, null)).thenReturn(9001L);
        lenient().when(miniAppActorResolver.requirePharmacyActor(9001L, null)).thenReturn(
                com.tenahub.bot.dto.PharmacyActor.builder()
                        .pharmacyId(3L)
                        .pharmacyTelegramId(9001L)
                        .actorTelegramId(9001L)
                        .permissions(java.util.EnumSet.allOf(com.tenahub.bot.entity.PharmacyPermission.class))
                        .build());
    }

    @Test
    void getAllReservations_pendingUsesPendingQuery() {
        when(reservationService.getPendingReservations(9001L)).thenReturn(List.of(
                MedicineReservation.builder().id(1L).status(MedicineReservationStatus.PENDING).build()));
        when(prescriptionFileRepository.findRefsByReservationIdIn(List.of(1L)))
                .thenReturn(List.of());

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
        when(miniAppActorResolver.requirePharmacyActor(9001L, null)).thenReturn(
                com.tenahub.bot.dto.PharmacyActor.builder()
                        .pharmacyId(3L)
                        .pharmacyTelegramId(9001L)
                        .actorTelegramId(9001L)
                        .permissions(java.util.EnumSet.allOf(com.tenahub.bot.entity.PharmacyPermission.class))
                        .build());

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
        when(reservationService.rejectReservationAndNotify(5L, "no stock")).thenReturn(rejected);
        when(miniAppActorResolver.requirePharmacyActor(9001L, null)).thenReturn(
                com.tenahub.bot.dto.PharmacyActor.builder()
                        .pharmacyId(3L)
                        .pharmacyTelegramId(9001L)
                        .actorTelegramId(9001L)
                        .permissions(java.util.EnumSet.allOf(com.tenahub.bot.entity.PharmacyPermission.class))
                        .build());

        ResponseEntity<?> response = controller.rejectReservation(5L, 9001L, null, Map.of("reason", "no stock"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(reservationService).assertPharmacyOwnsReservation(5L, 9001L);
        verify(reservationService).rejectReservationAndNotify(5L, "no stock");
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

    @Test
    void approveGroup_usesService() {
        when(reservationService.approveGroupAndNotify("grp-1", 9001L)).thenReturn(List.of(
                MedicineReservation.builder().id(10L).status(MedicineReservationStatus.READY_FOR_PICKUP).build()));

        ResponseEntity<?> response = controller.approveGroup("grp-1", 9001L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(reservationService).approveGroupAndNotify("grp-1", 9001L);
        @SuppressWarnings("unchecked")
        List<PharmacyMiniAppReservationDTO> body = (List<PharmacyMiniAppReservationDTO>) response.getBody();
        assertEquals(1, body.size());
        assertEquals(10L, body.get(0).getReservationId());
    }

    @Test
    void fulfillGroup_usesService() {
        when(reservationService.fulfillGroupAndNotify("grp-2", 9001L)).thenReturn(List.of(
                MedicineReservation.builder().id(11L).status(MedicineReservationStatus.FULFILLED).build()));

        ResponseEntity<?> response = controller.fulfillGroup("grp-2", 9001L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(reservationService).fulfillGroupAndNotify("grp-2", 9001L);
    }

    @Test
    void cancelGroup_usesService() {
        when(reservationService.cancelGroupByPharmacy("grp-3", 9001L)).thenReturn(List.of(
                MedicineReservation.builder().id(12L).status(MedicineReservationStatus.CANCELLED).build()));

        ResponseEntity<?> response = controller.cancelGroup("grp-3", 9001L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(reservationService).cancelGroupByPharmacy("grp-3", 9001L);
    }

    @Test
    void getReservationHistory_assertsOwnershipThenLists() {
        when(reservationStatusHistoryService.listForPharmacy(9001L, 44L)).thenReturn(List.of());

        ResponseEntity<?> response = controller.getReservationHistory(44L, 9001L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(reservationService).assertPharmacyOwnsReservation(44L, 9001L);
        verify(reservationStatusHistoryService).listForPharmacy(9001L, 44L);
    }

    @Test
    void getReservationHistory_otherPharmacyForbidden() {
        org.mockito.Mockito.doThrow(new RuntimeException("Reservation does not belong to this pharmacy"))
                .when(reservationService).assertPharmacyOwnsReservation(44L, 9001L);

        ResponseEntity<?> response = controller.getReservationHistory(44L, 9001L, null);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
