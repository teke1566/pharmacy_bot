package com.tenahub.bot.service;

import com.tenahub.bot.dto.PharmacyDashboardDTO;

public interface PharmacyDashboardService {

    PharmacyDashboardDTO getDashboard(Long pharmacyTelegramId);
}
