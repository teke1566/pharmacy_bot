package com.tenahub.bot.service;

import com.tenahub.bot.dto.*;

import java.util.List;

public interface PharmacyPricingService {

    PharmacyPricingOverviewDTO overview(PharmacyActor actor);

    List<PharmacyPricingItemDTO> listItems(PharmacyActor actor);

    PharmacyPricingItemDTO getItem(PharmacyActor actor, Long itemId);

    List<PriceHistoryDTO> history(PharmacyActor actor, Long itemId);

    List<PriceHistoryDTO> recentHistory(PharmacyActor actor);

    Object submitChange(PharmacyActor actor, Long itemId, PriceChangeSubmitRequestDTO request);

    List<PriceChangeRequestDTO> listRequests(PharmacyActor actor, String status);

    PriceChangeRequestDTO approveRequest(PharmacyActor actor, Long requestId);

    PriceChangeRequestDTO rejectRequest(PharmacyActor actor, Long requestId, String reason);

    List<BulkPricePreviewRowDTO> bulkPreview(PharmacyActor actor, BulkPricePreviewRequestDTO request);

    List<Object> bulkApply(PharmacyActor actor, BulkPricePreviewRequestDTO request);

    List<PromotionDTO> listPromotions(PharmacyActor actor);

    PromotionDTO createPromotion(PharmacyActor actor, PromotionCreateRequestDTO request);

    PromotionDTO deactivatePromotion(PharmacyActor actor, Long promotionId);

    void activateDuePriceChanges();

    void refreshPromotionStatuses();

    java.math.BigDecimal computeWeightedAverageCost(Long inventoryId);
}
