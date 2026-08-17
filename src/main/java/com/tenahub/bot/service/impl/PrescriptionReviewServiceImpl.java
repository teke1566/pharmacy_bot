package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.PrescriptionFileMetadataDTO;
import com.tenahub.bot.dto.PrescriptionReviewRequestDTO;
import com.tenahub.bot.dto.PrescriptionStatusItemDTO;
import com.tenahub.bot.dto.PrescriptionStatusResponseDTO;
import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.entity.PrescriptionReviewStatus;
import com.tenahub.bot.entity.ReservationPrescriptionFile;
import com.tenahub.bot.repository.MedicineReservationRepository;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.repository.ReservationPrescriptionFileRepository;
import com.tenahub.bot.service.PharmacyNotificationService;
import com.tenahub.bot.service.PrescriptionReviewService;
import com.tenahub.bot.service.ReservationService;
import com.tenahub.bot.service.ReservationStatusHistoryService;
import com.tenahub.bot.entity.PharmacyNotificationType;
import com.tenahub.bot.util.TelegramClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PrescriptionReviewServiceImpl implements PrescriptionReviewService {

    private final MedicineReservationRepository reservationRepository;
    private final ReservationPrescriptionFileRepository prescriptionFileRepository;
    private final PharmacyRepository pharmacyRepository;
    private final PharmacyInventoryRepository pharmacyInventoryRepository;
    private final ReservationService reservationService;
    private final TelegramClient telegramClient;
    private final PharmacyNotificationService pharmacyNotificationService;
    private final ReservationStatusHistoryService reservationStatusHistoryService;

    @Value("${tenahub.reservation.pending-timeout-minutes:20}")
    private long pendingReservationTimeoutMinutes;

    @Override
    @Transactional
    public PrescriptionStatusResponseDTO uploadPrescriptionFiles(Long reservationId,
                                                                 String reservationGroupId,
                                                                 Long userId,
                                                                 Long pharmacyId,
                                                                 Long medicineId,
                                                                 String note,
                                                                 List<MultipartFile> files) {
        List<MedicineReservation> reservations = resolveReservations(reservationId, reservationGroupId);
        List<MedicineReservation> requiredReservations = filterPrescriptionReservations(reservations, medicineId);

        if (requiredReservations.isEmpty()) {
            throw new RuntimeException("No prescription-required reservation found for upload");
        }
        if (files == null || files.stream().noneMatch(file -> file != null && !file.isEmpty())) {
            throw new RuntimeException("At least one prescription file is required");
        }

        validateReservationStateForUpload(requiredReservations);

        Long resolvedUserId = requiredReservations.get(0).getUserId();
        Long resolvedPharmacyId = requiredReservations.get(0).getPharmacyId();
        Long requestedUserId = normalizeOptionalPositiveId(userId);
        if (requestedUserId != null && resolvedUserId != null && !Objects.equals(requestedUserId, resolvedUserId)) {
            System.out.println("[RX_UPLOAD] Ignoring mismatched telegramUserId=" + requestedUserId
                    + " for reservation owner=" + resolvedUserId
                    + " (reservationId=" + reservationId + ", reservationGroupId=" + reservationGroupId + ")");
        }
        if (pharmacyId != null && !Objects.equals(pharmacyId, resolvedPharmacyId)) {
            throw new RuntimeException("pharmacyId does not match reservation");
        }

        Long resolvedMedicineId = resolveMedicineId(medicineId, requiredReservations);
        String resolvedGroupId = resolveGroupId(requiredReservations);
        String resolvedNote = (note != null && !note.isBlank()) ? note.trim() : null;
        LocalDateTime uploadedAt = LocalDateTime.now();
        int savedFileCount = 0;
        List<Long> savedFileIds = new ArrayList<>();
        List<ReservationPrescriptionFile> savedFileEntities = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }

            try {
                byte[] fileBytes = file.getBytes();
                ReservationPrescriptionFile savedFile = prescriptionFileRepository.save(
                    ReservationPrescriptionFile.builder()
                        .reservationId(requiredReservations.size() == 1 ? requiredReservations.get(0).getId() : null)
                        .reservationGroupId(resolvedGroupId)
                        .userId(resolvedUserId != null ? resolvedUserId : 0L)
                        .pharmacyId(resolvedPharmacyId)
                        .medicineId(resolvedMedicineId)
                        .originalFilename(file.getOriginalFilename() == null ? "prescription" : file.getOriginalFilename())
                        .contentType(file.getContentType())
                        .fileSize(file.getSize())
                        .fileData(fileBytes)
                        .uploadedAt(uploadedAt)
                        .reviewStatus(PrescriptionReviewStatus.PENDING_REVIEW)
                        .build());
                savedFileIds.add(savedFile.getId());
                // Retain the byte array in-memory so afterCommit does not need to re-query the DB.
                savedFile.setFileData(fileBytes);
                savedFileEntities.add(savedFile);
                savedFileCount++;
            } catch (IOException e) {
                throw new RuntimeException("Failed to store prescription file", e);
            }
        }

        if (savedFileCount <= 0) {
            throw new RuntimeException("At least one prescription file is required");
        }

        LocalDateTime pendingExpiresAt = uploadedAt.plusMinutes(pendingReservationTimeoutMinutes);
        boolean clarificationReply = false;
        for (MedicineReservation reservation : reservations) {
            if (reservation.isPrescriptionRequired()) {
                PrescriptionReviewStatus previousRx = reservation.getPrescriptionReviewStatus();
                if (previousRx == PrescriptionReviewStatus.NEEDS_CLARIFICATION
                        || previousRx == PrescriptionReviewStatus.UPLOAD_REQUIRED) {
                    clarificationReply = clarificationReply
                            || previousRx == PrescriptionReviewStatus.NEEDS_CLARIFICATION;
                }
                reservation.setPrescriptionRequired(true);
                reservation.setPrescriptionReviewStatus(PrescriptionReviewStatus.PENDING_REVIEW);
                reservation.setPrescriptionReviewedAt(null);
                reservation.setPrescriptionReviewedBy(null);
                reservation.setPrescriptionRejectionReason(null);
                reservation.setPrescriptionClarificationMessage(null);
                reservation.setPrescriptionClarificationAt(null);
                reservationStatusHistoryService.record(
                        reservation,
                        reservation.getStatus() == null ? null : reservation.getStatus().name(),
                        reservation.getStatus() == null ? null : reservation.getStatus().name(),
                        resolvedUserId,
                        "rx:" + (previousRx == null ? "UNKNOWN" : previousRx.name()) + "→PENDING_REVIEW");
            }
            // Update note if provided with this upload
            if (resolvedNote != null) {
                reservation.setNote(resolvedNote);
            }
            reservation.setPendingExpiresAt(pendingExpiresAt);
            reservation.setFirstReminderSentAt(null);
            reservation.setSecondReminderSentAt(null);
            reservation.setSlaEscalatedAt(null);
            reservationRepository.save(reservation);
        }

        PrescriptionStatusResponseDTO statusResponse = buildStatusResponse(reservations);
        System.out.println("[RX_UPLOAD] reservationId=" + (reservationId != null ? reservationId : "group:" + reservationGroupId)
                + ", status=PENDING_REVIEW"
                + ", queue=prescription_review"
                + ", template=prescription_review_card"
                + ", savedFileCount=" + savedFileCount
                + ", savedFileIds=" + savedFileIds
                + ", pharmacyId=" + statusResponse.getPharmacyId()
                + ", note=" + resolvedNote
                + ", notificationTrigger=notifyPharmacyPrescriptionReadyAfterCommit"
                + ", fileCountInStatus=" + (statusResponse.getFiles() == null ? 0 : statusResponse.getFiles().size()));
        if (savedFileCount > 0) {
            notifyPharmacyPrescriptionReadyAfterCommit(statusResponse, savedFileEntities);
            PharmacyNotificationType inboxType = clarificationReply
                    ? PharmacyNotificationType.PRESCRIPTION_CLARIFICATION_REPLY
                    : PharmacyNotificationType.PRESCRIPTION_REVIEW;
            String title = clarificationReply ? "Clarification reply received" : "Prescription ready for review";
            String medicineName = (statusResponse.getItems() != null && !statusResponse.getItems().isEmpty())
                    ? statusResponse.getItems().get(0).getMedicineName()
                    : null;
            pharmacyNotificationService.create(
                    resolvedPharmacyId,
                    inboxType,
                    title,
                    title + (medicineName == null ? "" : " for " + medicineName),
                    statusResponse.getReservationId(),
                    medicineName);
        }

        // Notify the customer that their prescription has been submitted to the pharmacy.
        Long customerTelegramId = resolvedUserId;
        if (customerTelegramId != null && customerTelegramId > 0) {
            Long customerId = customerTelegramId;
            Long statusReservationId = statusResponse.getReservationId();
            String statusGroupId = statusResponse.getReservationGroupId();
            String medicineName = (statusResponse.getItems() != null && !statusResponse.getItems().isEmpty())
                    ? statusResponse.getItems().get(0).getMedicineName() : "your medicine";
            String customerMsg = "\u2705 Your prescription for <b>" + medicineName + "</b> has been submitted."
                    + "\nThe pharmacy has been notified and will review it shortly.";
            String statusUrl = telegramClient.buildMiniAppUserReservationStatusUrl(
                    "active", statusReservationId, statusGroupId);
            Runnable notifyCustomer = () -> {
                try {
                    telegramClient.sendMessageWithMiniAppButton(
                            customerId, customerMsg, statusUrl, "📄 View reservation");
                } catch (Exception e) {
                    System.out.println("[RX_UPLOAD] Failed to notify customer: " + e.getMessage());
                }
            };
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        notifyCustomer.run();
                    }
                });
            } else {
                notifyCustomer.run();
            }
        }

        return statusResponse;
    }

    @Override
    @Transactional(readOnly = true)
    public PrescriptionStatusResponseDTO getPrescriptionStatus(Long reservationId,
                                                               String reservationGroupId,
                                                               Long userId) {
        System.out.println("[RX_STATUS] getPrescriptionStatus called: reservationId=" + reservationId
                + ", groupId=" + reservationGroupId + ", userId=" + userId);
        List<MedicineReservation> reservations = resolveReservations(reservationId, reservationGroupId);
        Long resolvedUserId = reservations.get(0).getUserId();
        if (userId != null && !Objects.equals(userId, resolvedUserId)) {
            System.out.println("[RX_STATUS] userId mismatch: request=" + userId + ", owner=" + resolvedUserId);
            throw new RuntimeException("userId does not match reservation owner");
        }

        PrescriptionStatusResponseDTO response = buildStatusResponse(reservations);
        System.out.println("[RX_STATUS] result: reservationId=" + response.getReservationId()
                + ", groupId=" + response.getReservationGroupId()
                + ", reservationStatus=" + response.getReservationStatus()
                + ", reviewStatus=" + response.getReviewStatus()
                + ", pharmacyName=" + response.getPharmacyName()
                + ", medicineName=" + response.getMedicineName()
                + ", qty=" + response.getQuantity()
                + ", expiresAt=" + response.getExpiresAt()
                + ", items=" + (response.getItems() == null ? 0 : response.getItems().size())
                + ", files=" + (response.getFiles() == null ? 0 : response.getFiles().size()));
        return response;
    }

    @Override
    @Transactional
    public PrescriptionStatusResponseDTO reviewPrescription(Long reservationId,
                                                            String reservationGroupId,
                                                            Long pharmacyTelegramId,
                                                            PrescriptionReviewRequestDTO request) {
        List<MedicineReservation> reservations = resolveReservations(reservationId, reservationGroupId);
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(pharmacyTelegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        if (!Objects.equals(pharmacy.getId(), reservations.get(0).getPharmacyId())) {
            throw new RuntimeException("Reservation does not belong to this pharmacy");
        }

        PrescriptionReviewStatus decision = normalizeDecision(request == null ? null : request.getDecision());
        String rejectionReason = request == null ? null : request.getRejectionReason();
        String clarificationMessage = request == null ? null : request.getClarificationMessage();
        List<MedicineReservation> requiredReservations = reservations.stream()
                .filter(MedicineReservation::isPrescriptionRequired)
                .toList();

        if (requiredReservations.isEmpty()) {
            throw new RuntimeException("This reservation does not require prescription review");
        }

        LocalDateTime reviewedAt = LocalDateTime.now();
        List<ReservationPrescriptionFile> files = collectPrescriptionFiles(reservations);

        if (decision == PrescriptionReviewStatus.NEEDS_CLARIFICATION) {
            String resolvedMessage = clarificationMessage == null ? "" : clarificationMessage.trim();
            if (resolvedMessage.length() < 3) {
                throw new RuntimeException("clarificationMessage must be at least 3 characters");
            }
            for (MedicineReservation reservation : requiredReservations) {
                if (reservation.getPrescriptionReviewStatus() != PrescriptionReviewStatus.PENDING_REVIEW
                        && reservation.getPrescriptionReviewStatus() != PrescriptionReviewStatus.NEEDS_CLARIFICATION) {
                    throw new RuntimeException("Only prescriptions pending review can request clarification");
                }
                PrescriptionReviewStatus previousRx = reservation.getPrescriptionReviewStatus();
                reservation.setPrescriptionReviewStatus(PrescriptionReviewStatus.NEEDS_CLARIFICATION);
                reservation.setPrescriptionClarificationMessage(resolvedMessage);
                reservation.setPrescriptionClarificationAt(reviewedAt);
                reservation.setPrescriptionReviewedAt(reviewedAt);
                reservation.setPrescriptionReviewedBy(pharmacyTelegramId);
                reservation.setPrescriptionRejectionReason(null);
                reservationRepository.save(reservation);
                reservationStatusHistoryService.record(
                        reservation,
                        reservation.getStatus() == null ? null : reservation.getStatus().name(),
                        reservation.getStatus() == null ? null : reservation.getStatus().name(),
                        pharmacyTelegramId,
                        "rx:" + previousRx.name() + "→NEEDS_CLARIFICATION");
            }
            for (ReservationPrescriptionFile file : files) {
                file.setReviewStatus(PrescriptionReviewStatus.NEEDS_CLARIFICATION);
                file.setReviewedAt(reviewedAt);
                file.setReviewedBy(pharmacyTelegramId);
                file.setRejectionReason(null);
                prescriptionFileRepository.save(file);
            }
            PrescriptionStatusResponseDTO response = buildStatusResponse(reservations);
            notifyCustomerOfPrescriptionClarification(response, resolvedMessage);
            return response;
        }

        if (decision == PrescriptionReviewStatus.APPROVED) {
            if (files.isEmpty()) {
                throw new RuntimeException("No prescription files have been uploaded for this reservation");
            }

            for (MedicineReservation reservation : requiredReservations) {
                if (reservation.getStatus() != com.tenahub.bot.entity.MedicineReservationStatus.PENDING) {
                    throw new RuntimeException("Only pending reservations can have prescription approved");
                }
                PrescriptionReviewStatus previousRx = reservation.getPrescriptionReviewStatus();
                reservation.setPrescriptionReviewStatus(PrescriptionReviewStatus.APPROVED);
                reservation.setPrescriptionReviewedAt(reviewedAt);
                reservation.setPrescriptionReviewedBy(pharmacyTelegramId);
                reservation.setPrescriptionRejectionReason(null);
                reservation.setPrescriptionClarificationMessage(null);
                reservation.setPrescriptionClarificationAt(null);
                // Deduct stock now that prescription is approved (was deferred at reservation creation).
                if (!reservation.isInventoryHeld()) {
                    System.out.println("[RX_APPROVE] Holding inventory for approved prescription: reservationId="
                            + reservation.getId() + ", medicine=" + reservation.getMedicineName()
                            + ", qty=" + reservation.getRequestedQuantity());
                    try {
                        reservationService.holdInventoryForApprovedPrescription(reservation);
                    } catch (Exception e) {
                        System.out.println("[RX_APPROVE] WARNING: stock hold failed for reservationId="
                                + reservation.getId() + ": " + e.getMessage());
                    }
                } else {
                    System.out.println("[RX_APPROVE] Stock already held for reservationId=" + reservation.getId());
                }
                reservationRepository.save(reservation);
                reservationStatusHistoryService.record(
                        reservation,
                        reservation.getStatus() == null ? null : reservation.getStatus().name(),
                        reservation.getStatus() == null ? null : reservation.getStatus().name(),
                        pharmacyTelegramId,
                        "rx:" + (previousRx == null ? "UNKNOWN" : previousRx.name()) + "→APPROVED");
            }

            for (ReservationPrescriptionFile file : files) {
                file.setReviewStatus(PrescriptionReviewStatus.APPROVED);
                file.setReviewedAt(reviewedAt);
                file.setReviewedBy(pharmacyTelegramId);
                file.setRejectionReason(null);
                prescriptionFileRepository.save(file);
            }
        } else {
            String resolvedReason = rejectionReason == null || rejectionReason.isBlank()
                    ? "Prescription rejected by pharmacy"
                    : rejectionReason.trim();

            for (MedicineReservation reservation : requiredReservations) {
                // Re-open upload instead of cancelling the reservation so the customer can try again.
                PrescriptionReviewStatus previousRx = reservation.getPrescriptionReviewStatus();
                reservation.setStatus(com.tenahub.bot.entity.MedicineReservationStatus.PENDING);
                reservation.setPrescriptionReviewStatus(PrescriptionReviewStatus.UPLOAD_REQUIRED);
                reservation.setPrescriptionReviewedAt(reviewedAt);
                reservation.setPrescriptionReviewedBy(pharmacyTelegramId);
                reservation.setPrescriptionRejectionReason(resolvedReason);
                reservation.setPrescriptionClarificationMessage(null);
                reservation.setPrescriptionClarificationAt(null);
                reservation.setRejectionReason(null);
                reservation.setPendingExpiresAt(null);
                reservationRepository.save(reservation);
                reservationStatusHistoryService.record(
                        reservation,
                        "PENDING",
                        "PENDING",
                        pharmacyTelegramId,
                        "rx:" + (previousRx == null ? "UNKNOWN" : previousRx.name()) + "→UPLOAD_REQUIRED");
            }

            for (ReservationPrescriptionFile file : files) {
                file.setReviewStatus(PrescriptionReviewStatus.REJECTED);
                file.setReviewedAt(reviewedAt);
                file.setReviewedBy(pharmacyTelegramId);
                file.setRejectionReason(resolvedReason);
                prescriptionFileRepository.save(file);
            }
        }

        PrescriptionStatusResponseDTO response = buildStatusResponse(reservations);
        boolean approved = decision == PrescriptionReviewStatus.APPROVED;
        notifyCustomerOfPrescriptionDecision(response, approved);
        return response;
    }

    @Override
    public void notifyCustomerOfPrescriptionDecision(PrescriptionStatusResponseDTO status, boolean approved) {
        if (status == null || status.getUserId() == null) {
            return;
        }

        StringBuilder message = new StringBuilder();
        if (approved) {
            message.append("✅ Your prescription was approved.\n\n");
            message.append("Your reservation is now ready for pharmacy approval.");
        } else {
            message.append("❌ Your prescription was rejected.\n\n");
            if (status.getRejectionReason() != null && !status.getRejectionReason().isBlank()) {
                message.append("Reason: ").append(status.getRejectionReason()).append("\n\n");
            }
            message.append("Please upload a valid prescription and try again.");
        }

        if (status.getItems() != null && !status.getItems().isEmpty()) {
            message.append("\n\nMedicines:\n");
            for (var item : status.getItems()) {
                message.append("• ").append(item.getMedicineName()).append("\n");
            }
        }

        String statusUrl = telegramClient.buildMiniAppUserReservationStatusUrl(
                "active", status.getReservationId(), status.getReservationGroupId());
        telegramClient.sendMessageWithMiniAppButton(
                status.getUserId(),
                message.toString(),
                statusUrl,
                "📄 View reservation");
    }

    private void notifyCustomerOfPrescriptionClarification(PrescriptionStatusResponseDTO status, String clarificationMessage) {
        if (status == null || status.getUserId() == null) {
            return;
        }
        StringBuilder message = new StringBuilder();
        message.append("❓ The pharmacy needs clarification on your prescription.\n\n");
        message.append(clarificationMessage == null ? "" : clarificationMessage.trim());
        message.append("\n\nPlease upload an updated prescription or reply by re-uploading.");
        String statusUrl = telegramClient.buildMiniAppUserReservationStatusUrl(
                "active", status.getReservationId(), status.getReservationGroupId());
        telegramClient.sendMessageWithMiniAppButton(
                status.getUserId(),
                message.toString(),
                statusUrl,
                "📄 View reservation");
    }

    @Override
    @Transactional(readOnly = true)
    public PrescriptionFileContent downloadPrescriptionFile(Long prescriptionId, Long pharmacyTelegramId) {
        ReservationPrescriptionFile file = prescriptionFileRepository.findById(prescriptionId)
                .orElseThrow(() -> new RuntimeException("Prescription file not found"));
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(pharmacyTelegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        if (!Objects.equals(pharmacy.getId(), file.getPharmacyId())) {
            throw new RuntimeException("Prescription does not belong to this pharmacy");
        }

        return new PrescriptionFileContent(file.getFileData(), file.getContentType(), file.getOriginalFilename());
    }

    private List<MedicineReservation> resolveReservations(Long reservationId, String reservationGroupId) {
        if (reservationId == null && (reservationGroupId == null || reservationGroupId.isBlank())) {
            throw new RuntimeException("reservationId or reservationGroupId is required");
        }

        if (reservationId != null) {
            MedicineReservation reservation = reservationRepository.findById(reservationId)
                    .orElseThrow(() -> new RuntimeException("Reservation not found"));
            return List.of(reservation);
        }

        List<MedicineReservation> reservations = reservationRepository.findByReservationGroupIdOrderByCreatedAtDesc(reservationGroupId.trim());
        if (reservations.isEmpty()) {
            throw new RuntimeException("Reservation group not found");
        }
        return reservations;
    }

    private List<MedicineReservation> filterPrescriptionReservations(List<MedicineReservation> reservations, Long medicineId) {
        return reservations.stream()
                .filter(MedicineReservation::isPrescriptionRequired)
                .filter(reservation -> medicineId == null || Objects.equals(resolveReservationMedicineId(reservation), medicineId))
                .toList();
    }

    private void validateReservationStateForUpload(List<MedicineReservation> reservations) {
        for (MedicineReservation reservation : reservations) {
            switch (reservation.getStatus()) {
                case PENDING -> {
                }
                case READY_FOR_PICKUP, APPROVED, FULFILLED, CANCELLED, EXPIRED, REJECTED ->
                        throw new RuntimeException("Prescription files can only be uploaded for pending reservations");
                default ->
                        throw new RuntimeException("Prescription files can only be uploaded for pending reservations");
            }
        }
    }

    private Long normalizeOptionalPositiveId(Long id) {
        return id != null && id > 0 ? id : null;
    }

    private Long resolveMedicineId(Long requestedMedicineId, List<MedicineReservation> reservations) {
        if (requestedMedicineId != null) {
            PharmacyInventory inventory = pharmacyInventoryRepository.findById(requestedMedicineId)
                    .orElseThrow(() -> new RuntimeException("Medicine not found"));
            if (!Objects.equals(inventory.getPharmacyId(), reservations.get(0).getPharmacyId())) {
                throw new RuntimeException("Medicine does not belong to this pharmacy");
            }
            return requestedMedicineId;
        }

        if (reservations.size() == 1) {
            return resolveReservationMedicineId(reservations.get(0));
        }

        return null;
    }

    private Long resolveReservationMedicineId(MedicineReservation reservation) {
        if (reservation == null) {
            return null;
        }

        return pharmacyInventoryRepository.findByPharmacyIdAndMedicineNameIgnoreCase(
                        reservation.getPharmacyId(),
                        reservation.getMedicineName())
                .map(PharmacyInventory::getId)
                .orElse(null);
    }

    private String resolveGroupId(List<MedicineReservation> reservations) {
        return reservations.stream()
                .map(MedicineReservation::getReservationGroupId)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private PrescriptionReviewStatus normalizeDecision(String decision) {
        if (decision == null || decision.isBlank()) {
            throw new RuntimeException("decision is required");
        }

        String normalized = decision.trim().toLowerCase();
        return switch (normalized) {
            case "approve", "approved", "prescription_approved" -> PrescriptionReviewStatus.APPROVED;
            case "reject", "rejected", "prescription_rejected" -> PrescriptionReviewStatus.REJECTED;
            case "clarify", "clarification", "needs_clarification", "prescription_clarification" ->
                    PrescriptionReviewStatus.NEEDS_CLARIFICATION;
            default -> throw new RuntimeException("decision must be approve, reject, or clarify");
        };
    }

    private List<ReservationPrescriptionFile> collectPrescriptionFiles(List<MedicineReservation> reservations) {
        List<Long> reservationIds = reservations.stream()
                .map(MedicineReservation::getId)
                .toList();
        Map<Long, ReservationPrescriptionFile> files = new LinkedHashMap<>();

        prescriptionFileRepository.findByReservationIdInOrderByUploadedAtAsc(reservationIds)
                .forEach(file -> files.put(file.getId(), file));

        String reservationGroupId = resolveGroupId(reservations);
        if (reservationGroupId != null) {
            prescriptionFileRepository.findByReservationGroupIdOrderByUploadedAtAsc(reservationGroupId)
                    .forEach(file -> files.put(file.getId(), file));
        }

        return new ArrayList<>(files.values());
    }

    private PrescriptionStatusResponseDTO buildStatusResponse(List<MedicineReservation> reservations) {
        List<ReservationPrescriptionFile> files = collectPrescriptionFiles(reservations);
        List<MedicineReservation> sortedReservations = reservations.stream()
                .sorted(Comparator.comparing(MedicineReservation::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        MedicineReservation first = sortedReservations.get(0);
        PrescriptionReviewStatus reviewStatus = aggregateReviewStatus(sortedReservations);
        LocalDateTime reviewedAt = sortedReservations.stream()
                .map(MedicineReservation::getPrescriptionReviewedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        Long reviewedBy = sortedReservations.stream()
                .map(MedicineReservation::getPrescriptionReviewedBy)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        String rejectionReason = sortedReservations.stream()
                .map(MedicineReservation::getPrescriptionRejectionReason)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
        String clarificationMessage = sortedReservations.stream()
                .map(MedicineReservation::getPrescriptionClarificationMessage)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);

        // Resolve reservation status
        String reservationStatus = resolveAggregatedReservationStatus(sortedReservations);

        // Resolve pharmacy name
        String pharmacyName = null;
        if (first.getPharmacyId() != null) {
            pharmacyName = pharmacyRepository.findById(first.getPharmacyId())
                    .map(Pharmacy::getName)
                    .orElse(null);
        }

        // Resolve medicine name and quantity (single reservation or summarized)
        String medicineName;
        Integer quantity;
        if (sortedReservations.size() == 1) {
            medicineName = first.getMedicineName();
            quantity = first.getRequestedQuantity();
        } else {
            medicineName = sortedReservations.size() + " medicines";
            quantity = sortedReservations.stream()
                    .map(MedicineReservation::getRequestedQuantity)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .sum();
        }

        // Resolve expiry (earliest pending expiry or general expiry)
        LocalDateTime expiresAt = sortedReservations.stream()
                .map(r -> r.getPendingExpiresAt() != null ? r.getPendingExpiresAt() : r.getExpiresAt())
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);

        List<PrescriptionStatusItemDTO> items = sortedReservations.stream()
                .map(reservation -> PrescriptionStatusItemDTO.builder()
                        .reservationId(reservation.getId())
                        .reservationGroupId(reservation.getReservationGroupId())
                        .pharmacyId(reservation.getPharmacyId())
                        .medicineId(resolveReservationMedicineId(reservation))
                        .medicineName(reservation.getMedicineName())
                        .quantity(reservation.getRequestedQuantity())
                        .prescriptionRequired(reservation.isPrescriptionRequired())
                        .reviewStatus(reservation.getPrescriptionReviewStatus() == null ? null : reservation.getPrescriptionReviewStatus().name())
                        .reviewedAt(reservation.getPrescriptionReviewedAt())
                        .reviewedBy(reservation.getPrescriptionReviewedBy())
                        .rejectionReason(reservation.getPrescriptionRejectionReason())
                        .build())
                .toList();

        List<PrescriptionFileMetadataDTO> fileDTOs = files.stream()
                .map(file -> PrescriptionFileMetadataDTO.builder()
                        .prescriptionId(file.getId())
                        .reservationId(file.getReservationId())
                        .reservationGroupId(file.getReservationGroupId())
                        .userId(file.getUserId())
                        .pharmacyId(file.getPharmacyId())
                        .medicineId(file.getMedicineId())
                        .originalFilename(file.getOriginalFilename())
                        .contentType(file.getContentType())
                        .fileSize(file.getFileSize())
                        .uploadedAt(file.getUploadedAt())
                        .reviewStatus(file.getReviewStatus() == null ? null : file.getReviewStatus().name())
                        .reviewedAt(file.getReviewedAt())
                        .reviewedBy(file.getReviewedBy())
                        .rejectionReason(file.getRejectionReason())
                        .build())
                .toList();

        return PrescriptionStatusResponseDTO.builder()
                .reservationId(sortedReservations.size() == 1 ? first.getId() : null)
                .reservationGroupId(resolveGroupId(sortedReservations))
                .userId(first.getUserId())
                .pharmacyId(first.getPharmacyId())
                .customerPhone(first.getCustomerPhone())
                .customerName(first.getCustomerName())
                .note(sortedReservations.stream()
                        .map(MedicineReservation::getNote)
                        .filter(n -> n != null && !n.isBlank())
                        .findFirst().orElse(null))
                .prescriptionRequired(sortedReservations.stream().anyMatch(MedicineReservation::isPrescriptionRequired))
                .reviewStatus(reviewStatus.name())
                .reviewedAt(reviewedAt)
                .reviewedBy(reviewedBy)
                .rejectionReason(rejectionReason)
                .clarificationMessage(clarificationMessage)
                .reservationStatus(reservationStatus)
                .pharmacyName(pharmacyName)
                .medicineName(medicineName)
                .quantity(quantity)
                .expiresAt(expiresAt)
                .canShowQr(reviewStatus == PrescriptionReviewStatus.APPROVED
                        && ("APPROVED".equals(reservationStatus) || "READY_FOR_PICKUP".equals(reservationStatus)))
                .userFacingStage(deriveUserFacingStageForPrescription(reviewStatus, reservationStatus))
                .items(items)
                .files(fileDTOs)
                .build();
    }

    private String deriveUserFacingStageForPrescription(PrescriptionReviewStatus reviewStatus, String reservationStatus) {
        if ("FULFILLED".equals(reservationStatus)) return "COMPLETE";
        if ("CANCELLED".equals(reservationStatus)) return "CANCELLED";
        if ("EXPIRED".equals(reservationStatus)) return "EXPIRED";
        if ("REJECTED".equals(reservationStatus) || reviewStatus == PrescriptionReviewStatus.REJECTED) return "REJECTED";
        if (reviewStatus == PrescriptionReviewStatus.APPROVED
                && ("APPROVED".equals(reservationStatus) || "READY_FOR_PICKUP".equals(reservationStatus))) {
            return "READY_FOR_PICKUP";
        }
        if (reviewStatus == PrescriptionReviewStatus.APPROVED && "PENDING".equals(reservationStatus)) {
            return "WAITING_RESERVATION_APPROVAL";
        }
        if (reviewStatus == PrescriptionReviewStatus.PENDING_REVIEW) return "PRESCRIPTION_REVIEW";
        if (reviewStatus == PrescriptionReviewStatus.NEEDS_CLARIFICATION) return "PRESCRIPTION_CLARIFICATION";
        if (reviewStatus == PrescriptionReviewStatus.UPLOAD_REQUIRED) return "UPLOAD_PRESCRIPTION";
        return "RESERVED";
    }

    private String resolveAggregatedReservationStatus(List<MedicineReservation> reservations) {
        if (reservations == null || reservations.isEmpty()) {
            return null;
        }
        java.util.Set<String> statuses = reservations.stream()
                .map(MedicineReservation::getStatus)
                .filter(Objects::nonNull)
                .map(Enum::name)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (statuses.isEmpty()) {
            return null;
        }
        if (statuses.size() == 1) {
            return statuses.iterator().next();
        }
        return "MIXED";
    }

    private PrescriptionReviewStatus aggregateReviewStatus(List<MedicineReservation> reservations) {
        List<MedicineReservation> requiredReservations = reservations.stream()
                .filter(MedicineReservation::isPrescriptionRequired)
                .toList();

        if (requiredReservations.isEmpty()) {
            return PrescriptionReviewStatus.NOT_REQUIRED;
        }
        if (requiredReservations.stream().anyMatch(reservation ->
                reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.REJECTED)) {
            return PrescriptionReviewStatus.REJECTED;
        }
        if (requiredReservations.stream().anyMatch(reservation ->
                reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.NEEDS_CLARIFICATION)) {
            return PrescriptionReviewStatus.NEEDS_CLARIFICATION;
        }
        if (requiredReservations.stream().allMatch(reservation ->
                reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.APPROVED)) {
            return PrescriptionReviewStatus.APPROVED;
        }
        // PENDING_REVIEW takes priority over UPLOAD_REQUIRED: if any item has been uploaded (PENDING_REVIEW),
        // the pharmacy should be able to review it even if other items in the group are still UPLOAD_REQUIRED.
        if (requiredReservations.stream().anyMatch(reservation ->
                reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.PENDING_REVIEW)) {
            return PrescriptionReviewStatus.PENDING_REVIEW;
        }
        return PrescriptionReviewStatus.UPLOAD_REQUIRED;
    }

    private void notifyPharmacyPrescriptionReadyAfterCommit(PrescriptionStatusResponseDTO statusResponse,
                                                             List<ReservationPrescriptionFile> savedFiles) {
        if (statusResponse == null || statusResponse.getPharmacyId() == null) {
            System.out.println("[PRESC_NOTIFY] Skipped: statusResponse or pharmacyId is null");
            return;
        }

        // Resolve pharmacyTelegramId HERE, inside the active @Transactional context.
        Long pharmacyTelegramId = pharmacyRepository.findById(statusResponse.getPharmacyId())
                .map(Pharmacy::getTelegramId)
                .orElse(null);

        System.out.println("[PRESC_NOTIFY] reservationId=" + statusResponse.getReservationId()
                + ", fileCount=" + (savedFiles == null ? 0 : savedFiles.size())
                + ", pharmacyId=" + statusResponse.getPharmacyId()
                + ", pharmacyTelegramId=" + pharmacyTelegramId);

        if (pharmacyTelegramId == null || pharmacyTelegramId <= 0) {
            System.out.println("[PRESC_NOTIFY] Skipped: pharmacy has no Telegram ID registered");
            return;
        }

        Long resolvedPharmacyTelegramId = pharmacyTelegramId;
        // filesToSend kept only for logging context; binaries are viewed in Mini App.
        int fileCount = savedFiles == null ? 0 : savedFiles.size();

        Runnable notifier = () -> {
            try {
                System.out.println("[PRESC_NOTIFY] Sending prescription review card to pharmacyTelegramId="
                        + resolvedPharmacyTelegramId + " fileCount=" + fileCount);
                telegramClient.sendPharmacyPrescriptionReviewCard(resolvedPharmacyTelegramId, statusResponse);
                System.out.println("[PRESC_NOTIFY] Review card sent OK");
            } catch (Exception e) {
                System.out.println("[PRESC_NOTIFY] Exception sending card: " + e.getMessage());
                e.printStackTrace();
            }
            // Do not dump prescription binaries into Telegram chat.
            // Pharmacists open files via Mini App "View Prescription".
        };

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            System.out.println("[PRESC_NOTIFY] No active transaction synchronization — running notifier inline");
            notifier.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                System.out.println("[PRESC_NOTIFY] afterCommit() fired for pharmacyTelegramId=" + resolvedPharmacyTelegramId);
                notifier.run();
            }
        });
        System.out.println("[PRESC_NOTIFY] afterCommit registered for pharmacyTelegramId=" + resolvedPharmacyTelegramId);
    }
}