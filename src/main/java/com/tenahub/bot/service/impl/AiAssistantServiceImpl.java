package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.AiChatDebugResponseDTO;
import com.tenahub.bot.dto.AiChatRequestDTO;
import com.tenahub.bot.dto.AiChatResponseDTO;
import com.tenahub.bot.dto.MedicineBatchDTO;
import com.tenahub.bot.dto.MedicineInfoDTO;
import com.tenahub.bot.dto.MiniAppReservationCardDTO;
import com.tenahub.bot.dto.MiniAppMedicineSummaryDTO;
import com.tenahub.bot.dto.PharmacyNotificationDTO;
import com.tenahub.bot.dto.PharmacyPerformanceReportDTO;
import com.tenahub.bot.dto.PharmacySalesSummaryDTO;
import com.tenahub.bot.dto.RestockSuggestionDTO;
import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.PrescriptionReviewStatus;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.AdminService;
import com.tenahub.bot.service.AiAssistantService;
import com.tenahub.bot.service.InventoryService;
import com.tenahub.bot.service.LicenseComplianceService;
import com.tenahub.bot.service.MedicineKnowledgeService;
import com.tenahub.bot.service.MiniAppService;
import com.tenahub.bot.service.PharmacyNotificationService;
import com.tenahub.bot.service.PharmacyPerformanceService;
import com.tenahub.bot.service.PharmacySalesService;
import com.tenahub.bot.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiAssistantServiceImpl implements AiAssistantService {

    private final MiniAppService miniAppService;
    private final ReservationService reservationService;
    private final InventoryService inventoryService;
    private final AdminService adminService;
    private final LicenseComplianceService licenseComplianceService;
    private final PharmacyRepository pharmacyRepository;
    private final MedicineKnowledgeService medicineKnowledgeService;
    private final PharmacyPerformanceService pharmacyPerformanceService;
    private final PharmacySalesService pharmacySalesService;
    private final PharmacyNotificationService pharmacyNotificationService;

    @Value("${tenahub.admin.chat-id:0}")
    private long adminChatId;

    @Override
    public AiChatResponseDTO chat(AiChatRequestDTO request,
                                  Long headerUserTelegramId,
                                  Long headerPharmacyTelegramId,
                                  Long headerAdminTelegramId) {
        return execute(request, headerUserTelegramId, headerPharmacyTelegramId, headerAdminTelegramId).response();
        }

        @Override
        public AiChatDebugResponseDTO chatDebug(AiChatRequestDTO request,
                            Long headerUserTelegramId,
                            Long headerPharmacyTelegramId,
                            Long headerAdminTelegramId) {
        ExecutionResult execution = execute(request, headerUserTelegramId, headerPharmacyTelegramId, headerAdminTelegramId);
        return AiChatDebugResponseDTO.builder()
            .response(execution.response())
            .matchedIntent(execution.intent().name())
            .resolvedRole(execution.role().role().name().toLowerCase(Locale.ROOT))
            .actorTelegramId(execution.role().actorTelegramId())
            .dataSources(execution.dataSources())
            .build();
        }

        private ExecutionResult execute(AiChatRequestDTO request,
                        Long headerUserTelegramId,
                        Long headerPharmacyTelegramId,
                        Long headerAdminTelegramId) {
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            throw new RuntimeException("message is required");
        }

        ResolvedRole resolvedRole = resolveRole(request, headerUserTelegramId, headerPharmacyTelegramId, headerAdminTelegramId);
        Intent intent = classifyIntent(request.getMessage());

        return switch (intent) {
            case USER_RESERVATION_STATUS -> new ExecutionResult(
                handleUserReservationStatus(intent, resolvedRole, request),
                intent, resolvedRole,
                List.of("MiniAppService.getActiveReservations"));
            case USER_RESERVATION_PENDING_REASON -> new ExecutionResult(
                handleUserPendingReason(intent, resolvedRole, request),
                intent, resolvedRole,
                List.of("MiniAppService.getActiveReservations"));
            case USER_RESERVATION_HISTORY -> new ExecutionResult(
                handleUserReservationHistory(intent, resolvedRole),
                intent, resolvedRole,
                List.of("MiniAppService.getReservationHistory"));
            case USER_CANCEL_HELP -> new ExecutionResult(
                handleUserCancelHelp(intent, resolvedRole, request),
                intent, resolvedRole,
                List.of("MiniAppService.getActiveReservations"));
            case USER_PRESCRIPTION_STATUS -> new ExecutionResult(
                handleUserPrescriptionStatus(intent, resolvedRole, request),
                intent, resolvedRole,
                List.of("MiniAppService.getActiveReservations"));
            case USER_PRESCRIPTION_UPLOAD_GUIDE -> new ExecutionResult(
                handleUserPrescriptionUploadGuide(intent, resolvedRole, request),
                intent, resolvedRole,
                List.of("MiniAppService.getActiveReservations"));
            case USER_QR_VISIBILITY -> new ExecutionResult(
                handleUserQrVisibility(intent, resolvedRole, request),
                intent, resolvedRole,
                List.of("MiniAppService.getActiveReservations"));
            case PHARMACY_PENDING_RESERVATIONS -> new ExecutionResult(
                handlePharmacyPendingReservations(intent, resolvedRole),
                intent, resolvedRole,
                List.of("ReservationService.getPendingReservations"));
            case PHARMACY_APPROVED_RESERVATIONS -> new ExecutionResult(
                handlePharmacyApprovedReservations(intent, resolvedRole),
                intent, resolvedRole,
                List.of("ReservationService.getApprovedReservations"));
            case PHARMACY_PENDING_PRESCRIPTION_REVIEWS_COUNT -> new ExecutionResult(
                handlePharmacyPendingReviews(intent, resolvedRole),
                intent, resolvedRole,
                List.of("ReservationService.getPrescriptionReservations"));
            case PHARMACY_LOW_STOCK -> new ExecutionResult(
                handleLowStock(intent, resolvedRole),
                intent, resolvedRole,
                resolvedRole.role() == Role.ADMIN
                    ? List.of("AdminService.viewLowStockDetails")
                    : List.of("InventoryService.buildLowStockAlert"));
            case PHARMACY_DEMAND_INSIGHTS -> new ExecutionResult(
                handlePharmacyDemandInsights(intent, resolvedRole),
                intent, resolvedRole,
                List.of("InventoryService.getDemandInsights"));
            case PHARMACY_RESTOCK_SUGGESTIONS -> new ExecutionResult(
                handlePharmacyRestockSuggestions(intent, resolvedRole),
                intent, resolvedRole,
                List.of("InventoryService.listRestockSuggestions"));
            case PHARMACY_PERFORMANCE -> new ExecutionResult(
                handlePharmacyPerformance(intent, resolvedRole),
                intent, resolvedRole,
                List.of("PharmacyPerformanceService.getPerformanceReport"));
            case PHARMACY_SALES -> new ExecutionResult(
                handlePharmacySales(intent, resolvedRole),
                intent, resolvedRole,
                List.of("PharmacySalesService.summary"));
            case PHARMACY_EXPIRY -> new ExecutionResult(
                handlePharmacyExpiry(intent, resolvedRole),
                intent, resolvedRole,
                List.of("InventoryService.listExpiryBatches"));
            case PHARMACY_NOTIFICATIONS -> new ExecutionResult(
                handlePharmacyNotifications(intent, resolvedRole),
                intent, resolvedRole,
                List.of("PharmacyNotificationService.unreadCount", "PharmacyNotificationService.list"));
            case ADMIN_SYSTEM_SUMMARY -> new ExecutionResult(
                handleAdminSystemSummary(intent, resolvedRole),
                intent, resolvedRole,
                List.of("AdminService.viewDetailedSystemSummary"));
            case ADMIN_RESERVATION_OVERSIGHT -> new ExecutionResult(
                handleAdminReservationOversight(intent, resolvedRole),
                intent, resolvedRole,
                List.of("AdminService.viewDetailedReservationOversight"));
            case ADMIN_TOP_MEDICINES -> new ExecutionResult(
                handleAdminTopMedicines(intent, resolvedRole),
                intent, resolvedRole,
                List.of("AdminService.viewTopMedicinesDetails"));
            case ADMIN_COMPLIANCE_SUMMARY -> new ExecutionResult(
                handleAdminComplianceSummary(intent, resolvedRole),
                intent, resolvedRole,
                List.of("LicenseComplianceService.buildSummary"));
            case MEDICINE_USAGE -> new ExecutionResult(
                handleMedicineUsage(intent, resolvedRole, request),
                intent, resolvedRole, List.of("MedicineKnowledgeService.lookup"));
            case MEDICINE_HOW_TO_TAKE -> new ExecutionResult(
                handleMedicineHowToTake(intent, resolvedRole, request),
                intent, resolvedRole, List.of("MedicineKnowledgeService.lookup"));
            case MEDICINE_SIDE_EFFECTS -> new ExecutionResult(
                handleMedicineSideEffects(intent, resolvedRole, request),
                intent, resolvedRole, List.of("MedicineKnowledgeService.lookup"));
            case MEDICINE_WARNINGS -> new ExecutionResult(
                handleMedicineWarnings(intent, resolvedRole, request),
                intent, resolvedRole, List.of("MedicineKnowledgeService.lookup"));
            case MEDICINE_STORAGE -> new ExecutionResult(
                handleMedicineStorage(intent, resolvedRole, request),
                intent, resolvedRole, List.of("MedicineKnowledgeService.lookup"));
            case MEDICINE_MISSED_DOSE -> new ExecutionResult(
                handleMedicineMissedDose(intent, resolvedRole, request),
                intent, resolvedRole, List.of("MedicineKnowledgeService.lookup"));
            case MEDICINE_SEARCH -> new ExecutionResult(
                handleMedicineSearch(intent, resolvedRole, request),
                intent, resolvedRole, List.of("MiniAppService.searchMedicineCatalog"));
            case UNKNOWN -> new ExecutionResult(
                fallback(intent, resolvedRole),
                intent, resolvedRole,
                List.of());
        };
    }

    private AiChatResponseDTO handleUserReservationHistory(Intent intent, ResolvedRole role) {
        requireRole(role, Role.USER, "This question is available for user role only.");
        Long userId = requireActorId(role, "telegramUserId is required for user role.");

        List<MiniAppReservationCardDTO> history = miniAppService.getReservationHistory(userId);

        if (history == null || history.isEmpty()) {
            return response(intent, role,
                    "You have no reservation history yet.",
                    List.of("Browse pharmacies", "Create your first reservation"));
        }

        long fulfilled = history.stream().filter(c -> "FULFILLED".equalsIgnoreCase(safe(c.getReservationStatus(), c.getStatus(), ""))).count();
        long rejected = history.stream().filter(c -> "REJECTED".equalsIgnoreCase(safe(c.getReservationStatus(), c.getStatus(), ""))).count();
        long cancelled = history.stream().filter(c -> "CANCELLED".equalsIgnoreCase(safe(c.getReservationStatus(), c.getStatus(), ""))).count();
        long expired = history.stream().filter(c -> "EXPIRED".equalsIgnoreCase(safe(c.getReservationStatus(), c.getStatus(), ""))).count();
        long pending = history.stream().filter(c -> "PENDING".equalsIgnoreCase(safe(c.getReservationStatus(), c.getStatus(), ""))).count();
        long approved = history.stream().filter(c -> "APPROVED".equalsIgnoreCase(safe(c.getReservationStatus(), c.getStatus(), ""))).count();

        String answer = "Total reservations: " + history.size() + "."
                + " Fulfilled: " + fulfilled + "."
                + " Pending: " + pending + "."
                + " Approved: " + approved + "."
                + " Rejected: " + rejected + "."
                + " Cancelled: " + cancelled + "."
                + " Expired: " + expired + ".";

        return response(intent, role, answer,
                List.of("Open full reservation history", "View active reservations", "Create new reservation"));
    }

    private AiChatResponseDTO handleUserCancelHelp(Intent intent, ResolvedRole role, AiChatRequestDTO request) {
        requireRole(role, Role.USER, "This question is available for user role only.");
        Long userId = requireActorId(role, "telegramUserId is required for user role.");

        List<MiniAppReservationCardDTO> cards = miniAppService.getActiveReservations(userId);

        if (cards == null || cards.isEmpty()) {
            return response(intent, role,
                    "You have no active reservations to cancel.",
                    List.of("View reservation history", "Browse pharmacies"));
        }

        long cancellable = cards.stream()
                .filter(c -> "PENDING".equalsIgnoreCase(safe(c.getReservationStatus(), c.getStatus(), "")))
                .count();

        if (cancellable == 0) {
            return response(intent, role,
                    "None of your active reservations can be cancelled right now. Only PENDING reservations can be cancelled — approved ones must be handled by the pharmacy.",
                    List.of("Contact pharmacy to cancel approved reservation", "View reservation details"));
        }

        String answer = "You have " + cancellable + " PENDING reservation(s) that can be cancelled."
                + " To cancel: open the reservation in the app and tap the Cancel button."
                + " Cancellation is only available while the reservation is still pending pharmacy approval.";

        return response(intent, role, answer,
                List.of("Open Reservations", "Select the pending reservation", "Tap Cancel"));
    }

    private AiChatResponseDTO handlePharmacyPendingReservations(Intent intent, ResolvedRole role) {
        requireRole(role, Role.PHARMACY, "This question is available for pharmacy role only.");
        Long pharmacyTelegramId = requireActorId(role, "pharmacyTelegramId is required for pharmacy role.");

        List<MedicineReservation> reservations = reservationService.getPendingReservations(pharmacyTelegramId);

        if (reservations.isEmpty()) {
            return response(intent, role,
                    "You have no pending reservations right now.",
                    List.of("Check approved reservations", "View inventory"));
        }

        String medicines = reservations.stream()
                .map(MedicineReservation::getMedicineName)
                .filter(Objects::nonNull)
                .distinct()
                .limit(5)
                .reduce((a, b) -> a + ", " + b)
                .orElse("various medicines");

        String answer = "You have " + reservations.size() + " pending reservation(s) awaiting your approval."
                + " Medicines requested: " + medicines + "."
                + (reservations.size() > 5 ? " ...and more." : "");

        return response(intent, role, answer,
                List.of("Open pending reservations queue", "Approve or reject reservations", "Check inventory before approving"));
    }

    private AiChatResponseDTO handlePharmacyApprovedReservations(Intent intent, ResolvedRole role) {
        requireRole(role, Role.PHARMACY, "This question is available for pharmacy role only.");
        Long pharmacyTelegramId = requireActorId(role, "pharmacyTelegramId is required for pharmacy role.");

        List<MedicineReservation> reservations = reservationService.getApprovedReservations(pharmacyTelegramId);

        if (reservations.isEmpty()) {
            return response(intent, role,
                    "No approved reservations are currently waiting for pickup.",
                    List.of("Check pending reservations", "View reservation history"));
        }

        String medicines = reservations.stream()
                .map(MedicineReservation::getMedicineName)
                .filter(Objects::nonNull)
                .distinct()
                .limit(5)
                .reduce((a, b) -> a + ", " + b)
                .orElse("various medicines");

        String answer = "You have " + reservations.size() + " approved reservation(s) ready for pickup/fulfillment."
                + " Medicines: " + medicines + ".";

        return response(intent, role, answer,
                List.of("Open fulfillment queue", "Scan QR to fulfill", "Mark as fulfilled"));
    }

    private AiChatResponseDTO handlePharmacyDemandInsights(Intent intent, ResolvedRole role) {
        requireRole(role, Role.PHARMACY, "This question is available for pharmacy role only.");
        Long pharmacyTelegramId = requireActorId(role, "pharmacyTelegramId is required for pharmacy role.");

        String insights = inventoryService.getDemandInsights(pharmacyTelegramId);
        return response(intent, role,
                "Here are your demand insights:\n" + insights,
                List.of("Restock high-demand items", "Adjust prices accordingly", "Review advanced restock suggestions"));
    }

    private AiChatResponseDTO handlePharmacyRestockSuggestions(Intent intent, ResolvedRole role) {
        requireRole(role, Role.PHARMACY, "This question is available for pharmacy role only.");
        Long pharmacyTelegramId = requireActorId(role, "pharmacyTelegramId is required for pharmacy role.");

        List<RestockSuggestionDTO> suggestions = inventoryService.listRestockSuggestions(pharmacyTelegramId);
        if (suggestions == null || suggestions.isEmpty()) {
            return response(intent, role,
                    "No restock suggestions right now.",
                    List.of("Open Restock page", "Review demand insights", "Check low stock"));
        }

        StringBuilder answer = new StringBuilder("Top restock suggestions:");
        int shown = 0;
        for (RestockSuggestionDTO s : suggestions) {
            if (shown >= 5) {
                break;
            }
            answer.append(" ").append(s.getMedicineName())
                    .append(" (").append(s.getPriority() == null ? "—" : s.getPriority());
            if (s.getDemandLabel() != null && !s.getDemandLabel().isBlank()) {
                answer.append(", ").append(s.getDemandLabel());
            }
            answer.append(", qty ").append(s.getCurrentStock() == null ? 0 : s.getCurrentStock())
                    .append(" → recommend ").append(s.getRecommendedQuantity() == null ? 0 : s.getRecommendedQuantity())
                    .append(").");
            shown++;
        }
        if (suggestions.size() > shown) {
            answer.append(" Plus ").append(suggestions.size() - shown).append(" more in Restock.");
        }
        return response(intent, role, answer.toString(),
                List.of("Open Restock", "Create purchase order", "Review demand insights"));
    }

    private AiChatResponseDTO handlePharmacyPerformance(Intent intent, ResolvedRole role) {
        requireRole(role, Role.PHARMACY, "This question is available for pharmacy role only.");
        Long pharmacyTelegramId = requireActorId(role, "pharmacyTelegramId is required for pharmacy role.");

        PharmacyPerformanceReportDTO report = pharmacyPerformanceService.getPerformanceReport(pharmacyTelegramId, "weekly");
        String answer = "Weekly health score: " + report.getHealthScore()
                + " (grade " + report.getHealthGrade() + ")."
                + " Reservations: " + (report.getReservations() == null ? 0 : report.getReservations().getTotal())
                + " total, fulfillment "
                + (report.getReservations() == null || report.getReservations().getFulfillmentRate() == null
                        ? "0" : report.getReservations().getFulfillmentRate()) + "%."
                + " Critical restock items: "
                + (report.getCriticalRestockCount() == null ? 0 : report.getCriticalRestockCount()) + "."
                + " Open Performance in the Mini App for full factors.";

        return response(intent, role, answer,
                List.of("Open Performance", "Review restock", "Check sales"));
    }

    private AiChatResponseDTO handlePharmacySales(Intent intent, ResolvedRole role) {
        requireRole(role, Role.PHARMACY, "This question is available for pharmacy role only.");
        Long pharmacyTelegramId = requireActorId(role, "pharmacyTelegramId is required for pharmacy role.");

        PharmacySalesSummaryDTO summary = pharmacySalesService.summary(pharmacyTelegramId, "weekly");
        String answer = "Weekly sales: revenue "
                + (summary.getRevenue() == null ? "0" : summary.getRevenue())
                + " ETB, " + (summary.getSaleCount() == null ? 0 : summary.getSaleCount()) + " sale(s), "
                + (summary.getMedicinesDispensed() == null ? 0 : summary.getMedicinesDispensed())
                + " unit(s) dispensed.";

        return response(intent, role, answer,
                List.of("Open Sales", "View today's sales", "Check performance"));
    }

    private AiChatResponseDTO handlePharmacyExpiry(Intent intent, ResolvedRole role) {
        requireRole(role, Role.PHARMACY, "This question is available for pharmacy role only.");
        Long pharmacyTelegramId = requireActorId(role, "pharmacyTelegramId is required for pharmacy role.");

        List<MedicineBatchDTO> batches = inventoryService.listExpiryBatches(pharmacyTelegramId, "90");
        if (batches == null || batches.isEmpty()) {
            return response(intent, role,
                    "No medicines are expiring within 90 days.",
                    List.of("Open Expiring page", "Check inventory", "Review FEFO lots"));
        }

        String names = batches.stream()
                .map(MedicineBatchDTO::getMedicineName)
                .filter(Objects::nonNull)
                .distinct()
                .limit(5)
                .collect(Collectors.joining(", "));
        String answer = "You have " + batches.size() + " lot(s) expiring within 90 days."
                + (names.isBlank() ? "" : " Examples: " + names + ".")
                + " Open Expiring in the Mini App for FEFO details.";

        return response(intent, role, answer,
                List.of("Open Expiring", "Adjust near-expiry stock", "Review inventory"));
    }

    private AiChatResponseDTO handlePharmacyNotifications(Intent intent, ResolvedRole role) {
        requireRole(role, Role.PHARMACY, "This question is available for pharmacy role only.");
        Long pharmacyTelegramId = requireActorId(role, "pharmacyTelegramId is required for pharmacy role.");

        long unread = pharmacyNotificationService.unreadCount(pharmacyTelegramId);
        List<PharmacyNotificationDTO> unreadRows = pharmacyNotificationService.list(pharmacyTelegramId, true);
        if (unread <= 0) {
            return response(intent, role,
                    "You have no unread notifications.",
                    List.of("Open Notifications", "Mark all as read", "Check reservations"));
        }

        String titles = unreadRows.stream()
                .map(PharmacyNotificationDTO::getTitle)
                .filter(Objects::nonNull)
                .limit(3)
                .collect(Collectors.joining("; "));
        String answer = "You have " + unread + " unread notification(s)."
                + (titles.isBlank() ? "" : " Latest: " + titles + ".")
                + " Open Notifications in the Mini App to review.";

        return response(intent, role, answer,
                List.of("Open Notifications", "Mark all as read", "Review reservations"));
    }

    private AiChatResponseDTO handleAdminReservationOversight(Intent intent, ResolvedRole role) {
        requireRole(role, Role.ADMIN, "This question is available for admin role only.");
        String oversight = adminService.viewDetailedReservationOversight();
        return response(intent, role,
                "Platform reservation overview:\n" + oversight,
                List.of("Open admin reservations dashboard", "Filter by status", "Export report"));
    }

    private AiChatResponseDTO handleAdminTopMedicines(Intent intent, ResolvedRole role) {
        requireRole(role, Role.ADMIN, "This question is available for admin role only.");
        String topMedicines = adminService.viewTopMedicinesDetails();
        return response(intent, role,
                "Top medicines by reservation demand:\n" + topMedicines,
                List.of("Monitor high-demand stock", "Alert pharmacies with low supply", "Review pricing trends"));
    }

    private AiChatResponseDTO handleUserReservationStatus(Intent intent, ResolvedRole role, AiChatRequestDTO request) {
        requireRole(role, Role.USER, "This question is available for user role only.");
        Long userId = requireActorId(role, "telegramUserId is required for user role.");

        List<MiniAppReservationCardDTO> cards = miniAppService.getActiveReservations(userId);

        if (cards == null || cards.isEmpty()) {
            return response(intent, role,
                    "You have no active reservations right now.",
                    List.of("Browse pharmacies and reserve medicine", "Check reservation history"));
        }

        MiniAppReservationCardDTO target = selectTargetReservation(cards, request.getReservationId());

        if (target == null) {
            return response(intent, role,
                    "I couldn't find the specified reservation. You have " + cards.size() + " active reservation(s) total.",
                    List.of("Open Reservations", "Share reservationId for a specific check"));
        }

        String pharmacy = safe(target.getPharmacyName(), "Unknown Pharmacy");
        String medicine = safe(target.getMedicineName(), "Unknown Medicine");
        String status = safe(target.getReservationStatus(), target.getStatus(), "UNKNOWN");
        int qty = target.getQuantity() != null ? target.getQuantity() : 0;
        String stage = safe(target.getUserFacingStage(), "");

        StringBuilder answer = new StringBuilder();
        answer.append("Reservation #").append(target.getReservationId()).append(": ")
              .append(medicine).append(" x").append(qty)
              .append(" at ").append(pharmacy).append(".")
              .append(" Status: ").append(status).append(".");

        if (!stage.isBlank()) {
            answer.append(" Current stage: ").append(stage).append(".");
        }

        if (target.getExpiresAt() != null) {
            answer.append(" Hold until: ").append(target.getExpiresAt().toString()).append(".");
        }

        List<String> actions = switch (status) {
            case "APPROVED" -> List.of("Go to pharmacy for pickup", "Open QR code");
            case "PENDING" -> List.of("Wait for pharmacy approval", "Check upload requirements");
            case "REJECTED" -> List.of("Create a new reservation", "Contact pharmacy");
            default -> List.of("Open reservation details", "Refresh status");
        };

        return response(intent, role, answer.toString(), actions);
    }

    private AiChatResponseDTO handleUserPrescriptionStatus(Intent intent, ResolvedRole role, AiChatRequestDTO request) {
        requireRole(role, Role.USER, "This question is available for user role only.");
        Long userId = requireActorId(role, "telegramUserId is required for user role.");

        List<MiniAppReservationCardDTO> cards = miniAppService.getActiveReservations(userId);
        MiniAppReservationCardDTO target = selectTargetReservation(cards, request.getReservationId());

        if (target == null) {
            return response(intent, role,
                    "I couldn't find an active reservation with prescription details for your account.",
                    List.of("Open Reservations", "Refresh reservation list"));
        }

        if (!target.isPrescriptionRequired()) {
            String medicine = safe(target.getMedicineName(), "your selected medicine");
            return response(intent, role,
                    "No prescription is required for " + medicine + ". Your reservation can proceed without one.",
                    List.of("Check reservation status", "Open reservation details"));
        }

        String status = safe(target.getPrescriptionStatus(), target.getPrescriptionReviewStatus(), "UNKNOWN");
        String answer = switch (status) {
            case "UPLOAD_REQUIRED" ->
                "Prescription status: Waiting for your upload. Please upload a clear prescription to move forward.";
            case "PENDING_REVIEW" ->
                "Prescription status: Uploaded and under pharmacy review. No action needed from you right now.";
            case "APPROVED" ->
                "Prescription status: Approved. The pharmacy has verified your prescription.";
            case "REJECTED" -> {
                String reason = target.getPrescriptionRejectionReason();
                yield "Prescription status: Rejected."
                        + (reason != null && !reason.isBlank() ? " Reason: " + reason + "." : "")
                        + " Please upload a valid prescription.";
            }
            default -> "Prescription status: " + status + ".";
        };

        List<String> actions = switch (status) {
            case "UPLOAD_REQUIRED", "REJECTED" ->
                List.of("Upload prescription", "Use a clear, readable image", "Check file size limits");
            case "PENDING_REVIEW" ->
                List.of("Wait for review", "Contact pharmacy if it's taking too long");
            case "APPROVED" ->
                List.of("Check reservation status", "Proceed to pickup if approved");
            default ->
                List.of("Open reservation details", "Contact pharmacy");
        };

        return response(intent, role, answer, actions);
    }

    private AiChatResponseDTO handleUserPendingReason(Intent intent, ResolvedRole role, AiChatRequestDTO request) {
        requireRole(role, Role.USER, "This question is available for user role only.");
        Long userId = requireActorId(role, "telegramUserId is required for user role.");

        List<MiniAppReservationCardDTO> cards = miniAppService.getActiveReservations(userId);
        MiniAppReservationCardDTO target = selectTargetReservation(cards, request.getReservationId());

        if (target == null) {
            return response(intent, role,
                    "I couldn't find an active reservation for your account. If you just created one, refresh and try again.",
                    List.of("Open Reservations", "Share reservationId for a specific check"));
        }

        String reservationStatus = safe(target.getReservationStatus(), target.getStatus(), "UNKNOWN");
        String prescriptionStatus = safe(target.getPrescriptionStatus(), target.getPrescriptionReviewStatus(), "NOT_REQUIRED");
        String stage = safe(target.getUserFacingStage(), "RESERVED");
        String reason = switch (stage) {
            case "UPLOAD_PRESCRIPTION" -> "it still needs prescription upload";
            case "PRESCRIPTION_REVIEW" -> "the pharmacy has not finished prescription review";
            case "WAITING_RESERVATION_APPROVAL", "RESERVED" -> "the pharmacy has not approved the reservation yet";
            default -> "the reservation workflow is still in progress";
        };

        String answer = "Your reservation is currently " + reservationStatus
                + " because " + reason + "."
                + " Prescription status: " + prescriptionStatus + "."
                + " Stage: " + stage + ".";

        return response(intent, role, answer,
                List.of("Check reservation details", "Upload prescription if required", "Wait for pharmacy approval"));
    }

    private AiChatResponseDTO handleUserPrescriptionUploadGuide(Intent intent, ResolvedRole role, AiChatRequestDTO request) {
        requireRole(role, Role.USER, "This question is available for user role only.");
        Long userId = requireActorId(role, "telegramUserId is required for user role.");

        List<MiniAppReservationCardDTO> cards = miniAppService.getActiveReservations(userId);
        MiniAppReservationCardDTO target = selectTargetReservation(cards, request.getReservationId());

        String guidance = "To upload a prescription: open your active reservation, choose the prescription upload action, "
                + "attach clear images/files, and submit. The pharmacy will be notified after upload.";

        if (target == null) {
            return response(intent, role, guidance,
                    List.of("Open Active Reservations", "Select reservation needing prescription", "Upload clear image(s)"));
        }

        String status = safe(target.getPrescriptionStatus(), target.getPrescriptionReviewStatus(), "NOT_REQUIRED");
        String tailored = switch (status) {
            case "UPLOAD_REQUIRED" -> guidance + " Your reservation is waiting for upload now.";
            case "PENDING_REVIEW" -> "Your prescription is already uploaded and currently under pharmacy review.";
            case "APPROVED" -> "Your prescription is already approved. No further upload is needed for this reservation.";
            case "REJECTED" -> "Your previous prescription was rejected. Please upload a clearer/valid prescription file.";
            default -> guidance;
        };

        return response(intent, role, tailored,
                List.of("Open reservation", "Upload prescription", "Check prescription status"));
    }

    private AiChatResponseDTO handleUserQrVisibility(Intent intent, ResolvedRole role, AiChatRequestDTO request) {
        requireRole(role, Role.USER, "This question is available for user role only.");
        Long userId = requireActorId(role, "telegramUserId is required for user role.");

        List<MiniAppReservationCardDTO> cards = miniAppService.getActiveReservations(userId);
        MiniAppReservationCardDTO target = selectTargetReservation(cards, request.getReservationId());

        if (target == null) {
            return response(intent, role,
                    "I couldn't find an active reservation to evaluate QR visibility.",
                    List.of("Open reservations", "Share reservationId"));
        }

        if (target.isCanShowQr() && target.isShowQrCode()) {
            return response(intent, role,
                    "You can see your QR now. The reservation is ready for pickup.",
                    List.of("Open reservation QR", "Go to pharmacy for pickup"));
        }

        String stage = safe(target.getUserFacingStage(), "RESERVED");
        String blocker = switch (stage) {
            case "UPLOAD_PRESCRIPTION" -> "upload your prescription first";
            case "PRESCRIPTION_REVIEW" -> "wait for prescription review";
            case "WAITING_RESERVATION_APPROVAL", "RESERVED" -> "wait for pharmacy reservation approval";
            default -> "complete the pending reservation steps";
        };

        return response(intent, role,
                "Your QR will appear when the reservation is approved and pickup-ready. Right now, please " + blocker + ".",
                List.of("Check reservation stage", "Complete pending step", "Refresh reservation status"));
    }

    private AiChatResponseDTO handlePharmacyPendingReviews(Intent intent, ResolvedRole role) {
        requireRole(role, Role.PHARMACY, "This question is available for pharmacy role only.");
        Long pharmacyTelegramId = requireActorId(role, "pharmacyTelegramId is required for pharmacy role.");

        List<MedicineReservation> reservations = reservationService.getPrescriptionReservations(pharmacyTelegramId);
        long pendingReview = reservations.stream()
                .filter(res -> res.getPrescriptionReviewStatus() == PrescriptionReviewStatus.PENDING_REVIEW)
                .count();
        long awaitingUpload = reservations.stream()
                .filter(res -> res.getPrescriptionReviewStatus() == PrescriptionReviewStatus.UPLOAD_REQUIRED)
                .count();

        String answer = "You currently have " + pendingReview + " pending prescription review(s)."
                + " Additional reservations waiting for customer upload: " + awaitingUpload + ".";

        return response(intent, role, answer,
                List.of("Open prescription queue", "Review pending prescriptions", "Follow up high-priority items"));
    }

    private AiChatResponseDTO handleLowStock(Intent intent, ResolvedRole role) {
        if (role.role() == Role.PHARMACY) {
            Long pharmacyTelegramId = requireActorId(role, "pharmacyTelegramId is required for pharmacy role.");
            String lowStock = inventoryService.buildLowStockAlert(pharmacyTelegramId);
            return response(intent, role,
                    "Here is your low-stock summary:\n" + lowStock,
                    List.of("Restock critical items", "Adjust low-stock thresholds", "Review demand insights"));
        }

        if (role.role() == Role.ADMIN) {
            String lowStock = adminService.viewLowStockDetails();
            return response(intent, role,
                    "Here is today's platform low-stock summary:\n" + lowStock,
                    List.of("Open pharmacy management", "Prioritize high-risk stockouts"));
        }

        throw new RuntimeException("Low stock summary is not available for user role.");
    }

    private AiChatResponseDTO handleAdminSystemSummary(Intent intent, ResolvedRole role) {
        requireRole(role, Role.ADMIN, "This question is available for admin role only.");
        String summary = adminService.viewDetailedSystemSummary();
        return response(intent, role,
                "Today's system summary:\n" + summary,
                List.of("Open admin dashboard", "Review reservation oversight", "Check low-stock details"));
    }

    private AiChatResponseDTO handleAdminComplianceSummary(Intent intent, ResolvedRole role) {
        requireRole(role, Role.ADMIN, "This question is available for admin role only.");
        LicenseComplianceService.ComplianceSummary summary = licenseComplianceService.buildSummary();

        String answer = "Compliance summary: expiring soon=" + summary.expiringSoon()
                + ", expired=" + summary.expired()
                + ", missing license=" + summary.missingLicense()
                + ", pending review=" + summary.pendingReview()
                + ", suspended=" + summary.suspended() + ".";

        return response(intent, role, answer,
                List.of("Open compliance dashboard", "Review expired licenses", "Take enforcement actions"));
    }

    private AiChatResponseDTO fallback(Intent intent, ResolvedRole role) {
        String answer = switch (role.role()) {
            case USER -> "I can help with: reservation status, reservation history, cancel guidance, prescription status, prescription upload, QR visibility, and medicine information questions.";
            case PHARMACY -> "I can help with: pending/approved reservations, prescription reviews, low stock, demand, restock, performance/health score, sales, expiring lots, and unread notifications.";
            case ADMIN -> "I can help with: system summary, reservation oversight, top medicines, compliance summary, and platform low-stock status.";
        };
        return response(intent, role, answer,
                List.of("Ask a supported workflow question", "Be specific (reservation, prescription, inventory, system)"));
    }

        // ── Medicine handlers ─────────────────────────────────────────────────────
        private AiChatResponseDTO handleMedicineUsage(Intent intent, ResolvedRole role, AiChatRequestDTO request) {
        String name = medicineKnowledgeService.detectMedicineName(normalize(request.getMessage()));
        if (name == null) return askMedicineName(intent, role);
        Optional<MedicineInfoDTO> opt = medicineKnowledgeService.lookup(name);
        if (opt.isEmpty()) return medicineNotFound(intent, role, name);
        MedicineInfoDTO m = opt.get();
        return medicineResponse(intent, role, m.getName(),
            m.getUse() + " " + m.getSafetyNote(),
            m.getUse(), null, null, null, "general_education",
            List.of("Ask how to take " + m.getName(),
                "Ask about side effects of " + m.getName(),
                "Ask about warnings for " + m.getName()));
        }

        private AiChatResponseDTO handleMedicineHowToTake(Intent intent, ResolvedRole role, AiChatRequestDTO request) {
        String name = medicineKnowledgeService.detectMedicineName(normalize(request.getMessage()));
        if (name == null) return askMedicineName(intent, role);
        Optional<MedicineInfoDTO> opt = medicineKnowledgeService.lookup(name);
        if (opt.isEmpty()) return medicineNotFound(intent, role, name);
        MedicineInfoDTO m = opt.get();
        // Curated package text may appear on the card only — answer body stays non-numeric personal advice.
        String answer = "For " + m.getName()
                + ", follow the package label or your clinician's prescription. "
                + "Do not treat general package guidance as personal dosing advice. "
                + m.getSafetyNote();
        return medicineResponse(intent, role, m.getName(),
            answer,
            null, m.getHowToTake(), null, null, "consult_pharmacist",
            List.of("Ask about side effects of " + m.getName(),
                "Ask about warnings for " + m.getName(),
                "Ask about missed dose for " + m.getName()));
        }

        private AiChatResponseDTO handleMedicineSideEffects(Intent intent, ResolvedRole role, AiChatRequestDTO request) {
        String name = medicineKnowledgeService.detectMedicineName(normalize(request.getMessage()));
        if (name == null) return askMedicineName(intent, role);
        Optional<MedicineInfoDTO> opt = medicineKnowledgeService.lookup(name);
        if (opt.isEmpty()) return medicineNotFound(intent, role, name);
        MedicineInfoDTO m = opt.get();
        return medicineResponse(intent, role, m.getName(),
            m.getSideEffects() + " " + m.getSafetyNote(),
            null, null, m.getSideEffects(), null, "consult_pharmacist",
            List.of("Ask about warnings for " + m.getName(),
                "Ask how to take " + m.getName(),
                "Ask about storage of " + m.getName()));
        }

        private AiChatResponseDTO handleMedicineWarnings(Intent intent, ResolvedRole role, AiChatRequestDTO request) {
        String name = medicineKnowledgeService.detectMedicineName(normalize(request.getMessage()));
        if (name == null) return askMedicineName(intent, role);
        Optional<MedicineInfoDTO> opt = medicineKnowledgeService.lookup(name);
        if (opt.isEmpty()) return medicineNotFound(intent, role, name);
        MedicineInfoDTO m = opt.get();
        return medicineResponse(intent, role, m.getName(),
            m.getWarnings() + " " + m.getSafetyNote(),
            null, null, null, m.getWarnings(), "consult_pharmacist",
            List.of("Ask about side effects of " + m.getName(),
                "Ask how to take " + m.getName(),
                "Ask about storage of " + m.getName()));
        }

        private AiChatResponseDTO handleMedicineStorage(Intent intent, ResolvedRole role, AiChatRequestDTO request) {
        String name = medicineKnowledgeService.detectMedicineName(normalize(request.getMessage()));
        if (name == null) return askMedicineName(intent, role);
        Optional<MedicineInfoDTO> opt = medicineKnowledgeService.lookup(name);
        if (opt.isEmpty()) return medicineNotFound(intent, role, name);
        MedicineInfoDTO m = opt.get();
        return medicineResponse(intent, role, m.getName(),
            m.getStorage() + " " + m.getSafetyNote(),
            null, null, null, null, "general_education",
            List.of("Ask how to take " + m.getName(),
                "Ask about side effects of " + m.getName()));
        }

        private AiChatResponseDTO handleMedicineMissedDose(Intent intent, ResolvedRole role, AiChatRequestDTO request) {
        String name = medicineKnowledgeService.detectMedicineName(normalize(request.getMessage()));
        if (name == null) return askMedicineName(intent, role);
        Optional<MedicineInfoDTO> opt = medicineKnowledgeService.lookup(name);
        if (opt.isEmpty()) return medicineNotFound(intent, role, name);
        MedicineInfoDTO m = opt.get();
        return medicineResponse(intent, role, m.getName(),
            m.getMissedDose() + " " + m.getSafetyNote(),
            null, null, null, null, "consult_pharmacist",
            List.of("Ask how to take " + m.getName(),
                "Ask about warnings for " + m.getName()));
        }

        private AiChatResponseDTO handleMedicineSearch(Intent intent, ResolvedRole role, AiChatRequestDTO request) {
        String name = medicineKnowledgeService.detectMedicineName(normalize(request.getMessage()));
        if (name == null || name.isBlank()) {
            return response(intent, role,
                    "Which medicine should I search for in the catalog?",
                    List.of("Ask: where can I find insulin", "Ask: how much is paracetamol"));
        }

        List<MiniAppMedicineSummaryDTO> found = miniAppService.searchMedicineCatalog(name, null, null);
        if (found == null || found.isEmpty()) {
            return response(intent, role,
                    "No catalog match for \"" + name + "\" in pharmacy inventory.",
                    List.of("Try another medicine name", "Search in the Mini App"));
        }

        StringBuilder answer = new StringBuilder("Catalog results for " + name + ":");
        int shown = 0;
        for (MiniAppMedicineSummaryDTO item : found) {
            if (shown >= 5) {
                break;
            }
            answer.append(" ").append(item.getMedicineName() == null ? name : item.getMedicineName());
            if (item.getPrice() != null) {
                answer.append(" from ").append(item.getPrice()).append(" ETB");
            }
            if (item.isOutOfStock() || item.getAvailablePharmacies() <= 0) {
                answer.append(" (out of stock)");
            } else {
                answer.append(", available in ").append(item.getAvailablePharmacies())
                        .append(item.getAvailablePharmacies() == 1 ? " pharmacy" : " pharmacies");
            }
            if (item.isPrescriptionRequired()) {
                answer.append(", prescription required");
            }
            answer.append(".");
            shown++;
        }
        return response(intent, role, answer.toString(),
                List.of("Open Mini App search", "Ask about a different medicine"));
        }

        private AiChatResponseDTO medicineResponse(Intent intent, ResolvedRole role, String medicineName,
            String answer, String use, String howToTake, String sideEffects, String warnings,
            String safetyLevel, List<String> actions) {
        return AiChatResponseDTO.builder()
            .answer(answer)
            .intent(intent.name())
            .role(role.role().name().toLowerCase(Locale.ROOT))
            .actionSuggestions(actions == null ? new ArrayList<>() : actions)
            .medicineName(medicineName)
            .safetyLevel(safetyLevel)
            .use(use)
            .howToTake(howToTake)
            .sideEffects(sideEffects)
            .warnings(warnings)
            .build();
        }

        private AiChatResponseDTO askMedicineName(Intent intent, ResolvedRole role) {
        return response(intent, role,
            "Please include the medicine name in your question — for example, \"What are the side effects of ibuprofen?\"",
            List.of("Try again with the medicine name", "Ask about paracetamol, amoxicillin, metformin, and more"));
        }

        private AiChatResponseDTO medicineNotFound(Intent intent, ResolvedRole role, String name) {
        return response(intent, role,
            "Sorry, I don't have curated information about \"" + name + "\" yet. "
                + "I will not invent dosing or clinical guidance. Please consult a qualified pharmacist or clinician.",
            List.of("Ask about a different medicine", "Consult a pharmacist"));
        }

    private MiniAppReservationCardDTO selectTargetReservation(List<MiniAppReservationCardDTO> cards, Long reservationId) {
        if (cards == null || cards.isEmpty()) {
            return null;
        }
        if (reservationId != null) {
            return cards.stream()
                    .filter(card -> Objects.equals(card.getReservationId(), reservationId))
                    .findFirst()
                    .orElse(null);
        }
        return cards.stream()
                .sorted(Comparator.comparing(MiniAppReservationCardDTO::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .findFirst()
                .orElse(cards.get(0));
    }

    private AiChatResponseDTO response(Intent intent, ResolvedRole role, String answer, List<String> actions) {
        return AiChatResponseDTO.builder()
                .answer(answer)
                .intent(intent.name())
                .role(role.role().name().toLowerCase(Locale.ROOT))
                .actionSuggestions(actions == null ? new ArrayList<>() : actions)
                .build();
    }

    private Intent classifyIntent(String message) {
        String text = normalize(message);

        // ── PHARMACY-specific first to avoid collisions ──────────────────────
        if (containsAny(text, "pending prescription reviews", "pending reviews",
                "how many pending prescription")) {
            return Intent.PHARMACY_PENDING_PRESCRIPTION_REVIEWS_COUNT;
        }
        if (containsAny(text, "demand insights", "what is in demand", "what medicines are in demand",
                "popular medicines", "medicine demand")) {
            return Intent.PHARMACY_DEMAND_INSIGHTS;
        }
        if (containsAny(text, "restock suggestions", "what to restock", "restock advice",
                "advanced restock", "what should i restock")) {
            return Intent.PHARMACY_RESTOCK_SUGGESTIONS;
        }
        if (containsAny(text, "pending reservations", "how many pending reservations",
                "reservations pending approval", "pending orders")) {
            return Intent.PHARMACY_PENDING_RESERVATIONS;
        }
        if (containsAny(text, "approved reservations", "how many approved", "ready for pickup",
                "ready to fulfill", "fulfillable reservations")) {
            return Intent.PHARMACY_APPROVED_RESERVATIONS;
        }
        if (containsAny(text, "low stock", "low in stock", "stock low", "what is low in stock")) {
            return Intent.PHARMACY_LOW_STOCK;
        }
        if (containsAny(text, "health score", "pharmacy performance", "performance report",
                "analytics", "pharmacy analytics", "my performance")) {
            return Intent.PHARMACY_PERFORMANCE;
        }
        if (containsAny(text, "sales summary", "my sales", "revenue this", "weekly sales",
                "daily sales", "how much revenue", "dispensed this week", "sales report")) {
            return Intent.PHARMACY_SALES;
        }
        if (containsAny(text, "expiring", "expiry", "near expiry", "fefo", "expire soon",
                "expiring stock", "batches expiring")) {
            return Intent.PHARMACY_EXPIRY;
        }
        if (containsAny(text, "unread notifications", "notification inbox", "my notifications",
                "pharmacy notifications", "how many unread")) {
            return Intent.PHARMACY_NOTIFICATIONS;
        }

        // ── ADMIN-specific ────────────────────────────────────────────────────
        if (containsAny(text, "system summary", "today system summary", "today's system summary")) {
            return Intent.ADMIN_SYSTEM_SUMMARY;
        }
        if (containsAny(text, "reservation overview", "reservation report", "reservation oversight",
                "all reservations breakdown", "reservation breakdown")) {
            return Intent.ADMIN_RESERVATION_OVERSIGHT;
        }
        if (containsAny(text, "top medicines", "most reserved medicines", "most popular medicine",
                "top demanded", "top medicine demand", "medicine stats")) {
            return Intent.ADMIN_TOP_MEDICINES;
        }
        if (containsAny(text, "compliance summary", "license compliance", "compliance status")) {
            return Intent.ADMIN_COMPLIANCE_SUMMARY;
        }

        // ── USER-specific ─────────────────────────────────────────────────────
        // prescription status (before upload guide)
        if ((containsAny(text, "prescription status", "my prescription status",
                "status of my prescription", "what is my prescription", "check prescription",
                "check my prescription", "prescription state", "prescription approved",
                "prescription rejected"))
                && !containsAny(text, "upload", "file", "image", "how to", "how do")) {
            return Intent.USER_PRESCRIPTION_STATUS;
        }
        // upload guide
        if (containsAll(text, "upload", "prescription")
                || (containsAll(text, "how", "prescription") && containsAny(text, "upload", "file", "image"))) {
            return Intent.USER_PRESCRIPTION_UPLOAD_GUIDE;
        }
        // cancel help
        if (containsAny(text, "cancel reservation", "how to cancel", "can i cancel",
                "cancel my reservation", "cancel my order")) {
            return Intent.USER_CANCEL_HELP;
        }
        // reservation history
        if (containsAny(text, "reservation history", "past reservations", "all my reservations",
                "show my history", "all reservations", "previous reservations",
                "fulfilled reservations", "rejected reservations", "expired reservations")) {
            return Intent.USER_RESERVATION_HISTORY;
        }
        // why still pending (specific — before generic status)
        if (containsAll(text, "why", "pending")
                || containsAll(text, "why", "reservation")
                || containsAny(text, "reservation still pending", "why is it pending",
                        "why not approved")) {
            return Intent.USER_RESERVATION_PENDING_REASON;
        }
        // generic reservation status
        if (containsAny(text, "reservation status", "my reservation status",
                "status of my reservation", "check my reservation", "check reservation",
                "show my reservation", "show reservation", "what is my reservation",
                "my reservation", "reservation details", "reservation info")) {
            return Intent.USER_RESERVATION_STATUS;
        }
        // qr
        if (text.contains("qr") && (text.contains("when") || text.contains("see") || text.contains("show"))) {
            return Intent.USER_QR_VISIBILITY;
        }

        // ── MEDICINE catalog search (availability/price) ──────────────────────
        if (containsAny(text, "where can i find", "where can i buy", "where to buy", "where to find",
                "search for", "look for", "available nearby", "pharmacies with", "pharmacies that have",
                "how much is", "price of", "is available", "find medicine")) {
            return Intent.MEDICINE_SEARCH;
        }

        // ── MEDICINE information (role-agnostic) ──────────────────────────────
        if (containsAny(text, "side effects", "side effect", "adverse effects", "adverse reaction")) {
            return Intent.MEDICINE_SIDE_EFFECTS;
        }
        if (containsAny(text, "missed dose", "missed a dose", "forgot to take", "forgot my dose",
                "what if i forget", "what if i miss")) {
            return Intent.MEDICINE_MISSED_DOSE;
        }
        if (containsAny(text, "how to store", "how do i store", "storage of", "store this medicine",
                "keep in fridge", "refrigerate")) {
            return Intent.MEDICINE_STORAGE;
        }
        if (containsAny(text, "warning", "contraindication", "drug interaction", "can i take",
                "safe to take", "interact with", "should i avoid")) {
            return Intent.MEDICINE_WARNINGS;
        }
        if (containsAny(text, "how do i take", "how to take", "dosage", "how many mg", "when to take",
                "how much should i take", "dose for me", "what dose", "personal dose")
                && !text.contains("prescription") && !text.contains("reservation")) {
            return Intent.MEDICINE_HOW_TO_TAKE;
        }
        if (containsAny(text, "what is this medicine", "medicine information", "drug information",
                "medicine for", "used for", "uses of", "what does this medicine", "what does it do")) {
            return Intent.MEDICINE_USAGE;
        }

        return Intent.UNKNOWN;
    }

    private ResolvedRole resolveRole(AiChatRequestDTO request,
                                     Long headerUserTelegramId,
                                     Long headerPharmacyTelegramId,
                                     Long headerAdminTelegramId) {
        Long adminId = firstPositive(headerAdminTelegramId, request.getAdminTelegramId());
        if (adminId != null && adminChatId > 0 && adminId == adminChatId) {
            return new ResolvedRole(Role.ADMIN, adminId);
        }

        Long pharmacyId = firstPositive(headerPharmacyTelegramId, request.getPharmacyTelegramId());
        if (pharmacyId != null && pharmacyRepository.existsByTelegramId(pharmacyId)) {
            return new ResolvedRole(Role.PHARMACY, pharmacyId);
        }

        Long userId = firstPositive(headerUserTelegramId, request.getTelegramUserId());
        if (userId != null) {
            return new ResolvedRole(Role.USER, userId);
        }

        throw new RuntimeException("Unable to resolve role. Provide one of: user, pharmacy, or admin Telegram ID.");
    }

    private void requireRole(ResolvedRole resolvedRole, Role required, String message) {
        if (resolvedRole.role() != required) {
            throw new RuntimeException(message);
        }
    }

    private Long requireActorId(ResolvedRole role, String message) {
        if (role.actorTelegramId() == null || role.actorTelegramId() <= 0) {
            throw new RuntimeException(message);
        }
        return role.actorTelegramId();
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
    }

    private boolean containsAll(String text, String a, String b) {
        return text.contains(a) && text.contains(b);
    }

    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private Long firstPositive(Long first, Long second) {
        if (first != null && first > 0) {
            return first;
        }
        if (second != null && second > 0) {
            return second;
        }
        return null;
    }

    private String safe(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private String safe(String value1, String value2, String fallback) {
        String first = safe(value1, null);
        if (first != null) {
            return first;
        }
        String second = safe(value2, null);
        return second == null ? fallback : second;
    }

    private enum Intent {
        USER_RESERVATION_STATUS,
        USER_RESERVATION_PENDING_REASON,
        USER_RESERVATION_HISTORY,
        USER_CANCEL_HELP,
        USER_PRESCRIPTION_STATUS,
        USER_PRESCRIPTION_UPLOAD_GUIDE,
        USER_QR_VISIBILITY,
        PHARMACY_PENDING_RESERVATIONS,
        PHARMACY_APPROVED_RESERVATIONS,
        PHARMACY_PENDING_PRESCRIPTION_REVIEWS_COUNT,
        PHARMACY_LOW_STOCK,
        PHARMACY_DEMAND_INSIGHTS,
        PHARMACY_RESTOCK_SUGGESTIONS,
        PHARMACY_PERFORMANCE,
        PHARMACY_SALES,
        PHARMACY_EXPIRY,
        PHARMACY_NOTIFICATIONS,
        ADMIN_SYSTEM_SUMMARY,
        ADMIN_RESERVATION_OVERSIGHT,
        ADMIN_TOP_MEDICINES,
        ADMIN_COMPLIANCE_SUMMARY,
        MEDICINE_USAGE,
        MEDICINE_HOW_TO_TAKE,
        MEDICINE_SIDE_EFFECTS,
        MEDICINE_WARNINGS,
        MEDICINE_STORAGE,
        MEDICINE_MISSED_DOSE,
        MEDICINE_SEARCH,
        UNKNOWN
    }

    private enum Role {
        USER,
        PHARMACY,
        ADMIN
    }

    private record ResolvedRole(Role role, Long actorTelegramId) {
    }

    private record ExecutionResult(AiChatResponseDTO response,
                                   Intent intent,
                                   ResolvedRole role,
                                   List<String> dataSources) {
    }
}
