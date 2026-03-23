package com.tenahub.bot.registration;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MedicineSelectionSession {

    private boolean forRegistration;

    private boolean waitingCustomInput;

    private Integer pickerMessageId;

    private List<String> selectedMedicines = new ArrayList<>();
}