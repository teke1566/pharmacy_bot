package com.tenahub.bot.service;

import com.tenahub.bot.dto.PharmacyActor;
import com.tenahub.bot.entity.PharmacyPermission;

public interface PharmacyAuthorizationService {

    void require(PharmacyActor actor, PharmacyPermission permission);

    boolean has(PharmacyActor actor, PharmacyPermission permission);
}
