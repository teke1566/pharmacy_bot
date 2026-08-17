package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.*;
import com.tenahub.bot.entity.*;
import com.tenahub.bot.repository.*;
import com.tenahub.bot.service.PharmacyAuditService;
import com.tenahub.bot.service.PharmacyAuthorizationService;
import com.tenahub.bot.service.PharmacyNotificationService;
import com.tenahub.bot.service.PharmacyPricingService;
import com.tenahub.bot.util.PricingMath;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PharmacyPricingServiceImpl implements PharmacyPricingService {

    private static final BigDecimal LOW_MARGIN = new BigDecimal("10");

    private final PharmacyInventoryRepository inventoryRepository;
    private final MedicineBatchRepository medicineBatchRepository;
    private final PharmacyPricingPolicyRepository policyRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final PriceChangeRequestRepository priceChangeRequestRepository;
    private final PromotionRepository promotionRepository;
    private final PharmacyAuthorizationService authorizationService;
    private final PharmacyAuditService pharmacyAuditService;
    private final PharmacyNotificationService pharmacyNotificationService;

    @Override
    @Transactional(readOnly = true)
    public PharmacyPricingOverviewDTO overview(PharmacyActor actor) {
        authorizationService.require(actor, PharmacyPermission.PRICE_VIEW);
        List<PharmacyPricingItemDTO> items = listItems(actor);
        BigDecimal marginSum = BigDecimal.ZERO;
        int marginCount = 0;
        List<PharmacyPricingItemDTO> low = new ArrayList<>();
        for (PharmacyPricingItemDTO item : items) {
            if (item.getGrossMarginPercent() != null) {
                marginSum = marginSum.add(item.getGrossMarginPercent());
                marginCount++;
                if (item.getGrossMarginPercent().compareTo(LOW_MARGIN) < 0) {
                    low.add(item);
                }
            }
        }
        List<PharmacyPricingItemDTO> high = items.stream()
                .filter(i -> i.getGrossMarginPercent() != null)
                .sorted(Comparator.comparing(PharmacyPricingItemDTO::getGrossMarginPercent).reversed())
                .limit(5)
                .toList();
        low.sort(Comparator.comparing(PharmacyPricingItemDTO::getGrossMarginPercent));
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).toLocalDate().atStartOfDay();
        return PharmacyPricingOverviewDTO.builder()
                .averageMarginPercent(marginCount == 0 ? null : marginSum.divide(BigDecimal.valueOf(marginCount), PricingMath.RATIO_SCALE, PricingMath.ROUNDING))
                .pendingApprovals(priceChangeRequestRepository.countByPharmacyIdAndStatus(actor.getPharmacyId(), PriceChangeRequestStatus.PENDING_APPROVAL))
                .activePromotions(promotionRepository.countByPharmacyIdAndStatus(actor.getPharmacyId(), PromotionStatus.ACTIVE))
                .scheduledChanges(priceChangeRequestRepository.countByPharmacyIdAndStatus(actor.getPharmacyId(), PriceChangeRequestStatus.APPROVED))
                .priceChangesThisMonth(priceHistoryRepository.countByPharmacyIdAndCreatedAtGreaterThanEqual(actor.getPharmacyId(), monthStart))
                .pricedItems(items.stream().filter(i -> i.getSellingPrice() != null).count())
                .lowMarginCount(low.size())
                .lowMarginItems(low.stream().limit(7).toList())
                .highMarginItems(high)
                .recentChanges(recentHistory(actor).stream().limit(8).toList())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PharmacyPricingItemDTO> listItems(PharmacyActor actor) {
        authorizationService.require(actor, PharmacyPermission.PRICE_VIEW);
        return inventoryRepository.findByPharmacyId(actor.getPharmacyId()).stream()
                .filter(i -> !i.isArchived())
                .map(i -> toItemDto(actor.getPharmacyId(), i))
                .sorted(Comparator.comparing(PharmacyPricingItemDTO::getMedicineName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PharmacyPricingItemDTO getItem(PharmacyActor actor, Long itemId) {
        authorizationService.require(actor, PharmacyPermission.PRICE_VIEW);
        return toItemDto(actor.getPharmacyId(), requireItem(actor.getPharmacyId(), itemId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriceHistoryDTO> history(PharmacyActor actor, Long itemId) {
        authorizationService.require(actor, PharmacyPermission.PRICE_VIEW);
        requireItem(actor.getPharmacyId(), itemId);
        return priceHistoryRepository.findByPharmacyIdAndInventoryIdOrderByCreatedAtDesc(actor.getPharmacyId(), itemId)
                .stream().map(this::toHistoryDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriceHistoryDTO> recentHistory(PharmacyActor actor) {
        authorizationService.require(actor, PharmacyPermission.PRICE_VIEW);
        return priceHistoryRepository.findByPharmacyIdOrderByCreatedAtDesc(actor.getPharmacyId()).stream()
                .limit(50)
                .map(this::toHistoryDto)
                .toList();
    }

    @Override
    @Transactional
    public Object submitChange(PharmacyActor actor, Long itemId, PriceChangeSubmitRequestDTO request) {
        authorizationService.require(actor, PharmacyPermission.PRICE_EDIT);
        if (request == null || request.getProposedSellingPrice() == null) {
            throw new RuntimeException("Proposed selling price is required");
        }
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new RuntimeException("Reason is required for price changes");
        }
        PharmacyInventory item = requireItem(actor.getPharmacyId(), itemId);
        if (request.getExpectedVersion() != null && item.getVersion() != null
                && !Objects.equals(request.getExpectedVersion(), item.getVersion())) {
            throw new RuntimeException("Price was changed by another user. Refresh and review the latest price.");
        }
        BigDecimal proposed = PricingMath.money(request.getProposedSellingPrice());
        validateNonNegative(proposed, "Selling price");
        BigDecimal cost = request.getPurchaseCost() != null
                ? PricingMath.money(request.getPurchaseCost())
                : computeWeightedAverageCost(item.getId());
        if (cost == null) {
            cost = item.getPurchaseCost();
        }
        if (cost != null && proposed.compareTo(cost) < 0 && !request.isForceBelowCost()) {
            throw new RuntimeException("Current price is below purchase cost. Confirm with forceBelowCost=true.");
        }
        if (cost != null && proposed.compareTo(cost) < 0) {
            authorizationService.require(actor, PharmacyPermission.PRICE_APPROVE);
        }

        PharmacyPricingPolicy policy = ensurePolicy(actor.getPharmacyId());
        BigDecimal current = item.getPrice();
        BigDecimal pct = PricingMath.percentChange(current, proposed);
        boolean needsApproval = request.isSubmitForApproval() || requiresApproval(policy, pct, actor);

        LocalDateTime effectiveAt = request.getEffectiveAt();
        LocalDateTime now = nowInPolicy(policy);
        if (effectiveAt != null && effectiveAt.isAfter(now.plusMinutes(1))) {
            needsApproval = needsApproval || !actor.has(PharmacyPermission.PRICE_APPROVE);
            PriceChangeRequest changeRequest = createRequest(actor, item, current, proposed, cost, pct, request.getReason(),
                    effectiveAt, needsApproval ? PriceChangeRequestStatus.PENDING_APPROVAL : PriceChangeRequestStatus.APPROVED);
            pharmacyAuditService.record(actor, "PRICE_CHANGE_REQUESTED", "PRICING", "PriceChangeRequest",
                    String.valueOf(changeRequest.getId()),
                    String.valueOf(current), String.valueOf(proposed), request.getReason());
            if (needsApproval) {
                pharmacyNotificationService.create(actor.getPharmacyId(), PharmacyNotificationType.PRICE_APPROVAL_REQUIRED,
                        "Price approval required",
                        item.getMedicineName() + ": " + current + " → " + proposed,
                        null, item.getMedicineName());
            }
            return toRequestDto(changeRequest, needsApproval);
        }

        if (needsApproval) {
            PriceChangeRequest changeRequest = createRequest(actor, item, current, proposed, cost, pct, request.getReason(),
                    now, PriceChangeRequestStatus.PENDING_APPROVAL);
            pharmacyAuditService.record(actor, "PRICE_CHANGE_REQUESTED", "PRICING", "PriceChangeRequest",
                    String.valueOf(changeRequest.getId()),
                    String.valueOf(current), String.valueOf(proposed), request.getReason());
            pharmacyNotificationService.create(actor.getPharmacyId(), PharmacyNotificationType.PRICE_APPROVAL_REQUIRED,
                    "Price approval required",
                    item.getMedicineName() + ": " + current + " → " + proposed,
                    null, item.getMedicineName());
            return toRequestDto(changeRequest, true);
        }

        return publishPrice(actor, item, proposed, cost, request.getReason(), null, now);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriceChangeRequestDTO> listRequests(PharmacyActor actor, String status) {
        authorizationService.require(actor, PharmacyPermission.PRICE_VIEW);
        List<PriceChangeRequest> rows;
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            rows = priceChangeRequestRepository.findByPharmacyIdOrderByRequestedAtDesc(actor.getPharmacyId());
        } else {
            rows = priceChangeRequestRepository.findByPharmacyIdAndStatusOrderByRequestedAtDesc(
                    actor.getPharmacyId(), PriceChangeRequestStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)));
        }
        return rows.stream().map(r -> toRequestDto(r, r.getStatus() == PriceChangeRequestStatus.PENDING_APPROVAL)).toList();
    }

    @Override
    @Transactional
    public PriceChangeRequestDTO approveRequest(PharmacyActor actor, Long requestId) {
        authorizationService.require(actor, PharmacyPermission.PRICE_APPROVE);
        PriceChangeRequest request = priceChangeRequestRepository.findByIdAndPharmacyId(requestId, actor.getPharmacyId())
                .orElseThrow(() -> new RuntimeException("Price change request does not belong to this pharmacy"));
        if (request.getStatus() != PriceChangeRequestStatus.PENDING_APPROVAL
                && request.getStatus() != PriceChangeRequestStatus.APPROVED) {
            throw new RuntimeException("Request is not approvable");
        }
        LocalDateTime now = LocalDateTime.now();
        request.setStatus(PriceChangeRequestStatus.APPROVED);
        request.setApprovedAt(now);
        request.setApprovedByStaffId(actor.getStaffId());
        request.setApprovedByTelegramId(actor.getActorTelegramId());
        request.setUpdatedAt(now);
        priceChangeRequestRepository.save(request);

        LocalDateTime effective = request.getEffectiveAt() == null ? now : request.getEffectiveAt();
        if (!effective.isAfter(now.plusSeconds(5))) {
            PharmacyInventory item = requireItem(actor.getPharmacyId(), request.getInventoryId());
            publishPrice(actor, item, request.getProposedSellingPrice(), request.getPurchaseCostRef(),
                    request.getReason(), request.getId(), now);
            request.setStatus(PriceChangeRequestStatus.APPROVED);
            priceChangeRequestRepository.save(request);
        }
        pharmacyAuditService.record(actor, "PRICE_CHANGE_APPROVED", "PRICING", "PriceChangeRequest",
                String.valueOf(request.getId()), null, String.valueOf(request.getProposedSellingPrice()), request.getReason());
        pharmacyNotificationService.create(actor.getPharmacyId(), PharmacyNotificationType.PRICE_APPROVED,
                "Price change approved", request.getMedicineName() + " → " + request.getProposedSellingPrice(),
                null, request.getMedicineName());
        return toRequestDto(request, false);
    }

    @Override
    @Transactional
    public PriceChangeRequestDTO rejectRequest(PharmacyActor actor, Long requestId, String reason) {
        authorizationService.require(actor, PharmacyPermission.PRICE_APPROVE);
        PriceChangeRequest request = priceChangeRequestRepository.findByIdAndPharmacyId(requestId, actor.getPharmacyId())
                .orElseThrow(() -> new RuntimeException("Price change request does not belong to this pharmacy"));
        if (request.getStatus() != PriceChangeRequestStatus.PENDING_APPROVAL) {
            throw new RuntimeException("Only pending requests can be rejected");
        }
        request.setStatus(PriceChangeRequestStatus.REJECTED);
        request.setRejectionReason(reason);
        request.setApprovedAt(LocalDateTime.now());
        request.setApprovedByStaffId(actor.getStaffId());
        request.setApprovedByTelegramId(actor.getActorTelegramId());
        request.setUpdatedAt(LocalDateTime.now());
        priceChangeRequestRepository.save(request);
        pharmacyAuditService.record(actor, "PRICE_CHANGE_REJECTED", "PRICING", "PriceChangeRequest",
                String.valueOf(request.getId()), String.valueOf(request.getProposedSellingPrice()), null, reason);
        pharmacyNotificationService.create(actor.getPharmacyId(), PharmacyNotificationType.PRICE_REJECTED,
                "Price change rejected", request.getMedicineName(), null, request.getMedicineName());
        return toRequestDto(request, false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BulkPricePreviewRowDTO> bulkPreview(PharmacyActor actor, BulkPricePreviewRequestDTO request) {
        authorizationService.require(actor, PharmacyPermission.PRICE_BULK_UPDATE);
        return buildBulkRows(actor, request);
    }

    @Override
    @Transactional
    public List<Object> bulkApply(PharmacyActor actor, BulkPricePreviewRequestDTO request) {
        authorizationService.require(actor, PharmacyPermission.PRICE_BULK_UPDATE);
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new RuntimeException("Reason is required for bulk price updates");
        }
        List<BulkPricePreviewRowDTO> rows = buildBulkRows(actor, request);
        List<Object> results = new ArrayList<>();
        PharmacyPricingPolicy policy = ensurePolicy(actor.getPharmacyId());
        for (BulkPricePreviewRowDTO row : rows) {
            PriceChangeSubmitRequestDTO submit = PriceChangeSubmitRequestDTO.builder()
                    .proposedSellingPrice(row.getNewPrice())
                    .reason(request.getReason())
                    .effectiveAt(request.getEffectiveAt())
                    .forceBelowCost(false)
                    .submitForApproval(requiresApproval(policy, row.getPercentChange(), actor))
                    .build();
            results.add(submitChange(actor, row.getItemId(), submit));
        }
        pharmacyAuditService.record(actor, "BULK_PRICE_UPDATE", "PRICING", "PharmacyInventory",
                null, null, "items=" + rows.size(), request.getReason());
        return results;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromotionDTO> listPromotions(PharmacyActor actor) {
        authorizationService.require(actor, PharmacyPermission.PRICE_VIEW);
        return promotionRepository.findByPharmacyIdOrderByCreatedAtDesc(actor.getPharmacyId()).stream()
                .map(p -> toPromotionDto(actor.getPharmacyId(), p))
                .toList();
    }

    @Override
    @Transactional
    public PromotionDTO createPromotion(PharmacyActor actor, PromotionCreateRequestDTO request) {
        authorizationService.require(actor, PharmacyPermission.PRICE_DISCOUNT);
        if (request == null || request.getItemId() == null) {
            throw new RuntimeException("Promotion itemId is required");
        }
        PharmacyInventory item = requireItem(actor.getPharmacyId(), request.getItemId());
        DiscountType type = DiscountType.valueOf(request.getDiscountType().trim().toUpperCase(Locale.ROOT));
        BigDecimal value = PricingMath.money(request.getDiscountValue());
        validateNonNegative(value, "Discount");
        if (request.getStartAt() == null || request.getEndAt() == null || !request.getEndAt().isAfter(request.getStartAt())) {
            throw new RuntimeException("Invalid promotion dates");
        }
        BigDecimal customer = computePromoPrice(item.getPrice(), type, value, request.getMaxDiscount());
        if (customer == null || customer.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Discount would create an invalid price");
        }
        LocalDateTime now = LocalDateTime.now();
        PromotionStatus status = !request.getStartAt().isAfter(now) && !request.getEndAt().isBefore(now)
                ? PromotionStatus.ACTIVE : PromotionStatus.SCHEDULED;
        Promotion promo = Promotion.builder()
                .pharmacyId(actor.getPharmacyId())
                .inventoryId(item.getId())
                .medicineName(item.getMedicineName())
                .discountType(type)
                .discountValue(value)
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .minQuantity(request.getMinQuantity())
                .maxDiscount(PricingMath.money(request.getMaxDiscount()))
                .status(status)
                .createdByStaffId(actor.getStaffId())
                .createdByTelegramId(actor.getActorTelegramId())
                .createdAt(now)
                .updatedAt(now)
                .build();
        promo = promotionRepository.save(promo);
        pharmacyAuditService.record(actor, "PRICE_PROMOTION_CREATED", "PRICING", "Promotion",
                String.valueOf(promo.getId()), null, type + ":" + value, null);
        if (status == PromotionStatus.ACTIVE) {
            pharmacyNotificationService.create(actor.getPharmacyId(), PharmacyNotificationType.PROMOTION_STARTED,
                    "Promotion started", item.getMedicineName(), null, item.getMedicineName());
        }
        return toPromotionDto(actor.getPharmacyId(), promo);
    }

    @Override
    @Transactional
    public PromotionDTO deactivatePromotion(PharmacyActor actor, Long promotionId) {
        authorizationService.require(actor, PharmacyPermission.PRICE_DISCOUNT);
        Promotion promo = promotionRepository.findByIdAndPharmacyId(promotionId, actor.getPharmacyId())
                .orElseThrow(() -> new RuntimeException("Promotion does not belong to this pharmacy"));
        promo.setStatus(PromotionStatus.CANCELLED);
        promo.setUpdatedAt(LocalDateTime.now());
        promotionRepository.save(promo);
        pharmacyAuditService.record(actor, "PRICE_PROMOTION_UPDATED", "PRICING", "Promotion",
                String.valueOf(promo.getId()), PromotionStatus.ACTIVE.name(), PromotionStatus.CANCELLED.name(), null);
        return toPromotionDto(actor.getPharmacyId(), promo);
    }

    @Override
    @Transactional
    public void activateDuePriceChanges() {
        LocalDateTime now = LocalDateTime.now();
        List<PriceChangeRequest> due = priceChangeRequestRepository
                .findByStatusAndEffectiveAtLessThanEqual(PriceChangeRequestStatus.APPROVED, now);
        for (PriceChangeRequest request : due) {
            try {
                PharmacyInventory item = inventoryRepository.findById(request.getInventoryId()).orElse(null);
                if (item == null || !Objects.equals(item.getPharmacyId(), request.getPharmacyId())) {
                    continue;
                }
                PharmacyActor system = PharmacyActor.builder()
                        .pharmacyId(request.getPharmacyId())
                        .actorTelegramId(request.getApprovedByTelegramId())
                        .staffId(request.getApprovedByStaffId())
                        .displayName("System")
                        .employeeId(null)
                        .permissions(java.util.EnumSet.allOf(PharmacyPermission.class))
                        .build();
                publishPrice(system, item, request.getProposedSellingPrice(), request.getPurchaseCostRef(),
                        request.getReason(), request.getId(), now);
                request.setStatus(PriceChangeRequestStatus.EXPIRED);
                request.setUpdatedAt(now);
                priceChangeRequestRepository.save(request);
                pharmacyNotificationService.create(request.getPharmacyId(), PharmacyNotificationType.PRICE_ACTIVATED,
                        "Scheduled price activated", request.getMedicineName() + " → " + request.getProposedSellingPrice(),
                        null, request.getMedicineName());
            } catch (Exception ignored) {
                // continue other activations
            }
        }
    }

    @Override
    @Transactional
    public void refreshPromotionStatuses() {
        LocalDateTime now = LocalDateTime.now();
        for (Promotion promo : promotionRepository.findByStatusAndEndAtLessThan(PromotionStatus.ACTIVE, now)) {
            promo.setStatus(PromotionStatus.EXPIRED);
            promo.setUpdatedAt(now);
            promotionRepository.save(promo);
            pharmacyNotificationService.create(promo.getPharmacyId(), PharmacyNotificationType.PROMOTION_EXPIRED,
                    "Promotion expired", promo.getMedicineName(), null, promo.getMedicineName());
        }
        for (Promotion promo : promotionRepository.findByStatusAndStartAtLessThanEqualAndEndAtGreaterThanEqual(
                PromotionStatus.SCHEDULED, now, now)) {
            promo.setStatus(PromotionStatus.ACTIVE);
            promo.setUpdatedAt(now);
            promotionRepository.save(promo);
            pharmacyNotificationService.create(promo.getPharmacyId(), PharmacyNotificationType.PROMOTION_STARTED,
                    "Promotion started", promo.getMedicineName(), null, promo.getMedicineName());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal computeWeightedAverageCost(Long inventoryId) {
        List<MedicineBatch> batches = medicineBatchRepository.findByInventoryId(inventoryId);
        BigDecimal costSum = BigDecimal.ZERO;
        long qtySum = 0;
        MedicineBatch latest = null;
        for (MedicineBatch batch : batches) {
            if (batch.getQuantity() == null || batch.getQuantity() <= 0 || batch.getPurchasePrice() == null) {
                continue;
            }
            costSum = costSum.add(batch.getPurchasePrice().multiply(BigDecimal.valueOf(batch.getQuantity())));
            qtySum += batch.getQuantity();
            if (latest == null || (batch.getReceivedAt() != null && (latest.getReceivedAt() == null
                    || batch.getReceivedAt().isAfter(latest.getReceivedAt())))) {
                latest = batch;
            }
        }
        if (qtySum > 0) {
            return PricingMath.money(costSum.divide(BigDecimal.valueOf(qtySum), PricingMath.SCALE, PricingMath.ROUNDING));
        }
        if (latest != null && latest.getPurchasePrice() != null) {
            return PricingMath.money(latest.getPurchasePrice());
        }
        return null;
    }

    private PharmacyPricingItemDTO publishPrice(PharmacyActor actor, PharmacyInventory item, BigDecimal proposed,
                                                BigDecimal cost, String reason, Long requestId, LocalDateTime effectiveAt) {
        BigDecimal oldPrice = item.getPrice();
        BigDecimal oldCost = item.getPurchaseCost();
        try {
            item.setPrice(PricingMath.money(proposed));
            if (cost != null) {
                item.setPurchaseCost(PricingMath.money(cost));
            }
            if (item.getCurrency() == null || item.getCurrency().isBlank()) {
                item.setCurrency("ETB");
            }
            item.setUpdatedAt(LocalDateTime.now());
            inventoryRepository.saveAndFlush(item);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new RuntimeException("Price was changed by another user. Refresh and review the latest price.");
        }

        PriceHistory history = PriceHistory.builder()
                .pharmacyId(item.getPharmacyId())
                .inventoryId(item.getId())
                .medicineName(item.getMedicineName())
                .oldSellingPrice(oldPrice)
                .newSellingPrice(item.getPrice())
                .oldPurchaseCost(oldCost)
                .newPurchaseCost(item.getPurchaseCost())
                .currency(item.getCurrency())
                .reason(reason)
                .actorStaffId(actor.getStaffId())
                .actorTelegramId(actor.getActorTelegramId())
                .actorNameSnapshot(actor.getDisplayName())
                .requestId(requestId)
                .effectiveAt(effectiveAt)
                .createdAt(LocalDateTime.now())
                .build();
        priceHistoryRepository.save(history);
        pharmacyAuditService.record(actor, "PRICE_UPDATED", "PRICING", "PharmacyInventory",
                String.valueOf(item.getId()), String.valueOf(oldPrice), String.valueOf(item.getPrice()), reason);
        return toItemDto(item.getPharmacyId(), item);
    }

    private PriceChangeRequest createRequest(PharmacyActor actor, PharmacyInventory item, BigDecimal current,
                                             BigDecimal proposed, BigDecimal cost, BigDecimal pct, String reason,
                                             LocalDateTime effectiveAt, PriceChangeRequestStatus status) {
        LocalDateTime now = LocalDateTime.now();
        PriceChangeRequest request = PriceChangeRequest.builder()
                .pharmacyId(actor.getPharmacyId())
                .inventoryId(item.getId())
                .medicineName(item.getMedicineName())
                .currentSellingPrice(current)
                .proposedSellingPrice(proposed)
                .purchaseCostRef(cost)
                .marginBefore(PricingMath.grossMarginPercent(current, cost))
                .marginAfter(PricingMath.grossMarginPercent(proposed, cost))
                .percentChange(pct)
                .currency(item.getCurrency() == null ? "ETB" : item.getCurrency())
                .effectiveAt(effectiveAt)
                .reason(reason)
                .status(status)
                .requestedByStaffId(actor.getStaffId())
                .requestedByTelegramId(actor.getActorTelegramId())
                .requestedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return priceChangeRequestRepository.save(request);
    }

    private List<BulkPricePreviewRowDTO> buildBulkRows(PharmacyActor actor, BulkPricePreviewRequestDTO request) {
        if (request == null || request.getItemIds() == null || request.getItemIds().isEmpty()) {
            throw new RuntimeException("itemIds are required");
        }
        if (request.getMode() == null || request.getValue() == null) {
            throw new RuntimeException("mode and value are required");
        }
        String mode = request.getMode().trim().toUpperCase(Locale.ROOT);
        List<BulkPricePreviewRowDTO> rows = new ArrayList<>();
        for (Long itemId : request.getItemIds()) {
            PharmacyInventory item = requireItem(actor.getPharmacyId(), itemId);
            BigDecimal current = item.getPrice() == null ? BigDecimal.ZERO : item.getPrice();
            BigDecimal cost = item.getPurchaseCost() != null ? item.getPurchaseCost() : computeWeightedAverageCost(item.getId());
            BigDecimal next;
            switch (mode) {
                case "PERCENT" -> next = PricingMath.money(current.multiply(BigDecimal.ONE.add(
                        request.getValue().divide(BigDecimal.valueOf(100), PricingMath.RATIO_SCALE, PricingMath.ROUNDING))));
                case "MARGIN" -> {
                    if (cost == null) {
                        throw new RuntimeException("Cannot set margin without purchase cost for " + item.getMedicineName());
                    }
                    BigDecimal margin = request.getValue().divide(BigDecimal.valueOf(100), PricingMath.RATIO_SCALE, PricingMath.ROUNDING);
                    if (margin.compareTo(BigDecimal.ONE) >= 0) {
                        throw new RuntimeException("Margin must be below 100%");
                    }
                    next = PricingMath.money(cost.divide(BigDecimal.ONE.subtract(margin), PricingMath.SCALE, PricingMath.ROUNDING));
                }
                case "FIXED" -> next = PricingMath.money(request.getValue());
                default -> throw new RuntimeException("Unknown bulk mode: " + request.getMode());
            }
            validateNonNegative(next, "New price");
            rows.add(BulkPricePreviewRowDTO.builder()
                    .itemId(item.getId())
                    .medicineName(item.getMedicineName())
                    .currentPrice(current)
                    .newPrice(next)
                    .percentChange(PricingMath.percentChange(current, next))
                    .marginAfter(PricingMath.grossMarginPercent(next, cost))
                    .belowCost(cost != null && next.compareTo(cost) < 0)
                    .build());
        }
        return rows;
    }

    private PharmacyPricingItemDTO toItemDto(Long pharmacyId, PharmacyInventory item) {
        BigDecimal cost = item.getPurchaseCost() != null ? item.getPurchaseCost() : computeWeightedAverageCost(item.getId());
        BigDecimal sell = item.getPrice();
        Integer qty = item.getQuantity() == null ? 0 : item.getQuantity();
        BigDecimal invCost = cost == null ? null : PricingMath.money(cost.multiply(BigDecimal.valueOf(qty)));
        BigDecimal salesValue = sell == null ? null : PricingMath.money(sell.multiply(BigDecimal.valueOf(qty)));
        BigDecimal potentialProfit = (invCost != null && salesValue != null) ? PricingMath.money(salesValue.subtract(invCost)) : null;
        Promotion active = findActivePromotion(pharmacyId, item.getId());
        BigDecimal promoPrice = active == null ? null : computePromoPrice(sell, active.getDiscountType(), active.getDiscountValue(), active.getMaxDiscount());
        return PharmacyPricingItemDTO.builder()
                .itemId(item.getId())
                .medicineName(item.getMedicineName())
                .sellingPrice(sell)
                .purchaseCost(cost)
                .grossProfit(PricingMath.grossProfit(sell, cost))
                .grossMarginPercent(PricingMath.grossMarginPercent(sell, cost))
                .markupPercent(PricingMath.markupPercent(sell, cost))
                .currency(item.getCurrency() == null ? "ETB" : item.getCurrency())
                .stockQuantity(qty)
                .inventoryCostValue(invCost)
                .potentialSalesValue(salesValue)
                .potentialGrossProfit(potentialProfit)
                .belowCost(cost != null && sell != null && sell.compareTo(cost) < 0)
                .version(item.getVersion())
                .updatedAt(item.getUpdatedAt())
                .promotionalPrice(promoPrice)
                .activePromotionLabel(active == null ? null : active.getDiscountType() + " " + active.getDiscountValue())
                .build();
    }

    private Promotion findActivePromotion(Long pharmacyId, Long inventoryId) {
        List<Promotion> active = promotionRepository.findByPharmacyIdAndInventoryIdAndStatus(pharmacyId, inventoryId, PromotionStatus.ACTIVE);
        LocalDateTime now = LocalDateTime.now();
        return active.stream()
                .filter(p -> p.getStartAt() != null && p.getEndAt() != null)
                .filter(p -> !p.getStartAt().isAfter(now) && !p.getEndAt().isBefore(now))
                .findFirst()
                .orElse(null);
    }

    private PriceHistoryDTO toHistoryDto(PriceHistory h) {
        return PriceHistoryDTO.builder()
                .historyId(h.getId())
                .itemId(h.getInventoryId())
                .medicineName(h.getMedicineName())
                .oldSellingPrice(h.getOldSellingPrice())
                .newSellingPrice(h.getNewSellingPrice())
                .oldPurchaseCost(h.getOldPurchaseCost())
                .newPurchaseCost(h.getNewPurchaseCost())
                .currency(h.getCurrency())
                .reason(h.getReason())
                .actorName(h.getActorNameSnapshot())
                .actorStaffId(h.getActorStaffId())
                .requestId(h.getRequestId())
                .effectiveAt(h.getEffectiveAt())
                .createdAt(h.getCreatedAt())
                .build();
    }

    private PriceChangeRequestDTO toRequestDto(PriceChangeRequest r, boolean requiresApproval) {
        return PriceChangeRequestDTO.builder()
                .requestId(r.getId())
                .itemId(r.getInventoryId())
                .medicineName(r.getMedicineName())
                .currentSellingPrice(r.getCurrentSellingPrice())
                .proposedSellingPrice(r.getProposedSellingPrice())
                .purchaseCostRef(r.getPurchaseCostRef())
                .marginBefore(r.getMarginBefore())
                .marginAfter(r.getMarginAfter())
                .percentChange(r.getPercentChange())
                .currency(r.getCurrency())
                .effectiveAt(r.getEffectiveAt())
                .reason(r.getReason())
                .status(r.getStatus() == null ? null : r.getStatus().name())
                .requestedByStaffId(r.getRequestedByStaffId())
                .approvedByStaffId(r.getApprovedByStaffId())
                .requestedAt(r.getRequestedAt())
                .approvedAt(r.getApprovedAt())
                .rejectionReason(r.getRejectionReason())
                .requiresApproval(requiresApproval)
                .build();
    }

    private PromotionDTO toPromotionDto(Long pharmacyId, Promotion p) {
        PharmacyInventory item = inventoryRepository.findById(p.getInventoryId()).orElse(null);
        BigDecimal regular = item == null ? null : item.getPrice();
        return PromotionDTO.builder()
                .promotionId(p.getId())
                .itemId(p.getInventoryId())
                .medicineName(p.getMedicineName())
                .discountType(p.getDiscountType() == null ? null : p.getDiscountType().name())
                .discountValue(p.getDiscountValue())
                .startAt(p.getStartAt())
                .endAt(p.getEndAt())
                .minQuantity(p.getMinQuantity())
                .maxDiscount(p.getMaxDiscount())
                .status(p.getStatus() == null ? null : p.getStatus().name())
                .regularPrice(regular)
                .customerPrice(computePromoPrice(regular, p.getDiscountType(), p.getDiscountValue(), p.getMaxDiscount()))
                .build();
    }

    private BigDecimal computePromoPrice(BigDecimal sell, DiscountType type, BigDecimal value, BigDecimal maxDiscount) {
        if (sell == null || type == null || value == null) {
            return null;
        }
        BigDecimal next = type == DiscountType.PERCENTAGE
                ? PricingMath.applyPercentageDiscount(sell, value)
                : PricingMath.applyFixedDiscount(sell, value);
        if (maxDiscount != null && sell.subtract(next).compareTo(maxDiscount) > 0) {
            next = PricingMath.money(sell.subtract(maxDiscount));
        }
        return next;
    }

    private PharmacyInventory requireItem(Long pharmacyId, Long itemId) {
        PharmacyInventory item = inventoryRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Inventory item not found"));
        if (!Objects.equals(item.getPharmacyId(), pharmacyId)) {
            throw new RuntimeException("Inventory item does not belong to this pharmacy");
        }
        return item;
    }

    private PharmacyPricingPolicy ensurePolicy(Long pharmacyId) {
        return policyRepository.findByPharmacyId(pharmacyId).orElseGet(() -> policyRepository.save(
                PharmacyPricingPolicy.builder()
                        .pharmacyId(pharmacyId)
                        .approvalThresholdPercent(new BigDecimal("15"))
                        .timezone("Africa/Addis_Ababa")
                        .taxRate(BigDecimal.ZERO)
                        .costingMethod("WEIGHTED_AVERAGE")
                        .currency("ETB")
                        .updatedAt(LocalDateTime.now())
                        .build()));
    }

    private boolean requiresApproval(PharmacyPricingPolicy policy, BigDecimal percentChange, PharmacyActor actor) {
        if (percentChange == null || policy.getApprovalThresholdPercent() == null) {
            return false;
        }
        if (actor.has(PharmacyPermission.PRICE_APPROVE) && percentChange.abs().compareTo(policy.getApprovalThresholdPercent()) <= 0) {
            return false;
        }
        return percentChange.abs().compareTo(policy.getApprovalThresholdPercent()) > 0
                && !actor.has(PharmacyPermission.PRICE_APPROVE);
    }

    private LocalDateTime nowInPolicy(PharmacyPricingPolicy policy) {
        try {
            return LocalDateTime.now(ZoneId.of(policy.getTimezone() == null ? "Africa/Addis_Ababa" : policy.getTimezone()));
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private void validateNonNegative(BigDecimal value, String label) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException(label + " cannot be negative");
        }
    }
}
