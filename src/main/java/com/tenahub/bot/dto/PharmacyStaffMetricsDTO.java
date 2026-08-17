package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PharmacyStaffMetricsDTO {
    private long activeStaff;
    private long invitedStaff;
    private long suspendedStaff;
    private long disabledStaff;
    private long activeToday;
    private long auditEventsToday;
}
