package com.tenahub.bot.registration;

import lombok.Data;

@Data
public class RegistrationSession {

    private RegistrationStep step;

    private String name;
    private String city;
    private String area;
    private String phone;
    private String medicines;
    private String openTime;
    private String closeTime;

    private Integer tempHour;

    private Double latitude;
    private Double longitude;
    private String formattedAddress;
    private String landmark;
private boolean waitingForLandmark;
    private String plusCode;

    private boolean waitingForExactLocation;
    private boolean waitingForGoogleMapLink;
    private boolean waitingForRegionSelection;
    private boolean waitingForCitySelection;
    private boolean waitingForSubCitySelection;
    private boolean waitingForAreaSelection;

    private String selectedRegion;
    private String selectedCity;
    private String selectedSubCity;
    

    public void clearLocationFlags() {
        this.waitingForExactLocation = false;
        this.waitingForGoogleMapLink = false;
        this.waitingForRegionSelection = false;
        this.waitingForCitySelection = false;
        this.waitingForSubCitySelection = false;
        this.waitingForAreaSelection = false;
        this.waitingForLandmark = false;
    }

    public void setExactLocationMode() {
        clearLocationFlags();
        this.waitingForExactLocation = true;
    }

    public void setGoogleMapMode() {
        clearLocationFlags();
        this.waitingForGoogleMapLink = true;
    }

    public void setRegionMode() {
        clearLocationFlags();
        this.waitingForRegionSelection = true;
    }

    public void setCityMode() {
        clearLocationFlags();
        this.waitingForCitySelection = true;
    }

    public void setSubCityMode() {
        clearLocationFlags();
        this.waitingForSubCitySelection = true;
    }

    public void setAreaMode() {
        clearLocationFlags();
        this.waitingForAreaSelection = true;
    }

    public boolean isInAnyLocationMode() {
        return waitingForExactLocation
                || waitingForGoogleMapLink
                || waitingForRegionSelection
                || waitingForCitySelection
                || waitingForSubCitySelection
                || waitingForAreaSelection;
    }
    public void setLandmarkMode() {
    clearLocationFlags();
    this.waitingForLandmark = true;
}
}