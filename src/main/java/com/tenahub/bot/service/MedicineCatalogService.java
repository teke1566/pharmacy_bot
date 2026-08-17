package com.tenahub.bot.service;

import com.tenahub.bot.entity.Medicine;
import com.tenahub.bot.entity.PharmacyInventory;

public interface MedicineCatalogService {

    Medicine findOrCreateFromName(String rawName);

    void attachCatalogMedicine(PharmacyInventory item);

    void refreshPrescriptionRequired(Long catalogMedicineId);

    void repairDerivedFields();
}
