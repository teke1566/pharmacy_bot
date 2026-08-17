package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiniAppPharmacyReportRequestDTO {
    private String issueType;
    private String note;
    private String medicineName;
    private String pharmacyName;
}
