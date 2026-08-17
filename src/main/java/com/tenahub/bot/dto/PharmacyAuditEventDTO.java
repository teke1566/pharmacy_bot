package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PharmacyAuditEventDTO {
    private Long eventId;
    private Long staffId;
    private String employeeId;
    private String userName;
    private String action;
    private String module;
    private String entityType;
    private String entityId;
    private String oldValue;
    private String newValue;
    private String reason;
    private String correlationId;
    private LocalDateTime createdAt;
}
