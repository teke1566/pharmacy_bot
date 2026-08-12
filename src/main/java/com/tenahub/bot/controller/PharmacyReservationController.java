package com.tenahub.bot.controller;

import com.tenahub.bot.dto.MiniAppReservationConfirmItemResponseDTO;
import com.tenahub.bot.dto.PharmacyReservationFulfillResponseDTO;
import com.tenahub.bot.dto.PharmacyReservationErrorResponseDTO;
import com.tenahub.bot.dto.PharmacyReservationScanRequestDTO;
import com.tenahub.bot.dto.PharmacyReservationScanResponseDTO;
import com.tenahub.bot.dto.PrescriptionReviewRequestDTO;
import com.tenahub.bot.dto.PrescriptionStatusResponseDTO;
import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.MedicineReservationStatus;
import com.tenahub.bot.repository.MedicineReservationRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.MiniAppActorResolver;
import com.tenahub.bot.service.MiniAppAuthException;
import com.tenahub.bot.service.PrescriptionReviewService;
import com.tenahub.bot.service.ReservationService;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping({"/api/pharmacy", "/proxyapi/api/pharmacy"})
public class PharmacyReservationController {

    private final ReservationService reservationService;
    private final PharmacyRepository pharmacyRepository;
    private final MedicineReservationRepository medicineReservationRepository;
    private final PrescriptionReviewService prescriptionReviewService;
    private final MiniAppActorResolver miniAppActorResolver;

    public PharmacyReservationController(ReservationService reservationService,
                                         PharmacyRepository pharmacyRepository,
                                         MedicineReservationRepository medicineReservationRepository,
                                         PrescriptionReviewService prescriptionReviewService,
                                         MiniAppActorResolver miniAppActorResolver) {
        this.reservationService = reservationService;
        this.pharmacyRepository = pharmacyRepository;
        this.medicineReservationRepository = medicineReservationRepository;
        this.prescriptionReviewService = prescriptionReviewService;
        this.miniAppActorResolver = miniAppActorResolver;
    }

    @PostMapping("/reservations/scan")
        public ResponseEntity<?> scanReservation(@RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long pharmacyTelegramId,
                                             @RequestBody PharmacyReservationScanRequestDTO request) {
        try {
            Long resolvedPharmacyTelegramId = resolvePharmacyTelegramId(
                pharmacyTelegramId,
                request == null ? null : request.getPharmacyTelegramId()
            );

            MedicineReservation reservation = reservationService.scanReservationByQrToken(
                    request == null ? null : request.getQrToken(),
                resolvedPharmacyTelegramId
            );

                List<MedicineReservation> groupedReservations = resolveGroupedScanReservations(request == null ? null : request.getQrToken(), reservation);

            Pharmacy pharmacy = pharmacyRepository.findById(reservation.getPharmacyId()).orElse(null);

                if (groupedReservations.size() > 1 && reservation.getReservationGroupId() != null) {
                return ResponseEntity.ok(PharmacyReservationScanResponseDTO.builder()
                    .valid(true)
                    .grouped(true)
                    .reservationGroupId(reservation.getReservationGroupId())
                    .status(resolveGroupedStatus(groupedReservations))
                    .canFulfill(canFulfill(groupedReservations))
                    .prescriptionRequired(groupedReservations.stream().anyMatch(MedicineReservation::isPrescriptionRequired))
                    .prescriptionReviewStatus(resolveGroupedPrescriptionStatus(groupedReservations))
                    .prescriptionRejectionReason(resolveGroupedPrescriptionRejectionReason(groupedReservations))
                    .pharmacyId(reservation.getPharmacyId())
                    .pharmacyName(pharmacy == null ? null : pharmacy.getName())
                    .phone(reservation.getCustomerPhone())
                    .qrToken(reservation.getQrToken())
                    .expiresAt(resolveGroupedExpiresAt(groupedReservations))
                    .items(groupedReservations.stream().map(this::toScanItem).toList())
                    .build());
                }

            return ResponseEntity.ok(PharmacyReservationScanResponseDTO.builder()
                    .valid(true)
                    .grouped(false)
                    .reservationId(reservation.getId())
                    .reservationGroupId(reservation.getReservationGroupId())
                    .status(reservation.getStatus() == null ? null : reservation.getStatus().name())
                    .canFulfill(canFulfill(List.of(reservation)))
                    .prescriptionRequired(reservation.isPrescriptionRequired())
                    .prescriptionReviewStatus(reservation.getPrescriptionReviewStatus() == null ? null : reservation.getPrescriptionReviewStatus().name())
                    .prescriptionRejectionReason(reservation.getPrescriptionRejectionReason())
                    .pharmacyId(reservation.getPharmacyId())
                    .pharmacyName(pharmacy == null ? null : pharmacy.getName())
                    .medicineName(reservation.getMedicineName())
                    .quantity(reservation.getRequestedQuantity())
                    .phone(reservation.getCustomerPhone())
                    .qrToken(reservation.getQrToken())
                    .expiresAt(resolveExpiresAt(reservation))
                    .items(List.of(toScanItem(reservation)))
                    .build());
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error while scanning reservation.");
        }
    }

    @PostMapping("/reservations/{reservationId}/fulfill")
        public ResponseEntity<?> fulfillReservation(@RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long pharmacyTelegramId,
                            @RequestParam(value = "pharmacyTelegramId", required = false) Long pharmacyTelegramIdParam,
                            @PathVariable Long reservationId) {
        try {
            Long resolvedPharmacyTelegramId = resolvePharmacyTelegramId(pharmacyTelegramId, pharmacyTelegramIdParam);

            MedicineReservation reservation = reservationService.fulfillReservationAndNotify(
                    reservationId,
                resolvedPharmacyTelegramId
            );

            return ResponseEntity.ok(PharmacyReservationFulfillResponseDTO.builder()
                    .success(true)
                    .status(reservation.getStatus() == null ? null : reservation.getStatus().name())
                    .build());
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error while fulfilling reservation.");
        }
    }

    @PostMapping("/reservations/prescriptions/review")
    public ResponseEntity<?> reviewPrescription(@RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long pharmacyTelegramId,
                                                @RequestParam(value = "pharmacyTelegramId", required = false) Long pharmacyTelegramIdParam,
                                                @RequestParam(required = false) Long reservationId,
                                                @RequestParam(required = false) String reservationGroupId,
                                                @RequestBody PrescriptionReviewRequestDTO request) {
        try {
            Long resolvedPharmacyTelegramId = resolvePharmacyTelegramId(pharmacyTelegramId, pharmacyTelegramIdParam);
            PrescriptionStatusResponseDTO response = prescriptionReviewService.reviewPrescription(
                    reservationId,
                    reservationGroupId,
                    resolvedPharmacyTelegramId,
                    request);
            return ResponseEntity.ok(response);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error while reviewing prescription.");
        }
    }

    @GetMapping("/prescriptions/{prescriptionId}/file")
    public ResponseEntity<?> downloadPrescriptionFile(@RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long pharmacyTelegramId,
                                                      @RequestParam(value = "pharmacyTelegramId", required = false) Long pharmacyTelegramIdParam,
                                                      @PathVariable Long prescriptionId) {
        try {
            Long resolvedPharmacyTelegramId = resolvePharmacyTelegramId(pharmacyTelegramId, pharmacyTelegramIdParam);
            PrescriptionReviewService.PrescriptionFileContent file = prescriptionReviewService.downloadPrescriptionFile(
                    prescriptionId,
                    resolvedPharmacyTelegramId);

            MediaType mediaType;
            try {
                mediaType = file.contentType() == null || file.contentType().isBlank()
                        ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(file.contentType());
            } catch (InvalidMediaTypeException ignored) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(file.originalFilename()).build().toString())
                    .body(file.fileData());
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error while downloading prescription.");
        }
    }

    private ResponseEntity<PharmacyReservationErrorResponseDTO> errorResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(PharmacyReservationErrorResponseDTO.builder()
                        .success(false)
                        .message(message)
                        .build());
    }

    private Long resolvePharmacyTelegramId(Long headerValue, Long fallbackValue) {
        return miniAppActorResolver.requirePharmacyTelegramId(headerValue, fallbackValue);
    }

    private LocalDateTime resolveExpiresAt(MedicineReservation reservation) {
        if (reservation == null) {
            return null;
        }
        return reservation.getExpiresAt() != null
                ? reservation.getExpiresAt()
                : reservation.getPendingExpiresAt();
    }

    private List<MedicineReservation> resolveGroupedScanReservations(String qrToken, MedicineReservation reservation) {
        if (reservation == null) {
            return List.of();
        }

        List<MedicineReservation> byToken = medicineReservationRepository.findAllByQrToken(qrToken == null ? null : qrToken.trim());
        if (byToken != null && !byToken.isEmpty()) {
            return byToken;
        }

        if (reservation.getReservationGroupId() != null && !reservation.getReservationGroupId().isBlank()) {
            return medicineReservationRepository.findByReservationGroupId(reservation.getReservationGroupId());
        }

        return List.of(reservation);
    }

    private LocalDateTime resolveGroupedExpiresAt(List<MedicineReservation> reservations) {
        return reservations.stream()
                .map(this::resolveExpiresAt)
                .filter(value -> value != null)
                .min(LocalDateTime::compareTo)
                .orElse(null);
    }

    private String resolveGroupedStatus(List<MedicineReservation> reservations) {
        return reservations.stream()
                .map(MedicineReservation::getStatus)
                .filter(value -> value != null)
                .map(MedicineReservationStatus::name)
                .distinct()
                .reduce((left, right) -> "MIXED")
                .orElse(null);
    }

    private String resolveGroupedPrescriptionStatus(List<MedicineReservation> reservations) {
        boolean prescriptionRequired = reservations.stream().anyMatch(MedicineReservation::isPrescriptionRequired);
        if (!prescriptionRequired) {
            return com.tenahub.bot.entity.PrescriptionReviewStatus.NOT_REQUIRED.name();
        }
        if (reservations.stream().anyMatch(reservation ->
                reservation.getPrescriptionReviewStatus() == com.tenahub.bot.entity.PrescriptionReviewStatus.REJECTED)) {
            return com.tenahub.bot.entity.PrescriptionReviewStatus.REJECTED.name();
        }
        if (reservations.stream()
                .filter(MedicineReservation::isPrescriptionRequired)
                .allMatch(reservation -> reservation.getPrescriptionReviewStatus()
                        == com.tenahub.bot.entity.PrescriptionReviewStatus.APPROVED)) {
            return com.tenahub.bot.entity.PrescriptionReviewStatus.APPROVED.name();
        }
        if (reservations.stream()
            .filter(MedicineReservation::isPrescriptionRequired)
            .anyMatch(reservation -> reservation.getPrescriptionReviewStatus()
                == com.tenahub.bot.entity.PrescriptionReviewStatus.UPLOAD_REQUIRED)) {
            return com.tenahub.bot.entity.PrescriptionReviewStatus.UPLOAD_REQUIRED.name();
        }
        return com.tenahub.bot.entity.PrescriptionReviewStatus.PENDING_REVIEW.name();
    }

    private String resolveGroupedPrescriptionRejectionReason(List<MedicineReservation> reservations) {
        return reservations.stream()
                .map(MedicineReservation::getPrescriptionRejectionReason)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private boolean canFulfill(List<MedicineReservation> reservations) {
        return reservations.stream().allMatch(reservation ->
                (reservation.getStatus() == MedicineReservationStatus.APPROVED
                        || reservation.getStatus() == MedicineReservationStatus.READY_FOR_PICKUP)
                        && (!reservation.isPrescriptionRequired()
                        || reservation.getPrescriptionReviewStatus() == com.tenahub.bot.entity.PrescriptionReviewStatus.APPROVED));
    }

    private MiniAppReservationConfirmItemResponseDTO toScanItem(MedicineReservation reservation) {
        return MiniAppReservationConfirmItemResponseDTO.builder()
                .reservationId(reservation.getId())
                .medicineName(reservation.getMedicineName())
                .quantity(reservation.getRequestedQuantity())
                .status(reservation.getStatus() == null ? null : reservation.getStatus().name())
                .prescriptionRequired(reservation.isPrescriptionRequired())
                .prescriptionReviewStatus(reservation.getPrescriptionReviewStatus() == null ? null : reservation.getPrescriptionReviewStatus().name())
                .prescriptionRejectionReason(reservation.getPrescriptionRejectionReason())
                .expiresAt(resolveExpiresAt(reservation))
                .qrToken(reservation.getQrToken())
                .build();
    }
}