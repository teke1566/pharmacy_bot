package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.MedicineReservationStatus;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.repository.MedicineReservationRepository;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.ReservationService;
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

    private static final int APPROVED_HOLD_MINUTES = 60;

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

    String normalizedMedicine = medicineName.trim().toLowerCase();

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
            .createdAt(LocalDateTime.now())
            .customerPhone(customerPhone)
            .customerName(customerName)
            .build();

    return reservationRepository.save(reservation);
}

    @Override
    public MedicineReservation approveReservation(Long reservationId) {
        MedicineReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        if (reservation.getStatus() != MedicineReservationStatus.PENDING) {
            throw new RuntimeException("Only pending reservations can be approved.");
        }

        var inventory = inventoryRepository
                .findByPharmacyIdAndMedicineNameIgnoreCase(
                        reservation.getPharmacyId(),
                        reservation.getMedicineName()
                )
                .orElseThrow(() -> new RuntimeException("Medicine inventory not found"));

        Integer availableQty = inventory.getQuantity() == null ? 0 : inventory.getQuantity();

        if (inventory.isOutOfStock() || availableQty <= 0) {
            throw new RuntimeException("Medicine is currently out of stock.");
        }

        if (reservation.getRequestedQuantity() > availableQty) {
            throw new RuntimeException("Not enough stock to approve this reservation.");
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

        reservation.setStatus(MedicineReservationStatus.REJECTED);
        reservation.setRejectionReason(reason);
        return reservationRepository.save(reservation);
    }

    @Override
    public MedicineReservation fulfillReservation(Long reservationId) {
        MedicineReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        if (reservation.getStatus() != MedicineReservationStatus.APPROVED) {
            throw new RuntimeException("Only approved reservations can be fulfilled.");
        }

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

        reservation.setStatus(MedicineReservationStatus.FULFILLED);
        reservation.setFulfilledAt(LocalDateTime.now());

        return reservationRepository.save(reservation);
    }

    @Override
    public MedicineReservation expireReservation(Long reservationId) {
        MedicineReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        if (reservation.getStatus() != MedicineReservationStatus.APPROVED) {
            throw new RuntimeException("Only approved reservations can expire.");
        }

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

        reservation.setStatus(MedicineReservationStatus.CANCELLED);
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
        return "📜 <b>Reservation History</b>\n\nNo reservations found.";
    }

    StringBuilder sb = new StringBuilder("📜 <b>Reservation History</b>\n\n");

    appendReservationSection(sb, "⏳ Pending", reservations, MedicineReservationStatus.PENDING);
    appendReservationSection(sb, "✅ Approved", reservations, MedicineReservationStatus.APPROVED);
    appendReservationSection(sb, "📦 Fulfilled", reservations, MedicineReservationStatus.FULFILLED);
    appendReservationSection(sb, "❌ Cancelled", reservations, MedicineReservationStatus.CANCELLED);
    appendReservationSection(sb, "⌛ Expired", reservations, MedicineReservationStatus.EXPIRED);
    appendReservationSection(sb, "🚫 Rejected", reservations, MedicineReservationStatus.REJECTED);

    return sb.toString().trim();
}
    @Override
    public String buildUserReservationStatusLabel(MedicineReservation reservation) {
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

    appendReservationSection(sb, "⏳ Pending", reservations, MedicineReservationStatus.PENDING);
    appendReservationSection(sb, "✅ Approved", reservations, MedicineReservationStatus.APPROVED);

    return sb.toString().trim();
}
private void refreshExpiredReservationsForUser(Long userId) {
    List<MedicineReservation> reservations = reservationRepository.findByUserIdOrderByCreatedAtDesc(userId);

    for (MedicineReservation reservation : reservations) {
        if (reservation.getStatus() == MedicineReservationStatus.APPROVED
                && reservation.getExpiresAt() != null
                && !reservation.getExpiresAt().isAfter(LocalDateTime.now())) {

            reservation.setStatus(MedicineReservationStatus.EXPIRED);
            reservationRepository.save(reservation);
        }
    }
}

private void appendReservationSection(StringBuilder sb,
                                      String title,
                                      List<MedicineReservation> reservations,
                                      MedicineReservationStatus status) {
    List<MedicineReservation> filtered = reservations.stream()
            .filter(r -> r.getStatus() == status)
            .toList();

    if (filtered.isEmpty()) {
        return;
    }

    sb.append("<b>").append(title).append("</b>\n\n");

    for (MedicineReservation r : filtered) {
        Pharmacy pharmacy = pharmacyRepository.findById(r.getPharmacyId()).orElse(null);
        String pharmacyName = pharmacy != null ? pharmacy.getName() : "Unknown Pharmacy";

        sb.append("🆔 ").append(r.getId()).append("\n")
                .append("🏥 ").append(pharmacyName).append("\n")
                .append("💊 ").append(r.getMedicineName()).append("\n")
                .append("🔢 Qty: ").append(r.getRequestedQuantity()).append("\n")
                .append("📌 ").append(buildUserReservationStatusLabel(r)).append("\n");

        if (r.getStatus() == MedicineReservationStatus.APPROVED && r.getExpiresAt() != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d, h:mm a");
            sb.append("⏳ Hold Until: ").append(r.getExpiresAt().format(formatter)).append("\n");
        }

        if (r.getStatus() == MedicineReservationStatus.REJECTED
                && r.getRejectionReason() != null
                && !r.getRejectionReason().isBlank()) {
            sb.append("❌ Reason: ").append(r.getRejectionReason()).append("\n");
        }

        sb.append("\n");
    }
}
@Override
public List<MedicineReservation> getPendingReservations(Long pharmacyTelegramId) {
    Pharmacy pharmacy = pharmacyRepository.findByTelegramId(pharmacyTelegramId)
            .orElseThrow(() -> new RuntimeException("Pharmacy not found"));

    return reservationRepository.findByPharmacyIdAndStatus(pharmacy.getId(), MedicineReservationStatus.PENDING)
            .stream()
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
}