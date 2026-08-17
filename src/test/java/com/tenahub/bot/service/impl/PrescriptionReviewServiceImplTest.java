package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.PrescriptionReviewRequestDTO;
import com.tenahub.bot.dto.PrescriptionStatusResponseDTO;
import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.MedicineReservationStatus;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.entity.PrescriptionReviewStatus;
import com.tenahub.bot.entity.ReservationPrescriptionFile;
import com.tenahub.bot.repository.MedicineReservationRepository;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.repository.ReservationPrescriptionFileRepository;
import com.tenahub.bot.service.PharmacyNotificationService;
import com.tenahub.bot.service.ReservationService;
import com.tenahub.bot.service.ReservationStatusHistoryService;
import com.tenahub.bot.entity.PharmacyNotificationType;
import com.tenahub.bot.util.TelegramClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrescriptionReviewServiceImplTest {

    @Mock
    private MedicineReservationRepository reservationRepository;
    @Mock
    private ReservationPrescriptionFileRepository prescriptionFileRepository;
    @Mock
    private PharmacyRepository pharmacyRepository;
    @Mock
    private PharmacyInventoryRepository pharmacyInventoryRepository;
    @Mock
    private ReservationService reservationService;
    @Mock
    private TelegramClient telegramClient;
    @Mock
    private PharmacyNotificationService pharmacyNotificationService;
    @Mock
    private ReservationStatusHistoryService reservationStatusHistoryService;

    private PrescriptionReviewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PrescriptionReviewServiceImpl(
                reservationRepository,
                prescriptionFileRepository,
                pharmacyRepository,
                pharmacyInventoryRepository,
                reservationService,
                telegramClient,
                pharmacyNotificationService,
                reservationStatusHistoryService);
        ReflectionTestUtils.setField(service, "pendingReservationTimeoutMinutes", 20L);
    }

    @Test
    void uploadPrescriptionFiles_setsPendingReviewAndSendsPharmacyNotification() {
        MedicineReservation reservation = MedicineReservation.builder()
                .id(10L)
                .pharmacyId(2L)
                .userId(1000L)
                .medicineName("amoxicillin")
                .requestedQuantity(1)
                .status(MedicineReservationStatus.PENDING)
                .prescriptionRequired(true)
                .prescriptionReviewStatus(PrescriptionReviewStatus.UPLOAD_REQUIRED)
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .build();

        Pharmacy pharmacy = Pharmacy.builder().id(2L).name("Main Pharmacy").telegramId(777L).build();
        PharmacyInventory inventory = PharmacyInventory.builder().id(99L).pharmacyId(2L).medicineName("amoxicillin").build();

        byte[] payload = "content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "rx.png",
                "image/png",
                payload);

        when(reservationRepository.findById(10L)).thenReturn(Optional.of(reservation));
        when(pharmacyInventoryRepository.findByPharmacyIdAndMedicineNameIgnoreCase(2L, "amoxicillin"))
                .thenReturn(Optional.of(inventory));
        when(pharmacyRepository.findById(2L)).thenReturn(Optional.of(pharmacy));
        when(reservationRepository.save(any(MedicineReservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(prescriptionFileRepository.save(any(ReservationPrescriptionFile.class)))
                .thenAnswer(invocation -> {
                    ReservationPrescriptionFile saved = invocation.getArgument(0);
                    if (saved.getId() == null) {
                        saved.setId(501L);
                    }
                    return saved;
                });
        when(prescriptionFileRepository.findByReservationIdInOrderByUploadedAtAsc(List.of(10L)))
                .thenReturn(List.of(ReservationPrescriptionFile.builder()
                        .id(501L)
                        .reservationId(10L)
                        .pharmacyId(2L)
                        .userId(1000L)
                        .originalFilename("rx.png")
                        .contentType("image/png")
                        .fileSize((long) payload.length)
                        .fileData(payload)
                        .uploadedAt(LocalDateTime.now())
                        .reviewStatus(PrescriptionReviewStatus.PENDING_REVIEW)
                        .build()));
        when(telegramClient.buildMiniAppUserReservationStatusUrl(eq("active"), eq(10L), any()))
                .thenReturn("https://tenahub-miniapp.vercel.app/#/search?section=active&reservationId=10");

        PrescriptionStatusResponseDTO response = service.uploadPrescriptionFiles(
                10L,
                null,
                1000L,
                2L,
                null,
                "Need urgent refill",
                List.of(file));

        assertEquals("PENDING_REVIEW", response.getReviewStatus());
        assertEquals("amoxicillin", response.getMedicineName());
        assertNotNull(response.getExpiresAt());

        verify(telegramClient).sendPharmacyPrescriptionReviewCard(eq(777L), any(PrescriptionStatusResponseDTO.class));
        verify(telegramClient, never()).sendDocumentBytes(anyLong(), any(byte[].class), anyString(), anyString());
        verify(telegramClient).sendMessageWithMiniAppButton(eq(1000L), any(String.class), anyString(), anyString());
    }

    @Test
    void reviewPrescription_approve_marksReservationAndFilesApproved_andHoldsInventory() {
        MedicineReservation reservation = MedicineReservation.builder()
                .id(21L)
                .pharmacyId(3L)
                .userId(500L)
                .medicineName("cefixime")
                .status(MedicineReservationStatus.PENDING)
                .prescriptionRequired(true)
                .prescriptionReviewStatus(PrescriptionReviewStatus.PENDING_REVIEW)
                .inventoryHeld(false)
                .createdAt(LocalDateTime.now())
                .build();

        Pharmacy pharmacy = Pharmacy.builder().id(3L).telegramId(9000L).build();
        ReservationPrescriptionFile file = ReservationPrescriptionFile.builder()
                .id(601L)
                .reservationId(21L)
                .pharmacyId(3L)
                .userId(500L)
                .originalFilename("prescription.jpg")
                .contentType("image/jpeg")
                .fileData(new byte[]{1, 2, 3})
                .uploadedAt(LocalDateTime.now())
                .reviewStatus(PrescriptionReviewStatus.PENDING_REVIEW)
                .build();

        when(reservationRepository.findById(21L)).thenReturn(Optional.of(reservation));
        when(pharmacyRepository.findByTelegramId(9000L)).thenReturn(Optional.of(pharmacy));
        when(reservationRepository.save(any(MedicineReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(prescriptionFileRepository.findByReservationIdInOrderByUploadedAtAsc(List.of(21L))).thenReturn(List.of(file));
        when(prescriptionFileRepository.save(any(ReservationPrescriptionFile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pharmacyInventoryRepository.findByPharmacyIdAndMedicineNameIgnoreCase(3L, "cefixime"))
                .thenReturn(Optional.of(PharmacyInventory.builder().id(333L).pharmacyId(3L).medicineName("cefixime").build()));
        when(pharmacyRepository.findById(3L)).thenReturn(Optional.of(pharmacy));
        when(telegramClient.buildMiniAppUserReservationStatusUrl(eq("active"), eq(21L), any()))
                .thenReturn("https://tenahub-miniapp.vercel.app/#/search?section=active&reservationId=21");

        PrescriptionReviewRequestDTO request = PrescriptionReviewRequestDTO.builder()
                .decision("approve")
                .build();

        PrescriptionStatusResponseDTO response = service.reviewPrescription(21L, null, 9000L, request);

        assertEquals("APPROVED", response.getReviewStatus());
        verify(reservationService).holdInventoryForApprovedPrescription(any(MedicineReservation.class));
        verify(prescriptionFileRepository, atLeastOnce()).save(any(ReservationPrescriptionFile.class));
        verify(telegramClient).sendMessageWithMiniAppButton(eq(500L), any(String.class), anyString(), anyString());

        ArgumentCaptor<MedicineReservation> reservationCaptor = ArgumentCaptor.forClass(MedicineReservation.class);
        verify(reservationRepository, atLeastOnce()).save(reservationCaptor.capture());
        assertEquals(PrescriptionReviewStatus.APPROVED, reservationCaptor.getValue().getPrescriptionReviewStatus());
    }

    @Test
    void reviewPrescription_reject_reopensUploadWithoutCancellingReservation() {
        MedicineReservation reservation = MedicineReservation.builder()
                .id(30L)
                .pharmacyId(4L)
                .userId(700L)
                .medicineName("azithromycin")
                .status(MedicineReservationStatus.PENDING)
                .prescriptionRequired(true)
                .prescriptionReviewStatus(PrescriptionReviewStatus.PENDING_REVIEW)
                .createdAt(LocalDateTime.now())
                .build();

        Pharmacy pharmacy = Pharmacy.builder().id(4L).telegramId(444L).build();
        ReservationPrescriptionFile file = ReservationPrescriptionFile.builder()
                .id(900L)
                .reservationId(30L)
                .pharmacyId(4L)
                .userId(700L)
                .fileData(new byte[]{7})
                .originalFilename("rx.pdf")
                .uploadedAt(LocalDateTime.now())
                .reviewStatus(PrescriptionReviewStatus.PENDING_REVIEW)
                .build();

        when(reservationRepository.findById(30L)).thenReturn(Optional.of(reservation));
        when(pharmacyRepository.findByTelegramId(444L)).thenReturn(Optional.of(pharmacy));
        when(reservationRepository.save(any(MedicineReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(prescriptionFileRepository.findByReservationIdInOrderByUploadedAtAsc(List.of(30L))).thenReturn(List.of(file));
        when(prescriptionFileRepository.save(any(ReservationPrescriptionFile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pharmacyInventoryRepository.findByPharmacyIdAndMedicineNameIgnoreCase(4L, "azithromycin"))
                .thenReturn(Optional.of(PharmacyInventory.builder().id(404L).pharmacyId(4L).medicineName("azithromycin").build()));
        when(pharmacyRepository.findById(4L)).thenReturn(Optional.of(pharmacy));
        when(telegramClient.buildMiniAppUserReservationStatusUrl(eq("active"), eq(30L), any()))
                .thenReturn("https://tenahub-miniapp.vercel.app/#/search?section=active&reservationId=30");

        PrescriptionReviewRequestDTO request = PrescriptionReviewRequestDTO.builder()
                .decision("reject")
                .rejectionReason("invalid dosage")
                .build();

        PrescriptionStatusResponseDTO response = service.reviewPrescription(30L, null, 444L, request);

        assertEquals("UPLOAD_REQUIRED", response.getReviewStatus());
        verify(reservationService, never()).rejectReservation(anyLong(), anyString());
        verify(reservationService, never()).holdInventoryForApprovedPrescription(any(MedicineReservation.class));
        verify(telegramClient).sendMessageWithMiniAppButton(eq(700L), org.mockito.ArgumentMatchers.contains("invalid dosage"), anyString(), anyString());

        ArgumentCaptor<MedicineReservation> reservationCaptor = ArgumentCaptor.forClass(MedicineReservation.class);
        verify(reservationRepository, atLeastOnce()).save(reservationCaptor.capture());
        MedicineReservation saved = reservationCaptor.getValue();
        assertEquals(MedicineReservationStatus.PENDING, saved.getStatus());
        assertEquals(PrescriptionReviewStatus.UPLOAD_REQUIRED, saved.getPrescriptionReviewStatus());
        assertEquals("invalid dosage", saved.getPrescriptionRejectionReason());
    }

    @Test
    void uploadPrescriptionFiles_nonPendingReservation_throws() {
        MedicineReservation reservation = MedicineReservation.builder()
                .id(77L)
                .pharmacyId(2L)
                .userId(3L)
                .medicineName("drug")
                .status(MedicineReservationStatus.APPROVED)
                .prescriptionRequired(true)
                .build();

        MockMultipartFile file = new MockMultipartFile("files", "rx.png", "image/png", new byte[]{1});

        when(reservationRepository.findById(77L)).thenReturn(Optional.of(reservation));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.uploadPrescriptionFiles(77L, null, 3L, 2L, null, null, List.of(file)));

        assertEquals("Prescription files can only be uploaded for pending reservations", ex.getMessage());
    }

    @Test
    void reviewPrescription_rejectsOtherPharmacy() {
        MedicineReservation reservation = MedicineReservation.builder()
                .id(40L)
                .pharmacyId(4L)
                .userId(700L)
                .medicineName("azithromycin")
                .status(MedicineReservationStatus.PENDING)
                .prescriptionRequired(true)
                .prescriptionReviewStatus(PrescriptionReviewStatus.PENDING_REVIEW)
                .build();
        Pharmacy otherPharmacy = Pharmacy.builder().id(9L).telegramId(999L).build();
        when(reservationRepository.findById(40L)).thenReturn(Optional.of(reservation));
        when(pharmacyRepository.findByTelegramId(999L)).thenReturn(Optional.of(otherPharmacy));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.reviewPrescription(40L, null, 999L,
                        PrescriptionReviewRequestDTO.builder().decision("approve").build()));

        assertEquals("Reservation does not belong to this pharmacy", error.getMessage());
        verify(reservationService, never()).holdInventoryForApprovedPrescription(any(MedicineReservation.class));
    }

    @Test
    void reviewPrescription_clarify_setsNeedsClarificationAndNotifiesCustomer() {
        MedicineReservation reservation = MedicineReservation.builder()
                .id(50L)
                .pharmacyId(8L)
                .userId(800L)
                .medicineName("metformin")
                .status(MedicineReservationStatus.PENDING)
                .prescriptionRequired(true)
                .prescriptionReviewStatus(PrescriptionReviewStatus.PENDING_REVIEW)
                .createdAt(LocalDateTime.now())
                .build();
        Pharmacy pharmacy = Pharmacy.builder().id(8L).telegramId(8800L).build();
        ReservationPrescriptionFile file = ReservationPrescriptionFile.builder()
                .id(950L)
                .reservationId(50L)
                .pharmacyId(8L)
                .userId(800L)
                .fileData(new byte[]{1})
                .originalFilename("rx.jpg")
                .uploadedAt(LocalDateTime.now())
                .reviewStatus(PrescriptionReviewStatus.PENDING_REVIEW)
                .build();

        when(reservationRepository.findById(50L)).thenReturn(Optional.of(reservation));
        when(pharmacyRepository.findByTelegramId(8800L)).thenReturn(Optional.of(pharmacy));
        when(reservationRepository.save(any(MedicineReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(prescriptionFileRepository.findByReservationIdInOrderByUploadedAtAsc(List.of(50L))).thenReturn(List.of(file));
        when(prescriptionFileRepository.save(any(ReservationPrescriptionFile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pharmacyInventoryRepository.findByPharmacyIdAndMedicineNameIgnoreCase(8L, "metformin"))
                .thenReturn(Optional.of(PharmacyInventory.builder().id(808L).pharmacyId(8L).medicineName("metformin").build()));
        when(pharmacyRepository.findById(8L)).thenReturn(Optional.of(pharmacy));
        when(telegramClient.buildMiniAppUserReservationStatusUrl(eq("active"), eq(50L), any()))
                .thenReturn("https://tenahub-miniapp.vercel.app/#/search?section=active&reservationId=50");

        PrescriptionStatusResponseDTO response = service.reviewPrescription(50L, null, 8800L,
                PrescriptionReviewRequestDTO.builder()
                        .decision("clarify")
                        .clarificationMessage("Please upload a clearer photo of the dosage")
                        .build());

        assertEquals("NEEDS_CLARIFICATION", response.getReviewStatus());
        assertEquals("Please upload a clearer photo of the dosage", response.getClarificationMessage());
        verify(telegramClient).sendMessageWithMiniAppButton(eq(800L), any(String.class), anyString(), anyString());
        verify(reservationStatusHistoryService).record(
                any(MedicineReservation.class),
                eq("PENDING"),
                eq("PENDING"),
                eq(8800L),
                eq("rx:PENDING_REVIEW→NEEDS_CLARIFICATION"));
        verify(pharmacyNotificationService, never()).create(anyLong(), any(), anyString(), anyString(), any(), any());
    }

    @Test
    void uploadPrescriptionFiles_fromClarification_setsPendingReviewAndInboxReply() {
        MedicineReservation reservation = MedicineReservation.builder()
                .id(60L)
                .pharmacyId(9L)
                .userId(900L)
                .medicineName("insulin")
                .requestedQuantity(1)
                .status(MedicineReservationStatus.PENDING)
                .prescriptionRequired(true)
                .prescriptionReviewStatus(PrescriptionReviewStatus.NEEDS_CLARIFICATION)
                .prescriptionClarificationMessage("Need clearer dose")
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .build();
        Pharmacy pharmacy = Pharmacy.builder().id(9L).name("Rx Nine").telegramId(9900L).build();
        PharmacyInventory inventory = PharmacyInventory.builder().id(909L).pharmacyId(9L).medicineName("insulin").build();
        byte[] payload = "reply".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("files", "rx2.png", "image/png", payload);

        when(reservationRepository.findById(60L)).thenReturn(Optional.of(reservation));
        when(pharmacyInventoryRepository.findByPharmacyIdAndMedicineNameIgnoreCase(9L, "insulin"))
                .thenReturn(Optional.of(inventory));
        when(pharmacyRepository.findById(9L)).thenReturn(Optional.of(pharmacy));
        when(reservationRepository.save(any(MedicineReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(prescriptionFileRepository.save(any(ReservationPrescriptionFile.class)))
                .thenAnswer(invocation -> {
                    ReservationPrescriptionFile saved = invocation.getArgument(0);
                    if (saved.getId() == null) {
                        saved.setId(701L);
                    }
                    return saved;
                });
        when(prescriptionFileRepository.findByReservationIdInOrderByUploadedAtAsc(List.of(60L)))
                .thenReturn(List.of(ReservationPrescriptionFile.builder()
                        .id(701L)
                        .reservationId(60L)
                        .pharmacyId(9L)
                        .userId(900L)
                        .originalFilename("rx2.png")
                        .contentType("image/png")
                        .fileSize((long) payload.length)
                        .fileData(payload)
                        .uploadedAt(LocalDateTime.now())
                        .reviewStatus(PrescriptionReviewStatus.PENDING_REVIEW)
                        .build()));
        when(telegramClient.buildMiniAppUserReservationStatusUrl(eq("active"), eq(60L), any()))
                .thenReturn("https://tenahub-miniapp.vercel.app/#/search?section=active&reservationId=60");

        PrescriptionStatusResponseDTO response = service.uploadPrescriptionFiles(
                60L, null, 900L, 9L, null, null, List.of(file));

        assertEquals("PENDING_REVIEW", response.getReviewStatus());
        verify(pharmacyNotificationService).create(
                eq(9L),
                eq(PharmacyNotificationType.PRESCRIPTION_CLARIFICATION_REPLY),
                anyString(),
                anyString(),
                eq(60L),
                eq("insulin"));
        verify(reservationStatusHistoryService).record(
                any(MedicineReservation.class),
                eq("PENDING"),
                eq("PENDING"),
                eq(900L),
                eq("rx:NEEDS_CLARIFICATION→PENDING_REVIEW"));
    }
}
