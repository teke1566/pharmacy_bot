package com.tenahub.bot.scheduler;

import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.InventoryService;
import com.tenahub.bot.util.TelegramClient;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class InventorySummaryScheduler {

    private final PharmacyRepository pharmacyRepository;
    private final InventoryService inventoryService;
    private final TelegramClient telegramClient;

    @Scheduled(cron = "0 0 8 * * *")
    public void sendDailySummaries() {
        sendPeriodSummary("daily");
    }

    @Scheduled(cron = "0 5 8 * * MON")
    public void sendWeeklySummaries() {
        sendPeriodSummary("weekly");
    }

    @Scheduled(cron = "0 10 8 1 * *")
    public void sendMonthlySummaries() {
        sendPeriodSummary("monthly");
    }

    @Scheduled(cron = "0 15 8 1 1 *")
    public void sendYearlySummaries() {
        sendPeriodSummary("yearly");
    }

    @Scheduled(cron = "0 30 7 * * *")
    public void sendLowStockAlerts() {
        List<Pharmacy> pharmacies = pharmacyRepository.findAll();

        for (Pharmacy pharmacy : pharmacies) {
            if (pharmacy.getTelegramId() == null) continue;

            try {
                String alert = inventoryService.buildLowStockAlert(pharmacy.getTelegramId());
                telegramClient.sendMessage(pharmacy.getTelegramId(), alert);
            } catch (Exception ignored) {
            }
        }
    }

    private void sendPeriodSummary(String period) {
        List<Pharmacy> pharmacies = pharmacyRepository.findAll();

        for (Pharmacy pharmacy : pharmacies) {
            if (pharmacy.getTelegramId() == null) continue;

            try {
                String summary = inventoryService.buildSummary(pharmacy.getTelegramId(), period);
                telegramClient.sendMessage(pharmacy.getTelegramId(), summary);
            } catch (Exception ignored) {
            }
        }
    }
}