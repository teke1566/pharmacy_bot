package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.MedicineReservationStatus;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PrescriptionReviewStatus;
import com.tenahub.bot.repository.MedicineReservationRepository;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.ReservationService;
import com.tenahub.bot.util.LocalizationService;
import com.tenahub.bot.util.MedicineSearchNormalizer;
import com.tenahub.bot.util.TelegramClient;
import lombok.RequiredArgsConstructor;
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

    private static final int APPROVED_HOLD_MINUTES = 60;

    private void releaseHeldInventory(MedicineReservation reservation) {
        if (reservation == null || !reservation.isInventoryHeld()) {
            return;
        }

        var inventory = inventoryRepository
                .findByPharmacyIdAndMedicineNameIgnoreCase(
                        reservation.getPharmacyId(),
                        reservation.getMedicineName()
                )
                .orElse(null);

        if (inventory != null) {
            int currentQty = inventory.getQuantity() == null ? 0 : inventory.getQuantity();
            int releaseQty = reservation.getRequestedQuantity() == null ? 0 : reservation.getRequestedQuantity();
            int newQty = currentQty + Math.max(releaseQty, 0);

            inventory.setQuantity(newQty);
            inventory.setOutOfStock(newQty <= 0);
            inventoryRepository.save(inventory);
        }

        reservation.setInventoryHeld(false);
    }

    private void holdInventoryOrThrow(MedicineReservation reservation) {
        var inventory = inventoryRepository
                .findByPharmacyIdAndMedicineNameIgnoreCase(
                        reservation.getPharmacyId(),
                        reservation.getMedicineName()
                )
                .orElseThrow(() -> new RuntimeException("Medicine inventory not found"));

        int availableQty = inventory.getQuantity() == null ? 0 : inventory.getQuantity();
        int requiredQty = reservation.getRequestedQuantity() == null ? 0 : reservation.getRequestedQuantity();

        if (inventory.isOutOfStock() || availableQty <= 0) {
            throw new RuntimeException("Medicine is currently out of stock.");
        }

        if (requiredQty > availableQty) {
            throw new RuntimeException("Requested quantity exceeds available stock.");
        }

        int newQty = availableQty - requiredQty;
        inventory.setQuantity(Math.max(newQty, 0));
        inventory.setOutOfStock(newQty <= 0);
        inventoryRepository.save(inventory);

        reservation.setInventoryHeld(true);
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

    Integer availableQty = inventory.getQuantity() == null ? 0 : inventory.getQuantity();

    if (inventory.isOutOfStock() || availableQty <= 0) {
        throw new RuntimeException("Medicine is currently out of stock.");
    }

    if (requestedQuantity > availableQty) {
        throw new RuntimeException("Requested quantity exceeds available stock.");
    }

    MedicineReservation reservation = MedicineReservation.builder()
            .userId(userId)
            .pharmacyId(pharmacyId)
            .medicineName(normalizedMedicine)
            .requestedQuantity(requestedQuantity)
            .status(MedicineReservationStatus.PENDING)
            .prescriptionRequired(inventory.isRequiresPrescription())
            .prescriptionReviewStatus(inventory.isRequiresPrescription()
                ? PrescriptionReviewStatus.PRESCRIPTION_UPLOAD_REQUIRED
                    : PrescriptionReviewStatus.NOT_REQUIRED)
            .createdAt(LocalDateTime.now())
            .customerPhone(customerPhone)
            .customerName(customerName)
                .inventoryHeld(false)
            .build();

            holdInventoryOrThrow(reservation);

    return reservationRepository.save(reservation);
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

            Integer availableQty = inventory.getQuantity() == null ? 0 : inventory.getQuantity();

            if (inventory.isOutOfStock() || availableQty <= 0) {
                throw new RuntimeException("Medicine is currently out of stock: " + medicineName);
            }

            if (requestedQuantity > availableQty) {
                throw new RuntimeException("Requested quantity exceeds available stock for: " + medicineName);
            }

            MedicineReservation reservation = MedicineReservation.builder()
                    .userId(userId)
                    .pharmacyId(pharmacyId)
                    .medicineName(normalizedMedicine)
                    .requestedQuantity(requestedQuantity)
                    .status(MedicineReservationStatus.PENDING)
                    .prescriptionRequired(inventory.isRequiresPrescription())
                    .prescriptionReviewStatus(inventory.isRequiresPrescription()
                        ? PrescriptionReviewStatus.PRESCRIPTION_UPLOAD_REQUIRED
                        : PrescriptionReviewStatus.NOT_REQUIRED)
                    .createdAt(java.time.LocalDateTime.now())
                    .customerPhone(customerPhone)
                    .customerName(customerName)
                    .inventoryHeld(false)
                    .reservationGroupId(groupId)
                    .build();

            holdInventoryOrThrow(reservation);

            reservations.add(reservationRepository.save(reservation));
        }

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
            if (reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.PRESCRIPTION_UPLOAD_REQUIRED) {
                throw new RuntimeException("Prescription upload is required before pharmacy review can begin.");
            }
            if (reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.PRESCRIPTION_REJECTED) {
                throw new RuntimeException("Prescription was rejected for this reservation.");
            }
            if (reservation.getPrescriptionReviewStatus() != PrescriptionReviewStatus.PRESCRIPTION_APPROVED) {
                throw new RuntimeException("Prescription review is still pending for this reservation.");
            }
        }

        // Normally inventory is already held on reservation creation.
        // Fallback: if hold flag is missing, try to hold now before approving.
        if (!reservation.isInventoryHeld()) {
            holdInventoryOrThrow(reservation);
        }

        reservation.setStatus(MedicineReservationStatus.APPROVED);
        reservation.setApprovedAt(LocalDateTime.now());
        reservation.setExpiresAt(LocalDateTime.now().plusMinutes(APPROVED_HOLD_MINUTES));

        return reservationRepository.save(reservation);
    }

    @Override
    public MedicineReservation rejectReservation(Long reservationId, String reason) {
        MedicineReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        if (reservation.getStatus() != MedicineReservationStatus.PENDING) {
            throw new RuntimeException("Only pending reservations can be rejected.");
        }

        releaseHeldInventory(reservation);
        reservation.setStatus(MedicineReservationStatus.REJECTED);
        reservation.setRejectionReason(reason);
        return reservationRepository.save(reservation);
    }

    @Override
    public MedicineReservation fulfillReservation(Long reservationId) {
        MedicineReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        return fulfillReservationInternal(reservation);
    }

    @Override
    public MedicineReservation fulfillReservationAndNotify(Long reservationId) {
        MedicineReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        MedicineReservation fulfilledReservation = fulfillReservationInternal(reservation);
        notifyCustomerReservationFulfilled(fulfilledReservation);
        return fulfilledReservation;
    }

    @Override
    public MedicineReservation fulfillReservationAndNotify(Long reservationId, Long pharmacyTelegramId) {
        MedicineReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        validateReservationForPharmacyFulfillment(reservation, pharmacyTelegramId);

        MedicineReservation fulfilledReservation = fulfillReservationInternal(reservation);
        notifyCustomerReservationFulfilled(fulfilledReservation);
        return fulfilledReservation;
    }

    private MedicineReservation fulfillReservationInternal(MedicineReservation reservation) {
        if (reservation == null) {
            throw new RuntimeException("Reservation not found");
        }

        if (reservation.getStatus() != MedicineReservationStatus.APPROVED) {
            throw new RuntimeException("Only approved reservations can be fulfilled.");
        }

        // If hold exists, fulfillment consumes held stock with no further deduction.
        // Fallback for legacy rows: deduct now if not held.
        if (!reservation.isInventoryHeld()) {
            var inventory = inventoryRepository
                .findByPharmacyIdAndMedicineNameIgnoreCase(
                    reservation.getPharmacyId(),
                    reservation.getMedicineName()
                )
                .orElseThrow(() -> new RuntimeException("Medicine inventory not found"));

            Integer currentQty = inventory.getQuantity() == null ? 0 : inventory.getQuantity();
            int newQty = currentQty - reservation.getRequestedQuantity();

            inventory.setQuantity(Math.max(newQty, 0));
            inventory.setOutOfStock(newQty <= 0);
            inventoryRepository.save(inventory);
        }

        reservation.setInventoryHeld(false);
        reservation.setStatus(MedicineReservationStatus.FULFILLED);
        reservation.setFulfilledAt(LocalDateTime.now());

        return reservationRepository.save(reservation);
    }

    private void notifyCustomerReservationFulfilled(MedicineReservation reservation) {
        if (reservation == null || reservation.getUserId() == null) {
            return;
        }

        String medicineName = MedicineSearchNormalizer.toDisplayName(
                reservation.getMedicineName(),
                localizationService.getLanguage(reservation.getUserId())
        );

        telegramClient.sendMessage(
                reservation.getUserId(),
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

        if (reservation.getStatus() != MedicineReservationStatus.APPROVED) {
            throw new RuntimeException("Only approved reservations can expire.");
        }

        releaseHeldInventory(reservation);
        reservation.setStatus(MedicineReservationStatus.EXPIRED);
        return reservationRepository.save(reservation);
    }

    @Override
    public MedicineReservation cancelReservationByUser(Long userId, Long reservationId) {
        MedicineReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        if (!reservation.getUserId().equals(userId)) {
            throw new RuntimeException("You are not allowed to cancel this reservation.");
        }

        if (reservation.getStatus() != MedicineReservationStatus.PENDING
                && reservation.getStatus() != MedicineReservationStatus.APPROVED) {
            throw new RuntimeException("Only pending or approved reservations can be cancelled.");
        }

        releaseHeldInventory(reservation);
        reservation.setStatus(MedicineReservationStatus.CANCELLED);
        return reservationRepository.save(reservation);
    }

    @Override
    public MedicineReservation autoCancelPendingReservation(Long reservationId, String reason) {
        MedicineReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        if (reservation.getStatus() != MedicineReservationStatus.PENDING) {
            throw new RuntimeException("Reservation is no longer pending");
        }

        releaseHeldInventory(reservation);
        reservation.setStatus(MedicineReservationStatus.CANCELLED);
        reservation.setNote(reason == null || reason.isBlank() ? "AUTO_CANCELLED_PENDING_TIMEOUT" : reason);
        return reservationRepository.save(reservation);
    }

  @Override
public List<MedicineReservation> getUserReservations(Long userId) {
    refreshExpiredReservationsForUser(userId);
    return reservationRepository.findByUserIdOrderByCreatedAtDesc(userId);
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
                reservationRepository.findByPharmacyIdAndStatus(
                        pharmacy.getId(),
                        MedicineReservationStatus.APPROVED
                );

        return buildPharmacyReservationList("✅ Approved Reservations", reservations);
    }

    @Override
    public String viewFulfillableReservations(Long pharmacyTelegramId) {
        Pharmacy pharmacy = pharmacyRepository.findByTelegramId(pharmacyTelegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

        List<MedicineReservation> reservations =
                reservationRepository.findByPharmacyIdAndStatus(
                        pharmacy.getId(),
                        MedicineReservationStatus.APPROVED
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
                && reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.PRESCRIPTION_UPLOAD_REQUIRED) {
            return "PENDING • PRESCRIPTION_UPLOAD_REQUIRED";
        }

        if (reservation.isPrescriptionRequired()
                && reservation.getStatus() == MedicineReservationStatus.PENDING
                && reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.PRESCRIPTION_PENDING) {
            return "PENDING • PRESCRIPTION_PENDING";
        }

        if (reservation.isPrescriptionRequired()
                && reservation.getPrescriptionReviewStatus() == PrescriptionReviewStatus.PRESCRIPTION_REJECTED) {
            return "REJECTED • PRESCRIPTION_REJECTED";
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
                    MedicineReservationStatus.APPROVED
            )
    );

    if (reservations == null || reservations.isEmpty()) {
        return "📦 <b>Active Reservations</b>\n\nNo active reservations found.";
    }

    StringBuilder sb = new StringBuilder("📦 <b>Active Reservations</b>\n\n");

    appendReservationSection(sb, chatId, "res_hist_section_pending", reservations, MedicineReservationStatus.PENDING);
    appendReservationSection(sb, chatId, "res_hist_section_approved", reservations, MedicineReservationStatus.APPROVED);

    return sb.toString().trim();
}
private void refreshExpiredReservationsForUser(Long userId) {
    List<MedicineReservation> reservations = reservationRepository.findByUserIdOrderByCreatedAtDesc(userId);

    for (MedicineReservation reservation : reservations) {
        if (reservation.getStatus() == MedicineReservationStatus.APPROVED
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
        case FULFILLED -> "res_status_fulfilled";
        case CANCELLED -> "res_status_cancelled";
        case EXPIRED -> "res_status_expired";
        case REJECTED -> "res_status_rejected";
    };
    String base = localizationService.text(userId, statusKey);
    if (r.getStatus() == MedicineReservationStatus.APPROVED && r.getExpiresAt() != null) {
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
        .filter(this::isVisibleInPharmacyPendingQueue)
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .toList();
}

@Override
public List<MedicineReservation> getApprovedReservations(Long pharmacyTelegramId) {
    Pharmacy pharmacy = pharmacyRepository.findByTelegramId(pharmacyTelegramId)
            .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

    return reservationRepository.findByPharmacyIdAndStatus(pharmacy.getId(), MedicineReservationStatus.APPROVED)
            .stream()
            .filter(r -> r.getExpiresAt() == null || r.getExpiresAt().isAfter(LocalDateTime.now()))
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .toList();
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
            && reservation.getPrescriptionReviewStatus() != PrescriptionReviewStatus.PRESCRIPTION_APPROVED
            && reservation.getStatus() == MedicineReservationStatus.PENDING) {
        return reservation;
    }

    if (reservation.getStatus() == MedicineReservationStatus.APPROVED
            && reservation.getExpiresAt() != null
            && !reservation.getExpiresAt().isAfter(now)) {
        expireReservation(reservation.getId());
        throw new RuntimeException("Reservation is expired.");
    }

    if (reservation.getStatus() != MedicineReservationStatus.APPROVED) {
        throw new RuntimeException("Only approved reservations can be fulfilled.");
    }

    return reservation;
}

private boolean isVisibleInPharmacyPendingQueue(MedicineReservation reservation) {
    return reservation == null
            || !reservation.isPrescriptionRequired()
            || reservation.getPrescriptionReviewStatus() != PrescriptionReviewStatus.PRESCRIPTION_UPLOAD_REQUIRED;
}

}