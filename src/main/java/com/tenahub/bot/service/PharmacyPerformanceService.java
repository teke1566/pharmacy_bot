package com.tenahub.bot.service;

import com.tenahub.bot.dto.PharmacyPerformanceDemandItemDTO;
import com.tenahub.bot.dto.PharmacyPerformanceReportDTO;

import java.util.List;

public interface PharmacyPerformanceService {

    String buildPerformanceCard(Long pharmacyTelegramId);

    PharmacyPerformanceReportDTO getPerformanceReport(Long pharmacyTelegramId, String period);

    List<PharmacyPerformanceDemandItemDTO> listDemandItems(Long pharmacyTelegramId, String period);
}
