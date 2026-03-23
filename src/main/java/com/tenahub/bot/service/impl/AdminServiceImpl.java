package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.MedicineReservationStatus;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.entity.PharmacyRegistration;
import com.tenahub.bot.repository.MedicineReservationRepository;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyRegistrationRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final PharmacyRegistrationRepository registrationRepository;
    private final PharmacyRepository pharmacyRepository;
    private final MedicineReservationRepository reservationRepository;
    private final PharmacyInventoryRepository pharmacyInventoryRepository;

    private static final int DETAIL_LIMIT = 3;
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

    @Override
    public String viewPendingRegistrations() {

        List<PharmacyRegistration> list = registrationRepository.findByStatusOrderByIdDesc("PENDING");

        if (list.isEmpty()) {
            return "🆕 No pending registrations.";
        }

        StringBuilder sb = new StringBuilder("🆕 <b>Pending Registrations</b>\n\n");

        for (PharmacyRegistration reg : list) {
            sb.append("🆔 ").append(reg.getId()).append("\n")
              .append("🏥 ").append(nullSafe(reg.getName())).append("\n")
              .append("🏙️ ").append(nullSafe(reg.getCity())).append("\n")
              .append("📍 ").append(nullSafe(reg.getArea())).append("\n")
              .append("📞 ").append(nullSafe(reg.getPhone())).append("\n")
              .append("💊 ").append(nullSafe(reg.getMedicines())).append("\n")
              .append("🕒 Open: ").append(nullSafe(reg.getOpenTime())).append("\n")
              .append("🌙 Close: ").append(nullSafe(reg.getCloseTime())).append("\n")
              .append("👤 Telegram ID: ").append(reg.getTelegramId()).append("\n")
              .append("📄 License: ").append(reg.getLicenseFileId() == null ? "Not uploaded" : "Uploaded").append("\n\n");
        }

        return sb.toString().trim();
    }

    @Override
    public String viewPendingLicenseUpdates() {

        List<Pharmacy> list = pharmacyRepository.findByLicenseUpdateStatusOrderByIdDesc("PENDING");

        if (list.isEmpty()) {
            return "📄 No pending license updates.";
        }

        StringBuilder sb = new StringBuilder("📄 <b>Pending License Updates</b>\n\n");

        for (Pharmacy pharmacy : list) {
            sb.append("🆔 ").append(pharmacy.getId()).append("\n")
              .append("🏥 ").append(nullSafe(pharmacy.getName())).append("\n")
              .append("🏙️ ").append(nullSafe(pharmacy.getCity())).append("\n")
              .append("📍 ").append(nullSafe(pharmacy.getArea())).append("\n")
              .append("📞 ").append(nullSafe(pharmacy.getPhone())).append("\n")
              .append("👤 Telegram ID: ").append(pharmacy.getTelegramId()).append("\n")
              .append("📄 Pending License: ")
              .append(pharmacy.getPendingLicenseFileId() == null ? "Missing" : "Uploaded")
              .append("\n\n");
        }

        return sb.toString().trim();
    }

    @Override
    public String viewReservationOversight() {

        List<MedicineReservation> pending = reservationRepository.findByStatusOrderByCreatedAtDesc(MedicineReservationStatus.PENDING);
        List<MedicineReservation> approved = reservationRepository.findByStatusOrderByCreatedAtDesc(MedicineReservationStatus.APPROVED);
        List<MedicineReservation> fulfilled = reservationRepository.findByStatusOrderByCreatedAtDesc(MedicineReservationStatus.FULFILLED);
        List<MedicineReservation> rejected = reservationRepository.findByStatusOrderByCreatedAtDesc(MedicineReservationStatus.REJECTED);
        List<MedicineReservation> expired = reservationRepository.findByStatusOrderByCreatedAtDesc(MedicineReservationStatus.EXPIRED);

        StringBuilder sb = new StringBuilder("📦 <b>Reservation Oversight</b>\n\n")
                .append("⏳ Pending: ").append(pending.size()).append("\n")
                .append("✅ Approved: ").append(approved.size()).append("\n")
                .append("📦 Fulfilled: ").append(fulfilled.size()).append("\n")
                .append("❌ Rejected: ").append(rejected.size()).append("\n")
                .append("⌛ Expired: ").append(expired.size()).append("\n\n");

        if (!pending.isEmpty()) {
            sb.append("<b>Latest Pending</b>\n");
            appendReservations(sb, pending, 5);
        }

        if (!approved.isEmpty()) {
            sb.append("\n<b>Latest Approved</b>\n");
            appendReservations(sb, approved, 5);
        }

        return sb.toString().trim();
    }

    @Override
    public String viewSystemSummary() {

        long totalPharmacies = pharmacyRepository.count();
        long approvedPharmacies = pharmacyRepository.countByApprovedTrue();
        long pendingRegistrations = registrationRepository.countByStatus("PENDING");
        long pendingLicenseUpdates = pharmacyRepository.countByLicenseUpdateStatus("PENDING");

        long totalReservations = reservationRepository.count();
        long pendingReservations = reservationRepository.countByStatus(MedicineReservationStatus.PENDING);
        long approvedReservations = reservationRepository.countByStatus(MedicineReservationStatus.APPROVED);
        long fulfilledReservations = reservationRepository.countByStatus(MedicineReservationStatus.FULFILLED);
        long rejectedReservations = reservationRepository.countByStatus(MedicineReservationStatus.REJECTED);
        long expiredReservations = reservationRepository.countByStatus(MedicineReservationStatus.EXPIRED);

        return "📊 <b>System Summary</b>\n\n"
                + "🏥 Total Pharmacies: " + totalPharmacies + "\n"
                + "✅ Approved Pharmacies: " + approvedPharmacies + "\n"
                + "🆕 Pending Registrations: " + pendingRegistrations + "\n"
                + "📄 Pending License Updates: " + pendingLicenseUpdates + "\n\n"
                + "📦 Total Reservations: " + totalReservations + "\n"
                + "⏳ Pending Reservations: " + pendingReservations + "\n"
                + "✅ Approved Reservations: " + approvedReservations + "\n"
                + "📦 Fulfilled Reservations: " + fulfilledReservations + "\n"
                + "❌ Rejected Reservations: " + rejectedReservations + "\n"
                + "⌛ Expired Reservations: " + expiredReservations;
    }

    @Override
    public String viewDetailedReservationOversight() {

        List<MedicineReservation> pending = reservationRepository.findTop10ByStatusOrderByCreatedAtDesc(MedicineReservationStatus.PENDING);
        List<MedicineReservation> approved = reservationRepository.findTop10ByStatusOrderByCreatedAtDesc(MedicineReservationStatus.APPROVED);
        List<MedicineReservation> fulfilled = reservationRepository.findTop10ByStatusOrderByCreatedAtDesc(MedicineReservationStatus.FULFILLED);
        List<MedicineReservation> rejected = reservationRepository.findTop10ByStatusOrderByCreatedAtDesc(MedicineReservationStatus.REJECTED);
        List<MedicineReservation> expired = reservationRepository.findTop10ByStatusOrderByCreatedAtDesc(MedicineReservationStatus.EXPIRED);

        StringBuilder sb = new StringBuilder("📦 <b>Reservation Oversight</b>\n\n");

        sb.append("⏳ <b>Pending Reservations: </b>")
          .append(reservationRepository.countByStatus(MedicineReservationStatus.PENDING))
          .append("\n");
        appendReservations(sb, pending, DETAIL_LIMIT);

        sb.append("\n✅ <b>Approved Reservations: </b>")
          .append(reservationRepository.countByStatus(MedicineReservationStatus.APPROVED))
          .append("\n");
        appendReservations(sb, approved, DETAIL_LIMIT);

        sb.append("\n📦 <b>Fulfilled Reservations: </b>")
          .append(reservationRepository.countByStatus(MedicineReservationStatus.FULFILLED))
          .append("\n");
        appendReservations(sb, fulfilled, DETAIL_LIMIT);

        sb.append("\n❌ <b>Rejected Reservations: </b>")
          .append(reservationRepository.countByStatus(MedicineReservationStatus.REJECTED))
          .append("\n");
        appendReservations(sb, rejected, DETAIL_LIMIT);

        sb.append("\n⌛ <b>Expired Reservations: </b>")
          .append(reservationRepository.countByStatus(MedicineReservationStatus.EXPIRED))
          .append("\n");
        appendReservations(sb, expired, DETAIL_LIMIT);

        return sb.toString().trim();
    }

    @Override
public String viewDetailedSystemSummary() {

    List<Pharmacy> allPharmacies = pharmacyRepository.findTop10ByOrderByIdDesc();
    List<Pharmacy> approvedPharmacies = pharmacyRepository.findTop10ByApprovedTrueOrderByIdDesc();
    List<Pharmacy> pendingLicenseUpdates = pharmacyRepository.findTop10ByLicenseUpdateStatusOrderByIdDesc("PENDING");
    List<PharmacyRegistration> pendingRegistrations = registrationRepository.findTop10ByStatusOrderByIdDesc("PENDING");
    List<MedicineReservation> latestReservations = reservationRepository.findTop10ByOrderByCreatedAtDesc();

    List<PharmacyInventory> lowStockItems =
            pharmacyInventoryRepository.findTop10ByOutOfStockFalseAndQuantityLessThanEqualOrderByQuantityAsc(10);

    LocalDateTime now = LocalDateTime.now();

    long totalPharmacies = pharmacyRepository.count();
    long approvedPharmacyCount = pharmacyRepository.countByApprovedTrue();
    long pendingRegistrationCount = registrationRepository.countByStatus("PENDING");
    long pendingLicenseCount = pharmacyRepository.countByLicenseUpdateStatus("PENDING");

    long totalReservations = reservationRepository.count();
    long pendingReservations = reservationRepository.countByStatus(MedicineReservationStatus.PENDING);
    long approvedReservations = reservationRepository.countByStatus(MedicineReservationStatus.APPROVED);
    long fulfilledReservations = reservationRepository.countByStatus(MedicineReservationStatus.FULFILLED);
    long rejectedReservations = reservationRepository.countByStatus(MedicineReservationStatus.REJECTED);
    long expiredReservations = reservationRepository.countByStatus(MedicineReservationStatus.EXPIRED);

    long todayCount = reservationRepository.countByCreatedAtAfter(now.minusDays(1));
    long weekCount = reservationRepository.countByCreatedAtAfter(now.minusDays(7));
    long monthCount = reservationRepository.countByCreatedAtAfter(now.minusDays(30));

    double fulfillmentRate = totalReservations == 0 ? 0.0 : (fulfilledReservations * 100.0 / totalReservations);
    double rejectionRate = totalReservations == 0 ? 0.0 : (rejectedReservations * 100.0 / totalReservations);
    double expiryRate = totalReservations == 0 ? 0.0 : (expiredReservations * 100.0 / totalReservations);

    StringBuilder sb = new StringBuilder("📊 <b>System Summary</b>\n\n");

    sb.append("🏥 <b>Pharmacies</b>\n")
      .append("• Total: ").append(totalPharmacies).append("\n")
      .append("• Approved: ").append(approvedPharmacyCount).append("\n")
      .append("• Pending registrations: ").append(pendingRegistrationCount).append("\n")
      .append("• Pending license updates: ").append(pendingLicenseCount).append("\n");

    sb.append("\n📦 <b>Reservations</b>\n")
      .append("• Total: ").append(totalReservations).append("\n")
      .append("• Pending: ").append(pendingReservations).append("\n")
      .append("• Approved: ").append(approvedReservations).append("\n")
      .append("• Fulfilled: ").append(fulfilledReservations).append("\n")
      .append("• Rejected: ").append(rejectedReservations).append("\n")
      .append("• Expired: ").append(expiredReservations).append("\n");

    sb.append("\n📅 <b>Activity</b>\n")
      .append("• Today: ").append(todayCount).append("\n")
      .append("• This week: ").append(weekCount).append("\n")
      .append("• This month: ").append(monthCount).append("\n");

    sb.append("\n📈 <b>Performance</b>\n")
      .append("• Fulfillment rate: ").append(String.format("%.1f", fulfillmentRate)).append("%\n")
      .append("• Rejection rate: ").append(String.format("%.1f", rejectionRate)).append("%\n")
      .append("• Expiry rate: ").append(String.format("%.1f", expiryRate)).append("%\n");

    sb.append("\n💊 <b>Top Requested Medicines</b>\n");
    appendTopMedicines(sb, reservationRepository.findTopRequestedMedicines());

    sb.append("\n⚠️ <b>Low Stock Items</b>\n");
    appendLowStockList(sb, lowStockItems);

    sb.append("\n🆕 <b>Pending Registrations</b>\n");
    appendRegistrationList(sb, pendingRegistrations);

    sb.append("\n📄 <b>Pending License Updates</b>\n");
    appendPendingLicenseList(sb, pendingLicenseUpdates);

    sb.append("\n🏥 <b>Latest Pharmacies</b>\n");
    appendPharmacyList(sb, allPharmacies, 3);

    sb.append("\n✅ <b>Latest Approved Pharmacies</b>\n");
    appendPharmacyList(sb, approvedPharmacies, 3);

    sb.append("\n🕒 <b>Latest Reservations</b>\n");
    appendReservations(sb, latestReservations, 3);

    return sb.toString().trim();
}
    private void appendReservations(StringBuilder sb,
                                    List<MedicineReservation> list,
                                    int limit) {
        if (list == null || list.isEmpty()) {
            sb.append("• None\n");
            return;
        }

        int size = Math.min(list.size(), limit);

        for (int i = 0; i < size; i++) {
            MedicineReservation r = list.get(i);

            sb.append("• #").append(r.getId())
              .append(" — ").append(nullSafe(r.getMedicineName()))
              .append(" x").append(r.getRequestedQuantity())
              .append(" — ").append(nullSafe(r.getCustomerName()))
              .append(" — ").append(nullSafe(r.getCustomerPhone()))
              .append(" — ").append(r.getStatus());

            if (r.getCreatedAt() != null) {
                sb.append(" — ").append(r.getCreatedAt().format(FORMATTER));
            }

            if (r.getExpiresAt() != null) {
                sb.append(" — hold until ").append(r.getExpiresAt().format(FORMATTER));
            }

            if (r.getRejectionReason() != null && !r.getRejectionReason().isBlank()) {
                sb.append(" — reason: ").append(r.getRejectionReason());
            }

            sb.append("\n");
        }

        if (list.size() > limit) {
            sb.append("...and ").append(list.size() - limit).append(" more\n");
        }
    }

    private void appendPharmacyList(StringBuilder sb, List<Pharmacy> list, int limit) {
    if (list == null || list.isEmpty()) {
        sb.append("• None\n");
        return;
    }

    int size = Math.min(list.size(), limit);

    for (int i = 0; i < size; i++) {
        Pharmacy p = list.get(i);

        sb.append("• ")
          .append(nullSafe(p.getName()))
          .append(" — ")
          .append(nullSafe(p.getCity()))
          .append(", ")
          .append(nullSafe(p.getArea()))
          .append(" — ")
          .append(nullSafe(p.getPhone()))
          .append(" — ")
          .append(p.isApproved() ? "Approved ✅" : "Not Approved")
          .append("\n");
    }

    if (list.size() > limit) {
        sb.append("...and ").append(list.size() - limit).append(" more\n");
    }
}

    private void appendPendingLicenseList(StringBuilder sb, List<Pharmacy> list) {
        if (list == null || list.isEmpty()) {
            sb.append("• None\n");
            return;
        }

        for (Pharmacy p : list) {
            sb.append("• #").append(p.getId())
              .append(" — ").append(nullSafe(p.getName()))
              .append(" — ").append(nullSafe(p.getCity()))
              .append(", ").append(nullSafe(p.getArea()))
              .append(" — ").append(nullSafe(p.getPhone()))
              .append(" — Telegram: ").append(p.getTelegramId())
              .append("\n");
        }
    }
@Override
public String viewPharmacyDetails() {
    List<Pharmacy> pharmacies = pharmacyRepository.findTop10ByOrderByIdDesc();

    StringBuilder sb = new StringBuilder("🏥 <b>Pharmacy Details</b>\n\n");

    if (pharmacies == null || pharmacies.isEmpty()) {
        sb.append("• None");
        return sb.toString();
    }

    for (Pharmacy p : pharmacies) {
        sb.append("• ")
          .append(nullSafe(p.getName()))
          .append(" — ")
          .append(nullSafe(p.getCity()))
          .append(", ")
          .append(nullSafe(p.getArea()))
          .append(" — ")
          .append(nullSafe(p.getPhone()))
          .append(" — Telegram: ")
          .append(p.getTelegramId())
          .append(" — ")
          .append(p.isApproved() ? "Approved ✅" : "Not Approved")
          .append("\n");
    }

    return sb.toString().trim();
}
@Override
public String viewTopMedicinesDetails() {
    List<Object[]> rows = reservationRepository.findTopRequestedMedicines();

    StringBuilder sb = new StringBuilder("💊 <b>Top Requested Medicines</b>\n\n");

    if (rows == null || rows.isEmpty()) {
        sb.append("• None");
        return sb.toString();
    }

    int rank = 1;
    for (Object[] row : rows.stream().limit(20).toList()) {
        String medicine = row[0] == null ? "N/A" : row[0].toString();
        long count = row[1] == null ? 0L : ((Number) row[1]).longValue();

        sb.append(rank++)
          .append(". ")
          .append(medicine)
          .append(" — ")
          .append(count)
          .append("\n");
    }

    return sb.toString().trim();
}
@Override
public String viewLowStockDetails() {
    List<PharmacyInventory> items =
            pharmacyInventoryRepository.findTop10ByOutOfStockFalseAndQuantityLessThanEqualOrderByQuantityAsc(10);

    StringBuilder sb = new StringBuilder("⚠️ <b>Low Stock Details</b>\n\n");

    if (items == null || items.isEmpty()) {
        sb.append("• None");
        return sb.toString();
    }

    for (PharmacyInventory item : items) {
        sb.append("• Pharmacy ID ")
          .append(item.getPharmacyId())
          .append(" — ")
          .append(nullSafe(item.getMedicineName()))
          .append(" — qty: ")
          .append(item.getQuantity() == null ? 0 : item.getQuantity())
          .append("\n");
    }

    return sb.toString().trim();
}
@Override
public String viewReservationsByStatus(MedicineReservationStatus status) {

    List<MedicineReservation> list = reservationRepository.findByStatusOrderByCreatedAtDesc(status);

    StringBuilder sb = new StringBuilder("📦 <b>")
            .append(status.name())
            .append(" Reservations</b>\n\n");

    if (list == null || list.isEmpty()) {
        sb.append("• None");
        return sb.toString();
    }

    int limit = Math.min(list.size(), 20);

    for (int i = 0; i < limit; i++) {
        MedicineReservation r = list.get(i);

        sb.append("• #").append(r.getId())
          .append(" — ").append(nullSafe(r.getMedicineName()))
          .append(" x").append(r.getRequestedQuantity())
          .append(" — ").append(nullSafe(r.getCustomerName()))
          .append(" — ").append(nullSafe(r.getCustomerPhone()))
          .append(" — ").append(r.getStatus());

        if (r.getCreatedAt() != null) {
            sb.append(" — ").append(r.getCreatedAt().format(FORMATTER));
        }

        if (r.getExpiresAt() != null) {
            sb.append(" — hold until ").append(r.getExpiresAt().format(FORMATTER));
        }

        if (r.getRejectionReason() != null && !r.getRejectionReason().isBlank()) {
            sb.append(" — reason: ").append(r.getRejectionReason());
        }

        sb.append("\n");
    }

    if (list.size() > limit) {
        sb.append("\n...and ").append(list.size() - limit).append(" more");
    }

    return sb.toString().trim();
}
    private void appendRegistrationList(StringBuilder sb, List<PharmacyRegistration> list) {
        if (list == null || list.isEmpty()) {
            sb.append("• None\n");
            return;
        }

        for (PharmacyRegistration r : list) {
            sb.append("• #").append(r.getId())
              .append(" — ").append(nullSafe(r.getName()))
              .append(" — ").append(nullSafe(r.getCity()))
              .append(", ").append(nullSafe(r.getArea()))
              .append(" — ").append(nullSafe(r.getPhone()))
              .append(" — Telegram: ").append(r.getTelegramId())
              .append("\n");
        }
    }

    private void appendTopMedicines(StringBuilder sb, List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) {
            sb.append("• None\n");
            return;
        }

        int rank = 1;

        for (Object[] row : rows.stream().limit(10).toList()) {
            String medicine = row[0] == null ? "N/A" : row[0].toString();
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();

            sb.append(rank++)
              .append(". ")
              .append(medicine)
              .append(" — ")
              .append(count)
              .append("\n");
        }
    }

    private void appendLowStockList(StringBuilder sb, List<PharmacyInventory> items) {
        if (items == null || items.isEmpty()) {
            sb.append("• None\n");
            return;
        }

        for (PharmacyInventory item : items) {
            sb.append("• Pharmacy ID ")
              .append(item.getPharmacyId())
              .append(" — ")
              .append(nullSafe(item.getMedicineName()))
              .append(" — qty: ")
              .append(item.getQuantity() == null ? 0 : item.getQuantity())
              .append("\n");
        }
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }

    @Override
    public List<PharmacyRegistration> getPendingRegistrations() {
        return registrationRepository.findByStatusOrderByIdDesc("PENDING");
    }

    @Override
    public List<Pharmacy> getPendingLicenseUpdates() {
        return pharmacyRepository.findByLicenseUpdateStatusOrderByIdDesc("PENDING");
    }

    @Override
    public Page<PharmacyRegistration> getPendingRegistrationsPage(int page, int size) {
        return registrationRepository.findByStatusOrderByIdDesc("PENDING", PageRequest.of(page, size));
    }

    @Override
    public Page<Pharmacy> getPendingLicenseUpdatesPage(int page, int size) {
        return pharmacyRepository.findByLicenseUpdateStatusOrderByIdDesc("PENDING", PageRequest.of(page, size));
    }
}