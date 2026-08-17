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
@Table(name = "pharmacy_pricing_policies")
public class PharmacyPricingPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pharmacy_id", nullable = false, unique = true)
    private Long pharmacyId;

    @Column(name = "approval_threshold_percent", precision = 8, scale = 2)
    private BigDecimal approvalThresholdPercent;

    @Builder.Default
    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "Africa/Addis_Ababa";

    @Builder.Default
    @Column(name = "tax_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "costing_method", nullable = false, length = 32)
    private String costingMethod = "WEIGHTED_AVERAGE";

    @Builder.Default
    @Column(name = "currency", length = 8)
    private String currency = "ETB";

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
