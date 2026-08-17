package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PharmacyStaffInviteRequestDTO {
    private String firstName;
    private String lastName;
    private String phone;
    private String email;
    private String employeeId;
    private String role;
    private LocalDate startDate;
    private String licenseInfo;
    private String notes;
    private Long invitedTelegramId;
    private List<String> grantPermissions;
    private List<String> denyPermissions;
}
