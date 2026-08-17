package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.PharmacySaleDTO;
import com.tenahub.bot.dto.PharmacySaleItemDTO;
import com.tenahub.bot.dto.PharmacySalesSummaryDTO;
import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.entity.PharmacySale;
import com.tenahub.bot.entity.PharmacySaleItem;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.repository.PharmacySaleItemRepository;
import com.tenahub.bot.repository.PharmacySaleRepository;
import com.tenahub.bot.service.PharmacySalesService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PharmacySalesServiceImpl implements PharmacySalesService {

    private final PharmacyRepository pharmacyRepository;
    private final PharmacyInventoryRepository inventoryRepository;
    private final PharmacySaleRepository saleRepository;
    private final PharmacySaleItemRepository saleItemRepository;

    @Override
    @Transactional
    public void recordFromReservation(MedicineReservation reservation, Long actorTelegramId) {
        if (reservation == null || reservation.getId() == null) {
            return;
        }
        if (saleRepository.findByReservationId(reservation.getId()).isPresent()) {
            return;
        }
        int qty = reservation.getRequestedQuantity() == null ? 0 : reservation.getRequestedQuantity();
        if (qty < 1) {
            return;
        }
        PharmacyInventory sku = inventoryRepository
                .findByPharmacyIdAndMedicineNameIgnoreCase(reservation.getPharmacyId(), reservation.getMedicineName())
                .orElse(null);
        BigDecimal unit = reservation.getUnitPrice() != null
                ? reservation.getUnitPrice()
                : (sku != null && sku.getPrice() != null ? sku.getPrice() : BigDecimal.ZERO);
        BigDecimal total = reservation.getTotalPrice() != null
                ? reservation.getTotalPrice()
                : unit.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        String currency = reservation.getCurrency() != null && !reservation.getCurrency().isBlank()
                ? reservation.getCurrency()
                : (sku != null && sku.getCurrency() != null ? sku.getCurrency() : "ETB");
        PharmacySale sale = saleRepository.save(PharmacySale.builder()
                .pharmacyId(reservation.getPharmacyId())
                .reservationId(reservation.getId())
                .customerName(reservation.getCustomerName())
                .actorTelegramId(actorTelegramId)
                .totalAmount(total)
                .currency(currency)
                .createdAt(LocalDateTime.now())
                .build());
        saleItemRepository.save(PharmacySaleItem.builder()
                .saleId(sale.getId())
                .pharmacyId(reservation.getPharmacyId())
                .medicineName(reservation.getMedicineName())
                .quantity(qty)
                .unitPrice(unit)
                .totalPrice(total)
                .build());
    }

    @Override
    public PharmacySalesSummaryDTO summary(Long pharmacyTelegramId, String period) {
        Pharmacy pharmacy = resolvePharmacy(pharmacyTelegramId);
        LocalDateTime[] range = rangeFor(period);
        List<PharmacySale> sales = saleRepository.findByPharmacyIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                pharmacy.getId(), range[0], range[1]);
        List<Long> saleIds = sales.stream().map(PharmacySale::getId).toList();
        List<PharmacySaleItem> items = saleIds.isEmpty()
                ? List.of()
                : saleItemRepository.findByPharmacyIdAndSaleIdIn(pharmacy.getId(), saleIds);
        BigDecimal revenue = sales.stream()
                .map(sale -> sale.getTotalAmount() == null ? BigDecimal.ZERO : sale.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int dispensed = items.stream().mapToInt(item -> item.getQuantity() == null ? 0 : item.getQuantity()).sum();
        Map<String, PharmacySaleItemDTO> byMedicine = new HashMap<>();
        for (PharmacySaleItem item : items) {
            String name = item.getMedicineName() == null ? "" : item.getMedicineName();
            PharmacySaleItemDTO current = byMedicine.computeIfAbsent(name, key -> PharmacySaleItemDTO.builder()
                    .medicineName(name)
                    .quantity(0)
                    .totalPrice(BigDecimal.ZERO)
                    .build());
            current.setQuantity(current.getQuantity() + (item.getQuantity() == null ? 0 : item.getQuantity()));
            current.setTotalPrice(current.getTotalPrice().add(item.getTotalPrice() == null ? BigDecimal.ZERO : item.getTotalPrice()));
        }
        List<PharmacySaleItemDTO> top = new ArrayList<>(byMedicine.values());
        top.sort(Comparator.comparing((PharmacySaleItemDTO dto) -> dto.getQuantity() == null ? 0 : dto.getQuantity()).reversed());
        if (top.size() > 5) {
            top = top.subList(0, 5);
        }
        return PharmacySalesSummaryDTO.builder()
                .period(normalizePeriod(period))
                .revenue(revenue)
                .saleCount(sales.size())
                .medicinesDispensed(dispensed)
                .topMedicines(top)
                .build();
    }

    @Override
    public List<PharmacySaleDTO> history(Long pharmacyTelegramId, String period) {
        Pharmacy pharmacy = resolvePharmacy(pharmacyTelegramId);
        LocalDateTime[] range = rangeFor(period);
        List<PharmacySale> sales = saleRepository.findByPharmacyIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                pharmacy.getId(), range[0], range[1]);
        List<PharmacySaleDTO> result = new ArrayList<>();
        for (PharmacySale sale : sales) {
            List<PharmacySaleItemDTO> items = saleItemRepository.findBySaleIdOrderByIdAsc(sale.getId()).stream()
                    .map(item -> PharmacySaleItemDTO.builder()
                            .medicineName(item.getMedicineName())
                            .quantity(item.getQuantity())
                            .unitPrice(item.getUnitPrice())
                            .totalPrice(item.getTotalPrice())
                            .build())
                    .toList();
            result.add(PharmacySaleDTO.builder()
                    .saleId(sale.getId())
                    .reservationId(sale.getReservationId())
                    .customerName(sale.getCustomerName())
                    .actorTelegramId(sale.getActorTelegramId())
                    .totalAmount(sale.getTotalAmount())
                    .currency(sale.getCurrency())
                    .createdAt(sale.getCreatedAt())
                    .items(items)
                    .build());
        }
        return result;
    }

    private Pharmacy resolvePharmacy(Long pharmacyTelegramId) {
        if (pharmacyTelegramId == null || pharmacyTelegramId <= 0) {
            throw new RuntimeException("pharmacyTelegramId is required");
        }
        return pharmacyRepository.findByTelegramId(pharmacyTelegramId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found"));
    }

    private String normalizePeriod(String period) {
        String value = period == null ? "daily" : period.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "weekly", "week" -> "weekly";
            case "monthly", "month" -> "monthly";
            default -> "daily";
        };
    }

    private LocalDateTime[] rangeFor(String period) {
        LocalDate today = LocalDate.now();
        return switch (normalizePeriod(period)) {
            case "weekly" -> new LocalDateTime[] { today.minusDays(6).atStartOfDay(), today.atTime(LocalTime.MAX) };
            case "monthly" -> new LocalDateTime[] { today.minusDays(29).atStartOfDay(), today.atTime(LocalTime.MAX) };
            default -> new LocalDateTime[] { today.atStartOfDay(), today.atTime(LocalTime.MAX) };
        };
    }
}
