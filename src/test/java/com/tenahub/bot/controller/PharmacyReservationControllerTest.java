package com.tenahub.bot.controller;

import com.tenahub.bot.dto.PharmacyReservationFulfillResponseDTO;
import com.tenahub.bot.dto.PharmacyReservationScanRequestDTO;
import com.tenahub.bot.dto.PharmacyReservationScanResponseDTO;
import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.MedicineReservationStatus;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.repository.MedicineReservationRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.MiniAppActorResolver;
import com.tenahub.bot.service.MiniAppAuthException;
import com.tenahub.bot.service.PharmacyService;
import com.tenahub.bot.service.PrescriptionReviewService;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PharmacyReservationControllerTest {

    @Mock
    private ReservationService reservationService;
    @Mock
    private PharmacyRepository pharmacyRepository;
    @Mock
    private MedicineReservationRepository medicineReservationRepository;
    @Mock
    private PrescriptionReviewService prescriptionReviewService;
    @Mock
    private MiniAppActorResolver miniAppActorResolver;
    @Mock
    private PharmacyService pharmacyService;

    private PharmacyReservationController controller;

    @BeforeEach
    void setUp() {
        controller = new PharmacyReservationController(
                reservationService,
                pharmacyRepository,
                medicineReservationRepository,
                prescriptionReviewService,
                miniAppActorResolver,
                pharmacyService);
    }

    @Test
    void checkAccess_returnsRegisteredPharmacyFlag() {
        when(miniAppActorResolver.requirePharmacyTelegramId(9001L, null)).thenReturn(9001L);
        when(pharmacyService.isRegisteredPharmacy(9001L)).thenReturn(true);

        ResponseEntity<?> response = controller.checkAccess(9001L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertTrue((Boolean) body.get("isPharmacy"));
        assertEquals(9001L, body.get("telegramId"));
    }

    @Test
    void checkAccess_returnsFalseWhenNotRegistered() {
        when(miniAppActorResolver.requirePharmacyTelegramId(9001L, null)).thenReturn(9001L);
        when(pharmacyService.isRegisteredPharmacy(9001L)).thenReturn(false);

        ResponseEntity<?> response = controller.checkAccess(9001L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertFalse((Boolean) body.get("isPharmacy"));
    }

    @Test
    void scanReservation_usesResolvedPharmacyId() {
        when(miniAppActorResolver.requirePharmacyTelegramId(9001L, 9001L)).thenReturn(9001L);
        MedicineReservation reservation = MedicineReservation.builder()
                .id(10L)
                .pharmacyId(3L)
                .status(MedicineReservationStatus.READY_FOR_PICKUP)
                .qrToken("QR-1")
                .medicineName("paracetamol")
                .requestedQuantity(1)
                .build();
        when(reservationService.scanReservationByQrToken("QR-1", 9001L)).thenReturn(reservation);
        when(medicineReservationRepository.findAllByQrToken("QR-1")).thenReturn(List.of(reservation));
        when(pharmacyRepository.findById(3L)).thenReturn(Optional.of(Pharmacy.builder().id(3L).name("City").build()));

        ResponseEntity<?> response = controller.scanReservation(
                9001L,
                PharmacyReservationScanRequestDTO.builder().qrToken("QR-1").pharmacyTelegramId(9001L).build());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        PharmacyReservationScanResponseDTO body = (PharmacyReservationScanResponseDTO) response.getBody();
        assertTrue(body.isValid());
        assertEquals(10L, body.getReservationId());
        verify(reservationService).scanReservationByQrToken("QR-1", 9001L);
    }

    @Test
    void scanReservation_propagatesAuthError() {
        when(miniAppActorResolver.requirePharmacyTelegramId(null, 9001L))
                .thenThrow(new MiniAppAuthException("Telegram initData is required"));

        assertThrows(MiniAppAuthException.class, () -> controller.scanReservation(
                null,
                PharmacyReservationScanRequestDTO.builder().qrToken("QR-1").pharmacyTelegramId(9001L).build()));
    }

    @Test
    void fulfillReservation_usesResolvedPharmacyId() {
        when(miniAppActorResolver.requirePharmacyTelegramId(9001L, null)).thenReturn(9001L);
        when(reservationService.fulfillReservationAndNotify(10L, 9001L))
                .thenReturn(MedicineReservation.builder().id(10L).status(MedicineReservationStatus.FULFILLED).build());

        ResponseEntity<?> response = controller.fulfillReservation(9001L, null, 10L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        PharmacyReservationFulfillResponseDTO body = (PharmacyReservationFulfillResponseDTO) response.getBody();
        assertTrue(body.isSuccess());
        assertEquals("FULFILLED", body.getStatus());
        verify(reservationService).fulfillReservationAndNotify(10L, 9001L);
    }
}
