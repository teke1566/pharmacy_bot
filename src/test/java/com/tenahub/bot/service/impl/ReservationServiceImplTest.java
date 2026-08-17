package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.MedicineReservationStatus;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.entity.PrescriptionReviewStatus;
import com.tenahub.bot.repository.MedicineReservationRepository;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.MedicineLotService;
import com.tenahub.bot.service.PharmacySalesService;
import com.tenahub.bot.service.ReservationStatusHistoryService;
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
import static org.mockito.ArgumentMatchers.anyString;
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
    @Mock
    private MedicineLotService medicineLotService;
    @Mock
    private PharmacySalesService pharmacySalesService;
    @Mock
    private ReservationStatusHistoryService reservationStatusHistoryService;

    private ReservationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReservationServiceImpl(
                reservationRepository,
                pharmacyRepository,
                inventoryRepository,
                localizationService,
                telegramClient,
                reservationWorkflowService,
                medicineLotService,
                pharmacySalesService,
                reservationStatusHistoryService);
        ReflectionTestUtils.setField(service, "pendingTimeoutMinutes", 20L);
        org.mockito.Mockito.lenient().when(medicineLotService.hasExpiredStock(any())).thenReturn(false);
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            MedicineReservation reservation = invocation.getArgument(0);
            PharmacyInventory inventory = inventoryRepository
                    .findByPharmacyIdAndMedicineNameIgnoreCase(reservation.getPharmacyId(), reservation.getMedicineName())
                    .orElseThrow(() -> new RuntimeException("Medicine inventory not found"));
            int availableQty = inventory.getQuantity() == null ? 0 : inventory.getQuantity();
            int requiredQty = reservation.getRequestedQuantity() == null ? 0 : reservation.getRequestedQuantity();
            if (inventory.isOutOfStock() || availableQty <= 0) {
                throw new RuntimeException("Medicine is currently out of stock.");
            }
            if (requiredQty > availableQty) {
                throw new RuntimeException("Requested quantity exceeds available stock.");
            }
            inventory.setQuantity(availableQty - requiredQty);
            inventory.setOutOfStock(inventory.getQuantity() <= 0);
            inventoryRepository.save(inventory);
            reservation.setInventoryHeld(true);
            return null;
        }).when(medicineLotService).holdForReservation(any());
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            MedicineReservation reservation = invocation.getArgument(0);
            if (reservation == null || !reservation.isInventoryHeld()) {
                return null;
            }
            inventoryRepository.findByPharmacyIdAndMedicineNameIgnoreCase(
                            reservation.getPharmacyId(), reservation.getMedicineName())
                    .ifPresent(inventory -> {
                        int currentQty = inventory.getQuantity() == null ? 0 : inventory.getQuantity();
                        int releaseQty = reservation.getRequestedQuantity() == null ? 0 : reservation.getRequestedQuantity();
                        inventory.setQuantity(currentQty + Math.max(releaseQty, 0));
                        inventory.setOutOfStock(inventory.getQuantity() <= 0);
                        inventoryRepository.save(inventory);
                    });
            reservation.setInventoryHeld(false);
            return null;
        }).when(medicineLotService).releaseHeldForReservation(any());
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            MedicineReservation reservation = invocation.getArgument(0);
            if (!reservation.isInventoryHeld()) {
                PharmacyInventory inventory = inventoryRepository
                        .findByPharmacyIdAndMedicineNameIgnoreCase(reservation.getPharmacyId(), reservation.getMedicineName())
                        .orElseThrow(() -> new RuntimeException("Medicine inventory not found"));
                int currentQty = inventory.getQuantity() == null ? 0 : inventory.getQuantity();
                int newQty = currentQty - reservation.getRequestedQuantity();
                if (newQty < 0) {
                    throw new RuntimeException("Requested quantity exceeds available stock.");
                }
                inventory.setQuantity(newQty);
                inventory.setOutOfStock(newQty <= 0);
                inventoryRepository.save(inventory);
            }
            reservation.setInventoryHeld(false);
            return null;
        }).when(medicineLotService).fulfillReservation(any(), org.mockito.ArgumentMatchers.nullable(Long.class));
        org.mockito.Mockito.lenient().doAnswer(invocation -> null).when(medicineLotService).ensureBackfillAndSync(any());
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
        when(reservationRepository.findByUserIdAndStatusIn(eq(9L), any()))
                .thenReturn(List.of());
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
        when(reservationRepository.findByUserIdAndStatusIn(eq(11L), any()))
                .thenReturn(List.of());
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
    void createReservation_blocksActiveDuplicateSamePharmacyMedicine() {
        Long pharmacyId = 5L;
        Pharmacy pharmacy = Pharmacy.builder().id(pharmacyId).build();
        PharmacyInventory inventory = PharmacyInventory.builder()
                .pharmacyId(pharmacyId)
                .medicineName("paracetamol")
                .quantity(10)
                .outOfStock(false)
                .requiresPrescription(false)
                .build();
        MedicineReservation existing = MedicineReservation.builder()
                .id(1L)
                .userId(9L)
                .pharmacyId(pharmacyId)
                .medicineName("paracetamol")
                .status(MedicineReservationStatus.PENDING)
                .build();

        when(pharmacyRepository.findById(pharmacyId)).thenReturn(Optional.of(pharmacy));
        when(inventoryRepository.findByPharmacyIdAndMedicineNameIgnoreCase(pharmacyId, "paracetamol"))
                .thenReturn(Optional.of(inventory));
        when(reservationRepository.findByUserIdAndStatusIn(eq(9L), any()))
                .thenReturn(List.of(existing));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.createReservation(9L, pharmacyId, "paracetamol", 1, "+251911000000", "Abel"));
        assertTrue(ex.getMessage().contains("already have an active reservation"));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void createReservation_allowsSameMedicineAtDifferentPharmacy() {
        Long pharmacyId = 6L;
        Pharmacy pharmacy = Pharmacy.builder().id(pharmacyId).build();
        PharmacyInventory inventory = PharmacyInventory.builder()
                .pharmacyId(pharmacyId)
                .medicineName("paracetamol")
                .quantity(10)
                .outOfStock(false)
                .requiresPrescription(false)
                .build();
        MedicineReservation existingOtherPharmacy = MedicineReservation.builder()
                .id(1L)
                .userId(9L)
                .pharmacyId(5L)
                .medicineName("paracetamol")
                .status(MedicineReservationStatus.PENDING)
                .build();

        when(pharmacyRepository.findById(pharmacyId)).thenReturn(Optional.of(pharmacy));
        when(inventoryRepository.findByPharmacyIdAndMedicineNameIgnoreCase(pharmacyId, "paracetamol"))
                .thenReturn(Optional.of(inventory));
        when(reservationRepository.findByUserIdAndStatusIn(eq(9L), any()))
                .thenReturn(List.of(existingOtherPharmacy));
        when(reservationRepository.save(any(MedicineReservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MedicineReservation created = service.createReservation(9L, pharmacyId, "paracetamol", 1, "+251911000000", "Abel");
        assertEquals(MedicineReservationStatus.PENDING, created.getStatus());
        assertEquals(pharmacyId, created.getPharmacyId());
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
        when(telegramClient.buildMiniAppUserReservationStatusUrl(eq("history"), eq(reservationId), any()))
                .thenReturn("https://tenahub-miniapp.vercel.app/#/search?section=history&reservationId=50");

        MedicineReservation saved = service.cancelReservationByPharmacy(reservationId, pharmacyTelegramId);

        assertEquals(MedicineReservationStatus.CANCELLED, saved.getStatus());
        assertEquals(null, saved.getQrToken());
        verify(telegramClient).sendMessageWithMiniAppButton(eq(555L), any(String.class), anyString(), anyString());
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
        when(telegramClient.buildMiniAppUserReservationStatusUrl(eq("active"), eq(86L), any()))
                .thenReturn("https://tenahub-miniapp.vercel.app/#/search?section=active&reservationId=86");

        MedicineReservation saved = service.approveReservationAndNotify(86L);

        assertEquals(MedicineReservationStatus.READY_FOR_PICKUP, saved.getStatus());
        verify(telegramClient).sendMessageWithMiniAppButton(eq(11L), eq("approved"), anyString(), anyString());
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
    void rejectReservationAndNotify_sendsCustomerDmWithReason() {
        MedicineReservation reservation = MedicineReservation.builder()
                .id(83L)
                .userId(11L)
                .medicineName("paracetamol")
                .requestedQuantity(2)
                .status(MedicineReservationStatus.PENDING)
                .inventoryHeld(false)
                .build();
        when(reservationRepository.findById(83L)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(MedicineReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(localizationService.getLanguage(11L)).thenReturn(BotLanguage.ENGLISH);
        when(localizationService.text(eq(11L), eq("reservation_rejected_user"), any(), any(), any()))
                .thenReturn("rejected-with-reason");
        when(telegramClient.buildMiniAppUserReservationStatusUrl(eq("history"), eq(83L), any()))
                .thenReturn("https://tenahub-miniapp.vercel.app/#/search?section=history&reservationId=83");

        MedicineReservation saved = service.rejectReservationAndNotify(83L, "blurry prescription");

        assertEquals(MedicineReservationStatus.REJECTED, saved.getStatus());
        assertEquals("blurry prescription", saved.getRejectionReason());
        verify(telegramClient).sendMessageWithMiniAppButton(eq(11L), eq("rejected-with-reason"), anyString(), anyString());
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
        verify(pharmacySalesService).recordFromReservation(saved, null);
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
        when(reservationRepository.findByUserIdAndStatusIn(eq(9L), any())).thenReturn(List.of());
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

    @Test
    void approveGroupAndNotify_rejectsForeignPharmacy() {
        Pharmacy pharmacy = Pharmacy.builder().id(1L).telegramId(9001L).build();
        MedicineReservation foreign = MedicineReservation.builder()
                .id(1L)
                .pharmacyId(99L)
                .reservationGroupId("g1")
                .status(MedicineReservationStatus.PENDING)
                .build();
        when(pharmacyRepository.findByTelegramId(9001L)).thenReturn(Optional.of(pharmacy));
        when(reservationRepository.findByReservationGroupId("g1")).thenReturn(List.of(foreign));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.approveGroupAndNotify("g1", 9001L));
        assertEquals("This reservation group does not belong to your pharmacy.", error.getMessage());
    }

    @Test
    void rejectGroup_rejectsPendingOwnedRows() {
        Pharmacy pharmacy = Pharmacy.builder().id(1L).telegramId(9001L).build();
        MedicineReservation pending = MedicineReservation.builder()
                .id(11L)
                .pharmacyId(1L)
                .reservationGroupId("g2")
                .status(MedicineReservationStatus.PENDING)
                .inventoryHeld(false)
                .build();
        when(pharmacyRepository.findByTelegramId(9001L)).thenReturn(Optional.of(pharmacy));
        when(reservationRepository.findByReservationGroupId("g2")).thenReturn(List.of(pending));
        when(reservationRepository.findByReservationGroupIdAndStatus("g2", MedicineReservationStatus.PENDING))
                .thenReturn(List.of(pending));
        when(reservationRepository.findById(11L)).thenReturn(Optional.of(pending));
        when(reservationRepository.save(any(MedicineReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<MedicineReservation> rejected = service.rejectGroup("g2", 9001L, "no stock");

        assertEquals(1, rejected.size());
        assertEquals(MedicineReservationStatus.REJECTED, rejected.get(0).getStatus());
        assertEquals("no stock", rejected.get(0).getRejectionReason());
    }

    @Test
    void cancelGroupByPharmacy_cancelsNonTerminalOwnedRows() {
        Pharmacy pharmacy = Pharmacy.builder().id(1L).telegramId(9001L).build();
        MedicineReservation ready = MedicineReservation.builder()
                .id(21L)
                .pharmacyId(1L)
                .reservationGroupId("g3")
                .status(MedicineReservationStatus.READY_FOR_PICKUP)
                .inventoryHeld(false)
                .build();
        MedicineReservation fulfilled = MedicineReservation.builder()
                .id(22L)
                .pharmacyId(1L)
                .reservationGroupId("g3")
                .status(MedicineReservationStatus.FULFILLED)
                .build();
        when(pharmacyRepository.findByTelegramId(9001L)).thenReturn(Optional.of(pharmacy));
        when(reservationRepository.findByReservationGroupId("g3")).thenReturn(List.of(ready, fulfilled));
        when(reservationRepository.findById(21L)).thenReturn(Optional.of(ready));
        when(reservationRepository.save(any(MedicineReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<MedicineReservation> cancelled = service.cancelGroupByPharmacy("g3", 9001L);

        assertEquals(1, cancelled.size());
        assertEquals(MedicineReservationStatus.CANCELLED, cancelled.get(0).getStatus());
    }

    @Test
    void fulfillReservationAndNotify_stampsPassedActorOnSaleLotsAndHistory() {
        MedicineReservation reservation = MedicineReservation.builder()
                .id(200L)
                .pharmacyId(5L)
                .userId(11L)
                .medicineName("paracetamol")
                .status(MedicineReservationStatus.READY_FOR_PICKUP)
                .inventoryHeld(true)
                .requestedQuantity(1)
                .build();
        Pharmacy pharmacy = Pharmacy.builder().id(5L).telegramId(777L).build();
        when(reservationRepository.findById(200L)).thenReturn(Optional.of(reservation));
        when(pharmacyRepository.findByTelegramId(9001L)).thenReturn(Optional.of(pharmacy));
        when(reservationRepository.save(any(MedicineReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(localizationService.getLanguage(11L)).thenReturn(BotLanguage.ENGLISH);
        when(localizationService.text(eq(11L), eq("reservation_fulfilled_user"), any(), any()))
                .thenReturn("fulfilled");
        when(telegramClient.buildMiniAppUserReservationStatusUrl(eq("history"), eq(200L), any()))
                .thenReturn("https://example/#/history");

        MedicineReservation saved = service.fulfillReservationAndNotify(200L, 9001L);

        assertEquals(MedicineReservationStatus.FULFILLED, saved.getStatus());
        assertEquals(9001L, saved.getFulfilledByTelegramId());
        verify(medicineLotService).fulfillReservation(saved, 9001L);
        verify(pharmacySalesService).recordFromReservation(saved, 9001L);
        verify(reservationStatusHistoryService).record(
                saved,
                MedicineReservationStatus.READY_FOR_PICKUP.name(),
                MedicineReservationStatus.FULFILLED.name(),
                9001L,
                "fulfilled");
    }

    @Test
    void approveReservation_appendsStatusHistory() {
        MedicineReservation reservation = MedicineReservation.builder()
                .id(201L)
                .pharmacyId(5L)
                .medicineName("ibuprofen")
                .status(MedicineReservationStatus.PENDING)
                .prescriptionRequired(false)
                .prescriptionReviewStatus(PrescriptionReviewStatus.NOT_REQUIRED)
                .inventoryHeld(true)
                .requestedQuantity(1)
                .build();
        when(reservationRepository.findById(201L)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(MedicineReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MedicineReservation saved = service.approveReservation(201L);

        assertEquals(MedicineReservationStatus.READY_FOR_PICKUP, saved.getStatus());
        verify(reservationStatusHistoryService).record(
                saved,
                MedicineReservationStatus.PENDING.name(),
                MedicineReservationStatus.READY_FOR_PICKUP.name(),
                null,
                "approved");
    }

    @Test
    void expireReservation_appendsStatusHistory() {
        MedicineReservation reservation = MedicineReservation.builder()
                .id(202L)
                .pharmacyId(5L)
                .status(MedicineReservationStatus.READY_FOR_PICKUP)
                .inventoryHeld(false)
                .build();
        when(reservationRepository.findById(202L)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(MedicineReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MedicineReservation saved = service.expireReservation(202L);

        assertEquals(MedicineReservationStatus.EXPIRED, saved.getStatus());
        verify(reservationStatusHistoryService).record(
                saved,
                MedicineReservationStatus.READY_FOR_PICKUP.name(),
                MedicineReservationStatus.EXPIRED.name(),
                null,
                "expired");
    }
}
