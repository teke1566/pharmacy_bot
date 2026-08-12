package com.tenahub.bot.controller;

import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.entity.*;
import com.tenahub.bot.repository.MedicineReservationRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.AdminAuditTrailService;
import com.tenahub.bot.service.AdminInboxService;
import com.tenahub.bot.service.AdminService;
import com.tenahub.bot.service.LicenseComplianceService;
import com.tenahub.bot.service.MiniAppActorResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/admin", "/proxyapi/api/admin"})
public class AdminMiniAppController {

    private final AdminService adminService;
    private final AdminInboxService adminInboxService;
    private final AdminAuditTrailService adminAuditTrailService;
    private final LicenseComplianceService licenseComplianceService;
    private final PharmacyRepository pharmacyRepository;
    private final MedicineReservationRepository reservationRepository;
    private final MiniAppActorResolver miniAppActorResolver;

    @Value("${tenahub.admin.chat-id:0}")
    private long adminChatId;

    // ==================== Access Check ====================

    @GetMapping("/check-access")
    public ResponseEntity<?> checkAccess(
            @RequestHeader(value = "X-Admin-Telegram-Id", required = false) Long headerAdminId,
            @RequestParam(value = "adminTelegramId", required = false) Long paramAdminId) {
        Long resolved = miniAppActorResolver.resolveAdminIdForAccessCheck(headerAdminId, paramAdminId);
        log.info("[AdminMiniApp] check-access called — resolved telegramId={}, configured adminChatId={}", resolved, adminChatId);
        boolean isAdmin = resolved != null && resolved > 0 && resolved.longValue() == adminChatId;
        log.info("[AdminMiniApp] check-access decision — isAdmin={}", isAdmin);
        return ResponseEntity.ok(Map.of(
                "isAdmin", isAdmin,
                "telegramId", resolved != null ? resolved : 0
        ));
    }

    // ==================== Dashboard ====================

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard(
            @RequestHeader(value = "X-Admin-Telegram-Id", required = false) Long headerAdminId,
            @RequestParam(value = "adminTelegramId", required = false) Long paramAdminId) {
        try {
            verifyAdmin(headerAdminId, paramAdminId);
            String systemSummary = adminService.viewDetailedSystemSummary();
            String reservationOversight = adminService.viewDetailedReservationOversight();
            return ResponseEntity.ok(Map.of(
                    "systemSummary", systemSummary,
                    "reservationOversight", reservationOversight
            ));
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    // ==================== Pharmacy Management ====================

    /**
     * GET /pharmacies/all — returns a flat JSON array of pharmacy maps
     * with a derived "status" field the frontend expects.
     */
    @GetMapping("/pharmacies/all")
    public ResponseEntity<?> getAllPharmacies(
            @RequestHeader(value = "X-Admin-Telegram-Id", required = false) Long headerAdminId,
            @RequestParam(value = "adminTelegramId", required = false) Long paramAdminId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "200") int size) {
        try {
            verifyAdmin(headerAdminId, paramAdminId);
            Page<Pharmacy> result = adminService.getPharmacyManagementPage(page, size);
            List<Map<String, Object>> list = result.getContent().stream()
                    .map(this::toPharmacyMap)
                    .collect(Collectors.toList());
            if (status != null && !status.isBlank()) {
                list = list.stream()
                        .filter(m -> status.equalsIgnoreCase((String) m.get("status")))
                        .collect(Collectors.toList());
            }
            return ResponseEntity.ok(list);
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    /**
     * GET /pharmacies/search?name=X | ?phone=X | ?telegramId=X
     */
    @GetMapping("/pharmacies/search")
    public ResponseEntity<?> searchPharmacies(
            @RequestHeader(value = "X-Admin-Telegram-Id", required = false) Long headerAdminId,
            @RequestParam(value = "adminTelegramId", required = false) Long paramAdminId,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "telegramId", required = false) String telegramId,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "200") int size) {
        try {
            verifyAdmin(headerAdminId, paramAdminId);
            Page<Pharmacy> result;
            if (name != null && !name.isBlank()) {
                result = adminService.searchPharmaciesByName(name, page, size);
            } else if (phone != null && !phone.isBlank()) {
                result = adminService.searchPharmaciesByPhone(phone, page, size);
            } else if (telegramId != null && !telegramId.isBlank()) {
                result = adminService.searchPharmaciesByTelegramId(telegramId, page, size);
            } else if (query != null && !query.isBlank()) {
                result = adminService.searchPharmaciesByName(query, page, size);
            } else {
                result = adminService.getPharmacyManagementPage(page, size);
            }
            List<Map<String, Object>> list = result.getContent().stream()
                    .map(this::toPharmacyMap)
                    .collect(Collectors.toList());
            if (status != null && !status.isBlank()) {
                list = list.stream()
                        .filter(m -> status.equalsIgnoreCase((String) m.get("status")))
                        .collect(Collectors.toList());
            }
            return ResponseEntity.ok(list);
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    @GetMapping("/pharmacies")
    public ResponseEntity<?> getPharmacies(
            @RequestHeader(value = "X-Admin-Telegram-Id", required = false) Long headerAdminId,
            @RequestParam(value = "adminTelegramId", required = false) Long paramAdminId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "searchBy", required = false) String searchBy) {
        try {
            verifyAdmin(headerAdminId, paramAdminId);
            Page<?> result;
            if (search != null && !search.isBlank()) {
                result = switch (searchBy != null ? searchBy : "name") {
                    case "phone" -> adminService.searchPharmaciesByPhone(search, page, size);
                    case "telegramId" -> adminService.searchPharmaciesByTelegramId(search, page, size);
                    default -> adminService.searchPharmaciesByName(search, page, size);
                };
            } else {
                result = adminService.getPharmacyManagementPage(page, size);
            }
            return ResponseEntity.ok(result);
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    @GetMapping("/pharmacies/{pharmacyId}")
    public ResponseEntity<?> getPharmacyDetail(
            @PathVariable Long pharmacyId,
            @RequestHeader(value = "X-Admin-Telegram-Id", required = false) Long headerAdminId,
            @RequestParam(value = "adminTelegramId", required = false) Long paramAdminId) {
        try {
            verifyAdmin(headerAdminId, paramAdminId);
            return ResponseEntity.ok(adminService.getPharmacy(pharmacyId));
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    @PostMapping("/pharmacies/{pharmacyId}/approve")
    public ResponseEntity<?> approvePharmacy(
            @PathVariable Long pharmacyId,
            @RequestHeader(value = "X-Admin-Telegram-Id", required = false) Long headerAdminId,
            @RequestParam(value = "adminTelegramId", required = false) Long paramAdminId) {
        try {
            Long admin = verifyAdmin(headerAdminId, paramAdminId);
            adminService.approvePharmacy(pharmacyId);
            adminAuditTrailService.record("PHARMACY_APPROVED", "PHARMACY", pharmacyId, admin, "Approved via mini app");
            return ok("Pharmacy approved");
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    @PostMapping("/pharmacies/{pharmacyId}/suspend")
    public ResponseEntity<?> suspendPharmacy(
            @PathVariable Long pharmacyId,
            @RequestHeader(value = "X-Admin-Telegram-Id", required = false) Long headerAdminId,
            @RequestParam(value = "adminTelegramId", required = false) Long paramAdminId) {
        try {
            Long admin = verifyAdmin(headerAdminId, paramAdminId);
            adminService.suspendPharmacy(pharmacyId);
            adminAuditTrailService.record("PHARMACY_SUSPENDED", "PHARMACY", pharmacyId, admin, "Suspended via mini app");
            return ok("Pharmacy suspended");
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    @PostMapping("/pharmacies/{pharmacyId}/reactivate")
    public ResponseEntity<?> reactivatePharmacy(
            @PathVariable Long pharmacyId,
            @RequestHeader(value = "X-Admin-Telegram-Id", required = false) Long headerAdminId,
            @RequestParam(value = "adminTelegramId", required = false) Long paramAdminId) {
        try {
            Long admin = verifyAdmin(headerAdminId, paramAdminId);
            adminService.reactivatePharmacy(pharmacyId);
            adminAuditTrailService.record("PHARMACY_REACTIVATED", "PHARMACY", pharmacyId, admin, "Reactivated via mini app");
            return ok("Pharmacy reactivated");
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    @GetMapping("/registrations")
    public ResponseEntity<?> getPendingRegistrations(
            @RequestHeader(value = "X-Admin-Telegram-Id", required = false) Long headerAdminId,
            @RequestParam(value = "adminTelegramId", required = false) Long paramAdminId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        try {
            verifyAdmin(headerAdminId, paramAdminId);
            return ResponseEntity.ok(adminService.getPendingRegistrationsPage(page, size));
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    // ==================== Reservation Oversight ====================

    @GetMapping("/reservations")
    public ResponseEntity<?> getReservations(
            @RequestHeader(value = "X-Admin-Telegram-Id", required = false) Long headerAdminId,
            @RequestParam(value = "adminTelegramId", required = false) Long paramAdminId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        try {
            verifyAdmin(headerAdminId, paramAdminId);
            Page<MedicineReservation> reservations;
            if (status != null && !status.isBlank()) {
                MedicineReservationStatus statusEnum = MedicineReservationStatus.valueOf(status.toUpperCase());
                reservations = adminService.getReservationsByStatusPage(statusEnum, page, size);
            } else {
                reservations = reservationRepository.findAll(
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
            }
            List<Map<String, Object>> list = reservations.getContent().stream()
                    .map(this::toReservationMap)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(list);
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    @GetMapping("/reservations/{id}")
    public ResponseEntity<?> getReservationDetail(
            @PathVariable Long id,
            @RequestHeader(value = "X-Admin-Telegram-Id", required = false) Long headerAdminId,
            @RequestParam(value = "adminTelegramId", required = false) Long paramAdminId) {
        try {
            verifyAdmin(headerAdminId, paramAdminId);
            return ResponseEntity.ok(adminService.getReservation(id));
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    @PostMapping("/reservations/{id}/cancel")
    public ResponseEntity<?> cancelReservation(
            @PathVariable Long id,
            @RequestHeader(value = "X-Admin-Telegram-Id", required = false) Long headerAdminId,
            @RequestParam(value = "adminTelegramId", required = false) Long paramAdminId) {
        try {
            Long admin = verifyAdmin(headerAdminId, paramAdminId);
            adminService.adminCancelReservation(id);
            adminAuditTrailService.record("RESERVATION_CANCELLED", "RESERVATION", id, admin, "Cancelled via admin mini app");
            return ok("Reservation cancelled");
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    @PostMapping("/reservations/{id}/fulfill")
    public ResponseEntity<?> fulfillReservation(
            @PathVariable Long id,
            @RequestHeader(value = "X-Admin-Telegram-Id", required = false) Long headerAdminId,
            @RequestParam(value = "adminTelegramId", required = false) Long paramAdminId) {
        try {
            Long admin = verifyAdmin(headerAdminId, paramAdminId);
            adminService.adminFulfillReservation(id);
            adminAuditTrailService.record("RESERVATION_FULFILLED", "RESERVATION", id, admin, "Fulfilled via admin mini app");
            return ok("Reservation fulfilled");
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    // ==================== Audit Trail ====================

    @GetMapping("/audit")
    public ResponseEntity<?> getAuditTrail(
            @RequestHeader(value = "X-Admin-Telegram-Id", required = false) Long headerAdminId,
            @RequestParam(value = "adminTelegramId", required = false) Long paramAdminId,
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "actionType", required = false) String actionType) {
        try {
            verifyAdmin(headerAdminId, paramAdminId);
            List<AdminAuditTrail> trails;
            if (actionType != null && !actionType.isBlank()) {
                trails = adminAuditTrailService.listRecentByActionType(actionType);
            } else if (targetType != null && !targetType.isBlank()) {
                trails = adminAuditTrailService.listRecentByTargetType(targetType);
            } else {
                trails = adminAuditTrailService.listRecent();
            }
            return ResponseEntity.ok(trails);
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    // ==================== License Compliance ====================

    @GetMapping("/compliance")
    public ResponseEntity<?> getComplianceSummary(
            @RequestHeader(value = "X-Admin-Telegram-Id", required = false) Long headerAdminId,
            @RequestParam(value = "adminTelegramId", required = false) Long paramAdminId) {
        try {
            verifyAdmin(headerAdminId, paramAdminId);
            return ResponseEntity.ok(licenseComplianceService.buildSummary());
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    @GetMapping("/compliance/{category}")
    public ResponseEntity<?> getComplianceByCategory(
            @PathVariable String category,
            @RequestHeader(value = "X-Admin-Telegram-Id", required = false) Long headerAdminId,
            @RequestParam(value = "adminTelegramId", required = false) Long paramAdminId) {
        try {
            verifyAdmin(headerAdminId, paramAdminId);
            return ResponseEntity.ok(licenseComplianceService.listByCategory(category));
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    @GetMapping("/compliance/pharmacy/{pharmacyId}")
    public ResponseEntity<?> getComplianceDetail(
            @PathVariable Long pharmacyId,
            @RequestHeader(value = "X-Admin-Telegram-Id", required = false) Long headerAdminId,
            @RequestParam(value = "adminTelegramId", required = false) Long paramAdminId) {
        try {
            verifyAdmin(headerAdminId, paramAdminId);
            return ResponseEntity.ok(licenseComplianceService.getDetail(pharmacyId));
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    @PostMapping("/compliance/pharmacy/{pharmacyId}/suspend")
    public ResponseEntity<?> suspendForCompliance(
            @PathVariable Long pharmacyId,
            @RequestHeader(value = "X-Admin-Telegram-Id", required = false) Long headerAdminId,
            @RequestParam(value = "adminTelegramId", required = false) Long paramAdminId) {
        try {
            Long admin = verifyAdmin(headerAdminId, paramAdminId);
            LicenseComplianceService.ComplianceDetail detail = licenseComplianceService.suspendForCompliance(pharmacyId, admin);
            return ResponseEntity.ok(detail);
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    // ==================== Feedback & Issues ====================

    @GetMapping("/feedback")
    public ResponseEntity<?> getFeedbackSummary(
            @RequestHeader(value = "X-Admin-Telegram-Id", required = false) Long headerAdminId,
            @RequestParam(value = "adminTelegramId", required = false) Long paramAdminId,
            @RequestParam(value = "status", required = false) String status) {
        try {
            verifyAdmin(headerAdminId, paramAdminId);
            // If status is provided, return items list (frontend expects array of items)
            if (status != null && !status.isBlank()) {
                AdminInboxItemStatus s = AdminInboxItemStatus.valueOf(status.toUpperCase());
                return ResponseEntity.ok(adminInboxService.listByStatusAndType(s, null));
            }
            // No status → return items list (all open items) for frontend compatibility
            return ResponseEntity.ok(adminInboxService.listOpen(null));
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    @GetMapping("/feedback/counts")
    public ResponseEntity<?> getFeedbackCounts(
            @RequestHeader(value = "X-Admin-Telegram-Id", required = false) Long headerAdminId,
            @RequestParam(value = "adminTelegramId", required = false) Long paramAdminId) {
        try {
            verifyAdmin(headerAdminId, paramAdminId);
            AdminInboxService.InboxCounts counts = adminInboxService.getCounts();
            return ResponseEntity.ok(Map.of(
                    "newCount", counts.newCount(),
                    "inReviewCount", counts.inReviewCount(),
                    "resolvedCount", counts.resolvedCount()
            ));
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    @GetMapping("/feedback/items")
    public ResponseEntity<?> getFeedbackItems(
            @RequestHeader(value = "X-Admin-Telegram-Id", required = false) Long headerAdminId,
            @RequestParam(value = "adminTelegramId", required = false) Long paramAdminId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "type", required = false) String type) {
        try {
            verifyAdmin(headerAdminId, paramAdminId);
            if (status != null && type != null) {
                AdminInboxItemStatus s = AdminInboxItemStatus.valueOf(status.toUpperCase());
                AdminInboxItemType t = AdminInboxItemType.valueOf(type.toUpperCase());
                return ResponseEntity.ok(adminInboxService.listByStatusAndType(s, t));
            }
            if (type != null) {
                AdminInboxItemType t = AdminInboxItemType.valueOf(type.toUpperCase());
                return ResponseEntity.ok(adminInboxService.listOpen(t));
            }
            return ResponseEntity.ok(adminInboxService.listOpen(null));
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    @GetMapping("/feedback/items/{id}")
    public ResponseEntity<?> getFeedbackItem(
            @PathVariable Long id,
            @RequestHeader(value = "X-Admin-Telegram-Id", required = false) Long headerAdminId,
            @RequestParam(value = "adminTelegramId", required = false) Long paramAdminId) {
        try {
            verifyAdmin(headerAdminId, paramAdminId);
            return ResponseEntity.ok(adminInboxService.getById(id));
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    @PostMapping("/feedback/items/{id}/in-review")
    public ResponseEntity<?> markInReview(
            @PathVariable Long id,
            @RequestHeader(value = "X-Admin-Telegram-Id", required = false) Long headerAdminId,
            @RequestParam(value = "adminTelegramId", required = false) Long paramAdminId) {
        try {
            Long admin = verifyAdmin(headerAdminId, paramAdminId);
            AdminInboxItem item = adminInboxService.markInReview(id);
            adminAuditTrailService.record("ISSUE_MARKED_IN_REVIEW", "ADMIN_INBOX", id, admin, "Marked in review via mini app");
            return ResponseEntity.ok(item);
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    @PostMapping("/feedback/items/{id}/resolve")
    public ResponseEntity<?> markResolved(
            @PathVariable Long id,
            @RequestHeader(value = "X-Admin-Telegram-Id", required = false) Long headerAdminId,
            @RequestParam(value = "adminTelegramId", required = false) Long paramAdminId) {
        try {
            Long admin = verifyAdmin(headerAdminId, paramAdminId);
            AdminInboxItem item = adminInboxService.markResolved(id);
            adminAuditTrailService.record("ISSUE_MARKED_RESOLVED", "ADMIN_INBOX", id, admin, "Resolved via mini app");
            return ResponseEntity.ok(item);
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    // ==================== System Summary ====================

    @GetMapping("/system")
    public ResponseEntity<?> getSystemSummary(
            @RequestHeader(value = "X-Admin-Telegram-Id", required = false) Long headerAdminId,
            @RequestParam(value = "adminTelegramId", required = false) Long paramAdminId) {
        try {
            verifyAdmin(headerAdminId, paramAdminId);
            return ResponseEntity.ok(Map.of(
                    "summary", adminService.viewDetailedSystemSummary(),
                    "pharmacyDetails", adminService.viewPharmacyDetails(),
                    "topMedicines", adminService.viewTopMedicinesDetails(),
                    "lowStock", adminService.viewLowStockDetails()
            ));
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    // ==================== Helpers ====================

    // ==================== DTO Helpers ====================

    private Map<String, Object> toPharmacyMap(Pharmacy p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("phone", p.getPhone());
        m.put("city", p.getCity());
        m.put("area", p.getArea());
        m.put("telegramId", p.getTelegramId());
        m.put("latitude", p.getLatitude());
        m.put("longitude", p.getLongitude());
        m.put("landmark", p.getLandmark());
        m.put("formattedAddress", p.getFormattedAddress());
        m.put("rating", p.getRating());
        m.put("approved", p.isApproved());
        m.put("licenseSuspended", p.isLicenseSuspended());
        m.put("temporarilyClosed", p.isTemporarilyClosed());
        // Derive a human-readable status the frontend can consume
        String status;
        if (p.isLicenseSuspended()) status = "suspended";
        else if (p.isTemporarilyClosed()) status = "closed";
        else if (!p.isApproved()) status = "pending";
        else status = "active";
        m.put("status", status);
        return m;
    }

    private Map<String, Object> toReservationMap(MedicineReservation r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("medicineName", r.getMedicineName());
        m.put("status", r.getStatus() != null ? r.getStatus().name() : "UNKNOWN");
        m.put("userName", r.getCustomerName());
        m.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        // Resolve pharmacy name
        String pharmacyName = null;
        if (r.getPharmacyId() != null) {
            pharmacyName = pharmacyRepository.findById(r.getPharmacyId())
                    .map(Pharmacy::getName).orElse("Pharmacy #" + r.getPharmacyId());
        }
        m.put("pharmacyName", pharmacyName);
        m.put("pharmacyId", r.getPharmacyId());
        m.put("requestedQuantity", r.getRequestedQuantity());
        return m;
    }

    private Long verifyAdmin(Long headerValue, Long paramValue) {
        Long resolved = miniAppActorResolver.requireAdminTelegramId(headerValue, paramValue);
        log.info("[AdminMiniApp] Admin access granted for telegramId={}", resolved);
        return resolved;
    }

    private ResponseEntity<?> forbidden(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(MiniAppOperationResponseDTO.builder().success(false).message(e.getMessage()).build());
    }

    private ResponseEntity<?> ok(String message) {
        return ResponseEntity.ok(MiniAppOperationResponseDTO.builder().success(true).message(message).build());
    }
}
