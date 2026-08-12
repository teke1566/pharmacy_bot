package com.tenahub.bot.service;

import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.MedicineReservationStatus;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyRegistration;

import java.time.LocalTime;
import java.util.List;

import org.springframework.data.domain.Page;

public interface AdminService {

    String viewPendingRegistrations();

    String viewPendingLicenseUpdates();

    String viewReservationOversight();

    String viewSystemSummary();
    String viewDetailedReservationOversight();

    String viewDetailedSystemSummary();

    List<PharmacyRegistration> getPendingRegistrations();

    List<Pharmacy> getPendingLicenseUpdates();

    Page<PharmacyRegistration> getPendingRegistrationsPage(int page, int size);

    Page<Pharmacy> getPendingLicenseUpdatesPage(int page, int size);
    Page<Pharmacy> getPharmacyManagementPage(int page, int size);
    Page<Pharmacy> searchPharmaciesByName(String name, int page, int size);
    Page<Pharmacy> searchPharmaciesByPhone(String phone, int page, int size);
    Page<Pharmacy> searchPharmaciesByTelegramId(String telegramIdText, int page, int size);
    Pharmacy getPharmacy(Long pharmacyId);
    long countInventoryItems(Long pharmacyId);
    long countReservations(Long pharmacyId, MedicineReservationStatus status);
    void approvePharmacy(Long pharmacyId);
    void setPharmacyNotApproved(Long pharmacyId);
    void suspendPharmacy(Long pharmacyId);
    void reactivatePharmacy(Long pharmacyId);
    void updatePharmacyName(Long pharmacyId, String name);
    void updatePharmacyPhone(Long pharmacyId, String phone);
    void updatePharmacyLandmark(Long pharmacyId, String landmark);
    void updatePharmacyHours(Long pharmacyId, LocalTime openTime, LocalTime closeTime);
    void updatePharmacyLocation(Long pharmacyId, Double latitude, Double longitude, String city, String area);
    String viewPharmacyDetails();

String viewTopMedicinesDetails();

String viewLowStockDetails();
String viewReservationsByStatus(MedicineReservationStatus status);

    MedicineReservation getReservation(Long id);

    String buildAdminReservationDetail(Long id);

    void adminCancelReservation(Long id);

    void adminExpireReservation(Long id);

    void adminFulfillReservation(Long id);

    org.springframework.data.domain.Page<MedicineReservation> getReservationsByStatusPage(
            MedicineReservationStatus status, int page, int size);

}