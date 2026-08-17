package com.tenahub.bot.scheduler;

import com.tenahub.bot.service.PharmacyPricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PricingScheduler {

    private final PharmacyPricingService pharmacyPricingService;

    @Scheduled(fixedDelayString = "${tenahub.pricing.scheduler-delay-ms:60000}")
    public void activateScheduledPricesAndPromotions() {
        try {
            pharmacyPricingService.activateDuePriceChanges();
            pharmacyPricingService.refreshPromotionStatuses();
        } catch (Exception e) {
            log.warn("Pricing scheduler failed: {}", e.getMessage());
        }
    }
}
