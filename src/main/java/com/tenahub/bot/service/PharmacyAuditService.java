package com.tenahub.bot.service;

import com.tenahub.bot.dto.PharmacyActor;
import com.tenahub.bot.entity.PharmacyAuditEvent;

import java.time.LocalDateTime;
import java.util.List;

public interface PharmacyAuditService {

    PharmacyAuditEvent record(PharmacyActor actor,
                              String action,
                              String module,
                              String entityType,
                              String entityId,
                              String oldValue,
                              String newValue,
                              String reason);

    List<PharmacyAuditEvent> listForStaff(Long pharmacyId, Long staffId, LocalDateTime from, LocalDateTime to);

    List<PharmacyAuditEvent> search(Long pharmacyId, Long staffId, String module, String action,
                                    LocalDateTime from, LocalDateTime to);
}
