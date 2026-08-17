package com.tenahub.bot.service;

import com.tenahub.bot.dto.PharmacyActor;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyStaff;

public interface PharmacyStaffAccessService {

    PharmacyStaff ensureOwnerStaff(Pharmacy pharmacy);

    PharmacyActor resolveActor(Pharmacy pharmacy, Long actorTelegramId);

    PharmacyActor toActor(Pharmacy pharmacy, PharmacyStaff staff);
}
