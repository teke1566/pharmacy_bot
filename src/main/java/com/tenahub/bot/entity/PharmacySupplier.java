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
@Table(name = "pharmacy_suppliers")
public class PharmacySupplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pharmacy_id", nullable = false)
    private Long pharmacyId;

    @Column(nullable = false)
    private String name;

    private String phone;

    private String email;

    @Column(length = 1000)
    private String address;

    @Column(name = "contact_person")
    private String contactPerson;

    @Builder.Default
    @Column(nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(length = 2000)
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
