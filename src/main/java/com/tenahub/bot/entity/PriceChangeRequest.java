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
@Table(name = "price_change_requests")
public class PriceChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pharmacy_id", nullable = false)
    private Long pharmacyId;

    @Column(name = "inventory_id", nullable = false)
    private Long inventoryId;

    @Column(name = "medicine_name")
    private String medicineName;

    @Column(name = "current_selling_price", precision = 12, scale = 2)
    private BigDecimal currentSellingPrice;

    @Column(name = "proposed_selling_price", precision = 12, scale = 2)
    private BigDecimal proposedSellingPrice;

    @Column(name = "purchase_cost_ref", precision = 12, scale = 2)
    private BigDecimal purchaseCostRef;

    @Column(name = "margin_before", precision = 8, scale = 4)
    private BigDecimal marginBefore;

    @Column(name = "margin_after", precision = 8, scale = 4)
    private BigDecimal marginAfter;

    @Column(name = "percent_change", precision = 8, scale = 4)
    private BigDecimal percentChange;

    @Column(name = "currency", length = 8)
    private String currency;

    @Column(name = "effective_at")
    private LocalDateTime effectiveAt;

    @Column(name = "reason", length = 2000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PriceChangeRequestStatus status;

    @Column(name = "requested_by_staff_id")
    private Long requestedByStaffId;

    @Column(name = "requested_by_telegram_id")
    private Long requestedByTelegramId;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "approved_by_staff_id")
    private Long approvedByStaffId;

    @Column(name = "approved_by_telegram_id")
    private Long approvedByTelegramId;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejection_reason", length = 2000)
    private String rejectionReason;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
