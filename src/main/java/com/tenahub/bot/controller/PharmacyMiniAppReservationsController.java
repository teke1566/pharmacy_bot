package com.tenahub.bot.controller;

import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.dto.PharmacyMiniAppReservationDTO;
import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.repository.ReservationPrescriptionFileRepository;
import com.tenahub.bot.service.MiniAppActorResolver;
import com.tenahub.bot.service.MiniAppAuthException;
import com.tenahub.bot.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/pharmacy/reservations", "/proxyapi/api/pharmacy/reservations"})
public class PharmacyMiniAppReservationsController {

    private final ReservationService reservationService;
    private final MiniAppActorResolver miniAppActorResolver;
    private final ReservationPrescriptionFileRepository prescriptionFileRepository;
    private final com.tenahub.bot.service.ReservationStatusHistoryService reservationStatusHistoryService;
    private final com.tenahub.bot.service.PharmacyAuthorizationService pharmacyAuthorizationService;

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
            } else if ("terminal".equalsIgnoreCase(status) || "history".equalsIgnoreCase(status)) {
                reservations = reservationService.getTerminalReservations(pharmacyTelegramId);
            } else {
                reservations = reservationService.getPharmacyReservations(pharmacyTelegramId);
            }

            List<PharmacyMiniAppReservationDTO> dtos = toDTOs(reservations, pharmacyTelegramId);
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
            List<PharmacyMiniAppReservationDTO> dtos = toDTOs(
                    reservationService.getPendingReservations(pharmacyTelegramId), pharmacyTelegramId);
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
            List<PharmacyMiniAppReservationDTO> dtos = toDTOs(
                    reservationService.getApprovedReservations(pharmacyTelegramId), pharmacyTelegramId);
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
            List<PharmacyMiniAppReservationDTO> dtos = toDTOs(
                    reservationService.getPrescriptionReservations(pharmacyTelegramId), pharmacyTelegramId);
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
            var actor = miniAppActorResolver.requirePharmacyActor(headerPharmacyId, paramPharmacyId);
            pharmacyAuthorizationService.require(actor, com.tenahub.bot.entity.PharmacyPermission.RESERVATION_APPROVE);
            Long pharmacyTelegramId = actor.getPharmacyTelegramId();
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
            var actor = miniAppActorResolver.requirePharmacyActor(headerPharmacyId, paramPharmacyId);
            pharmacyAuthorizationService.require(actor, com.tenahub.bot.entity.PharmacyPermission.RESERVATION_CANCEL);
            Long pharmacyTelegramId = actor.getPharmacyTelegramId();
            log.info("[PharmacyMiniApp] POST reject, reservationId={}, pharmacyTelegramId={}", reservationId, pharmacyTelegramId);
            reservationService.assertPharmacyOwnsReservation(reservationId, pharmacyTelegramId);
            String reason = body != null ? body.get("reason") : null;
            MedicineReservation reservation = reservationService.rejectReservationAndNotify(reservationId, reason);
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

    @PostMapping("/{reservationId}/hide")
    public ResponseEntity<?> hideReservation(
            @PathVariable Long reservationId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            MedicineReservation reservation = reservationService.hideReservationFromPharmacy(reservationId, pharmacyTelegramId);
            return ResponseEntity.ok(toDTO(reservation));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PostMapping("/hide")
    public ResponseEntity<?> hideReservations(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody Map<String, Object> body) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            @SuppressWarnings("unchecked")
            List<Object> rawIds = body == null ? List.of() : (List<Object>) body.getOrDefault("reservationIds", List.of());
            List<Long> ids = rawIds.stream()
                    .filter(Objects::nonNull)
                    .map(value -> Long.valueOf(String.valueOf(value)))
                    .toList();
            int hidden = reservationService.hideReservationsFromPharmacy(ids, pharmacyTelegramId);
            return ResponseEntity.ok(MiniAppOperationResponseDTO.builder()
                    .success(true)
                    .message("Dismissed " + hidden + " reservation(s).")
                    .build());
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PostMapping("/hide-terminal")
    public ResponseEntity<?> hideTerminalReservations(
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            int hidden = reservationService.hideTerminalReservationsFromPharmacy(pharmacyTelegramId);
            return ResponseEntity.ok(MiniAppOperationResponseDTO.builder()
                    .success(true)
                    .message("Dismissed " + hidden + " idle reservation(s).")
                    .build());
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @GetMapping("/{reservationId}/history")
    public ResponseEntity<?> getReservationHistory(
            @PathVariable Long reservationId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            reservationService.assertPharmacyOwnsReservation(reservationId, pharmacyTelegramId);
            return ResponseEntity.ok(reservationStatusHistoryService.listForPharmacy(pharmacyTelegramId, reservationId));
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return forbidden(e);
        }
    }

    // --- Group actions ---

    @PostMapping("/groups/{groupId}/approve")
    public ResponseEntity<?> approveGroup(
            @PathVariable String groupId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            var actor = miniAppActorResolver.requirePharmacyActor(headerPharmacyId, paramPharmacyId);
            pharmacyAuthorizationService.require(actor, com.tenahub.bot.entity.PharmacyPermission.RESERVATION_APPROVE);
            Long pharmacyTelegramId = actor.getPharmacyTelegramId();
            log.info("[PharmacyMiniApp] POST group approve, groupId={}, pharmacyTelegramId={}", groupId, pharmacyTelegramId);
            List<PharmacyMiniAppReservationDTO> dtos = reservationService.approveGroupAndNotify(groupId, pharmacyTelegramId)
                    .stream().map(this::toDTO).toList();
            return ResponseEntity.ok(dtos);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PostMapping("/groups/{groupId}/reject")
    public ResponseEntity<?> rejectGroup(
            @PathVariable String groupId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            log.info("[PharmacyMiniApp] POST group reject, groupId={}, pharmacyTelegramId={}", groupId, pharmacyTelegramId);
            String reason = body != null ? body.get("reason") : null;
            List<PharmacyMiniAppReservationDTO> dtos = reservationService.rejectGroup(groupId, pharmacyTelegramId, reason)
                    .stream().map(this::toDTO).toList();
            return ResponseEntity.ok(dtos);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PostMapping("/groups/{groupId}/fulfill")
    public ResponseEntity<?> fulfillGroup(
            @PathVariable String groupId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            var actor = miniAppActorResolver.requirePharmacyActor(headerPharmacyId, paramPharmacyId);
            pharmacyAuthorizationService.require(actor, com.tenahub.bot.entity.PharmacyPermission.RESERVATION_FULFILL);
            Long pharmacyTelegramId = actor.getPharmacyTelegramId();
            log.info("[PharmacyMiniApp] POST group fulfill, groupId={}, pharmacyTelegramId={}", groupId, pharmacyTelegramId);
            List<PharmacyMiniAppReservationDTO> dtos = reservationService.fulfillGroupAndNotify(groupId, pharmacyTelegramId)
                    .stream().map(this::toDTO).toList();
            return ResponseEntity.ok(dtos);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PostMapping("/groups/{groupId}/cancel")
    public ResponseEntity<?> cancelGroup(
            @PathVariable String groupId,
            @RequestHeader(value = "X-Pharmacy-Telegram-Id", required = false) Long headerPharmacyId,
            @RequestParam(value = "pharmacyTelegramId", required = false) Long paramPharmacyId) {
        try {
            Long pharmacyTelegramId = resolve(headerPharmacyId, paramPharmacyId);
            log.info("[PharmacyMiniApp] POST group cancel, groupId={}, pharmacyTelegramId={}", groupId, pharmacyTelegramId);
            List<PharmacyMiniAppReservationDTO> dtos = reservationService.cancelGroupByPharmacy(groupId, pharmacyTelegramId)
                    .stream().map(this::toDTO).toList();
            return ResponseEntity.ok(dtos);
        } catch (MiniAppAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    // --- Helpers ---

    private List<PharmacyMiniAppReservationDTO> toDTOs(List<MedicineReservation> reservations, Long pharmacyTelegramId) {
        if (reservations == null || reservations.isEmpty()) {
            return List.of();
        }
        Map<Long, List<String>> imagesByReservationId = loadPrescriptionImageUrls(reservations, pharmacyTelegramId);
        return reservations.stream()
                .map(r -> toDTO(r, imagesByReservationId.getOrDefault(r.getId(), List.of())))
                .toList();
    }

    private Map<Long, List<String>> loadPrescriptionImageUrls(List<MedicineReservation> reservations, Long pharmacyTelegramId) {
        Map<Long, List<String>> result = new LinkedHashMap<>();
        List<Long> reservationIds = reservations.stream()
                .map(MedicineReservation::getId)
                .filter(Objects::nonNull)
                .toList();
        if (!reservationIds.isEmpty()) {
            // Chunk to avoid oversized IN clauses; use LOB-free projection so list stays fast/reliable.
            final int chunkSize = 200;
            for (int i = 0; i < reservationIds.size(); i += chunkSize) {
                List<Long> chunk = reservationIds.subList(i, Math.min(i + chunkSize, reservationIds.size()));
                for (ReservationPrescriptionFileRepository.PrescriptionFileRef file
                        : prescriptionFileRepository.findRefsByReservationIdIn(chunk)) {
                    if (file.getReservationId() == null || file.getId() == null) {
                        continue;
                    }
                    result.computeIfAbsent(file.getReservationId(), ignored -> new ArrayList<>())
                            .add(buildPrescriptionFileUrl(file.getId(), pharmacyTelegramId));
                }
            }
            log.info("[PharmacyMiniApp] Loaded prescription image refs for {} reservations ({} with files)",
                    reservationIds.size(), result.size());
        }
        Set<String> groupIds = new LinkedHashSet<>();
        for (MedicineReservation reservation : reservations) {
            if (reservation.getReservationGroupId() != null && !reservation.getReservationGroupId().isBlank()) {
                groupIds.add(reservation.getReservationGroupId());
            }
        }
        for (String groupId : groupIds) {
            List<ReservationPrescriptionFileRepository.PrescriptionFileRef> groupFiles =
                    prescriptionFileRepository.findRefsByReservationGroupId(groupId);
            if (groupFiles.isEmpty()) {
                continue;
            }
            for (MedicineReservation reservation : reservations) {
                if (!Objects.equals(groupId, reservation.getReservationGroupId()) || reservation.getId() == null) {
                    continue;
                }
                List<String> urls = result.computeIfAbsent(reservation.getId(), ignored -> new ArrayList<>());
                for (ReservationPrescriptionFileRepository.PrescriptionFileRef file : groupFiles) {
                    if (file.getId() == null) {
                        continue;
                    }
                    String url = buildPrescriptionFileUrl(file.getId(), pharmacyTelegramId);
                    if (!urls.contains(url)) {
                        urls.add(url);
                    }
                }
            }
        }
        return result;
    }

    private String buildPrescriptionFileUrl(Long prescriptionId, Long pharmacyTelegramId) {
        String encodedPharmacyId = URLEncoder.encode(String.valueOf(pharmacyTelegramId), StandardCharsets.UTF_8);
        return "/api/pharmacy/prescriptions/" + prescriptionId + "/file?pharmacyTelegramId=" + encodedPharmacyId;
    }

    private PharmacyMiniAppReservationDTO toDTO(MedicineReservation r, List<String> prescriptionImages) {
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
                .prescriptionClarificationMessage(r.getPrescriptionClarificationMessage())
                .rejectionReason(r.getRejectionReason())
                .prescriptionImages(prescriptionImages == null ? List.of() : prescriptionImages)
                .qrToken(r.getQrToken())
                .fulfilledByTelegramId(r.getFulfilledByTelegramId())
                .createdAt(r.getCreatedAt())
                .approvedAt(r.getApprovedAt())
                .expiresAt(r.getExpiresAt() != null ? r.getExpiresAt() : r.getPendingExpiresAt())
                .fulfilledAt(r.getFulfilledAt())
                .build();
    }

    private PharmacyMiniAppReservationDTO toDTO(MedicineReservation r) {
        return toDTO(r, List.of());
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
