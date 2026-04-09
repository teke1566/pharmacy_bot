package com.tenahub.bot.registration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminPharmacyEditSession {
    private Long pharmacyId;
    private AdminPharmacyEditField field;
    private String openTime;
}