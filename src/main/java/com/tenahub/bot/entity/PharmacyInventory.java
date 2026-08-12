package com.tenahub.bot.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "pharmacy_inventory")
public class PharmacyInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long pharmacyId;

    private String medicineName;

    private Integer quantity;

    private boolean outOfStock;

    @Column(name = "low_stock_threshold")
    private Integer lowStockThreshold;

    @Builder.Default
    @Column(name = "low_stock_alert_sent")
    private Boolean lowStockAlertSent = false;
    @Column(name = "price", precision = 12, scale = 2)
private BigDecimal price;

@Column(name = "currency")
private String currency;

@Builder.Default
@Column(name = "requires_prescription", nullable = false)
private boolean requiresPrescription = false;

private LocalDateTime updatedAt;
}