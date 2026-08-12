package com.tenahub.bot.registration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MultiReservationSession {

    private Long pharmacyId;
    
    private List<String> matchedMedicines;
    
    private Map<String, Integer> medicineQuantities;
    
    private String customerName;
    
    private String customerPhone;
    
    private String currentStep;
    
    private String currentMedicineBeingEdited;
    
    private boolean waitingForName;
    
    private boolean waitingForPhone;
    
    public MultiReservationSession() {
        this.medicineQuantities = new HashMap<>();
        this.currentStep = "PICKING_QUANTITIES";
        this.waitingForName = false;
        this.waitingForPhone = false;
    }

    public Long getPharmacyId() {
        return pharmacyId;
    }

    public void setPharmacyId(Long pharmacyId) {
        this.pharmacyId = pharmacyId;
    }

    public List<String> getMatchedMedicines() {
        return matchedMedicines;
    }

    public void setMatchedMedicines(List<String> matchedMedicines) {
        this.matchedMedicines = matchedMedicines;
    }

    public Map<String, Integer> getMedicineQuantities() {
        return medicineQuantities;
    }

    public void setMedicineQuantities(Map<String, Integer> medicineQuantities) {
        this.medicineQuantities = medicineQuantities;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(String currentStep) {
        this.currentStep = currentStep;
    }

    public String getCurrentMedicineBeingEdited() {
        return currentMedicineBeingEdited;
    }

    public void setCurrentMedicineBeingEdited(String currentMedicineBeingEdited) {
        this.currentMedicineBeingEdited = currentMedicineBeingEdited;
    }

    public boolean isWaitingForName() {
        return waitingForName;
    }

    public void setWaitingForName(boolean waitingForName) {
        this.waitingForName = waitingForName;
    }

    public boolean isWaitingForPhone() {
        return waitingForPhone;
    }

    public void setWaitingForPhone(boolean waitingForPhone) {
        this.waitingForPhone = waitingForPhone;
    }

    public void setQuantityForMedicine(String medicineName, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            this.medicineQuantities.remove(medicineName);
        } else {
            this.medicineQuantities.put(medicineName, quantity);
        }
    }

    public Integer getQuantityForMedicine(String medicineName) {
        return this.medicineQuantities.getOrDefault(medicineName, null);
    }

    public int getSelectedMedicinesCount() {
        return (int) this.medicineQuantities.values().stream()
                .filter(q -> q != null && q > 0)
                .count();
    }

    public boolean allMedicinesHaveQuantities() {
        if (matchedMedicines == null || matchedMedicines.isEmpty()) {
            return false;
        }
        return matchedMedicines.stream()
                .allMatch(medicine -> getQuantityForMedicine(medicine) != null && getQuantityForMedicine(medicine) > 0);
    }
}
