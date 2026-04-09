package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.AdminAuditTrail;
import com.tenahub.bot.repository.AdminAuditTrailRepository;
import com.tenahub.bot.service.AdminAuditTrailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminAuditTrailServiceImpl implements AdminAuditTrailService {

    private final AdminAuditTrailRepository adminAuditTrailRepository;

    @Override
    public void record(String actionType,
                       String targetEntityType,
                       Long targetEntityId,
                       Long adminTelegramId,
                       String details) {
        record(actionType, targetEntityType, targetEntityId, adminTelegramId, details, null, null);
    }

    @Override
    public void record(String actionType,
                       String targetEntityType,
                       Long targetEntityId,
                       Long adminTelegramId,
                       String details,
                       String oldValue,
                       String newValue) {
        if (adminTelegramId == null) {
            return;
        }

        AdminAuditTrail entry = AdminAuditTrail.builder()
                .actionType(normalize(actionType))
                .targetEntityType(normalize(targetEntityType))
                .targetEntityId(targetEntityId)
                .adminTelegramId(adminTelegramId)
                .actionTimestamp(LocalDateTime.now())
                .details(safe(details))
                .oldValue(safe(oldValue))
                .newValue(safe(newValue))
                .build();

        adminAuditTrailRepository.save(entry);
    }

    @Override
    public List<AdminAuditTrail> listRecent() {
        return adminAuditTrailRepository.findTop30ByOrderByActionTimestampDesc();
    }

    @Override
    public List<AdminAuditTrail> listRecentByActionType(String actionType) {
        return adminAuditTrailRepository.findTop30ByActionTypeOrderByActionTimestampDesc(normalize(actionType));
    }

    @Override
    public List<AdminAuditTrail> listRecentByTargetType(String targetEntityType) {
        return adminAuditTrailRepository.findTop30ByTargetEntityTypeOrderByActionTimestampDesc(normalize(targetEntityType));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
