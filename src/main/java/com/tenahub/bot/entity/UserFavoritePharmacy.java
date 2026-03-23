package com.tenahub.bot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_favorite_pharmacies",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "pharmacy_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserFavoritePharmacy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "pharmacy_id", nullable = false)
    private Long pharmacyId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}