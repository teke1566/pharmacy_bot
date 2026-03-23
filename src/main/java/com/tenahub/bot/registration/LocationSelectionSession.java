package com.tenahub.bot.registration;

import lombok.Data;

@Data
public class LocationSelectionSession {

    private Long chatId;
    private LocationFlowType flowType;

    private String selectedRegion;
    private String selectedCity;
    private String selectedSubCity;
    private String selectedArea;

    private boolean waitingRegion;
    private boolean waitingCity;
    private boolean waitingSubCity;
    private boolean waitingArea;

    public void clearFlags() {
        this.waitingRegion = false;
        this.waitingCity = false;
        this.waitingSubCity = false;
        this.waitingArea = false;
    }

    public void setRegionMode() {
        clearFlags();
        this.waitingRegion = true;
    }

    public void setCityMode() {
        clearFlags();
        this.waitingCity = true;
    }

    public void setSubCityMode() {
        clearFlags();
        this.waitingSubCity = true;
    }

    public void setAreaMode() {
        clearFlags();
        this.waitingArea = true;
    }

    public boolean isInFlow() {
        return waitingRegion || waitingCity || waitingSubCity || waitingArea;
    }
}