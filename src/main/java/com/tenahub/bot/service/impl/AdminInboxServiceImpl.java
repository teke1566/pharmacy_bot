package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.AdminInboxItem;
import com.tenahub.bot.entity.AdminInboxItemStatus;
import com.tenahub.bot.entity.AdminInboxItemType;
import com.tenahub.bot.repository.AdminInboxItemRepository;
import com.tenahub.bot.service.AdminInboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminInboxServiceImpl implements AdminInboxService {

    private final AdminInboxItemRepository adminInboxItemRepository;

    @Override
    public void createFeedbackItem(Long userTelegramId, String messageText) {
        LocalDateTime now = LocalDateTime.now();
        AdminInboxItem item = AdminInboxItem.builder()
                .type(AdminInboxItemType.FEEDBACK)
                .status(AdminInboxItemStatus.NEW)
                .userTelegramId(userTelegramId)
                .messageText(messageText == null || messageText.isBlank() ? "N/A" : messageText.trim())
                .createdAt(now)
                .updatedAt(now)
                .build();
        adminInboxItemRepository.save(item);
    }

    @Override
    public void createIssueItem(Long userTelegramId,
                                Long pharmacyId,
                                String medicineName,
                                String issueType,
                                String messageText) {
        LocalDateTime now = LocalDateTime.now();
        AdminInboxItem item = AdminInboxItem.builder()
                .type(AdminInboxItemType.ISSUE)
                .status(AdminInboxItemStatus.NEW)
                .userTelegramId(userTelegramId)
                .pharmacyId(pharmacyId)
                .medicineName(medicineName)
                .issueType(issueType)
                .messageText(messageText == null || messageText.isBlank() ? "N/A" : messageText.trim())
                .createdAt(now)
                .updatedAt(now)
                .build();
        adminInboxItemRepository.save(item);
    }

    @Override
    public InboxCounts getCounts() {
        return new InboxCounts(
                adminInboxItemRepository.countByStatus(AdminInboxItemStatus.NEW),
                adminInboxItemRepository.countByStatus(AdminInboxItemStatus.IN_REVIEW),
                adminInboxItemRepository.countByStatus(AdminInboxItemStatus.RESOLVED)
        );
    }

    @Override
    public List<AdminInboxItem> listByStatusAndType(AdminInboxItemStatus status, AdminInboxItemType type) {
        if (type == null) {
            return adminInboxItemRepository.findTop30ByStatusOrderByCreatedAtDesc(status);
        }
        return adminInboxItemRepository.findTop30ByStatusAndTypeOrderByCreatedAtDesc(status, type);
    }

    @Override
    public List<AdminInboxItem> listOpen(AdminInboxItemType type) {
        List<AdminInboxItemStatus> openStatuses = List.of(AdminInboxItemStatus.NEW, AdminInboxItemStatus.IN_REVIEW);
        if (type == null) {
            return adminInboxItemRepository.findTop30ByStatusInOrderByCreatedAtDesc(openStatuses);
        }
        return adminInboxItemRepository.findTop30ByStatusInAndTypeOrderByCreatedAtDesc(openStatuses, type);
    }

    @Override
    public AdminInboxItem getById(Long id) {
        return adminInboxItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Inbox item not found"));
    }

    @Override
    public AdminInboxItem markInReview(Long id) {
        AdminInboxItem item = getById(id);
        item.setStatus(AdminInboxItemStatus.IN_REVIEW);
        item.setUpdatedAt(LocalDateTime.now());
        return adminInboxItemRepository.save(item);
    }

    @Override
    public AdminInboxItem markResolved(Long id) {
        AdminInboxItem item = getById(id);
        item.setStatus(AdminInboxItemStatus.RESOLVED);
        item.setUpdatedAt(LocalDateTime.now());
        return adminInboxItemRepository.save(item);
    }
}
