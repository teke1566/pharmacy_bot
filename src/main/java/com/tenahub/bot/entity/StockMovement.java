package com.tenahub.bot.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "stock_movements")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pharmacy_id", nullable = false)
    private Long pharmacyId;

    @Column(name = "inventory_id", nullable = false)
    private Long inventoryId;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "medicine_name")
    private String medicineName;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 32)
    private StockMovementType movementType;

    @Column(name = "quantity_change", nullable = false)
    private Integer quantityChange;

    @Column(name = "quantity_before")
    private Integer quantityBefore;

    @Column(name = "quantity_after")
    private Integer quantityAfter;

    @Column(name = "batch_quantity_before")
    private Integer batchQuantityBefore;

    @Column(name = "batch_quantity_after")
    private Integer batchQuantityAfter;

    @Column(name = "actor_telegram_id")
    private Long actorTelegramId;

    @Column(length = 2000)
    private String reason;

    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
