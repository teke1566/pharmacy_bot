package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.MedicineBatchDTO;
import com.tenahub.bot.dto.PharmacyDashboardDTO;
import com.tenahub.bot.dto.PharmacyMiniAppInventoryItemDTO;
import com.tenahub.bot.dto.PharmacySalesSummaryDTO;
import com.tenahub.bot.entity.MedicineReservationStatus;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PrescriptionReviewStatus;
import com.tenahub.bot.repository.MedicineReservationRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.InventoryService;
import com.tenahub.bot.service.PharmacyDashboardService;
import com.tenahub.bot.service.PharmacyNotificationService;
import com.tenahub.bot.service.PharmacySalesService;
import com.tenahub.bot.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PharmacyDashboardServiceImpl implements PharmacyDashboardService {

    private final PharmacyRepository pharmacyRepository;
    private final MedicineReservationRepository reservationRepository;
    private final ReservationService reservationService;
    private final InventoryService inventoryService;
    private final PharmacySalesService pharmacySalesService;
    private final PharmacyNotificationService pharmacyNotificationService;

    @Override
    public PharmacyDashboardDTO getDashboard(Long pharmacyTelegramId) {
        Pharmacy pharmacy = resolvePharmacy(pharmacyTelegramId);

        int pendingReservations = reservationService.getPendingReservations(pharmacyTelegramId).size();
        int pendingPrescriptions = (int) reservationService.getPrescriptionReservations(pharmacyTelegramId).stream()
                .filter(r -> r.getPrescriptionReviewStatus() == PrescriptionReviewStatus.PENDING_REVIEW
                        || r.getPrescriptionReviewStatus() == PrescriptionReviewStatus.NEEDS_CLARIFICATION)
                .count();

        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        int fulfilledToday = (int) reservationRepository.findByPharmacyIdOrderByCreatedAtDesc(pharmacy.getId()).stream()
                .filter(r -> r.getStatus() == MedicineReservationStatus.FULFILLED)
                .filter(r -> r.getFulfilledAt() != null && !r.getFulfilledAt().isBefore(todayStart))
                .count();

        PharmacySalesSummaryDTO sales = pharmacySalesService.summary(pharmacyTelegramId, "daily");
        BigDecimal todayRevenue = sales.getRevenue() == null ? BigDecimal.ZERO : sales.getRevenue();
        int todaySaleCount = sales.getSaleCount() == null ? 0 : sales.getSaleCount();

        List<PharmacyMiniAppInventoryItemDTO> inventory = inventoryService.getPharmacyMiniAppInventory(pharmacyTelegramId);
        int totalItems = inventory.size();
        int outOfStock = (int) inventory.stream().filter(PharmacyMiniAppInventoryItemDTO::isOutOfStock).count();
        int lowStock = (int) inventory.stream().filter(PharmacyMiniAppInventoryItemDTO::isLowStock).count();
        int inStock = (int) inventory.stream().filter(i -> i.isInStock() && !i.isOutOfStock()).count();

        List<MedicineBatchDTO> expiring = inventoryService.listExpiryBatches(pharmacyTelegramId, "30");
        int expiringSoon = expiring == null ? 0 : expiring.size();

        long unread = pharmacyNotificationService.unreadCount(pharmacyTelegramId);

        return PharmacyDashboardDTO.builder()
                .pendingPrescriptions(pendingPrescriptions)
                .pendingReservations(pendingReservations)
                .fulfilledToday(fulfilledToday)
                .todayRevenue(todayRevenue)
                .todaySaleCount(todaySaleCount)
                .totalItems(totalItems)
                .inStock(inStock)
                .lowStock(lowStock)
                .outOfStock(outOfStock)
                .expiringSoon(expiringSoon)
                .unreadNotifications(unread)
                .build();
    }

    private Pharmacy resolvePharmacy(Long pharmacyTelegramId) {
        if (pharmacyTelegramId == null || pharmacyTelegramId <= 0) {
            throw new RuntimeException("pharmacyTelegramId is required");
        }
        return pharmacyRepository.findByTelegramId(pharmacyTelegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));
    }
}
