package com.tenahub.bot.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tenahub.bot.dto.MiniAppMedicinePhotosDTO;
import com.tenahub.bot.dto.MiniAppMedicineSummaryDTO;
import com.tenahub.bot.dto.MiniAppAuthSendCodeRequestDTO;
import com.tenahub.bot.dto.MiniAppAuthVerifyCodeRequestDTO;
import com.tenahub.bot.dto.MiniAppAuthVerifyCodeResponseDTO;
import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.dto.MiniAppPharmacyDetailDTO;
import com.tenahub.bot.dto.MiniAppPharmacyPhotosDTO;
import com.tenahub.bot.dto.MultiMedicinePharmacyResultDTO;
import com.tenahub.bot.dto.MiniAppReservationCardDTO;
import com.tenahub.bot.dto.MiniAppReservationConfirmRequestDTO;
import com.tenahub.bot.dto.MiniAppReservationConfirmResponseDTO;
import com.tenahub.bot.dto.MiniAppReservationPreloadResponseDTO;
import com.tenahub.bot.dto.MiniAppReservationCreateRequestDTO;
import com.tenahub.bot.dto.MiniAppReservationResponseDTO;
import com.tenahub.bot.dto.PrescriptionStatusResponseDTO;
import com.tenahub.bot.dto.MiniAppPharmacyReportRequestDTO;
import com.tenahub.bot.dto.PharmacyResponseDTO;
import com.tenahub.bot.service.AdminInboxService;
import com.tenahub.bot.service.MiniAppActorResolver;
import com.tenahub.bot.service.MiniAppAuthException;
import com.tenahub.bot.service.MiniAppService;
import com.tenahub.bot.service.PrescriptionReviewService;
import com.tenahub.bot.service.TelegramWebAppAuthService;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * REST Controller for Telegram Mini App backend API.
 * Provides read-only endpoints for pharmacy-related data.
 * 
 * API Base Path: /api/miniapp
 */
@RestController
@RequestMapping({"/api/miniapp", "/proxyapi/api/miniapp"})
public class MiniAppController {

    private static final Logger log = LoggerFactory.getLogger(MiniAppController.class);
    
    @Autowired
    private MiniAppService miniAppService;

    @Autowired
    private PrescriptionReviewService prescriptionReviewService;

    @Autowired
    private TelegramWebAppAuthService telegramWebAppAuthService;

    @Autowired
    private MiniAppActorResolver miniAppActorResolver;

    @Autowired
    private AdminInboxService adminInboxService;

    private Long requireVerifiedTelegramUserId(String... initDataCandidates) {
        return telegramWebAppAuthService.requireUserId(
                Stream.concat(
                                Stream.of(initDataCandidates),
                                Stream.of(miniAppActorResolver.currentInitData()))
                        .toArray(String[]::new));
    }

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }

    @PostMapping("/auth/send-code")
    public ResponseEntity<?> sendCode(@RequestBody MiniAppAuthSendCodeRequestDTO request) {
        try {
            MiniAppOperationResponseDTO response = miniAppService.sendVerificationCode(request);
            return ResponseEntity.ok(response);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error sending verification code: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }

    @PostMapping("/auth/verify-code")
    public ResponseEntity<?> verifyCode(@RequestBody MiniAppAuthVerifyCodeRequestDTO request) {
        try {
            MiniAppAuthVerifyCodeResponseDTO response = miniAppService.verifyCode(request);
            return ResponseEntity.ok(response);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error verifying code: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam(required = false) String medicine,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false, name = "sortBy") String sortBy,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false, name = "filterBy") String filterBy,
            @RequestParam(required = false) Long medicineId
    ) {
        try {
            List<PharmacyResponseDTO> result = miniAppService.search(
                    medicine,
                    medicineId,
                    latitude,
                    longitude,
                    userId,
                    firstNonBlank(sort, sortBy),
                    firstNonBlank(filter, filterBy));
            return ResponseEntity.ok(result);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error searching pharmacies: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }

    @GetMapping("/medicines/search")
    public ResponseEntity<?> searchMedicineCatalog(
            @RequestParam(required = false) String medicine,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude
    ) {
        try {
            List<MiniAppMedicineSummaryDTO> result = miniAppService.searchMedicineCatalog(
                    medicine,
                    latitude,
                    longitude);
            return ResponseEntity.ok(result);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error searching medicines: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }

    @GetMapping("/search/multi")
    public ResponseEntity<?> searchMultipleMedicines(
            @RequestParam(required = false) String medicines,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Long userId
    ) {
        try {
            List<String> names = parseMedicineNames(medicines);
            if (names.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("medicines is required");
            }
            if (latitude == null || longitude == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("latitude and longitude are required");
            }
            List<MultiMedicinePharmacyResultDTO> result = miniAppService.searchMultipleMedicines(
                    names,
                    latitude,
                    longitude,
                    userId);
            return ResponseEntity.ok(result);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error searching pharmacies: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }

    @GetMapping("/medicines/analogues")
    public ResponseEntity<?> searchAnalogues(
            @RequestParam String medicine,
            @RequestParam(required = false) String medicineId,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Long userId
    ) {
        try {
            List<MiniAppMedicineSummaryDTO> result = miniAppService.searchAnalogues(
                    medicine,
                    parseOptionalLong(medicineId),
                    latitude,
                    longitude,
                    userId);
            return ResponseEntity.ok(result);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error searching analogues: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }

    @GetMapping("/pharmacies/{pharmacyId}")
    public ResponseEntity<?> getPharmacyDetails(@PathVariable Long pharmacyId) {
        try {
            MiniAppPharmacyDetailDTO result = miniAppService.getPharmacyDetails(pharmacyId);
            return ResponseEntity.ok(result);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error fetching pharmacy details: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }

    @PostMapping("/reservations")
    public ResponseEntity<?> createReservation(@RequestBody MiniAppReservationCreateRequestDTO request,
                                               @RequestHeader(value = MiniAppActorResolver.INIT_DATA_HEADER, required = false) String initDataHeader) {
        try {
            applyInitData(request, initDataHeader);
            log.info("MiniApp reservation request body: {}", request);
            log.info("MiniApp reservation request received: pharmacyId={}, userId={}, medicineName={}",
                    request == null ? null : request.getPharmacyId(),
                    request == null ? null : request.getUserId(),
                    request == null ? null : request.getMedicineName());
            MiniAppReservationResponseDTO created = miniAppService.createReservation(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error creating reservation: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }

    @PostMapping("/reservations/confirm")
    public ResponseEntity<?> confirmReservation(@RequestBody MiniAppReservationConfirmRequestDTO request,
                                                @RequestHeader(value = MiniAppActorResolver.INIT_DATA_HEADER, required = false) String initDataHeader) {
        try {
            applyInitData(request, initDataHeader);
            MiniAppReservationConfirmResponseDTO response = miniAppService.confirmReservation(request);
            return ResponseEntity.ok(response);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error confirming reservation: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }

    @GetMapping("/reservations/preload")
    public ResponseEntity<?> getReservationPreload(@RequestParam Long pharmacyId,
                                                   @RequestParam(required = false) Long medicineId,
                                                   @RequestParam(required = false) String medicineIds) {
        try {
            List<Long> requestedMedicineIds = Stream.concat(
                            medicineId == null ? Stream.empty() : Stream.of(medicineId),
                            parseMedicineIds(medicineIds).stream())
                    .distinct()
                    .toList();

            MiniAppReservationPreloadResponseDTO response = miniAppService.getReservationPreload(pharmacyId, requestedMedicineIds);
            return ResponseEntity.ok(response);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error resolving reservation preload: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }

    @GetMapping("/reservations/active")
    public ResponseEntity<?> getActiveReservations(@RequestParam(required = false) Long telegramUserId,
                                                   @RequestParam(required = false) String telegramInitData,
                                                   @RequestParam(required = false) String initData,
                                                   @RequestHeader(value = MiniAppActorResolver.INIT_DATA_HEADER, required = false) String initDataHeader) {
        try {
            Long resolvedUserId = requireVerifiedTelegramUserId(initDataHeader, telegramInitData, initData);
            log.info("[API] /reservations/active called, telegramUserId={}", resolvedUserId);
            List<MiniAppReservationCardDTO> response = miniAppService.getActiveReservations(resolvedUserId);
            log.info("[API] /reservations/active result count={}", response != null ? response.size() : 0);
            if (response != null) {
                for (MiniAppReservationCardDTO card : response) {
                    log.info("[API] ActiveReservation: reservationId={}, groupId={}, reservationStatus={}, prescriptionStatus={}, canShowQr={}, userFacingStage={}, expiresAt={}, readyForPickup={}, showQrCode={}",
                        card.getReservationId(), card.getReservationGroupId(), card.getReservationStatus(), card.getPrescriptionStatus(), card.isCanShowQr(), card.getUserFacingStage(), card.getExpiresAt(), card.isReadyForPickup(), card.isShowQrCode());
                }
            }
            return ResponseEntity.ok(response);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("[API] /reservations/active RuntimeException: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error fetching active reservations: " + e.getMessage());
        } catch (Exception e) {
            log.error("[API] /reservations/active Unexpected error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    public ResponseEntity<?> cancelReservation(@PathVariable Long reservationId,
                                               @RequestParam(required = false) Long telegramUserId,
                                               @RequestParam(required = false) String telegramInitData,
                                               @RequestParam(required = false) String initData,
                                               @RequestHeader(value = MiniAppActorResolver.INIT_DATA_HEADER, required = false) String initDataHeader) {
        try {
            Long resolvedUserId = requireVerifiedTelegramUserId(initDataHeader, telegramInitData, initData);
            if (telegramUserId != null && telegramUserId > 0 && !telegramUserId.equals(resolvedUserId)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(MiniAppOperationResponseDTO.builder().success(false).message("Telegram identity does not match telegramUserId").build());
            }
            log.info("[API] /reservations/{}/cancel called, telegramUserId={}", reservationId, resolvedUserId);
            MiniAppOperationResponseDTO response = miniAppService.cancelReservation(reservationId, resolvedUserId);
            return ResponseEntity.ok(response);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("[API] /reservations/{}/cancel rejected: {}", reservationId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(MiniAppOperationResponseDTO.builder().success(false).message(e.getMessage()).build());
        } catch (Exception e) {
            log.error("[API] /reservations/{}/cancel error: {}", reservationId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(MiniAppOperationResponseDTO.builder().success(false).message("Unexpected error").build());
        }
    }

    @GetMapping("/reservations/history")
    public ResponseEntity<?> getReservationHistory(@RequestParam(required = false) Long telegramUserId,
                                                   @RequestParam(required = false) String telegramInitData,
                                                   @RequestParam(required = false) String initData,
                                                   @RequestHeader(value = MiniAppActorResolver.INIT_DATA_HEADER, required = false) String initDataHeader) {
        try {
            Long resolvedUserId = requireVerifiedTelegramUserId(initDataHeader, telegramInitData, initData);
            List<MiniAppReservationCardDTO> response = miniAppService.getReservationHistory(resolvedUserId);
            return ResponseEntity.ok(response);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error fetching reservation history: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }

    @PostMapping("/reservations/{reservationId}/hide-history")
    public ResponseEntity<?> hideReservationFromHistory(@PathVariable Long reservationId,
                                                        @RequestParam(required = false) Long telegramUserId,
                                                        @RequestParam(required = false) String telegramInitData,
                                                        @RequestParam(required = false) String initData,
                                                        @RequestHeader(value = MiniAppActorResolver.INIT_DATA_HEADER, required = false) String initDataHeader) {
        try {
            Long resolvedUserId = requireVerifiedTelegramUserId(initDataHeader, telegramInitData, initData);
            if (telegramUserId != null && telegramUserId > 0 && !telegramUserId.equals(resolvedUserId)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(MiniAppOperationResponseDTO.builder().success(false).message("Telegram identity does not match telegramUserId").build());
            }
            MiniAppOperationResponseDTO response = miniAppService.hideReservationFromHistory(reservationId, resolvedUserId);
            return ResponseEntity.ok(response);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("[API] /reservations/{}/hide-history rejected: {}", reservationId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(MiniAppOperationResponseDTO.builder().success(false).message(e.getMessage()).build());
        } catch (Exception e) {
            log.error("[API] /reservations/{}/hide-history error: {}", reservationId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(MiniAppOperationResponseDTO.builder().success(false).message("Unexpected error").build());
        }
    }

    @PostMapping("/reservations/history/clear")
    public ResponseEntity<?> clearReservationHistory(@RequestParam(required = false) Long telegramUserId,
                                                     @RequestParam(required = false) String telegramInitData,
                                                     @RequestParam(required = false) String initData,
                                                     @RequestHeader(value = MiniAppActorResolver.INIT_DATA_HEADER, required = false) String initDataHeader) {
        try {
            Long resolvedUserId = requireVerifiedTelegramUserId(initDataHeader, telegramInitData, initData);
            if (telegramUserId != null && telegramUserId > 0 && !telegramUserId.equals(resolvedUserId)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(MiniAppOperationResponseDTO.builder().success(false).message("Telegram identity does not match telegramUserId").build());
            }
            MiniAppOperationResponseDTO response = miniAppService.clearReservationHistory(resolvedUserId);
            return ResponseEntity.ok(response);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("[API] /reservations/history/clear rejected: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(MiniAppOperationResponseDTO.builder().success(false).message(e.getMessage()).build());
        } catch (Exception e) {
            log.error("[API] /reservations/history/clear error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(MiniAppOperationResponseDTO.builder().success(false).message("Unexpected error").build());
        }
    }

    @PostMapping("/reservations/history/hide-selected")
    public ResponseEntity<?> hideSelectedReservationHistory(@RequestBody(required = false) Map<String, Object> body,
                                                            @RequestParam(required = false) Long telegramUserId,
                                                            @RequestParam(required = false) String telegramInitData,
                                                            @RequestParam(required = false) String initData,
                                                            @RequestHeader(value = MiniAppActorResolver.INIT_DATA_HEADER, required = false) String initDataHeader) {
        try {
            Long resolvedUserId = requireVerifiedTelegramUserId(initDataHeader, telegramInitData, initData);
            if (telegramUserId != null && telegramUserId > 0 && !telegramUserId.equals(resolvedUserId)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(MiniAppOperationResponseDTO.builder().success(false).message("Telegram identity does not match telegramUserId").build());
            }
            @SuppressWarnings("unchecked")
            List<Object> rawIds = body == null ? List.of() : (List<Object>) body.getOrDefault("reservationIds", List.of());
            List<Long> ids = rawIds.stream()
                    .filter(Objects::nonNull)
                    .map(value -> Long.valueOf(String.valueOf(value)))
                    .toList();
            MiniAppOperationResponseDTO response = miniAppService.hideReservationsFromHistory(ids, resolvedUserId);
            return ResponseEntity.ok(response);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("[API] /reservations/history/hide-selected rejected: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(MiniAppOperationResponseDTO.builder().success(false).message(e.getMessage()).build());
        } catch (Exception e) {
            log.error("[API] /reservations/history/hide-selected error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(MiniAppOperationResponseDTO.builder().success(false).message("Unexpected error").build());
        }
    }

    @PostMapping(value = "/reservations/prescriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadPrescriptionFiles(@RequestParam(required = false) Long reservationId,
                                                     @RequestParam(required = false) String reservationGroupId,
                                                     @RequestParam(required = false) Long telegramUserId,
                                                     @RequestParam(required = false) Long pharmacyId,
                                                     @RequestParam(required = false) Long medicineId,
                                                     @RequestParam(required = false) String note,
                                                     @RequestParam(required = false) String telegramInitData,
                                                     @RequestParam(required = false) String initData,
                                                     @RequestHeader(value = MiniAppActorResolver.INIT_DATA_HEADER, required = false) String initDataHeader,
                                                     @RequestParam("files") List<MultipartFile> files) {
        try {
            Long resolvedUserId = requireVerifiedTelegramUserId(initDataHeader, telegramInitData, initData);
            PrescriptionStatusResponseDTO response = prescriptionReviewService.uploadPrescriptionFiles(
                    reservationId,
                    reservationGroupId,
                    resolvedUserId,
                    pharmacyId,
                    medicineId,
                    note,
                    files);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error uploading prescription files: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }

    @GetMapping("/reservations/prescription-status")
    public ResponseEntity<?> getPrescriptionStatus(@RequestParam(required = false) Long reservationId,
                                                   @RequestParam(required = false) String reservationGroupId,
                                                   @RequestParam(required = false) Long telegramUserId,
                                                   @RequestHeader(value = MiniAppActorResolver.INIT_DATA_HEADER, required = false) String initDataHeader) {
        try {
            Long resolvedUserId = requireVerifiedTelegramUserId(initDataHeader);
            log.info("[API] /reservations/prescription-status called, reservationId={}, groupId={}, telegramUserId={}",
                    reservationId, reservationGroupId, resolvedUserId);
            PrescriptionStatusResponseDTO response = prescriptionReviewService.getPrescriptionStatus(
                    reservationId,
                    reservationGroupId,
                    resolvedUserId);
            log.info("[API] /reservations/prescription-status result: reservationId={}, groupId={}, reservationStatus={}, reviewStatus={}, canShowQr={}, userFacingStage={}, pharmacyName={}, medicineName={}, qty={}, expiresAt={}",
                    response.getReservationId(), response.getReservationGroupId(),
                    response.getReservationStatus(), response.getReviewStatus(),
                    response.isCanShowQr(), response.getUserFacingStage(),
                    response.getPharmacyName(), response.getMedicineName(),
                    response.getQuantity(), response.getExpiresAt());
            return ResponseEntity.ok(response);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("[API] /reservations/prescription-status RuntimeException: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error fetching prescription status: " + e.getMessage());
        } catch (Exception e) {
            log.error("[API] /reservations/prescription-status Unexpected error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }
    
    @PostMapping("/pharmacies/{pharmacyId}/report")
    public ResponseEntity<?> reportPharmacy(
            @PathVariable Long pharmacyId,
            @RequestBody(required = false) MiniAppPharmacyReportRequestDTO request,
            @RequestParam(required = false) String telegramInitData,
            @RequestParam(required = false) String initData,
            @RequestHeader(value = MiniAppActorResolver.INIT_DATA_HEADER, required = false) String initDataHeader) {
        try {
            Long userId = requireVerifiedTelegramUserId(initDataHeader, telegramInitData, initData);
            MiniAppPharmacyReportRequestDTO body = request == null ? new MiniAppPharmacyReportRequestDTO() : request;
            String issueType = body.getIssueType() == null || body.getIssueType().isBlank() ? "other" : body.getIssueType().trim();
            String pharmacyName = body.getPharmacyName() == null ? "" : body.getPharmacyName().trim();
            String note = body.getNote() == null ? "" : body.getNote().trim();
            String message = "Pharmacy report"
                    + (pharmacyName.isBlank() ? "" : ": " + pharmacyName)
                    + " (#" + pharmacyId + ")\nReason: " + issueType
                    + (note.isBlank() ? "" : "\n" + note);
            adminInboxService.createIssueItem(userId, pharmacyId, body.getMedicineName(), issueType, message);
            return ResponseEntity.ok(MiniAppOperationResponseDTO.builder()
                    .success(true)
                    .message("Report received")
                    .build());
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(MiniAppOperationResponseDTO.builder().success(false).message(e.getMessage()).build());
        } catch (Exception e) {
            log.error("[API] /pharmacies/{}/report error: {}", pharmacyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(MiniAppOperationResponseDTO.builder().success(false).message("Unexpected error").build());
        }
    }

    /**
     * GET /api/miniapp/pharmacies/{pharmacyId}/photos
     * 
     * Fetch all photos for a specific pharmacy in Mini App format.
     * 
     * @param pharmacyId the pharmacy ID
     * @return 200 OK with MiniAppPharmacyPhotosDTO
     *         - photos list is empty if pharmacy has no photos
     *         404 NOT_FOUND if pharmacy does not exist
     *         500 INTERNAL_SERVER_ERROR for unexpected errors
     */
    @GetMapping("/pharmacies/{pharmacyId}/photos")
    public ResponseEntity<?> getPharmacyPhotos(@PathVariable Long pharmacyId) {
        try {
            MiniAppPharmacyPhotosDTO result = miniAppService.getPharmacyPhotos(pharmacyId);
            return ResponseEntity.ok(result);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            // Assume RuntimeException with "not found" message means 404
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            }
            // Other runtime exceptions → 500
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error fetching pharmacy photos: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Unexpected error: " + e.getMessage());
        }
    }
    
    /**
     * GET /api/miniapp/pharmacies/{pharmacyId}/photo/{photoId}/image
     * 
     * Download a specific pharmacy photo as binary image data.
     * Returns the image file with appropriate content-type (image/jpeg, image/png, etc).
     * 
     * @param pharmacyId the pharmacy ID
     * @param photoId the photo ID
     * @return 200 OK with image binary data (content-type: application/octet-stream)
     *         404 NOT_FOUND if pharmacy or photo does not exist
     *         403 FORBIDDEN if photo does not belong to pharmacy
     *         500 INTERNAL_SERVER_ERROR for Telegram download or other errors
     */
    @GetMapping("/pharmacies/{pharmacyId}/photo/{photoId}/image")
    public ResponseEntity<?> downloadPharmacyPhoto(@PathVariable Long pharmacyId, @PathVariable Long photoId) {
        try {
            byte[] imageData = miniAppService.downloadPharmacyPhoto(pharmacyId, photoId);
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(imageData);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            String message = e.getMessage() != null ? e.getMessage() : "";
            // Pharmacy or photo not found
            if (message.contains("not found")) {
                return ResponseEntity.notFound().build();
            }
            // Photo doesn't belong to pharmacy
            if (message.contains("does not belong")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Photo does not belong to this pharmacy");
            }
            // Other runtime exceptions → 500
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error downloading pharmacy photo: " + message);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Unexpected error: " + e.getMessage());
        }
    }

    @GetMapping("/medicines/{medicineId}/photos")
    public ResponseEntity<?> getMedicinePhotos(@PathVariable Long medicineId) {
        try {
            MiniAppMedicinePhotosDTO result = miniAppService.getMedicinePhotos(medicineId);
            return ResponseEntity.ok(result);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching medicine photos: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }

    @GetMapping("/medicines/{medicineId}/photo/{photoId}/image")
    public ResponseEntity<?> downloadMedicinePhoto(@PathVariable Long medicineId, @PathVariable Long photoId) {
        try {
            byte[] imageData = miniAppService.downloadMedicinePhoto(medicineId, photoId);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(imageData);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            String message = e.getMessage() != null ? e.getMessage() : "";
            if (message.contains("not found")) {
                return ResponseEntity.notFound().build();
            }
            if (message.contains("does not belong")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("Medicine photo does not belong to this medicine");
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error downloading medicine photo: " + message);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }

    @GetMapping("/medicines/resolve")
    public ResponseEntity<?> resolveMedicineId(@RequestParam Long pharmacyId, @RequestParam String medicineName) {
        try {
            Long medicineId = miniAppService.resolveMedicineId(pharmacyId, medicineName);
            return ResponseEntity.ok(java.util.Map.of("medicineId", medicineId));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }

    private void applyInitData(MiniAppReservationCreateRequestDTO request, String initDataHeader) {
        if (request == null) {
            return;
        }
        if (isBlank(request.getTelegramInitData()) && isBlank(request.getInitData()) && !isBlank(initDataHeader)) {
            request.setTelegramInitData(initDataHeader);
        }
    }

    private void applyInitData(MiniAppReservationConfirmRequestDTO request, String initDataHeader) {
        if (request == null) {
            return;
        }
        if (isBlank(request.getTelegramInitData()) && isBlank(request.getInitData()) && !isBlank(initDataHeader)) {
            request.setTelegramInitData(initDataHeader);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private List<Long> parseMedicineIds(String medicineIds) {
        if (medicineIds == null || medicineIds.isBlank()) {
            return List.of();
        }

        return java.util.Arrays.stream(medicineIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Long::valueOf)
                .toList();
    }

    private List<String> parseMedicineNames(String medicines) {
        if (medicines == null || medicines.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(medicines.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private String firstNonBlank(String... values) {
        return Stream.of(values)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private Long parseOptionalLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
