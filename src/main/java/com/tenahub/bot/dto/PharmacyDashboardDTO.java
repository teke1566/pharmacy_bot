package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PharmacyDashboardDTO {
    private Integer pendingPrescriptions;
    private Integer pendingReservations;
    private Integer fulfilledToday;
    private BigDecimal todayRevenue;
    private Integer todaySaleCount;
    private Integer totalItems;
    private Integer inStock;
    private Integer lowStock;
    private Integer outOfStock;
    private Integer expiringSoon;
    private Long unreadNotifications;
}
