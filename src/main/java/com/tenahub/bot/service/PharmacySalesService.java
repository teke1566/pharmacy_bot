package com.tenahub.bot.service;

import com.tenahub.bot.dto.PharmacySaleDTO;
import com.tenahub.bot.dto.PharmacySalesSummaryDTO;
import com.tenahub.bot.entity.MedicineReservation;

import java.util.List;

public interface PharmacySalesService {

    void recordFromReservation(MedicineReservation reservation, Long actorTelegramId);

    PharmacySalesSummaryDTO summary(Long pharmacyTelegramId, String period);

    List<PharmacySaleDTO> history(Long pharmacyTelegramId, String period);
}
