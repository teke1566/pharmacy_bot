package com.tenahub.bot.repository;

import com.tenahub.bot.entity.AdminInboxItem;
import com.tenahub.bot.entity.AdminInboxItemStatus;
import com.tenahub.bot.entity.AdminInboxItemType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminInboxItemRepository extends JpaRepository<AdminInboxItem, Long> {

    long countByStatus(AdminInboxItemStatus status);

    List<AdminInboxItem> findTop30ByStatusOrderByCreatedAtDesc(AdminInboxItemStatus status);

    List<AdminInboxItem> findTop30ByStatusAndTypeOrderByCreatedAtDesc(AdminInboxItemStatus status, AdminInboxItemType type);

    List<AdminInboxItem> findTop30ByStatusInOrderByCreatedAtDesc(List<AdminInboxItemStatus> statuses);

    List<AdminInboxItem> findTop30ByStatusInAndTypeOrderByCreatedAtDesc(List<AdminInboxItemStatus> statuses, AdminInboxItemType type);
}
