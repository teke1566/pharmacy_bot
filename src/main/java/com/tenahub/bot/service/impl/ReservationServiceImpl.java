package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.MedicineReservationStatus;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PrescriptionReviewStatus;
import com.tenahub.bot.repository.MedicineReservationRepository;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.MedicineLotService;
import com.tenahub.bot.service.PharmacySalesService;
import com.tenahub.bot.service.ReservationService;
import com.tenahub.bot.service.ReservationStatusHistoryService;
import com.tenahub.bot.service.ReservationWorkflowService;
import com.tenahub.bot.util.LocalizationService;
import com.tenahub.bot.util.MedicineSearchNormalizer;
import com.tenahub.bot.util.TelegramClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final MedicineReservationRepository reservationRepository;
    private final PharmacyRepository pharmacyRepository;
    private final PharmacyInventoryRepository inventoryRepository;
    private final LocalizationService localizationService;
    private final TelegramClient telegramClient;
    private final ReservationWorkflowService reservationWorkflowService;
    private final MedicineLotService medicineLotService;
    private final PharmacySalesService pharmacySalesService;
    private final ReservationStatusHistoryService reservationStatusHistoryService;

    @Value("${tenahub.reservation.pending-timeout-minutes:20}")
    private long pendingTimeoutMinutes;

    @Value("${tenahub.reservation.approved-hold-minutes:60}")
    private long approvedHoldMinutes;

    private static final List<MedicineReservationStatus> TERMINAL_STATUSES = List.of(
            MedicineReservationStatus.FULFILLED,
            MedicineReservationStatus.EXPIRED,
            MedicineReservationStatus.REJECTED,
            MedicineReservationStatus.CANCELLED
    );

    private void releaseHeldInventory(MedicineReservation reservation) {
        medicineLotService.releaseHeldForReservation(reservation);
    }

    private void holdInventoryOrThrow(MedicineReservation reservation) {
        medicineLotService.holdForReservation(reservation);
    }

   @Override
public MedicineReservation createReservation(Long userId,
                                             Long pharmacyId,
                                             String medicineName,
                                             Integer requestedQuantity,
                                             String customerPhone,
                                             String customerName) {

    if (requestedQuantity == null || requestedQuantity <= 0) {
        throw new RuntimeException("Quantity must be greater than 0.");
    }

    String normalizedMedicine = MedicineSearchNormalizer.normalizeToEnglishCanonical(medicineName);

    pharmacyRepository.findById(pharmacyId)
            .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

    var inventory = inventoryRepository
            .findByPharmacyIdAndMedicineNameIgnoreCase(pharmacyId, normalizedMedicine)
            .orElseThrow(() -> new RuntimeException("Medicine not found in pharmacy inventory"));
    medicineLotService.ensureBackfillAndSync(inventory);

    Integer availableQty = inventory.getQuantity() == null ? 0 : inventory.getQuantity();

    if (inventory.isOutOfStock() || availableQty <= 0) {
        if (medicineLotService.hasExpiredStock(inventory)) {
            throw new RuntimeException("Cannot dispense expired medicine.");
        }
        throw new RuntimeException("Medicine is currently out of stock.");
    }

    if (requestedQuantity > availableQty) {
        throw new RuntimeException("Requested quantity exceeds available stock.");
    }

    assertNoActiveDuplicateForPharmacyMedicine(userId, pharmacyId, normalizedMedicine);

    MedicineReservation reservation = MedicineReservation.builder()
            .userId(userId)
            .pharmacyId(pharmacyId)
            .medicineName(normalizedMedicine)
            .requestedQuantity(requestedQuantity)
            .status(MedicineReservationStatus.PENDING)
            .prescriptionRequired(inventory.isRequiresPrescription())
            .prescriptionReviewStatus(inventory.isRequiresPrescription()
                ? PrescriptionReviewStatus.UPLOAD_REQUIRED
                    : PrescriptionReviewStatus.NOT_REQUIRED)
            .createdAt(LocalDateTime.now())
            .customerPhone(customerPhone)
            .customerName(customerName)
                .inventoryHeld(false)
            .unitPrice(inventory.getPrice())
            .totalPrice(inventory.getPrice() == null ? null
                    : inventory.getPrice().multiply(java.math.BigDecimal.valueOf(requestedQuantity))
                    .setScale(2, java.math.RoundingMode.HALF_UP))
            .currency(inventory.getCurrency() == null || inventory.getCurrency().isBlank() ? "ETB" : inventory.getCurrency())
            .priceLockedAt(LocalDateTime.now())
            .build();

        if (!reservation.isPrescriptionRequired()) {
            System.out.println("[STOCK] Holding stock at creation: reservationId=pending, medicine="
                    + normalizedMedicine + ", qty=" + requestedQuantity);
            holdInventoryOrThrow(reservation);
        } else {
            System.out.println("[STOCK] Deferring stock hold (prescription required): medicine="
                    + normalizedMedicine + ", qty=" + requestedQuantity);
        }

    MedicineReservation saved = reservationRepository.save(reservation);
    reservationWorkflowService.notifyPharmacyPendingReservation(saved, pendingTimeoutMinutes);
    return saved;
}

    @Override
    public List<MedicineReservation> createReservationGroup(Long userId,
                                                             Long pharmacyId,
                                                             java.util.Map<String, Integer> medicineQuantities,
                                                             String customerPhone,
                                                             String customerName) {

        if (medicineQuantities == null || medicineQuantities.isEmpty()) {
            throw new RuntimeException("No medicines selected for reservation group.");
        }

        String groupId = java.util.UUID.randomUUID().toString();
        java.util.List<MedicineReservation> reservations = new java.util.ArrayList<>();

        pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        for (java.util.Map.Entry<String, Integer> entry : medicineQuantities.entrySet()) {
            String medicineName = entry.getKey();
            Integer requestedQuantity = entry.getValue();

            if (requestedQuantity == null || requestedQuantity <= 0) {
                continue;
            }

            String normalizedMedicine = MedicineSearchNormalizer.normalizeToEnglishCanonical(medicineName);

            var inventory = inventoryRepository
                    .findByPharmacyIdAndMedicineNameIgnoreCase(pharmacyId, normalizedMedicine)
                    .orElseThrow(() -> new RuntimeException("Medicine not found in pharmacy inventory: " + medicineName));
            medicineLotService.ensureBackfillAndSync(inventory);

            Integer availableQty = inventory.getQuantity() == null ? 0 : inventory.getQuantity();

            if (inventory.isOutOfStock() || availableQty <= 0) {
                if (medicineLotService.hasExpiredStock(inventory)) {
                    throw new RuntimeException("Cannot dispense expired medicine: " + medicineName);
                }
                throw new RuntimeException("Medicine is currently out of stock: " + medicineName);
            }

            if (requestedQuantity > availableQty) {
                throw new RuntimeException("Requested quantity exceeds available stock for: " + medicineName);
            }

            assertNoActiveDuplicateForPharmacyMedicine(userId, pharmacyId, normalizedMedicine);

            MedicineReservation reservation = MedicineReservation.builder()
                    .userId(userId)
                    .pharmacyId(pharmacyId)
                    .medicineName(normalizedMedicine)
                    .requestedQuantity(requestedQuantity)
                    .status(MedicineReservationStatus.PENDING)
                    .prescriptionRequired(inventory.isRequiresPrescription())
                    .prescriptionReviewStatus(inventory.isRequiresPrescription()
                        ? PrescriptionReviewStatus.UPLOAD_REQUIRED
                        : PrescriptionReviewStatus.NOT_REQUIRED)
                    .createdAt(java.time.LocalDateTime.now())
                    .customerPhone(customerPhone)
                    .customerName(customerName)
                    .inventoryHeld(false)
                    .reservationGroupId(groupId)
                    .unitPrice(inventory.getPrice())
                    .totalPrice(inventory.getPrice() == null ? null
                            : inventory.getPrice().multiply(java.math.BigDecimal.valueOf(requestedQuantity))
                            .setScale(2, java.math.RoundingMode.HALF_UP))
                    .currency(inventory.getCurrency() == null || inventory.getCurrency().isBlank() ? "ETB" : inventory.getCurrency())
                    .priceLockedAt(java.time.LocalDateTime.now())
                    .build();

            if (!reservation.isPrescriptionRequired()) {
                System.out.println("[STOCK] Holding stock at creation (group): medicine="
                        + normalizedMedicine + ", qty=" + requestedQuantity);
                holdInventoryOrThrow(reservation);
            } else {
                System.out.println("[STOCK] Deferring stock hold (prescription required, group): medicine="
                        + normalizedMedicine + ", qty=" + requestedQuantity);
            }

            reservations.add(reservationRepository.save(reservation));
        }

        reservationWorkflowService.notifyPharmacyPendingReservations(reservations, pendingTimeoutMinutes);
        return reservations;
    }

    @Override
    public MedicineReservation approveReservation(Long reservationId) {
        MedicineReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        if (reservation.getStatus() != MedicineReservationStatus.PENDING) {
            throw new RuntimeException("Only pending reservations can be approved.");
        }

        if (reservation.isPrescriptionRequired()) {
            if (reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.UPLOAD_REQUIRED) {
                throw new RuntimeException("Prescription upload is required before pharmacy review can begin.");
            }
            if (reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.NEEDS_CLARIFICATION) {
                throw new RuntimeException("Prescription clarification is still pending for this reservation.");
            }
            if (reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.REJECTED) {
                throw new RuntimeException("Prescription was rejected for this reservation.");
            }
            if (reservation.getPrescriptionReviewStatus() != PrescriptionReviewStatus.APPROVED) {
                throw new RuntimeException("Prescription review is still pending for this reservation.");
            }
        }

        // Normally inventory is already held on reservation creation.
        // Fallback: if hold flag is missing, try to hold now before approving.
        if (!reservation.isInventoryHeld()) {
            holdInventoryOrThrow(reservation);
        }

        reservation.setStatus(MedicineReservationStatus.READY_FOR_PICKUP);
        reservation.setApprovedAt(LocalDateTime.now());
        reservation.setPendingExpiresAt(null);
        reservation.setFirstReminderSentAt(null);
        reservation.setSecondReminderSentAt(null);
        reservation.setSlaEscalatedAt(null);
        reservation.setExpiresAt(LocalDateTime.now().plusMinutes(approvedHoldMinutes));

        MedicineReservation saved = reservationRepository.save(reservation);
        reservationStatusHistoryService.record(
                saved,
                MedicineReservationStatus.PENDING.name(),
                MedicineReservationStatus.READY_FOR_PICKUP.name(),
                null,
                "approved");
        return saved;
    }

    @Override
    public MedicineReservation approveReservationAndNotify(Long reservationId) {
        MedicineReservation approved = approveReservation(reservationId);
        notifyCustomerReservationApproved(approved);
        return approved;
    }

    private void assertNoActiveDuplicateForPharmacyMedicine(Long userId, Long pharmacyId, String normalizedMedicine) {
        if (userId == null || pharmacyId == null || normalizedMedicine == null || normalizedMedicine.isBlank()) {
            return;
        }
        List<MedicineReservation> active = reservationRepository.findByUserIdAndStatusIn(
                userId,
                List.of(
                        MedicineReservationStatus.PENDING,
                        MedicineReservationStatus.APPROVED,
                        MedicineReservationStatus.READY_FOR_PICKUP
                )
        );
        boolean duplicate = active.stream().anyMatch(existing ->
                pharmacyId.equals(existing.getPharmacyId())
                        && normalizedMedicine.equalsIgnoreCase(existing.getMedicineName()));
        if (duplicate) {
            throw new RuntimeException(
                    "You already have an active reservation for this medicine at this pharmacy.");
        }
    }

    private void notifyCustomerWithReservationMiniApp(MedicineReservation reservation, String section, String text) {
        if (reservation == null || reservation.getUserId() == null || reservation.getUserId() <= 0) {
            return;
        }
        if (text == null || text.isBlank()) {
            return;
        }
        String statusUrl = telegramClient.buildMiniAppUserReservationStatusUrl(
                section,
                reservation.getId(),
                reservation.getReservationGroupId());
        telegramClient.sendMessageWithMiniAppButton(
                reservation.getUserId(),
                text,
                statusUrl,
                "📄 View reservation");
    }

    private void notifyCustomerReservationApproved(MedicineReservation reservation) {
        if (reservation == null || reservation.getUserId() == null) {
            return;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");
        String holdUntil = reservation.getExpiresAt() == null
                ? "N/A"
                : reservation.getExpiresAt().format(formatter);

        String medicineName = MedicineSearchNormalizer.toDisplayName(
                reservation.getMedicineName(),
                localizationService.getLanguage(reservation.getUserId())
        );

        notifyCustomerWithReservationMiniApp(
                reservation,
                "active",
                localizationService.text(
                        reservation.getUserId(),
                        "reservation_approved_user",
                        medicineName,
                        reservation.getRequestedQuantity(),
                        holdUntil
                )
        );
    }

    @Override
    public MedicineReservation rejectReservation(Long reservationId, String reason) {
        MedicineReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        if (reservation.getStatus() != MedicineReservationStatus.PENDING) {
            throw new RuntimeException("Only pending reservations can be rejected.");
        }

        releaseHeldInventory(reservation);
        String from = reservation.getStatus() == null ? null : reservation.getStatus().name();
        reservation.setStatus(MedicineReservationStatus.REJECTED);
        reservation.setRejectionReason(reason);
        MedicineReservation saved = reservationRepository.save(reservation);
        reservationStatusHistoryService.record(
                saved, from, MedicineReservationStatus.REJECTED.name(), null, reason);
        return saved;
    }

    @Override
    public MedicineReservation rejectReservationAndNotify(Long reservationId, String reason) {
        String resolvedReason = (reason == null || reason.isBlank()) ? "Rejected by pharmacy" : reason.trim();
        MedicineReservation rejected = rejectReservation(reservationId, resolvedReason);
        notifyCustomerReservationRejected(rejected);
        return rejected;
    }

    private void notifyCustomerReservationRejected(MedicineReservation reservation) {
        if (reservation == null || reservation.getUserId() == null || reservation.getUserId() <= 0) {
            return;
        }
        String medicineName = MedicineSearchNormalizer.toDisplayName(
                reservation.getMedicineName(),
                localizationService.getLanguage(reservation.getUserId())
        );
        String reason = reservation.getRejectionReason() == null || reservation.getRejectionReason().isBlank()
                ? "Rejected by pharmacy"
                : reservation.getRejectionReason();
        notifyCustomerWithReservationMiniApp(
                reservation,
                "history",
                localizationService.text(
                        reservation.getUserId(),
                        "reservation_rejected_user",
                        medicineName,
                        reservation.getRequestedQuantity(),
                        reason
                )
        );
    }

    @Override
    public MedicineReservation fulfillReservation(Long reservationId) {
        MedicineReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        return fulfillReservationInternal(reservation, null);
    }

    @Override
    public MedicineReservation fulfillReservationAndNotify(Long reservationId) {
        MedicineReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        MedicineReservation fulfilledReservation = fulfillReservationInternal(reservation, null);
        notifyCustomerReservationFulfilled(fulfilledReservation);
        return fulfilledReservation;
    }

    @Override
    public MedicineReservation fulfillReservationAndNotify(Long reservationId, Long pharmacyTelegramId) {
        MedicineReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        validateReservationForPharmacyFulfillment(reservation, pharmacyTelegramId);

        MedicineReservation fulfilledReservation = fulfillReservationInternal(reservation, pharmacyTelegramId);
        notifyCustomerReservationFulfilled(fulfilledReservation);
        return fulfilledReservation;
    }

    private MedicineReservation fulfillReservationInternal(MedicineReservation reservation, Long actorTelegramId) {
        if (reservation == null) {
            throw new RuntimeException("Reservation not found");
        }

        if (reservation.getStatus() != MedicineReservationStatus.APPROVED
                && reservation.getStatus() != MedicineReservationStatus.READY_FOR_PICKUP) {
            throw new RuntimeException("Only approved reservations can be fulfilled.");
        }

        String from = reservation.getStatus() == null ? null : reservation.getStatus().name();
        Long resolvedActor = actorTelegramId;
        if (resolvedActor == null || resolvedActor <= 0) {
            resolvedActor = pharmacyRepository.findById(reservation.getPharmacyId())
                    .map(Pharmacy::getTelegramId)
                    .orElse(null);
        }
        medicineLotService.fulfillReservation(reservation, resolvedActor);

        reservation.setStatus(MedicineReservationStatus.FULFILLED);
        reservation.setFulfilledAt(LocalDateTime.now());
        reservation.setFulfilledByTelegramId(resolvedActor);

        MedicineReservation saved = reservationRepository.save(reservation);
        pharmacySalesService.recordFromReservation(saved, resolvedActor);
        reservationStatusHistoryService.record(
                saved, from, MedicineReservationStatus.FULFILLED.name(), resolvedActor, "fulfilled");
        return saved;
    }

    private void notifyCustomerReservationFulfilled(MedicineReservation reservation) {
        if (reservation == null || reservation.getUserId() == null) {
            return;
        }

        String medicineName = MedicineSearchNormalizer.toDisplayName(
                reservation.getMedicineName(),
                localizationService.getLanguage(reservation.getUserId())
        );

        notifyCustomerWithReservationMiniApp(
                reservation,
                "history",
                localizationService.text(
                        reservation.getUserId(),
                        "reservation_fulfilled_user",
                        medicineName,
                        reservation.getRequestedQuantity()
                )
        );
    }

    @Override
    public MedicineReservation scanReservationByQrToken(String qrToken, Long pharmacyTelegramId) {
        List<MedicineReservation> reservations = reservationRepository.findAllByQrToken(normalizeQrToken(qrToken));
        if (reservations == null || reservations.isEmpty()) {
            throw new RuntimeException("Invalid QR token");
        }

        MedicineReservation reservation = reservations.get(0);

        return validateReservationForPharmacyFulfillment(reservation, pharmacyTelegramId);
    }

    @Override
    public MedicineReservation fulfillReservationForPharmacy(Long reservationId, Long pharmacyTelegramId) {
        return fulfillReservationAndNotify(reservationId, pharmacyTelegramId);
    }

    @Override
    public MedicineReservation expireReservation(Long reservationId) {
        MedicineReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        if (reservation.getStatus() != MedicineReservationStatus.APPROVED
                && reservation.getStatus() != MedicineReservationStatus.READY_FOR_PICKUP) {
            throw new RuntimeException("Only approved reservations can expire.");
        }

        releaseHeldInventory(reservation);
        String from = reservation.getStatus() == null ? null : reservation.getStatus().name();
        reservation.setStatus(MedicineReservationStatus.EXPIRED);
        MedicineReservation saved = reservationRepository.save(reservation);
        reservationStatusHistoryService.record(
                saved, from, MedicineReservationStatus.EXPIRED.name(), null, "expired");
        return saved;
    }

    @Override
    public MedicineReservation cancelReservationByUser(Long userId, Long reservationId) {
        MedicineReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        if (!reservation.getUserId().equals(userId)) {
            throw new RuntimeException("You are not allowed to cancel this reservation.");
        }

        MedicineReservationStatus status = reservation.getStatus();
        if (status == MedicineReservationStatus.FULFILLED
                || status == MedicineReservationStatus.REJECTED
                || status == MedicineReservationStatus.EXPIRED
                || status == MedicineReservationStatus.CANCELLED) {
            throw new RuntimeException("This reservation cannot be cancelled (status: " + status.name() + ").");
        }

        if (status != MedicineReservationStatus.PENDING
                && status != MedicineReservationStatus.APPROVED
                && status != MedicineReservationStatus.READY_FOR_PICKUP) {
            throw new RuntimeException("This reservation cannot be cancelled (status: " + status.name() + ").");
        }

        releaseHeldInventory(reservation);
        String fromStatus = reservation.getStatus() == null ? null : reservation.getStatus().name();
        reservation.setStatus(MedicineReservationStatus.CANCELLED);
        MedicineReservation saved = reservationRepository.save(reservation);
        reservationStatusHistoryService.record(
                saved, fromStatus, MedicineReservationStatus.CANCELLED.name(), userId, "cancelled_by_user");
        return saved;
    }

    @Override
    public MedicineReservation cancelReservationByPharmacy(Long reservationId, Long pharmacyTelegramId) {
        MedicineReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(pharmacyTelegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        if (!reservation.getPharmacyId().equals(pharmacy.getId())) {
            throw new RuntimeException("This reservation does not belong to your pharmacy.");
        }

        MedicineReservationStatus status = reservation.getStatus();
        if (status == MedicineReservationStatus.FULFILLED
                || status == MedicineReservationStatus.CANCELLED
                || status == MedicineReservationStatus.EXPIRED) {
            throw new RuntimeException("This reservation cannot be cancelled (status: " + status.name() + ").");
        }

        releaseHeldInventory(reservation);
        String fromStatus = reservation.getStatus() == null ? null : reservation.getStatus().name();
        reservation.setStatus(MedicineReservationStatus.CANCELLED);
        reservation.setQrToken(null);
        MedicineReservation saved = reservationRepository.save(reservation);
        reservationStatusHistoryService.record(
                saved, fromStatus, MedicineReservationStatus.CANCELLED.name(), pharmacyTelegramId, "cancelled_by_pharmacy");

        // Notify user
        if (saved.getUserId() != null && saved.getUserId() > 0) {
            String medicineName = MedicineSearchNormalizer.toDisplayName(
                    saved.getMedicineName(),
                    localizationService.getLanguage(saved.getUserId()));
            notifyCustomerWithReservationMiniApp(
                    saved,
                    "history",
                    "❌ Your reservation has been cancelled by the pharmacy.\n\n"
                            + "🆔 ID: " + saved.getId() + "\n"
                            + "💊 Medicine: " + medicineName + "\n"
                            + "🔢 Qty: " + saved.getRequestedQuantity() + "\n"
                            + "🏥 Pharmacy: " + pharmacy.getName()
            );
        }

        return saved;
    }

    @Override
    public MedicineReservation autoCancelPendingReservation(Long reservationId, String reason) {
        MedicineReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        if (reservation.getStatus() != MedicineReservationStatus.PENDING) {
            throw new RuntimeException("Reservation is no longer pending");
        }

        releaseHeldInventory(reservation);
        String fromStatus = reservation.getStatus() == null ? null : reservation.getStatus().name();
        reservation.setStatus(MedicineReservationStatus.CANCELLED);
        reservation.setNote(reason == null || reason.isBlank() ? "AUTO_CANCELLED_PENDING_TIMEOUT" : reason);
        MedicineReservation saved = reservationRepository.save(reservation);
        reservationStatusHistoryService.record(
                saved, fromStatus, MedicineReservationStatus.CANCELLED.name(), null, saved.getNote());
        return saved;
    }

  @Override
public List<MedicineReservation> getUserReservations(Long userId) {
    refreshExpiredReservationsForUser(userId);
    return reservationRepository.findByUserIdOrderByCreatedAtDesc(userId);
}

    @Override
    public List<MedicineReservation> getPharmacyReservations(Long pharmacyTelegramId) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(pharmacyTelegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));
        return reservationRepository.findByPharmacyIdOrderByCreatedAtDesc(pharmacy.getId()).stream()
                .filter(r -> r.getHiddenFromPharmacyAt() == null)
                .toList();
    }

    @Override
    public void assertPharmacyOwnsReservation(Long reservationId, Long pharmacyTelegramId) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(pharmacyTelegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));
        MedicineReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));
        if (!pharmacy.getId().equals(reservation.getPharmacyId())) {
            throw new RuntimeException("Reservation does not belong to this pharmacy");
        }
    }

    private Pharmacy requirePharmacyForGroup(String reservationGroupId, Long pharmacyTelegramId) {
        if (reservationGroupId == null || reservationGroupId.isBlank()) {
            throw new RuntimeException("Reservation group id is required");
        }
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(pharmacyTelegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));
        List<MedicineReservation> reservations = reservationRepository.findByReservationGroupId(reservationGroupId);
        if (reservations == null || reservations.isEmpty()) {
            throw new RuntimeException("No reservations found for this group.");
        }
        boolean ownsAll = reservations.stream().allMatch(r -> pharmacy.getId().equals(r.getPharmacyId()));
        if (!ownsAll) {
            throw new RuntimeException("This reservation group does not belong to your pharmacy.");
        }
        return pharmacy;
    }

    @Override
    public List<MedicineReservation> approveGroupAndNotify(String reservationGroupId, Long pharmacyTelegramId) {
        requirePharmacyForGroup(reservationGroupId, pharmacyTelegramId);
        List<MedicineReservation> pending = reservationRepository.findByReservationGroupIdAndStatus(
                reservationGroupId,
                MedicineReservationStatus.PENDING
        );
        List<MedicineReservation> approved = new java.util.ArrayList<>();
        for (MedicineReservation res : pending) {
            try {
                approved.add(approveReservationAndNotify(res.getId()));
            } catch (RuntimeException e) {
                System.out.println("Error approving reservation " + res.getId() + ": " + e.getMessage());
            }
        }
        return approved;
    }

    @Override
    public List<MedicineReservation> rejectGroup(String reservationGroupId, Long pharmacyTelegramId, String reason) {
        requirePharmacyForGroup(reservationGroupId, pharmacyTelegramId);
        String rejectReason = (reason == null || reason.isBlank()) ? "Rejected by pharmacy" : reason;
        List<MedicineReservation> pending = reservationRepository.findByReservationGroupIdAndStatus(
                reservationGroupId,
                MedicineReservationStatus.PENDING
        );
        List<MedicineReservation> rejected = new java.util.ArrayList<>();
        for (MedicineReservation res : pending) {
            try {
                rejected.add(rejectReservationAndNotify(res.getId(), rejectReason));
            } catch (RuntimeException e) {
                System.out.println("Error rejecting reservation " + res.getId() + ": " + e.getMessage());
            }
        }
        return rejected;
    }

    @Override
    public List<MedicineReservation> fulfillGroupAndNotify(String reservationGroupId, Long pharmacyTelegramId) {
        requirePharmacyForGroup(reservationGroupId, pharmacyTelegramId);
        List<MedicineReservation> reservations = reservationRepository.findByReservationGroupId(reservationGroupId);
        List<MedicineReservation> fulfilled = new java.util.ArrayList<>();
        for (MedicineReservation r : reservations) {
            if (r.getStatus() == MedicineReservationStatus.APPROVED
                    || r.getStatus() == MedicineReservationStatus.READY_FOR_PICKUP) {
                try {
                    fulfilled.add(fulfillReservationAndNotify(r.getId(), pharmacyTelegramId));
                } catch (RuntimeException e) {
                    System.out.println("Error fulfilling reservation " + r.getId() + ": " + e.getMessage());
                }
            }
        }
        return fulfilled;
    }

    @Override
    public List<MedicineReservation> cancelGroupByPharmacy(String reservationGroupId, Long pharmacyTelegramId) {
        requirePharmacyForGroup(reservationGroupId, pharmacyTelegramId);
        List<MedicineReservation> reservations = reservationRepository.findByReservationGroupId(reservationGroupId);
        List<MedicineReservation> cancelled = new java.util.ArrayList<>();
        for (MedicineReservation r : reservations) {
            if (r.getStatus() != MedicineReservationStatus.FULFILLED
                    && r.getStatus() != MedicineReservationStatus.CANCELLED
                    && r.getStatus() != MedicineReservationStatus.EXPIRED
                    && r.getStatus() != MedicineReservationStatus.REJECTED) {
                try {
                    cancelled.add(cancelReservationByPharmacy(r.getId(), pharmacyTelegramId));
                } catch (RuntimeException e) {
                    System.out.println("Error cancelling reservation " + r.getId() + ": " + e.getMessage());
                }
            }
        }
        return cancelled;
    }

    @Override
    public String viewPendingReservations(Long pharmacyTelegramId) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(pharmacyTelegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        List<MedicineReservation> reservations =
                reservationRepository.findByPharmacyIdAndStatus(
                        pharmacy.getId(),
                        MedicineReservationStatus.PENDING
                );

        return buildPharmacyReservationList("⏳ Pending Reservations", reservations);
    }

    @Override
    public String viewApprovedReservations(Long pharmacyTelegramId) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(pharmacyTelegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        List<MedicineReservation> reservations =
                reservationRepository.findByPharmacyIdAndStatusIn(
                        pharmacy.getId(),
                        List.of(MedicineReservationStatus.APPROVED, MedicineReservationStatus.READY_FOR_PICKUP)
                );

        return buildPharmacyReservationList("✅ Approved Reservations", reservations);
    }

    @Override
    public String viewFulfillableReservations(Long pharmacyTelegramId) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(pharmacyTelegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        List<MedicineReservation> reservations =
                reservationRepository.findByPharmacyIdAndStatusIn(
                        pharmacy.getId(),
                        List.of(MedicineReservationStatus.APPROVED, MedicineReservationStatus.READY_FOR_PICKUP)
                );

        return buildPharmacyReservationList("📦 Ready to Fulfill", reservations);
    }

   @Override
public String viewReservationHistory(Long userId) {
    refreshExpiredReservationsForUser(userId);

    List<MedicineReservation> reservations = reservationRepository.findByUserIdOrderByCreatedAtDesc(userId);

    if (reservations == null || reservations.isEmpty()) {
        return localizationService.text(userId, "res_hist_empty");
    }

    StringBuilder sb = new StringBuilder(localizationService.text(userId, "res_hist_title")).append("\n\n");

    appendReservationSection(sb, userId, "res_hist_section_pending", reservations, MedicineReservationStatus.PENDING);
    appendReservationSection(sb, userId, "res_hist_section_approved", reservations, MedicineReservationStatus.APPROVED);
    appendReservationSection(sb, userId, "res_hist_section_fulfilled", reservations, MedicineReservationStatus.FULFILLED);
    appendReservationSection(sb, userId, "res_hist_section_cancelled", reservations, MedicineReservationStatus.CANCELLED);
    appendReservationSection(sb, userId, "res_hist_section_expired", reservations, MedicineReservationStatus.EXPIRED);
    appendReservationSection(sb, userId, "res_hist_section_rejected", reservations, MedicineReservationStatus.REJECTED);

    return sb.toString().trim();
}
    @Override
    public String buildUserReservationStatusLabel(MedicineReservation reservation) {
        if (reservation.isPrescriptionRequired()
                && reservation.getStatus() == MedicineReservationStatus.PENDING
                && reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.UPLOAD_REQUIRED) {
            return "PENDING • UPLOAD_REQUIRED";
        }

        if (reservation.isPrescriptionRequired()
                && reservation.getStatus() == MedicineReservationStatus.PENDING
                && reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.PENDING_REVIEW) {
            return "PENDING • PENDING_REVIEW";
        }

        if (reservation.isPrescriptionRequired()
                && reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.REJECTED) {
            return "REJECTED • PRESCRIPTION_REJECTED";
        }

        if (reservation.getStatus() == MedicineReservationStatus.READY_FOR_PICKUP) {
            if (reservation.getExpiresAt() != null) {
                return "READY_FOR_PICKUP • " + buildExpiryCountdown(reservation.getExpiresAt());
            }
            return "READY_FOR_PICKUP";
        }

        if (reservation.getStatus() == MedicineReservationStatus.APPROVED && reservation.getExpiresAt() != null) {
            return "APPROVED • " + buildExpiryCountdown(reservation.getExpiresAt());
        }

        if (reservation.getStatus() == MedicineReservationStatus.EXPIRED && reservation.getExpiresAt() != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d, h:mm a");
            return "EXPIRED • " + reservation.getExpiresAt().format(formatter);
        }

        return reservation.getStatus().name();
    }

    private String buildPharmacyReservationList(String title, List<MedicineReservation> reservations) {
        if (reservations == null || reservations.isEmpty()) {
            return title + "\n\nNo reservations found.";
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d, h:mm a");
        StringBuilder sb = new StringBuilder("<b>").append(title).append("</b>\n\n");

        for (MedicineReservation r : reservations) {
            sb.append("🆔 ID: ").append(r.getId()).append("\n")
                    .append("💊 Medicine: ").append(r.getMedicineName()).append("\n")
                    .append("🔢 Quantity: ").append(r.getRequestedQuantity()).append("\n")
                    .append("👤 Name: ").append(r.getCustomerName() == null ? "N/A" : r.getCustomerName()).append("\n")
                    .append("📱 Phone: ").append(r.getCustomerPhone() == null ? "N/A" : r.getCustomerPhone()).append("\n")
                    .append("📌 Status: ").append(r.getStatus().name()).append("\n");

            if (r.getApprovedAt() != null) {
                sb.append("✅ Approved: ").append(r.getApprovedAt().format(formatter)).append("\n");
            }

            if (r.getExpiresAt() != null) {
                sb.append("⏳ Hold Until: ").append(r.getExpiresAt().format(formatter)).append("\n");
            }

            if (r.getFulfilledAt() != null) {
                sb.append("📦 Fulfilled: ").append(r.getFulfilledAt().format(formatter)).append("\n");
            }

            if (r.getRejectionReason() != null && !r.getRejectionReason().isBlank()) {
                sb.append("❌ Reason: ").append(r.getRejectionReason()).append("\n");
            }

            sb.append("\n");
        }

        return sb.toString().trim();
    }

    private String buildExpiryCountdown(LocalDateTime expiresAt) {
        if (expiresAt == null) {
            return "No expiry";
        }

        Duration duration = Duration.between(LocalDateTime.now(), expiresAt);

        if (duration.isNegative() || duration.isZero()) {
            return "Expired";
        }

        long totalMinutes = duration.toMinutes();
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;

        if (hours > 0) {
            return "⏳ Expires in " + hours + "h " + minutes + "m";
        }

        return "⏳ Expires in " + minutes + " min";
    }

@Override
public String viewActiveReservations(Long chatId) {
    refreshExpiredReservationsForUser(chatId);

    List<MedicineReservation> reservations = reservationRepository.findByUserIdAndStatusIn(
            chatId,
            List.of(
                    MedicineReservationStatus.PENDING,
                    MedicineReservationStatus.APPROVED,
                    MedicineReservationStatus.READY_FOR_PICKUP
            )
    );

    if (reservations == null || reservations.isEmpty()) {
        return "📦 <b>Active Reservations</b>\n\nNo active reservations found.";
    }

    StringBuilder sb = new StringBuilder("📦 <b>Active Reservations</b>\n\n");

    appendReservationSection(sb, chatId, "res_hist_section_pending", reservations, MedicineReservationStatus.PENDING);
    appendReservationSection(sb, chatId, "res_hist_section_approved", reservations, MedicineReservationStatus.APPROVED);
    appendReservationSection(sb, chatId, "res_hist_section_approved", reservations, MedicineReservationStatus.READY_FOR_PICKUP);

    return sb.toString().trim();
}
private void refreshExpiredReservationsForUser(Long userId) {
    List<MedicineReservation> reservations = reservationRepository.findByUserIdOrderByCreatedAtDesc(userId);

    for (MedicineReservation reservation : reservations) {
        if ((reservation.getStatus() == MedicineReservationStatus.APPROVED
                || reservation.getStatus() == MedicineReservationStatus.READY_FOR_PICKUP)
                && reservation.getExpiresAt() != null
                && !reservation.getExpiresAt().isAfter(LocalDateTime.now())) {
            try {
                expireReservation(reservation.getId());
            } catch (Exception ignored) {
            }
        }
    }
}

private void appendReservationSection(StringBuilder sb,
                                      Long userId,
                                      String sectionKey,
                                      List<MedicineReservation> reservations,
                                      MedicineReservationStatus status) {
    List<MedicineReservation> filtered = reservations.stream()
            .filter(r -> r.getStatus() == status)
        .toList();

    if (filtered.isEmpty()) {
        return;
    }

    String sectionTitle = localizationService.text(userId, sectionKey);
    sb.append("<b>").append(sectionTitle).append(" (").append(filtered.size()).append(")</b>\n\n");

    for (MedicineReservation r : filtered) {
        Pharmacy pharmacy = pharmacyRepository.findById(r.getPharmacyId()).orElse(null);
        String pharmacyName = pharmacy != null ? pharmacy.getName() : localizationService.text(userId, "unknown_pharmacy");

        sb.append("🆔 ").append(r.getId())
                .append(" · 🏥 ").append(pharmacyName).append("\n")
                .append("💊 ").append(r.getMedicineName())
                .append(" · 🔢 ").append(r.getRequestedQuantity()).append("\n")
                .append("📌 ").append(localizedStatusLabel(userId, r)).append("\n");

        if (r.getStatus() == MedicineReservationStatus.REJECTED
                && r.getRejectionReason() != null
                && !r.getRejectionReason().isBlank()) {
            sb.append("❌ ").append(localizationService.text(userId, "res_hist_reason"))
                    .append(": ").append(r.getRejectionReason()).append("\n");
        }

        sb.append("\n");
    }
}

private String localizedStatusLabel(Long userId, MedicineReservation r) {
    String statusKey = switch (r.getStatus()) {
        case PENDING -> "res_status_pending";
        case APPROVED -> "res_status_approved";
        case READY_FOR_PICKUP -> "res_status_approved";
        case FULFILLED -> "res_status_fulfilled";
        case CANCELLED -> "res_status_cancelled";
        case EXPIRED -> "res_status_expired";
        case REJECTED -> "res_status_rejected";
    };
    String base = localizationService.text(userId, statusKey);
    if ((r.getStatus() == MedicineReservationStatus.APPROVED
            || r.getStatus() == MedicineReservationStatus.READY_FOR_PICKUP)
            && r.getExpiresAt() != null) {
        return base + " • " + buildExpiryCountdown(r.getExpiresAt());
    }
    if (r.getStatus() == MedicineReservationStatus.EXPIRED && r.getExpiresAt() != null) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d, h:mm a");
        return base + " • " + r.getExpiresAt().format(formatter);
    }
    return base;
}
@Override
public List<MedicineReservation> getPendingReservations(Long pharmacyTelegramId) {
    Pharmacy pharmacy = pharmacyRepository.findByTelegramId(pharmacyTelegramId)
            .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

    return reservationRepository.findByPharmacyIdAndStatus(pharmacy.getId(), MedicineReservationStatus.PENDING)
            .stream()
            .filter(r -> r.getHiddenFromPharmacyAt() == null)
            .filter(this::isVisibleInPharmacyPendingQueue)
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .toList();
}

@Override
public List<MedicineReservation> getPrescriptionReservations(Long pharmacyTelegramId) {
    Pharmacy pharmacy = pharmacyRepository.findByTelegramId(pharmacyTelegramId)
            .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

    return reservationRepository.findByPharmacyIdOrderByCreatedAtDesc(pharmacy.getId())
            .stream()
            .filter(r -> r.getHiddenFromPharmacyAt() == null)
            .filter(MedicineReservation::isPrescriptionRequired)
            .filter(r -> {
                MedicineReservationStatus status = r.getStatus();
                return status == MedicineReservationStatus.PENDING
                        || status == MedicineReservationStatus.APPROVED
                        || status == MedicineReservationStatus.READY_FOR_PICKUP;
            })
            .filter(r -> {
                PrescriptionReviewStatus st = r.getPrescriptionReviewStatus();
                return st == PrescriptionReviewStatus.PENDING_REVIEW
                        || st == PrescriptionReviewStatus.NEEDS_CLARIFICATION
                        || st == PrescriptionReviewStatus.APPROVED
                        || st == PrescriptionReviewStatus.UPLOAD_REQUIRED;
            })
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .toList();
}

@Override
public List<MedicineReservation> getApprovedReservations(Long pharmacyTelegramId) {
    Pharmacy pharmacy = pharmacyRepository.findByTelegramId(pharmacyTelegramId)
            .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

    return reservationRepository.findByPharmacyIdAndStatusIn(
                    pharmacy.getId(),
                    List.of(MedicineReservationStatus.APPROVED, MedicineReservationStatus.READY_FOR_PICKUP))
            .stream()
            .filter(r -> r.getHiddenFromPharmacyAt() == null)
            .filter(r -> r.getExpiresAt() == null || r.getExpiresAt().isAfter(LocalDateTime.now()))
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .toList();
}

@Override
public List<MedicineReservation> getTerminalReservations(Long pharmacyTelegramId) {
    Pharmacy pharmacy = pharmacyRepository.findByTelegramId(pharmacyTelegramId)
            .orElseThrow(() -> new RuntimeException("Pharmacy not found"));
    return reservationRepository.findByPharmacyIdAndStatusIn(pharmacy.getId(), TERMINAL_STATUSES)
            .stream()
            .filter(r -> r.getHiddenFromPharmacyAt() == null)
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .toList();
}

@Override
public MedicineReservation hideReservationFromPharmacy(Long reservationId, Long pharmacyTelegramId) {
    assertPharmacyOwnsReservation(reservationId, pharmacyTelegramId);
    MedicineReservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new RuntimeException("Reservation not found"));
    if (!isTerminalStatus(reservation.getStatus())) {
        throw new RuntimeException("Only completed, expired, cancelled, or rejected reservations can be dismissed");
    }
    if (reservation.getHiddenFromPharmacyAt() == null) {
        reservation.setHiddenFromPharmacyAt(LocalDateTime.now());
        reservationRepository.save(reservation);
    }
    return reservation;
}

@Override
public int hideReservationsFromPharmacy(List<Long> reservationIds, Long pharmacyTelegramId) {
    if (reservationIds == null || reservationIds.isEmpty()) {
        return 0;
    }
    Pharmacy pharmacy = pharmacyRepository.findByTelegramId(pharmacyTelegramId)
            .orElseThrow(() -> new RuntimeException("Pharmacy not found"));
    LocalDateTime hiddenAt = LocalDateTime.now();
    int count = 0;
    for (Long id : reservationIds) {
        if (id == null) {
            continue;
        }
        MedicineReservation reservation = reservationRepository.findById(id).orElse(null);
        if (reservation == null || !pharmacy.getId().equals(reservation.getPharmacyId())) {
            continue;
        }
        if (!isTerminalStatus(reservation.getStatus())) {
            continue;
        }
        if (reservation.getHiddenFromPharmacyAt() == null) {
            reservation.setHiddenFromPharmacyAt(hiddenAt);
            reservationRepository.save(reservation);
            count++;
        }
    }
    return count;
}

@Override
public int hideTerminalReservationsFromPharmacy(Long pharmacyTelegramId) {
    Pharmacy pharmacy = pharmacyRepository.findByTelegramId(pharmacyTelegramId)
            .orElseThrow(() -> new RuntimeException("Pharmacy not found"));
    LocalDateTime hiddenAt = LocalDateTime.now();
    List<MedicineReservation> terminal = reservationRepository.findByPharmacyIdAndStatusIn(
            pharmacy.getId(), TERMINAL_STATUSES);
    int count = 0;
    for (MedicineReservation reservation : terminal) {
        if (reservation.getHiddenFromPharmacyAt() == null) {
            reservation.setHiddenFromPharmacyAt(hiddenAt);
            count++;
        }
    }
    if (count > 0) {
        reservationRepository.saveAll(terminal);
    }
    return count;
}

private boolean isTerminalStatus(MedicineReservationStatus status) {
    return status == MedicineReservationStatus.FULFILLED
            || status == MedicineReservationStatus.EXPIRED
            || status == MedicineReservationStatus.REJECTED
            || status == MedicineReservationStatus.CANCELLED;
}

@Override
public List<MedicineReservation> getFulfillableReservations(Long pharmacyTelegramId) {
    return getApprovedReservations(pharmacyTelegramId);
}

private String normalizeQrToken(String qrToken) {
    if (qrToken == null || qrToken.isBlank()) {
        throw new RuntimeException("qrToken is required");
    }
    return qrToken.trim();
}

private MedicineReservation validateReservationForPharmacyFulfillment(MedicineReservation reservation,
                                                                     Long pharmacyTelegramId) {
    Pharmacy pharmacy = pharmacyRepository.findByTelegramId(pharmacyTelegramId)
            .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

    if (!pharmacy.getId().equals(reservation.getPharmacyId())) {
        throw new RuntimeException("Reservation does not belong to this pharmacy.");
    }

    LocalDateTime now = LocalDateTime.now();

    if (reservation.getStatus() == MedicineReservationStatus.FULFILLED) {
        throw new RuntimeException("Reservation is already fulfilled.");
    }

    if (reservation.getStatus() == MedicineReservationStatus.EXPIRED) {
        throw new RuntimeException("Reservation is expired.");
    }

    if (reservation.getStatus() == MedicineReservationStatus.PENDING
            && reservation.getPendingExpiresAt() != null
            && !reservation.getPendingExpiresAt().isAfter(now)) {
        autoCancelPendingReservation(reservation.getId(), "AUTO_CANCELLED_PENDING_TIMEOUT");
        throw new RuntimeException("Reservation is expired.");
    }

    if (reservation.isPrescriptionRequired()
            && reservation.getPrescriptionReviewStatus() != PrescriptionReviewStatus.APPROVED
            && reservation.getStatus() == MedicineReservationStatus.PENDING) {
        return reservation;
    }

    if ((reservation.getStatus() == MedicineReservationStatus.APPROVED
            || reservation.getStatus() == MedicineReservationStatus.READY_FOR_PICKUP)
            && reservation.getExpiresAt() != null
            && !reservation.getExpiresAt().isAfter(now)) {
        expireReservation(reservation.getId());
        throw new RuntimeException("Reservation is expired.");
    }

    if (reservation.getStatus() != MedicineReservationStatus.APPROVED
            && reservation.getStatus() != MedicineReservationStatus.READY_FOR_PICKUP) {
        throw new RuntimeException("Only approved reservations can be fulfilled.");
    }

    return reservation;
}

private boolean isVisibleInPharmacyPendingQueue(MedicineReservation reservation) {
    // Prescription-required reservations stay in the prescription review queue until the
    // prescription itself is approved. After that they should appear in Pending Reservations
    // so the pharmacy can approve/reject the reservation normally.
    if (reservation.isPrescriptionRequired()) {
    boolean visibleInPending = reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.APPROVED;
    System.out.println("[QUEUE] reservationId=" + reservation.getId()
        + ", prescriptionRequired=true, prescriptionReviewStatus=" + reservation.getPrescriptionReviewStatus()
        + ", queue=" + (visibleInPending ? "pending_reservations" : "prescription_review"));
    return visibleInPending;
    }
    System.out.println("[QUEUE] reservationId=" + reservation.getId()
            + ", prescriptionRequired=false, queue=pending_reservations");
    return true;
}

    @Override
    public void holdInventoryForApprovedPrescription(MedicineReservation reservation) {
        if (reservation == null) {
            return;
        }
        System.out.println("[STOCK] Holding stock on prescription approval: reservationId="
                + reservation.getId() + ", medicine=" + reservation.getMedicineName()
                + ", qty=" + reservation.getRequestedQuantity());
        holdInventoryOrThrow(reservation);
        reservationRepository.save(reservation);
    }

}