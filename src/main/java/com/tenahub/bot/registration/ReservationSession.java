package com.tenahub.bot.registration;

public class ReservationSession {

    private Long pharmacyId;
    private String medicineName;
    private Integer quantity;
    private String customerName;

    private boolean waitingForCustomQuantity;
    private boolean waitingForName;
    private boolean waitingForPhone;

    // add this
    private Integer sourceMessageId;

    public Long getPharmacyId() {
        return pharmacyId;
    }

    public void setPharmacyId(Long pharmacyId) {
        this.pharmacyId = pharmacyId;
    }

    public String getMedicineName() {
        return medicineName;
    }

    public void setMedicineName(String medicineName) {
        this.medicineName = medicineName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public boolean isWaitingForCustomQuantity() {
        return waitingForCustomQuantity;
    }

    public void setWaitingForCustomQuantity(boolean waitingForCustomQuantity) {
        this.waitingForCustomQuantity = waitingForCustomQuantity;
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

    public Integer getSourceMessageId() {
        return sourceMessageId;
    }

    public void setSourceMessageId(Integer sourceMessageId) {
        this.sourceMessageId = sourceMessageId;
    }
}