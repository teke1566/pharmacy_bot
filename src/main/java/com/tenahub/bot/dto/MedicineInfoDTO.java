package com.tenahub.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicineInfoDTO {
    private String name;
    private String use;
    private String howToTake;
    private String sideEffects;
    private String warnings;
    private String storage;
    private String missedDose;
    private String safetyNote;
}
