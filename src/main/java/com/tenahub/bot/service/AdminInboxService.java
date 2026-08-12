package com.tenahub.bot.service;

import com.tenahub.bot.entity.AdminInboxItem;
import com.tenahub.bot.entity.AdminInboxItemStatus;
import com.tenahub.bot.entity.AdminInboxItemType;

import java.util.List;

public interface AdminInboxService {

    record InboxCounts(long newCount, long inReviewCount, long resolvedCount) {}

    void createFeedbackItem(Long userTelegramId, String messageText);

    void createIssueItem(Long userTelegramId,
                         Long pharmacyId,
                         String medicineName,
                         String issueType,
                         String messageText);

    InboxCounts getCounts();

    List<AdminInboxItem> listByStatusAndType(AdminInboxItemStatus status, AdminInboxItemType type);

    List<AdminInboxItem> listOpen(AdminInboxItemType type);

    AdminInboxItem getById(Long id);

    AdminInboxItem markInReview(Long id);

    AdminInboxItem markResolved(Long id);
}
