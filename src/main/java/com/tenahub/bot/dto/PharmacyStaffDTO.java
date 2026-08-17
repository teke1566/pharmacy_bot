package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PharmacyStaffDTO {
    private Long staffId;
    private String employeeId;
    private Long telegramId;
    private String firstName;
    private String lastName;
    private String displayName;
    private String email;
    private String phone;
    private String photoUrl;
    private String role;
    private String status;
    private LocalDate startDate;
    private LocalDateTime invitedAt;
    private LocalDateTime joinedAt;
    private LocalDateTime lastActiveAt;
    private LocalDateTime lastLoginAt;
    private String licenseInfo;
    private String notes;
    private String suspendReason;
    private List<String> permissions;
    private List<String> grantedOverrides;
    private List<String> deniedOverrides;
    private String inviteToken;
}
