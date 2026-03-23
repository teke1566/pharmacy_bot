package com.tenahub.bot.registration;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MedicineSearchSession {
    private String medicineName;
    private SearchFilterType filterType;
}