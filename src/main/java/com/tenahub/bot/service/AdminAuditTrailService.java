package com.tenahub.bot.service;

import com.tenahub.bot.entity.AdminAuditTrail;

import java.util.List;

public interface AdminAuditTrailService {

    void record(String actionType,
                String targetEntityType,
                Long targetEntityId,
                Long adminTelegramId,
                String details);

    void record(String actionType,
                String targetEntityType,
                Long targetEntityId,
                Long adminTelegramId,
                String details,
                String oldValue,
                String newValue);

    List<AdminAuditTrail> listRecent();

    List<AdminAuditTrail> listRecentByActionType(String actionType);

    List<AdminAuditTrail> listRecentByTargetType(String targetEntityType);
}
