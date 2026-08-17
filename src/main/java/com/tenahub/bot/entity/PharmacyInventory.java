package com.tenahub.bot.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    @Column(name = "catalog_medicine_id")
    private Long catalogMedicineId;

    private Integer quantity;

    private boolean outOfStock;

    @Column(name = "low_stock_threshold")
    private Integer lowStockThreshold;

    @Builder.Default
    @Column(name = "low_stock_alert_sent")
    private Boolean lowStockAlertSent = false;

    @Column(name = "price", precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "purchase_cost", precision = 12, scale = 2)
    private BigDecimal purchaseCost;

    @Column(name = "currency")
    private String currency;

    @Version
    @Column(name = "version")
    private Long version;

    @Builder.Default
    @Column(name = "requires_prescription", nullable = false)
    private boolean requiresPrescription = false;

    @Column(name = "batch_number", length = 64)
    private String batchNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "strength", length = 64)
    private String strength;

    @Column(name = "dosage_form", length = 64)
    private String dosageForm;

    @Builder.Default
    @Column(name = "archived", nullable = false)
    private boolean archived = false;

    private LocalDateTime updatedAt;
}