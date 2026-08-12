package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.AiChatDebugResponseDTO;
import com.tenahub.bot.dto.AiChatRequestDTO;
import com.tenahub.bot.dto.AiChatResponseDTO;
import com.tenahub.bot.dto.MiniAppReservationCardDTO;
import com.tenahub.bot.dto.MedicineInfoDTO;
import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.PrescriptionReviewStatus;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.AdminService;
import com.tenahub.bot.service.InventoryService;
import com.tenahub.bot.service.LicenseComplianceService;
import com.tenahub.bot.service.MiniAppService;
import com.tenahub.bot.service.ReservationService;
import com.tenahub.bot.service.MedicineKnowledgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAssistantServiceImplTest {

    @Mock
    private MiniAppService miniAppService;
    @Mock
    private ReservationService reservationService;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private AdminService adminService;
    @Mock
    private LicenseComplianceService licenseComplianceService;
    @Mock
    private PharmacyRepository pharmacyRepository;
        @Mock
        private MedicineKnowledgeService medicineKnowledgeService;

    private AiAssistantServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiAssistantServiceImpl(
                miniAppService,
                reservationService,
                inventoryService,
                adminService,
                licenseComplianceService,
                                pharmacyRepository,
                                medicineKnowledgeService);
        ReflectionTestUtils.setField(service, "adminChatId", 999L);
    }

    @Test
    void userPendingIntent_usesMiniAppServiceAsSourceOfTruth() {
        MiniAppReservationCardDTO card = MiniAppReservationCardDTO.builder()
                .reservationId(11L)
                .reservationStatus("PENDING")
                .prescriptionStatus("PENDING_REVIEW")
                .userFacingStage("PRESCRIPTION_REVIEW")
                .createdAt(LocalDateTime.now())
                .build();

        when(miniAppService.getActiveReservations(111L)).thenReturn(List.of(card));

        AiChatResponseDTO response = service.chat(
                AiChatRequestDTO.builder().message("why is my reservation pending").build(),
                111L,
                null,
                null);

        assertEquals("USER_RESERVATION_PENDING_REASON", response.getIntent());
        assertEquals("user", response.getRole());
        verify(miniAppService).getActiveReservations(111L);
    }

    @Test
    void pharmacyPendingReviewsCount_returnsCountsFromReservationService() {
        when(pharmacyRepository.existsByTelegramId(222L)).thenReturn(true);
        when(reservationService.getPrescriptionReservations(222L)).thenReturn(List.of(
                MedicineReservation.builder().id(1L).prescriptionReviewStatus(PrescriptionReviewStatus.PENDING_REVIEW).build(),
                MedicineReservation.builder().id(2L).prescriptionReviewStatus(PrescriptionReviewStatus.UPLOAD_REQUIRED).build(),
                MedicineReservation.builder().id(3L).prescriptionReviewStatus(PrescriptionReviewStatus.PENDING_REVIEW).build()
        ));

        AiChatResponseDTO response = service.chat(
                AiChatRequestDTO.builder().message("how many pending prescription reviews do I have").build(),
                null,
                222L,
                null);

        assertEquals("PHARMACY_PENDING_PRESCRIPTION_REVIEWS_COUNT", response.getIntent());
        assertEquals("pharmacy", response.getRole());
        assertEquals(true, response.getAnswer().contains("2"));
    }

    @Test
    void userCannotAccessAdminSystemSummary() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.chat(
                        AiChatRequestDTO.builder().message("what is today's system summary").build(),
                        101L,
                        null,
                        null));

        assertEquals("This question is available for admin role only.", ex.getMessage());
    }

    @Test
    void adminComplianceSummary_usesComplianceService() {
        when(licenseComplianceService.buildSummary()).thenReturn(
                new LicenseComplianceService.ComplianceSummary(3, 2, 1, 5, 4));

        AiChatResponseDTO response = service.chat(
                AiChatRequestDTO.builder().message("show compliance summary").build(),
                null,
                null,
                999L);

        assertEquals("ADMIN_COMPLIANCE_SUMMARY", response.getIntent());
        assertEquals("admin", response.getRole());
        assertEquals(true, response.getAnswer().contains("expiring soon=3"));
    }

        @Test
        void chatDebug_returnsMatchedIntentRoleAndDataSource() {
                when(pharmacyRepository.existsByTelegramId(222L)).thenReturn(true);
                when(reservationService.getPrescriptionReservations(222L)).thenReturn(List.of());

                AiChatDebugResponseDTO response = service.chatDebug(
                                AiChatRequestDTO.builder().message("how many pending prescription reviews do I have").build(),
                                null,
                                222L,
                                null);

                assertEquals("PHARMACY_PENDING_PRESCRIPTION_REVIEWS_COUNT", response.getMatchedIntent());
                assertEquals("pharmacy", response.getResolvedRole());
                assertEquals(222L, response.getActorTelegramId());
                assertEquals("ReservationService.getPrescriptionReservations", response.getDataSources().get(0));
        }

    @Test
    void userReservationStatus_showsMedicinePharmacyAndStatus() {
        MiniAppReservationCardDTO card = MiniAppReservationCardDTO.builder()
                .reservationId(55L)
                .medicineName("Paracetamol")
                .pharmacyName("Tena Pharmacy")
                .reservationStatus("APPROVED")
                .quantity(2)
                .userFacingStage("APPROVED")
                .createdAt(LocalDateTime.now())
                .build();

        when(miniAppService.getActiveReservations(111L)).thenReturn(List.of(card));

        AiChatResponseDTO response = service.chat(
                AiChatRequestDTO.builder().message("what is my reservation status").build(),
                111L,
                null,
                null);

        assertEquals("USER_RESERVATION_STATUS", response.getIntent());
        assertEquals("user", response.getRole());
        assertEquals(true, response.getAnswer().contains("Paracetamol"));
        assertEquals(true, response.getAnswer().contains("Tena Pharmacy"));
        assertEquals(true, response.getAnswer().contains("APPROVED"));
    }

    @Test
    void userPrescriptionStatus_showsStatusFromActiveReservation() {
        MiniAppReservationCardDTO card = MiniAppReservationCardDTO.builder()
                .reservationId(77L)
                .medicineName("Insulin")
                .prescriptionRequired(true)
                .prescriptionStatus("REJECTED")
                .prescriptionRejectionReason("Image was blurry")
                .createdAt(LocalDateTime.now())
                .build();

        when(miniAppService.getActiveReservations(111L)).thenReturn(List.of(card));

        AiChatResponseDTO response = service.chat(
                AiChatRequestDTO.builder().message("what is my prescription status").build(),
                111L,
                null,
                null);

        assertEquals("USER_PRESCRIPTION_STATUS", response.getIntent());
        assertEquals("user", response.getRole());
        assertEquals(true, response.getAnswer().contains("Rejected"));
        assertEquals(true, response.getAnswer().contains("Image was blurry"));
    }

    @Test
    void userReservationHistory_returnsSummaryFromMiniAppService() {
        MiniAppReservationCardDTO fulfilled = MiniAppReservationCardDTO.builder()
                .reservationId(10L).reservationStatus("FULFILLED").createdAt(LocalDateTime.now()).build();
        MiniAppReservationCardDTO rejected = MiniAppReservationCardDTO.builder()
                .reservationId(11L).reservationStatus("REJECTED").createdAt(LocalDateTime.now()).build();

        when(miniAppService.getReservationHistory(111L)).thenReturn(List.of(fulfilled, rejected));

        AiChatResponseDTO response = service.chat(
                AiChatRequestDTO.builder().message("show my reservation history").build(),
                111L, null, null);

        assertEquals("USER_RESERVATION_HISTORY", response.getIntent());
        assertEquals("user", response.getRole());
        assertEquals(true, response.getAnswer().contains("Total reservations: 2"));
    }

    @Test
    void userCancelHelp_showsCancellableCount() {
        MiniAppReservationCardDTO pending = MiniAppReservationCardDTO.builder()
                .reservationId(20L).reservationStatus("PENDING").createdAt(LocalDateTime.now()).build();
        MiniAppReservationCardDTO approved = MiniAppReservationCardDTO.builder()
                .reservationId(21L).reservationStatus("APPROVED").createdAt(LocalDateTime.now()).build();

        when(miniAppService.getActiveReservations(111L)).thenReturn(List.of(pending, approved));

        AiChatResponseDTO response = service.chat(
                AiChatRequestDTO.builder().message("how do I cancel my reservation").build(),
                111L, null, null);

        assertEquals("USER_CANCEL_HELP", response.getIntent());
        assertEquals(true, response.getAnswer().contains("1 PENDING reservation"));
    }

    @Test
    void pharmacyPendingReservations_returnsCountAndMedicines() {
        when(pharmacyRepository.existsByTelegramId(222L)).thenReturn(true);
        when(reservationService.getPendingReservations(222L)).thenReturn(List.of(
                MedicineReservation.builder().id(1L).medicineName("Amoxicillin").build(),
                MedicineReservation.builder().id(2L).medicineName("Ibuprofen").build()
        ));

        AiChatResponseDTO response = service.chat(
                AiChatRequestDTO.builder().message("how many pending reservations do I have").build(),
                null, 222L, null);

        assertEquals("PHARMACY_PENDING_RESERVATIONS", response.getIntent());
        assertEquals("pharmacy", response.getRole());
        assertEquals(true, response.getAnswer().contains("2 pending reservation"));
    }

    @Test
    void pharmacyApprovedReservations_returnsReadyForPickupCount() {
        when(pharmacyRepository.existsByTelegramId(222L)).thenReturn(true);
        when(reservationService.getApprovedReservations(222L)).thenReturn(List.of(
                MedicineReservation.builder().id(5L).medicineName("Metformin").build()
        ));

        AiChatResponseDTO response = service.chat(
                AiChatRequestDTO.builder().message("how many approved reservations are ready for pickup").build(),
                null, 222L, null);

        assertEquals("PHARMACY_APPROVED_RESERVATIONS", response.getIntent());
        assertEquals(true, response.getAnswer().contains("1 approved reservation"));
    }

    @Test
    void adminReservationOversight_usesDetailedOversightService() {
        when(adminService.viewDetailedReservationOversight()).thenReturn("Pending: 10, Approved: 5");

        AiChatResponseDTO response = service.chat(
                AiChatRequestDTO.builder().message("give me a reservation breakdown").build(),
                null, null, 999L);

        assertEquals("ADMIN_RESERVATION_OVERSIGHT", response.getIntent());
        assertEquals("admin", response.getRole());
        assertEquals(true, response.getAnswer().contains("Pending: 10"));
    }

    @Test
    void adminTopMedicines_usesTopMedicinesService() {
        when(adminService.viewTopMedicinesDetails()).thenReturn("1. Paracetamol: 50 reservations");

        AiChatResponseDTO response = service.chat(
                AiChatRequestDTO.builder().message("what are the top medicines by demand").build(),
                null, null, 999L);

        assertEquals("ADMIN_TOP_MEDICINES", response.getIntent());
        assertEquals(true, response.getAnswer().contains("Paracetamol"));
    }

    // ── Medicine tests ──────────────────────────────────────────────────────
    @Test
    void medicineUsage_returnsMedicineInfoFromKnowledgeService() {
        MedicineInfoDTO info = MedicineInfoDTO.builder()
                .name("Ibuprofen")
                .use("Used to relieve pain and reduce inflammation.")
                .safetyNote("Consult a pharmacist or clinician.")
                .build();
        when(medicineKnowledgeService.detectMedicineName("what is ibuprofen used for"))
                .thenReturn("ibuprofen");
        when(medicineKnowledgeService.lookup("ibuprofen")).thenReturn(Optional.of(info));

        AiChatResponseDTO response = service.chat(
                AiChatRequestDTO.builder().message("what is ibuprofen used for").build(),
                111L, null, null);

        assertEquals("MEDICINE_USAGE", response.getIntent());
        assertEquals("Ibuprofen", response.getMedicineName());
        assertEquals("general_education", response.getSafetyLevel());
        assertEquals(true, response.getAnswer().contains("pain"));
    }

    @Test
    void medicineHowToTake_includesConsultPharmacistSafetyLevel() {
        MedicineInfoDTO info = MedicineInfoDTO.builder()
                .name("Paracetamol")
                .howToTake("Take 500mg every 4–6 hours.")
                .safetyNote("Consult a pharmacist or clinician.")
                .build();
        when(medicineKnowledgeService.detectMedicineName("how do i take paracetamol"))
                .thenReturn("paracetamol");
        when(medicineKnowledgeService.lookup("paracetamol")).thenReturn(Optional.of(info));

        AiChatResponseDTO response = service.chat(
                AiChatRequestDTO.builder().message("how do i take paracetamol").build(),
                111L, null, null);

        assertEquals("MEDICINE_HOW_TO_TAKE", response.getIntent());
        assertEquals("consult_pharmacist", response.getSafetyLevel());
        assertEquals("Paracetamol", response.getMedicineName());
    }

    @Test
    void medicineSideEffects_unknownMedicineReturnsNotFoundMessage() {
        when(medicineKnowledgeService.detectMedicineName("side effects of xyz123"))
                .thenReturn("xyz123");
        when(medicineKnowledgeService.lookup("xyz123")).thenReturn(Optional.empty());

        AiChatResponseDTO response = service.chat(
                AiChatRequestDTO.builder().message("side effects of xyz123").build(),
                111L, null, null);

        assertEquals("MEDICINE_SIDE_EFFECTS", response.getIntent());
        assertEquals(true, response.getAnswer().contains("xyz123"));
        assertEquals(true, response.getAnswer().contains("don't have information"));
    }

    @Test
    void medicineIntent_noNameDetected_returnsAskForName() {
        when(medicineKnowledgeService.detectMedicineName("what are the side effects"))
                .thenReturn(null);

        AiChatResponseDTO response = service.chat(
                AiChatRequestDTO.builder().message("what are the side effects").build(),
                111L, null, null);

        assertEquals("MEDICINE_SIDE_EFFECTS", response.getIntent());
        assertEquals(true, response.getAnswer().contains("medicine name"));
    }

    @Test
    void userReservationHistoryIntent_usesMiniAppHistory() {
        MiniAppReservationCardDTO card = MiniAppReservationCardDTO.builder()
                .reservationId(21L)
                .reservationStatus("FULFILLED")
                .build();
        when(miniAppService.getReservationHistory(111L)).thenReturn(List.of(card));

        AiChatResponseDTO response = service.chat(
                AiChatRequestDTO.builder().message("show my reservation history").build(),
                111L,
                null,
                null);

        assertEquals("USER_RESERVATION_HISTORY", response.getIntent());
        assertEquals("user", response.getRole());
        verify(miniAppService).getReservationHistory(111L);
    }
}
