package com.tenahub.bot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "price_history")
public class PriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pharmacy_id", nullable = false)
    private Long pharmacyId;

    @Column(name = "inventory_id", nullable = false)
    private Long inventoryId;

    @Column(name = "medicine_name")
    private String medicineName;

    @Column(name = "old_selling_price", precision = 12, scale = 2)
    private BigDecimal oldSellingPrice;

    @Column(name = "new_selling_price", precision = 12, scale = 2)
    private BigDecimal newSellingPrice;

    @Column(name = "old_purchase_cost", precision = 12, scale = 2)
    private BigDecimal oldPurchaseCost;

    @Column(name = "new_purchase_cost", precision = 12, scale = 2)
    private BigDecimal newPurchaseCost;

    @Column(name = "currency", length = 8)
    private String currency;

    @Column(name = "reason", length = 2000)
    private String reason;

    @Column(name = "actor_staff_id")
    private Long actorStaffId;

    @Column(name = "actor_telegram_id")
    private Long actorTelegramId;

    @Column(name = "actor_name_snapshot", length = 255)
    private String actorNameSnapshot;

    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "effective_at")
    private LocalDateTime effectiveAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
