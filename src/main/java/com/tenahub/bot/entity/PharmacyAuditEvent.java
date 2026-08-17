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
@Table(name = "pharmacy_audit_events")
public class PharmacyAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pharmacy_id", nullable = false)
    private Long pharmacyId;

    @Column(name = "actor_telegram_id")
    private Long actorTelegramId;

    @Column(name = "staff_id")
    private Long staffId;

    @Column(name = "employee_id", length = 64)
    private String employeeId;

    @Column(name = "user_name_snapshot", length = 255)
    private String userNameSnapshot;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "module", nullable = false, length = 64)
    private String module;

    @Column(name = "entity_type", length = 64)
    private String entityType;

    @Column(name = "entity_id", length = 128)
    private String entityId;

    @Column(name = "old_value", length = 4000)
    private String oldValue;

    @Column(name = "new_value", length = 4000)
    private String newValue;

    @Column(name = "reason", length = 2000)
    private String reason;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
