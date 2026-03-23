package com.tenahub.bot.registration;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MultiMedicineSearchSession {
    private Long chatId;
    private List<String> selectedMedicines = new ArrayList<>();
    private boolean waitingForMedicineInput;
    private boolean waitingForLocationChoice;
    private boolean waitingForExactLocation;
}