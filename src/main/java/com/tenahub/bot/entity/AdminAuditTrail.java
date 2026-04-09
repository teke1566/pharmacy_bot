package com.tenahub.bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "admin_audit_trail")
public class AdminAuditTrail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String actionType;

    @Column(nullable = false, length = 64)
    private String targetEntityType;

    private Long targetEntityId;

    @Column(nullable = false)
    private Long adminTelegramId;

    @Column(nullable = false)
    private LocalDateTime actionTimestamp;

    @Column(length = 2000)
    private String details;

    @Column(length = 2000)
    private String oldValue;

    @Column(length = 2000)
    private String newValue;

    @PrePersist
    void prePersist() {
        if (actionTimestamp == null) {
            actionTimestamp = LocalDateTime.now();
        }
    }
}
