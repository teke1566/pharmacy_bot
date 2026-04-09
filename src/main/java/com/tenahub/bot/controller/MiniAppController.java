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
import com.tenahub.bot.dto.MiniAppAuthSendCodeRequestDTO;
import com.tenahub.bot.dto.MiniAppAuthVerifyCodeRequestDTO;
import com.tenahub.bot.dto.MiniAppAuthVerifyCodeResponseDTO;
import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.dto.MiniAppPharmacyDetailDTO;
import com.tenahub.bot.dto.MiniAppPharmacyPhotosDTO;
import com.tenahub.bot.dto.MiniAppReservationCardDTO;
import com.tenahub.bot.dto.MiniAppReservationConfirmRequestDTO;
import com.tenahub.bot.dto.MiniAppReservationConfirmResponseDTO;
import com.tenahub.bot.dto.MiniAppReservationPreloadResponseDTO;
import com.tenahub.bot.dto.MiniAppReservationCreateRequestDTO;
import com.tenahub.bot.dto.MiniAppReservationResponseDTO;
import com.tenahub.bot.dto.PrescriptionStatusResponseDTO;
import com.tenahub.bot.dto.PharmacyResponseDTO;
import com.tenahub.bot.service.MiniAppService;
import com.tenahub.bot.service.PrescriptionReviewService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
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

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }

    @PostMapping("/auth/send-code")
    public ResponseEntity<?> sendCode(@RequestBody MiniAppAuthSendCodeRequestDTO request) {
        try {
            MiniAppOperationResponseDTO response = miniAppService.sendVerificationCode(request);
            return ResponseEntity.ok(response);
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
            @RequestParam String medicine,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false, name = "sortBy") String sortBy,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false, name = "filterBy") String filterBy
    ) {
        try {
            List<PharmacyResponseDTO> result = miniAppService.search(
                    medicine,
                    latitude,
                    longitude,
                    userId,
                    firstNonBlank(sort, sortBy),
                    firstNonBlank(filter, filterBy));
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error searching pharmacies: " + e.getMessage());
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
    public ResponseEntity<?> createReservation(@RequestBody MiniAppReservationCreateRequestDTO request) {
        try {
            log.info("MiniApp reservation request body: {}", request);
            log.info("MiniApp reservation request received: pharmacyId={}, userId={}, medicineName={}",
                    request == null ? null : request.getPharmacyId(),
                    request == null ? null : request.getUserId(),
                    request == null ? null : request.getMedicineName());
            MiniAppReservationResponseDTO created = miniAppService.createReservation(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error creating reservation: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }

    @PostMapping("/reservations/confirm")
    public ResponseEntity<?> confirmReservation(@RequestBody MiniAppReservationConfirmRequestDTO request) {
        try {
            MiniAppReservationConfirmResponseDTO response = miniAppService.confirmReservation(request);
            return ResponseEntity.ok(response);
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
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error resolving reservation preload: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }

    @GetMapping("/reservations/active")
    public ResponseEntity<?> getActiveReservations(@RequestParam(required = false) Long telegramUserId) {
        try {
            List<MiniAppReservationCardDTO> response = miniAppService.getActiveReservations(telegramUserId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error fetching active reservations: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }

    @GetMapping("/reservations/history")
    public ResponseEntity<?> getReservationHistory(@RequestParam(required = false) Long telegramUserId) {
        try {
            List<MiniAppReservationCardDTO> response = miniAppService.getReservationHistory(telegramUserId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error fetching reservation history: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
        }
    }

    @PostMapping(value = "/reservations/prescriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadPrescriptionFiles(@RequestParam(required = false) Long reservationId,
                                                     @RequestParam(required = false) String reservationGroupId,
                                                     @RequestParam(required = false) Long telegramUserId,
                                                     @RequestParam(required = false) Long pharmacyId,
                                                     @RequestParam(required = false) Long medicineId,
                                                     @RequestParam("files") List<MultipartFile> files) {
        try {
            PrescriptionStatusResponseDTO response = prescriptionReviewService.uploadPrescriptionFiles(
                    reservationId,
                    reservationGroupId,
                    telegramUserId,
                    pharmacyId,
                    medicineId,
                    files);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
                                                   @RequestParam(required = false) Long telegramUserId) {
        try {
            PrescriptionStatusResponseDTO response = prescriptionReviewService.getPrescriptionStatus(
                    reservationId,
                    reservationGroupId,
                    telegramUserId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error fetching prescription status: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error: " + e.getMessage());
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

    private String firstNonBlank(String... values) {
        return Stream.of(values)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }
}
