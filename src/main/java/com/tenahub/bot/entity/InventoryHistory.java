package com.tenahub.bot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "inventory_history")
public class InventoryHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long pharmacyId;

    private String medicineName;

    private Integer oldQuantity;

    private Integer newQuantity;

    @Enumerated(EnumType.STRING)
    private InventoryEventType eventType;

    private LocalDateTime createdAt;
}