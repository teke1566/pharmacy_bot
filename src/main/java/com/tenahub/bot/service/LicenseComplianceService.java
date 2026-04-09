package com.tenahub.bot.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface LicenseComplianceService {

    String CATEGORY_EXPIRING_SOON = "expiring_soon";
    String CATEGORY_EXPIRED = "expired";
    String CATEGORY_MISSING_LICENSE = "missing_license";
    String CATEGORY_PENDING_REVIEW = "pending_review";
    String CATEGORY_SUSPENDED = "suspended";

    ComplianceSummary buildSummary();

    List<ComplianceListItem> listByCategory(String category);

    ComplianceDetail getDetail(Long pharmacyId);

    ComplianceDetail notifyPharmacy(Long pharmacyId, Long adminTelegramId);

    ComplianceDetail suspendForCompliance(Long pharmacyId, Long adminTelegramId);

    ComplianceDetail unsuspendForCompliance(Long pharmacyId, Long adminTelegramId);

    ComplianceDetail extendGracePeriod(Long pharmacyId, int days, Long adminTelegramId);

    ComplianceDetail clearComplianceIssue(Long pharmacyId, Long adminTelegramId);

    record ComplianceSummary(
            long expiringSoon,
            long expired,
            long missingLicense,
            long pendingReview,
            long suspended
    ) {}

    record ComplianceListItem(
            Long pharmacyId,
            String pharmacyName,
            Long telegramId,
            String status,
            LocalDate licenseExpiryDate
    ) {}

    record ComplianceDetail(
            Long pharmacyId,
            String pharmacyName,
            Long telegramId,
            String phone,
            String city,
            String area,
            String status,
            LocalDate licenseExpiryDate,
            String activeLicenseFileId,
            String pendingLicenseFileId,
            String licenseUpdateStatus,
            LocalDate gracePeriodUntil,
            String lastComplianceAction,
            LocalDateTime lastComplianceActionAt
    ) {}
}
