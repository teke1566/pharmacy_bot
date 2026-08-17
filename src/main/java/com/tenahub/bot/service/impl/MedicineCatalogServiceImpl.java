package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.Medicine;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.repository.MedicineRepository;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.service.MedicineCatalogService;
import com.tenahub.bot.util.MedicineSearchNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MedicineCatalogServiceImpl implements MedicineCatalogService {

    private final MedicineRepository medicineRepository;
    private final PharmacyInventoryRepository inventoryRepository;

    @Override
    public Medicine findOrCreateFromName(String rawName) {
        String canonical = MedicineSearchNormalizer.normalizeToEnglishCanonical(rawName);
        if (canonical.isBlank()) {
            throw new RuntimeException("medicineName is required");
        }

        return medicineRepository.findByCanonicalName(canonical).orElseGet(() -> {
            String displayName = rawName == null || rawName.isBlank() ? canonical : rawName.trim();
            String ingredient = blankToNull(MedicineSearchNormalizer.catalogActiveIngredient(canonical));
            String strength = blankToNull(MedicineSearchNormalizer.catalogStrength(canonical));
            String dosageForm = blankToNull(MedicineSearchNormalizer.catalogDosageForm(canonical));
            LocalDateTime now = LocalDateTime.now();
            return medicineRepository.save(Medicine.builder()
                    .name(displayName)
                    .canonicalName(canonical)
                    .activeIngredient(ingredient)
                    .strength(strength)
                    .dosageForm(dosageForm)
                    .prescriptionRequired(false)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        });
    }

    @Override
    public void attachCatalogMedicine(PharmacyInventory item) {
        if (item == null || item.getMedicineName() == null || item.getMedicineName().isBlank()) {
            return;
        }

        Medicine medicine = findOrCreateFromName(item.getMedicineName());
        item.setCatalogMedicineId(medicine.getId());
        if (item.isRequiresPrescription() && !medicine.isPrescriptionRequired()) {
            medicine.setPrescriptionRequired(true);
            medicine.setUpdatedAt(LocalDateTime.now());
            medicineRepository.save(medicine);
        }
    }

    @Override
    public void refreshPrescriptionRequired(Long catalogMedicineId) {
        if (catalogMedicineId == null) {
            return;
        }

        Medicine medicine = medicineRepository.findById(catalogMedicineId).orElse(null);
        if (medicine == null) {
            return;
        }

        boolean anyRx = inventoryRepository.findByCatalogMedicineId(catalogMedicineId).stream()
                .anyMatch(PharmacyInventory::isRequiresPrescription);
        if (medicine.isPrescriptionRequired() != anyRx) {
            medicine.setPrescriptionRequired(anyRx);
            medicine.setUpdatedAt(LocalDateTime.now());
            medicineRepository.save(medicine);
        }
    }

    @Override
    public void repairDerivedFields() {
        List<Medicine> medicines = medicineRepository.findAll();
        for (Medicine medicine : medicines) {
            if (applyDerivedFields(medicine)) {
                medicine.setUpdatedAt(LocalDateTime.now());
                medicineRepository.save(medicine);
            }
        }
    }

    private boolean applyDerivedFields(Medicine medicine) {
        String source = medicine.getCanonicalName() != null ? medicine.getCanonicalName() : medicine.getName();
        String ingredient = blankToNull(MedicineSearchNormalizer.catalogActiveIngredient(source));
        String strength = blankToNull(MedicineSearchNormalizer.catalogStrength(source));
        String dosageForm = blankToNull(MedicineSearchNormalizer.catalogDosageForm(source));
        boolean changed = false;
        if (!Objects.equals(medicine.getActiveIngredient(), ingredient)) {
            medicine.setActiveIngredient(ingredient);
            changed = true;
        }
        if (!Objects.equals(medicine.getStrength(), strength)) {
            medicine.setStrength(strength);
            changed = true;
        }
        if (!Objects.equals(medicine.getDosageForm(), dosageForm)) {
            medicine.setDosageForm(dosageForm);
            changed = true;
        }
        return changed;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
