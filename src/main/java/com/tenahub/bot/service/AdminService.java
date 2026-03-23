package com.tenahub.bot.service;

import com.tenahub.bot.entity.MedicineReservationStatus;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyRegistration;

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
    String viewPharmacyDetails();

String viewTopMedicinesDetails();

String viewLowStockDetails();
String viewReservationsByStatus(MedicineReservationStatus status);



}