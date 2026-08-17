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
@Table(name = "pharmacy_sales")
public class PharmacySale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pharmacy_id", nullable = false)
    private Long pharmacyId;

    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "actor_telegram_id")
    private Long actorTelegramId;

    @Column(name = "total_amount", precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Builder.Default
    @Column(length = 8)
    private String currency = "ETB";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
