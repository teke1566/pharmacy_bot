package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.PharmacyActor;
import com.tenahub.bot.entity.PharmacyPermission;
import com.tenahub.bot.service.MiniAppAuthException;
import com.tenahub.bot.service.PharmacyAuthorizationService;
import org.springframework.stereotype.Service;

@Service
public class PharmacyAuthorizationServiceImpl implements PharmacyAuthorizationService {

    @Override
    public void require(PharmacyActor actor, PharmacyPermission permission) {
        if (!has(actor, permission)) {
            throw new MiniAppAuthException("Missing permission: " + permission);
        }
    }

    @Override
    public boolean has(PharmacyActor actor, PharmacyPermission permission) {
        return actor != null && permission != null && actor.has(permission);
    }
}
