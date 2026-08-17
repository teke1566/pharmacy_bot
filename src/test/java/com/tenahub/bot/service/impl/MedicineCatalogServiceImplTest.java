package com.tenahub.bot.service.impl;

import com.tenahub.bot.entity.Medicine;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.repository.MedicineRepository;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicineCatalogServiceImplTest {

    @Mock
    private MedicineRepository medicineRepository;
    @Mock
    private PharmacyInventoryRepository inventoryRepository;

    private MedicineCatalogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MedicineCatalogServiceImpl(medicineRepository, inventoryRepository);
    }

    @Test
    void findOrCreateFromName_reusesCanonicalRow() {
        Medicine existing = Medicine.builder()
                .id(9L)
                .name("paracetamol")
                .canonicalName("paracetamol")
                .activeIngredient("paracetamol")
                .build();
        when(medicineRepository.findByCanonicalName("paracetamol")).thenReturn(Optional.of(existing));

        Medicine result = service.findOrCreateFromName("Panadol");

        assertEquals(9L, result.getId());
        verify(medicineRepository).findByCanonicalName("paracetamol");
    }

    @Test
    void attachCatalogMedicine_linksInventoryAndOrsRx() {
        when(medicineRepository.findByCanonicalName("insulin")).thenReturn(Optional.empty());
        when(medicineRepository.save(any(Medicine.class))).thenAnswer(invocation -> {
            Medicine saved = invocation.getArgument(0);
            saved.setId(4L);
            return saved;
        });

        PharmacyInventory item = PharmacyInventory.builder()
                .medicineName("insulin")
                .requiresPrescription(true)
                .build();

        service.attachCatalogMedicine(item);

        assertEquals(4L, item.getCatalogMedicineId());
        ArgumentCaptor<Medicine> captor = ArgumentCaptor.forClass(Medicine.class);
        verify(medicineRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        Medicine last = captor.getValue();
        assertTrue(last.isPrescriptionRequired());
        assertEquals("insulin", last.getCanonicalName());
        assertEquals("insulin", last.getActiveIngredient());
    }

    @Test
    void findOrCreateFromName_regularInsulinUsesInsulinIngredient() {
        when(medicineRepository.findByCanonicalName("regular insulin")).thenReturn(Optional.empty());
        when(medicineRepository.save(any(Medicine.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Medicine result = service.findOrCreateFromName("regular insulin");

        assertEquals("insulin", result.getActiveIngredient());
        assertEquals("regular insulin", result.getCanonicalName());
    }

    @Test
    void findOrCreateFromName_parsesStrengthAndDosageForm() {
        when(medicineRepository.findByCanonicalName("paracetamol 500mg tab")).thenReturn(Optional.empty());
        when(medicineRepository.save(any(Medicine.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Medicine result = service.findOrCreateFromName("paracetamol 500mg tab");

        assertEquals("paracetamol", result.getActiveIngredient());
        assertEquals("500mg", result.getStrength());
        assertEquals("tablet", result.getDosageForm());
    }

    @Test
    void repairDerivedFields_updatesExistingRowsFromStoredName() {
        Medicine regular = Medicine.builder()
                .id(8L)
                .name("regular insulin")
                .canonicalName("regular insulin")
                .activeIngredient("regular")
                .build();
        when(medicineRepository.findAll()).thenReturn(List.of(regular));

        service.repairDerivedFields();

        assertEquals("insulin", regular.getActiveIngredient());
        verify(medicineRepository).save(regular);
    }

    @Test
    void refreshPrescriptionRequired_clearsFlagWhenNoInventoryIsRx() {
        Medicine medicine = Medicine.builder()
                .id(4L)
                .canonicalName("insulin")
                .prescriptionRequired(true)
                .build();
        when(medicineRepository.findById(4L)).thenReturn(Optional.of(medicine));
        when(inventoryRepository.findByCatalogMedicineId(4L)).thenReturn(List.of(
                PharmacyInventory.builder().catalogMedicineId(4L).requiresPrescription(false).build()
        ));

        service.refreshPrescriptionRequired(4L);

        assertFalse(medicine.isPrescriptionRequired());
        verify(medicineRepository).save(medicine);
    }
}
