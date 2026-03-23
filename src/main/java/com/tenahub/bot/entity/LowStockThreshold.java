package com.tenahub.bot.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        name = "low_stock_thresholds",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"pharmacyId", "medicineName"})
        }
)
public class LowStockThreshold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long pharmacyId;

    private String medicineName;

    private Integer threshold;
}