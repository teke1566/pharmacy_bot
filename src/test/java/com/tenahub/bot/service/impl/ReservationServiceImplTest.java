package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.MedicineReservationStatus;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.entity.PrescriptionReviewStatus;
import com.tenahub.bot.repository.MedicineReservationRepository;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.ReservationWorkflowService;
import com.tenahub.bot.util.BotLanguage;
import com.tenahub.bot.util.LocalizationService;
import com.tenahub.bot.util.TelegramClient;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceImplTest {

    @Mock
    private MedicineReservationRepository reservationRepository;
    @Mock
    private PharmacyRepository pharmacyRepository;
    @Mock
    private PharmacyInventoryRepository inventoryRepository;
    @Mock
    private LocalizationService localizationService;
    @Mock
    private TelegramClient telegramClient;
    @Mock
    private ReservationWorkflowService reservationWorkflowService;

    private ReservationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReservationServiceImpl(
                reservationRepository,
                pharmacyRepository,
                inventoryRepository,
                localizationService,
                telegramClient,
                reservationWorkflowService);
        ReflectionTestUtils.setField(service, "pendingTimeoutMinutes", 20L);
    }

    @Test
    void createReservation_nonPrescription_holdsInventoryAndSetsNotRequired() {
        Long pharmacyId = 5L;
        Pharmacy pharmacy = Pharmacy.builder().id(pharmacyId).build();
        PharmacyInventory inventory = PharmacyInventory.builder()
                .id(22L)
                .pharmacyId(pharmacyId)
                .medicineName("paracetamol")
                .quantity(10)
                .outOfStock(false)
                .requiresPrescription(false)
                .build();

        when(pharmacyRepository.findById(pharmacyId)).thenReturn(Optional.of(pharmacy));
        when(inventoryRepository.findByPharmacyIdAndMedicineNameIgnoreCase(pharmacyId, "paracetamol"))
                .thenReturn(Optional.of(inventory));
        when(reservationRepository.save(any(MedicineReservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MedicineReservation created = service.createReservation(9L, pharmacyId, "paracetamol", 3, "+251911000000", "Abel");

        assertEquals(MedicineReservationStatus.PENDING, created.getStatus());
        assertEquals(PrescriptionReviewStatus.NOT_REQUIRED, created.getPrescriptionReviewStatus());
        assertTrue(created.isInventoryHeld());
        assertEquals(7, inventory.getQuantity());
        assertFalse(inventory.isOutOfStock());
        verify(inventoryRepository).save(inventory);
        verify(reservationWorkflowService).notifyPharmacyPendingReservation(created, 20L);
    }

    @Test
    void createReservation_prescriptionRequired_defersHoldAndSetsUploadRequired() {
        Long pharmacyId = 7L;
        Pharmacy pharmacy = Pharmacy.builder().id(pharmacyId).build();
        PharmacyInventory inventory = PharmacyInventory.builder()
                .pharmacyId(pharmacyId)
                .medicineName("amoxicillin")
                .quantity(8)
                .outOfStock(false)
                .requiresPrescription(true)
                .build();

        when(pharmacyRepository.findById(pharmacyId)).thenReturn(Optional.of(pharmacy));
        when(inventoryRepository.findByPharmacyIdAndMedicineNameIgnoreCase(pharmacyId, "amoxicillin"))
                .thenReturn(Optional.of(inventory));
        when(reservationRepository.save(any(MedicineReservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MedicineReservation created = service.createReservation(11L, pharmacyId, "amoxicillin", 2, "+251900000000", "Liya");

        assertEquals(PrescriptionReviewStatus.UPLOAD_REQUIRED, created.getPrescriptionReviewStatus());
        assertFalse(created.isInventoryHeld());
        assertEquals(8, inventory.getQuantity());
        verify(inventoryRepository, never()).save(inventory);
        verify(reservationWorkflowService).notifyPharmacyPendingReservation(created, 20L);
    }

    @Test
    void approveReservation_prescriptionPendingReview_throws() {
        MedicineReservation reservation = MedicineReservation.builder()
                .id(12L)
                .status(MedicineReservationStatus.PENDING)
                .prescriptionRequired(true)
                .prescriptionReviewStatus(PrescriptionReviewStatus.PENDING_REVIEW)
                .build();
        when(reservationRepository.findById(12L)).thenReturn(Optional.of(reservation));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.approveReservation(12L));

        assertEquals("Prescription review is still pending for this reservation.", ex.getMessage());
    }

    @Test
    void cancelReservationByPharmacy_notifiesUserAndClearsQrToken() {
        Long reservationId = 50L;
        Long pharmacyTelegramId = 12345L;
        Long pharmacyId = 77L;

        MedicineReservation reservation = MedicineReservation.builder()
                .id(reservationId)
                .pharmacyId(pharmacyId)
                .userId(555L)
                .medicineName("ibuprofen")
                .requestedQuantity(1)
                .status(MedicineReservationStatus.PENDING)
                .qrToken("QR123")
                .inventoryHeld(false)
                .build();

        Pharmacy pharmacy = Pharmacy.builder()
                .id(pharmacyId)
                .name("City Pharmacy")
                .telegramId(pharmacyTelegramId)
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(pharmacyRepository.findByTelegramId(pharmacyTelegramId)).thenReturn(Optional.of(pharmacy));
        when(localizationService.getLanguage(555L)).thenReturn(BotLanguage.ENGLISH);
        when(reservationRepository.save(any(MedicineReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MedicineReservation saved = service.cancelReservationByPharmacy(reservationId, pharmacyTelegramId);

        assertEquals(MedicineReservationStatus.CANCELLED, saved.getStatus());
        assertEquals(null, saved.getQrToken());
        verify(telegramClient).sendMessage(eq(555L), any(String.class));
    }

    @Test
    void getPendingReservations_excludesPrescriptionItemsUntilApproved() {
        Long pharmacyTelegramId = 600L;
        Long pharmacyId = 6L;

        Pharmacy pharmacy = Pharmacy.builder().id(pharmacyId).telegramId(pharmacyTelegramId).build();
        MedicineReservation normal = MedicineReservation.builder()
                .id(1L)
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .status(MedicineReservationStatus.PENDING)
                .prescriptionRequired(false)
                .build();
        MedicineReservation rxPendingReview = MedicineReservation.builder()
                .id(2L)
                .createdAt(LocalDateTime.now().minusMinutes(3))
                .status(MedicineReservationStatus.PENDING)
                .prescriptionRequired(true)
                .prescriptionReviewStatus(PrescriptionReviewStatus.PENDING_REVIEW)
                .build();
        MedicineReservation rxApproved = MedicineReservation.builder()
                .id(3L)
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .status(MedicineReservationStatus.PENDING)
                .prescriptionRequired(true)
                .prescriptionReviewStatus(PrescriptionReviewStatus.APPROVED)
                .build();

        when(pharmacyRepository.findByTelegramId(pharmacyTelegramId)).thenReturn(Optional.of(pharmacy));
        when(reservationRepository.findByPharmacyIdAndStatus(pharmacyId, MedicineReservationStatus.PENDING))
                .thenReturn(List.of(normal, rxPendingReview, rxApproved));

        List<MedicineReservation> result = service.getPendingReservations(pharmacyTelegramId);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(r -> r.getId().equals(1L)));
        assertTrue(result.stream().anyMatch(r -> r.getId().equals(3L)));
        assertFalse(result.stream().anyMatch(r -> r.getId().equals(2L)));
    }

    @Test
    void holdInventoryForApprovedPrescription_updatesInventoryAndMarksHeld() {
        MedicineReservation reservation = MedicineReservation.builder()
                .id(88L)
                .pharmacyId(9L)
                .medicineName("ceftriaxone")
                .requestedQuantity(2)
                .inventoryHeld(false)
                .build();

        PharmacyInventory inventory = PharmacyInventory.builder()
                .id(900L)
                .pharmacyId(9L)
                .medicineName("ceftriaxone")
                .quantity(5)
                .outOfStock(false)
                .build();

        when(inventoryRepository.findByPharmacyIdAndMedicineNameIgnoreCase(9L, "ceftriaxone"))
                .thenReturn(Optional.of(inventory));
        when(reservationRepository.save(any(MedicineReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.holdInventoryForApprovedPrescription(reservation);

        assertTrue(reservation.isInventoryHeld());
        assertEquals(3, inventory.getQuantity());
        verify(inventoryRepository).save(inventory);

        ArgumentCaptor<MedicineReservation> captor = ArgumentCaptor.forClass(MedicineReservation.class);
        verify(reservationRepository).save(captor.capture());
        assertTrue(captor.getValue().isInventoryHeld());
    }

    @Test
    void getPharmacyReservations_usesPharmacyIdNotUserId() {
        Pharmacy pharmacy = Pharmacy.builder().id(44L).telegramId(9001L).build();
        MedicineReservation reservation = MedicineReservation.builder().id(7L).pharmacyId(44L).build();

        when(pharmacyRepository.findByTelegramId(9001L)).thenReturn(Optional.of(pharmacy));
        when(reservationRepository.findByPharmacyIdOrderByCreatedAtDesc(44L)).thenReturn(List.of(reservation));

        List<MedicineReservation> result = service.getPharmacyReservations(9001L);

        assertEquals(1, result.size());
        assertEquals(7L, result.get(0).getId());
        verify(reservationRepository).findByPharmacyIdOrderByCreatedAtDesc(44L);
        verify(reservationRepository, never()).findByUserIdOrderByCreatedAtDesc(any());
    }

    @Test
    void assertPharmacyOwnsReservation_rejectsCrossPharmacyReservation() {
        Pharmacy pharmacy = Pharmacy.builder().id(44L).telegramId(9001L).build();
        MedicineReservation reservation = MedicineReservation.builder().id(7L).pharmacyId(99L).build();

        when(pharmacyRepository.findByTelegramId(9001L)).thenReturn(Optional.of(pharmacy));
        when(reservationRepository.findById(7L)).thenReturn(Optional.of(reservation));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.assertPharmacyOwnsReservation(7L, 9001L));
        assertEquals("Reservation does not belong to this pharmacy", error.getMessage());
    }

    @Test
    void approveReservation_pendingNonRx_setsReadyForPickup() {
        MedicineReservation reservation = MedicineReservation.builder()
                .id(80L)
                .status(MedicineReservationStatus.PENDING)
                .prescriptionRequired(false)
                .inventoryHeld(true)
                .build();
        when(reservationRepository.findById(80L)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(MedicineReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MedicineReservation saved = service.approveReservation(80L);

        assertEquals(MedicineReservationStatus.READY_FOR_PICKUP, saved.getStatus());
        assertNotNull(saved.getApprovedAt());
        assertNotNull(saved.getExpiresAt());
    }

    @Test
    void approveReservationAndNotify_sendsUserDm() {
        MedicineReservation reservation = MedicineReservation.builder()
                .id(86L)
                .userId(11L)
                .medicineName("paracetamol")
                .requestedQuantity(2)
                .status(MedicineReservationStatus.PENDING)
                .prescriptionRequired(false)
                .inventoryHeld(true)
                .expiresAt(LocalDateTime.now().plusHours(2))
                .build();
        when(reservationRepository.findById(86L)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(MedicineReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(localizationService.getLanguage(11L)).thenReturn(BotLanguage.ENGLISH);
        when(localizationService.text(eq(11L), eq("reservation_approved_user"), any(), any(), any()))
                .thenReturn("approved");

        MedicineReservation saved = service.approveReservationAndNotify(86L);

        assertEquals(MedicineReservationStatus.READY_FOR_PICKUP, saved.getStatus());
        verify(telegramClient).sendMessage(eq(11L), eq("approved"));
    }

    @Test
    void rejectReservation_pending_setsRejected() {
        MedicineReservation reservation = MedicineReservation.builder()
                .id(81L)
                .status(MedicineReservationStatus.PENDING)
                .inventoryHeld(false)
                .build();
        when(reservationRepository.findById(81L)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(MedicineReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MedicineReservation saved = service.rejectReservation(81L, "out of stock");

        assertEquals(MedicineReservationStatus.REJECTED, saved.getStatus());
        assertEquals("out of stock", saved.getRejectionReason());
    }

    @Test
    void fulfillReservation_readyForPickup_setsFulfilled() {
        MedicineReservation reservation = MedicineReservation.builder()
                .id(82L)
                .status(MedicineReservationStatus.READY_FOR_PICKUP)
                .inventoryHeld(true)
                .requestedQuantity(1)
                .build();
        when(reservationRepository.findById(82L)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(MedicineReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MedicineReservation saved = service.fulfillReservation(82L);

        assertEquals(MedicineReservationStatus.FULFILLED, saved.getStatus());
        assertFalse(saved.isInventoryHeld());
        assertNotNull(saved.getFulfilledAt());
    }

    @Test
    void cancelReservationByUser_onlyOwnReservation() {
        MedicineReservation reservation = MedicineReservation.builder()
                .id(83L)
                .userId(11L)
                .status(MedicineReservationStatus.PENDING)
                .inventoryHeld(false)
                .build();
        when(reservationRepository.findById(83L)).thenReturn(Optional.of(reservation));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.cancelReservationByUser(99L, 83L));
        assertEquals("You are not allowed to cancel this reservation.", error.getMessage());
        verify(reservationRepository, never()).save(any(MedicineReservation.class));
    }

    @Test
    void cancelReservationByUser_pendingOwnReservation_cancels() {
        MedicineReservation reservation = MedicineReservation.builder()
                .id(84L)
                .userId(11L)
                .status(MedicineReservationStatus.PENDING)
                .inventoryHeld(false)
                .build();
        when(reservationRepository.findById(84L)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(MedicineReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MedicineReservation saved = service.cancelReservationByUser(11L, 84L);

        assertEquals(MedicineReservationStatus.CANCELLED, saved.getStatus());
    }

    @Test
    void cancelReservationByUser_readyForPickup_cancels() {
        MedicineReservation reservation = MedicineReservation.builder()
                .id(85L)
                .userId(11L)
                .status(MedicineReservationStatus.READY_FOR_PICKUP)
                .inventoryHeld(false)
                .build();
        when(reservationRepository.findById(85L)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(MedicineReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MedicineReservation saved = service.cancelReservationByUser(11L, 85L);

        assertEquals(MedicineReservationStatus.CANCELLED, saved.getStatus());
    }

    @Test
    void createReservationGroup_notifiesPharmacyOnce() {
        Long pharmacyId = 5L;
        Pharmacy pharmacy = Pharmacy.builder().id(pharmacyId).telegramId(777L).build();
        PharmacyInventory paracetamol = PharmacyInventory.builder()
                .pharmacyId(pharmacyId)
                .medicineName("paracetamol")
                .quantity(10)
                .outOfStock(false)
                .requiresPrescription(false)
                .build();
        PharmacyInventory ibuprofen = PharmacyInventory.builder()
                .pharmacyId(pharmacyId)
                .medicineName("ibuprofen")
                .quantity(8)
                .outOfStock(false)
                .requiresPrescription(false)
                .build();

        when(pharmacyRepository.findById(pharmacyId)).thenReturn(Optional.of(pharmacy));
        when(inventoryRepository.findByPharmacyIdAndMedicineNameIgnoreCase(pharmacyId, "paracetamol"))
                .thenReturn(Optional.of(paracetamol));
        when(inventoryRepository.findByPharmacyIdAndMedicineNameIgnoreCase(pharmacyId, "ibuprofen"))
                .thenReturn(Optional.of(ibuprofen));
        when(reservationRepository.save(any(MedicineReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<MedicineReservation> created = service.createReservationGroup(
                9L,
                pharmacyId,
                java.util.Map.of("paracetamol", 1, "ibuprofen", 2),
                "+251911000000",
                "Abel");

        assertEquals(2, created.size());
        verify(reservationWorkflowService).notifyPharmacyPendingReservations(created, 20L);
    }

    @Test
    void scanReservationByQrToken_rejectsOtherPharmacy() {
        MedicineReservation reservation = MedicineReservation.builder()
                .id(90L)
                .pharmacyId(1L)
                .status(MedicineReservationStatus.READY_FOR_PICKUP)
                .qrToken("QR-1")
                .build();
        Pharmacy otherPharmacy = Pharmacy.builder().id(2L).telegramId(8000L).build();
        when(reservationRepository.findAllByQrToken("QR-1")).thenReturn(List.of(reservation));
        when(pharmacyRepository.findByTelegramId(8000L)).thenReturn(Optional.of(otherPharmacy));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.scanReservationByQrToken("QR-1", 8000L));
        assertEquals("Reservation does not belong to this pharmacy.", error.getMessage());
    }
}
