package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.PharmacyActor;
import com.tenahub.bot.entity.PharmacyAuditEvent;
import com.tenahub.bot.repository.PharmacyAuditEventRepository;
import com.tenahub.bot.service.PharmacyAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PharmacyAuditServiceImpl implements PharmacyAuditService {

    private final PharmacyAuditEventRepository pharmacyAuditEventRepository;

    @Override
    @Transactional
    public PharmacyAuditEvent record(PharmacyActor actor,
                                     String action,
                                     String module,
                                     String entityType,
                                     String entityId,
                                     String oldValue,
                                     String newValue,
                                     String reason) {
        if (actor == null || actor.getPharmacyId() == null) {
            throw new IllegalArgumentException("pharmacy actor required for audit");
        }
        PharmacyAuditEvent event = PharmacyAuditEvent.builder()
                .pharmacyId(actor.getPharmacyId())
                .actorTelegramId(actor.getActorTelegramId())
                .staffId(actor.getStaffId())
                .employeeId(actor.getEmployeeId())
                .userNameSnapshot(actor.getDisplayName())
                .action(action)
                .module(module)
                .entityType(entityType)
                .entityId(entityId)
                .oldValue(trim(oldValue))
                .newValue(trim(newValue))
                .reason(trim(reason))
                .correlationId(UUID.randomUUID().toString())
                .createdAt(LocalDateTime.now())
                .build();
        return pharmacyAuditEventRepository.save(event);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PharmacyAuditEvent> listForStaff(Long pharmacyId, Long staffId, LocalDateTime from, LocalDateTime to) {
        LocalDateTime start = from == null ? LocalDateTime.now().minusDays(30) : from;
        LocalDateTime end = to == null ? LocalDateTime.now().plusSeconds(1) : to;
        return pharmacyAuditEventRepository.search(pharmacyId, staffId, null, null, start, end);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PharmacyAuditEvent> search(Long pharmacyId, Long staffId, String module, String action,
                                           LocalDateTime from, LocalDateTime to) {
        LocalDateTime start = from == null ? LocalDateTime.now().minusDays(30) : from;
        LocalDateTime end = to == null ? LocalDateTime.now().plusSeconds(1) : to;
        return pharmacyAuditEventRepository.search(pharmacyId, staffId, module, action, start, end);
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 4000) {
            return trimmed;
        }
        return trimmed.substring(0, 4000);
    }
}
