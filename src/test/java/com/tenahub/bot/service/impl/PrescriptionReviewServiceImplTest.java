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
import com.tenahub.bot.service.ReservationService;
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

    private PrescriptionReviewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PrescriptionReviewServiceImpl(
                reservationRepository,
                prescriptionFileRepository,
                pharmacyRepository,
                pharmacyInventoryRepository,
                reservationService,
                telegramClient);
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
        verify(telegramClient).sendDocumentBytes(eq(777L), any(byte[].class), eq("rx.png"), any(String.class));
        verify(telegramClient).sendMessage(eq(1000L), any(String.class), eq("HTML"));
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

        PrescriptionReviewRequestDTO request = PrescriptionReviewRequestDTO.builder()
                .decision("approve")
                .build();

        PrescriptionStatusResponseDTO response = service.reviewPrescription(21L, null, 9000L, request);

        assertEquals("APPROVED", response.getReviewStatus());
        verify(reservationService).holdInventoryForApprovedPrescription(any(MedicineReservation.class));
        verify(prescriptionFileRepository, atLeastOnce()).save(any(ReservationPrescriptionFile.class));

        ArgumentCaptor<MedicineReservation> reservationCaptor = ArgumentCaptor.forClass(MedicineReservation.class);
        verify(reservationRepository, atLeastOnce()).save(reservationCaptor.capture());
        assertEquals(PrescriptionReviewStatus.APPROVED, reservationCaptor.getValue().getPrescriptionReviewStatus());
    }

    @Test
    void reviewPrescription_reject_marksRejectedAndDelegatesReservationRejection() {
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
        when(reservationService.rejectReservation(30L, "invalid dosage")).thenAnswer(invocation -> {
            reservation.setStatus(MedicineReservationStatus.REJECTED);
            return reservation;
        });
        when(reservationRepository.save(any(MedicineReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(prescriptionFileRepository.findByReservationIdInOrderByUploadedAtAsc(List.of(30L))).thenReturn(List.of(file));
        when(prescriptionFileRepository.save(any(ReservationPrescriptionFile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pharmacyInventoryRepository.findByPharmacyIdAndMedicineNameIgnoreCase(4L, "azithromycin"))
                .thenReturn(Optional.of(PharmacyInventory.builder().id(404L).pharmacyId(4L).medicineName("azithromycin").build()));
        when(pharmacyRepository.findById(4L)).thenReturn(Optional.of(pharmacy));

        PrescriptionReviewRequestDTO request = PrescriptionReviewRequestDTO.builder()
                .decision("reject")
                .rejectionReason("invalid dosage")
                .build();

        PrescriptionStatusResponseDTO response = service.reviewPrescription(30L, null, 444L, request);

        assertEquals("REJECTED", response.getReviewStatus());
        verify(reservationService).rejectReservation(30L, "invalid dosage");
        verify(reservationService, never()).holdInventoryForApprovedPrescription(any(MedicineReservation.class));
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
}
