package com.tenahub.bot.controller;

import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.dto.PharmacyMiniAppReservationDTO;
import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.service.MiniAppActorResolver;
import com.tenahub.bot.service.MiniAppAuthException;
import com.tenahub.bot.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/pharmacy/reservations", "/proxyapi/api/pharmacy/reservations"})
public class PharmacyMiniAppReservationsController {

    private final ReservationService reservationService;
    private final MiniAppActorResolver miniAppActorResolver;

    // --- Reservation list endpoints ---

    @GetMapping
    public ResponseEntity<?> getAllReservations(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestParam(value = "status", required = false) String status) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            log.info("[PharmacyMiniApp] GET reservations, status={}, pharmacyTelegramId={}", status, pharmacyTelegramId);

            List<MedicineReservation> reservations;
            if ("pending".equalsIgnoreCase(status)) {
                reservations = reservationService.getPendingReservations(pharmacyTelegramId);
            } else if ("approved".equalsIgnoreCase(status) || "fulfillable".equalsIgnoreCase(status)) {
                reservations = reservationService.getApprovedReservations(pharmacyTelegramId);
            } else if ("prescription".equalsIgnoreCase(status)) {
                reservations = reservationService.getPrescriptionReservations(pharmacyTelegramId);
            } else {
                reservations = reservationService.getPharmacyReservations(pharmacyTelegramId);
            }

            List<PharmacyMiniAppReservationDTO> dtos = reservations.stream()
                    .map(this::toDTO)
                    .toList();
            return ResponseEntity.ok(dtos);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("[PharmacyMiniApp] GET reservations rejected: {}", e.getMessage());
            return forbidden(e);
        }
    }

    @GetMapping("/pending")
    public ResponseEntity<?> getPendingReservations(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            List<PharmacyMiniAppReservationDTO> dtos = reservationService.getPendingReservations(pharmacyTelegramId)
                    .stream().map(this::toDTO).toList();
            return ResponseEntity.ok(dtos);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return forbidden(e);
        }
    }

    @GetMapping("/approved")
    public ResponseEntity<?> getApprovedReservations(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            List<PharmacyMiniAppReservationDTO> dtos = reservationService.getApprovedReservations(pharmacyTelegramId)
                    .stream().map(this::toDTO).toList();
            return ResponseEntity.ok(dtos);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return forbidden(e);
        }
    }

    @GetMapping("/prescriptions")
    public ResponseEntity<?> getPrescriptionReservations(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            List<PharmacyMiniAppReservationDTO> dtos = reservationService.getPrescriptionReservations(pharmacyTelegramId)
                    .stream().map(this::toDTO).toList();
            return ResponseEntity.ok(dtos);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return forbidden(e);
        }
    }

    // --- Reservation actions ---

    @PostMapping("/{reservationId}/approve")
    public ResponseEntity<?> approveReservation(
            @PathVariable Long reservationId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            log.info("[PharmacyMiniApp] POST approve, reservationId={}, pharmacyTelegramId={}", reservationId, pharmacyTelegramId);
            reservationService.assertPharmacyOwnsReservation(reservationId, pharmacyTelegramId);
            MedicineReservation reservation = reservationService.approveReservationAndNotify(reservationId);
            return ResponseEntity.ok(toDTO(reservation));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PostMapping("/{reservationId}/reject")
    public ResponseEntity<?> rejectReservation(
            @PathVariable Long reservationId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            log.info("[PharmacyMiniApp] POST reject, reservationId={}, pharmacyTelegramId={}", reservationId, pharmacyTelegramId);
            reservationService.assertPharmacyOwnsReservation(reservationId, pharmacyTelegramId);
            String reason = body != null ? body.get("reason") : null;
            MedicineReservation reservation = reservationService.rejectReservation(reservationId, reason);
            return ResponseEntity.ok(toDTO(reservation));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    // NOTE: fulfill, prescriptions/review, and prescriptions/{id}/file
    // are handled by PharmacyReservationController at /api/pharmacy/reservations/*
    // to avoid ambiguous request mappings.

    @PostMapping("/{reservationId}/cancel")
    public ResponseEntity<?> cancelReservation(
            @PathVariable Long reservationId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            log.info("[PharmacyMiniApp] POST cancel, reservationId={}, pharmacyTelegramId={}", reservationId, pharmacyTelegramId);
            MedicineReservation reservation = reservationService.cancelReservationByPharmacy(reservationId, pharmacyTelegramId);
            return ResponseEntity.ok(toDTO(reservation));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    // --- Helpers ---

    private PharmacyMiniAppReservationDTO toDTO(MedicineReservation r) {
        return PharmacyMiniAppReservationDTO.builder()
                .reservationId(r.getId())
                .reservationGroupId(r.getReservationGroupId())
                .medicineName(r.getMedicineName())
                .quantity(r.getRequestedQuantity())
                .status(r.getStatus() == null ? null : r.getStatus().name())
                .customerName(r.getCustomerName())
                .customerPhone(r.getCustomerPhone())
                .prescriptionRequired(r.isPrescriptionRequired())
                .prescriptionReviewStatus(r.getPrescriptionReviewStatus() == null ? null : r.getPrescriptionReviewStatus().name())
                .prescriptionRejectionReason(r.getPrescriptionRejectionReason())
                .rejectionReason(r.getRejectionReason())
                .qrToken(r.getQrToken())
                .createdAt(r.getCreatedAt())
                .approvedAt(r.getApprovedAt())
                .expiresAt(r.getExpiresAt() != null ? r.getExpiresAt() : r.getPendingExpiresAt())
                .fulfilledAt(r.getFulfilledAt())
                .build();
    }

    private Long resolve(Long headerValue, Long paramValue) {
        return miniAppActorResolver.requirePharmacyTelegramId(headerValue, paramValue);
    }

    private ResponseEntity<?> forbidden(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(MiniAppOperationResponseDTO.builder().success(false).message(e.getMessage()).build());
    }

    private ResponseEntity<?> error(RuntimeException e) {
        HttpStatus status = e.getMessage() != null && e.getMessage().contains("not found")
                ? HttpStatus.NOT_FOUND
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(MiniAppOperationResponseDTO.builder().success(false).message(e.getMessage()).build());
    }
}
