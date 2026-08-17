package com.tenahub.bot.service;

import com.tenahub.bot.dto.PharmacyActor;
import com.tenahub.bot.dto.PharmacyAuditEventDTO;
import com.tenahub.bot.dto.PharmacyStaffDTO;
import com.tenahub.bot.dto.PharmacyStaffInviteRequestDTO;
import com.tenahub.bot.dto.PharmacyStaffMetricsDTO;
import com.tenahub.bot.dto.PharmacyStaffUpdateRequestDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface PharmacyStaffService {

    List<PharmacyStaffDTO> list(PharmacyActor actor);

    PharmacyStaffDTO get(PharmacyActor actor, Long staffId);

    PharmacyStaffDTO invite(PharmacyActor actor, PharmacyStaffInviteRequestDTO request);

    PharmacyStaffDTO update(PharmacyActor actor, Long staffId, PharmacyStaffUpdateRequestDTO request);

    PharmacyStaffDTO suspend(PharmacyActor actor, Long staffId, String reason);

    PharmacyStaffDTO activate(PharmacyActor actor, Long staffId);

    PharmacyStaffDTO disable(PharmacyActor actor, Long staffId, String reason);

    PharmacyStaffDTO acceptInvite(String rawToken, Long actorTelegramId);

    PharmacyStaffDTO replacePermissions(PharmacyActor actor, Long staffId,
                                        List<String> grants, List<String> denials);

    List<PharmacyAuditEventDTO> activity(PharmacyActor actor, Long staffId,
                                         LocalDateTime from, LocalDateTime to);

    PharmacyStaffMetricsDTO metrics(PharmacyActor actor);

    Map<String, Object> rolesCatalog();
}
