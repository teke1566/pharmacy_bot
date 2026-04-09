package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.LicenseComplianceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LicenseComplianceServiceImpl implements LicenseComplianceService {

    private final PharmacyRepository pharmacyRepository;

    @Value("${tenahub.license.expiring-soon-days:30}")
    private int expiringSoonDays;

    @Override
    public ComplianceSummary buildSummary() {
        long expiringSoon = listByCategory(CATEGORY_EXPIRING_SOON).size();
        long expired = listByCategory(CATEGORY_EXPIRED).size();
        long missing = listByCategory(CATEGORY_MISSING_LICENSE).size();
        long pending = listByCategory(CATEGORY_PENDING_REVIEW).size();
        long suspended = listByCategory(CATEGORY_SUSPENDED).size();

        return new ComplianceSummary(expiringSoon, expired, missing, pending, suspended);
    }

    @Override
    public List<ComplianceListItem> listByCategory(String category) {
        LocalDate today = LocalDate.now();
        String normalized = normalizeCategory(category);

        List<Pharmacy> source = switch (normalized) {
            case CATEGORY_EXPIRED -> pharmacyRepository.findByLicenseExpiryDateBeforeOrderByIdDesc(today);
            case CATEGORY_MISSING_LICENSE -> pharmacyRepository.findMissingLicenseInfoOrderByIdDesc();
            case CATEGORY_PENDING_REVIEW -> pharmacyRepository.findByLicenseUpdateStatusOrderByIdDesc("PENDING");
            case CATEGORY_SUSPENDED -> pharmacyRepository.findByLicenseSuspendedTrueOrderByIdDesc();
            default -> pharmacyRepository.findByLicenseExpiryDateBetweenOrderByIdDesc(today, today.plusDays(expiringSoonDays));
        };

        return source.stream()
                .filter(p -> normalized.equals(classify(p, today)))
                .sorted(Comparator
                        .comparing(Pharmacy::getLicenseExpiryDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Pharmacy::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(p -> new ComplianceListItem(
                        p.getId(),
                        p.getName(),
                        p.getTelegramId(),
                        formatStatusLabel(p, today),
                        p.getLicenseExpiryDate()
                ))
                .toList();
    }

    @Override
    public ComplianceDetail getDetail(Long pharmacyId) {
        Pharmacy pharmacy = getPharmacy(pharmacyId);
        return toDetail(pharmacy);
    }

    @Override
    public ComplianceDetail notifyPharmacy(Long pharmacyId, Long adminTelegramId) {
        Pharmacy pharmacy = getPharmacy(pharmacyId);
        markComplianceAction(pharmacy, "NOTIFY_PHARMACY", adminTelegramId);
        return toDetail(pharmacyRepository.save(pharmacy));
    }

    @Override
    public ComplianceDetail suspendForCompliance(Long pharmacyId, Long adminTelegramId) {
        Pharmacy pharmacy = getPharmacy(pharmacyId);
        pharmacy.setApproved(false);
        pharmacy.setLicenseSuspended(true);
        markComplianceAction(pharmacy, "SUSPEND_COMPLIANCE", adminTelegramId);
        return toDetail(pharmacyRepository.save(pharmacy));
    }

    @Override
    public ComplianceDetail unsuspendForCompliance(Long pharmacyId, Long adminTelegramId) {
        Pharmacy pharmacy = getPharmacy(pharmacyId);
        pharmacy.setLicenseSuspended(false);
        markComplianceAction(pharmacy, "UNSUSPEND_COMPLIANCE", adminTelegramId);
        return toDetail(pharmacyRepository.save(pharmacy));
    }

    @Override
    public ComplianceDetail extendGracePeriod(Long pharmacyId, int days, Long adminTelegramId) {
        if (days <= 0) {
            throw new RuntimeException("Grace period days must be greater than zero");
        }

        Pharmacy pharmacy = getPharmacy(pharmacyId);
        LocalDate base = pharmacy.getGracePeriodUntil();
        LocalDate today = LocalDate.now();

        if (base == null || base.isBefore(today)) {
            base = today;
        }

        pharmacy.setGracePeriodUntil(base.plusDays(days));
        pharmacy.setLicenseSuspended(false);
        markComplianceAction(pharmacy, "EXTEND_GRACE_" + days + "D", adminTelegramId);
        return toDetail(pharmacyRepository.save(pharmacy));
    }

    @Override
    public ComplianceDetail clearComplianceIssue(Long pharmacyId, Long adminTelegramId) {
        Pharmacy pharmacy = getPharmacy(pharmacyId);

        boolean hasPendingUpdate = "PENDING".equalsIgnoreCase(safe(pharmacy.getLicenseUpdateStatus()))
                && hasText(pharmacy.getPendingLicenseFileId());

        if (hasPendingUpdate) {
            pharmacy.setLicenseFileId(pharmacy.getPendingLicenseFileId());
            pharmacy.setLicenseExpiryDate(pharmacy.getPendingLicenseExpiryDate());
            pharmacy.setPendingLicenseFileId(null);
            pharmacy.setPendingLicenseExpiryDate(null);
            pharmacy.setLicenseUpdateStatus("APPROVED");
        }

        pharmacy.setLicenseSuspended(false);
        pharmacy.setApproved(true);
        markComplianceAction(pharmacy, hasPendingUpdate ? "CLEAR_AND_APPROVE_PENDING_LICENSE" : "CLEAR_COMPLIANCE_ISSUE", adminTelegramId);
        return toDetail(pharmacyRepository.save(pharmacy));
    }

    private String classify(Pharmacy pharmacy, LocalDate today) {
        if (pharmacy.isLicenseSuspended()) {
            return CATEGORY_SUSPENDED;
        }

        if ("PENDING".equalsIgnoreCase(safe(pharmacy.getLicenseUpdateStatus()))) {
            return CATEGORY_PENDING_REVIEW;
        }

        boolean missingLicenseFile = !hasText(pharmacy.getLicenseFileId());
        boolean missingExpiry = pharmacy.getLicenseExpiryDate() == null;
        if (missingLicenseFile || missingExpiry) {
            return CATEGORY_MISSING_LICENSE;
        }

        LocalDate expiry = pharmacy.getLicenseExpiryDate();
        if (expiry.isBefore(today)) {
            if (hasActiveGrace(pharmacy, today)) {
                return CATEGORY_EXPIRING_SOON;
            }
            return CATEGORY_EXPIRED;
        }

        if (!expiry.isAfter(today.plusDays(expiringSoonDays))) {
            return CATEGORY_EXPIRING_SOON;
        }

        return "compliant";
    }

    private String formatStatusLabel(Pharmacy pharmacy, LocalDate today) {
        return switch (classify(pharmacy, today)) {
            case CATEGORY_EXPIRING_SOON -> "Expiring Soon";
            case CATEGORY_EXPIRED -> "Expired";
            case CATEGORY_MISSING_LICENSE -> "Missing License";
            case CATEGORY_PENDING_REVIEW -> "Pending Review";
            case CATEGORY_SUSPENDED -> "Suspended";
            default -> "Compliant";
        };
    }

    private ComplianceDetail toDetail(Pharmacy pharmacy) {
        return new ComplianceDetail(
                pharmacy.getId(),
                pharmacy.getName(),
                pharmacy.getTelegramId(),
                pharmacy.getPhone(),
                pharmacy.getCity(),
                pharmacy.getArea(),
                formatStatusLabel(pharmacy, LocalDate.now()),
                pharmacy.getLicenseExpiryDate(),
                pharmacy.getLicenseFileId(),
                pharmacy.getPendingLicenseFileId(),
                pharmacy.getLicenseUpdateStatus(),
                pharmacy.getGracePeriodUntil(),
                pharmacy.getLastComplianceAction(),
                pharmacy.getLastComplianceActionAt()
        );
    }

    private void markComplianceAction(Pharmacy pharmacy, String action, Long adminTelegramId) {
        pharmacy.setLastComplianceAction(action);
        pharmacy.setLastComplianceActionBy(adminTelegramId);
        pharmacy.setLastComplianceActionAt(LocalDateTime.now());
    }

    private String normalizeCategory(String category) {
        String value = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case CATEGORY_EXPIRING_SOON, "expiring", "soon" -> CATEGORY_EXPIRING_SOON;
            case CATEGORY_EXPIRED -> CATEGORY_EXPIRED;
            case CATEGORY_MISSING_LICENSE, "missing" -> CATEGORY_MISSING_LICENSE;
            case CATEGORY_PENDING_REVIEW, "pending" -> CATEGORY_PENDING_REVIEW;
            case CATEGORY_SUSPENDED, "suspend", "suspended_due_to_compliance" -> CATEGORY_SUSPENDED;
            default -> CATEGORY_EXPIRING_SOON;
        };
    }

    private boolean hasActiveGrace(Pharmacy pharmacy, LocalDate today) {
        return pharmacy.getGracePeriodUntil() != null && !pharmacy.getGracePeriodUntil().isBefore(today);
    }

    private Pharmacy getPharmacy(Long pharmacyId) {
        return pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
