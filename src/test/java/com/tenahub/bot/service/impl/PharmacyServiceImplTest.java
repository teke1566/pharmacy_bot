package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.PharmacyResponseDTO;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.repository.PharmacyRatingRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PharmacyServiceImplTest {

    @Mock
    private PharmacyRepository pharmacyRepository;
    @Mock
    private PharmacyRatingRepository ratingRepository;
    @Mock
    private PharmacyInventoryRepository inventoryRepository;

    @InjectMocks
    private PharmacyServiceImpl service;

    @Test
    void searchMedicine_returnsEmptyForBlankQuery() {
        assertEquals(List.of(), service.searchMedicine("  "));
    }

    @Test
    void searchMedicine_skipsLicenseSuspendedPharmacies() {
        PharmacyInventory item = PharmacyInventory.builder()
                .id(20L)
                .pharmacyId(2L)
                .medicineName("paracetamol")
                .quantity(5)
                .price(new BigDecimal("12.50"))
                .build();
        when(inventoryRepository.findByMedicineNameIgnoreCase("paracetamol")).thenReturn(List.of(item));
        when(pharmacyRepository.findById(2L)).thenReturn(Optional.of(
                Pharmacy.builder().id(2L).name("Hidden").licenseSuspended(true).build()));

        assertTrue(service.searchMedicine("Panadol").isEmpty());
    }

    @Test
    void searchMedicine_mapsActivePharmacy() {
        PharmacyInventory item = PharmacyInventory.builder()
                .id(20L)
                .pharmacyId(2L)
                .medicineName("paracetamol")
                .quantity(5)
                .price(new BigDecimal("12.50"))
                .build();
        when(inventoryRepository.findByMedicineNameIgnoreCase("paracetamol")).thenReturn(List.of(item));
        when(pharmacyRepository.findById(2L)).thenReturn(Optional.of(
                Pharmacy.builder()
                        .id(2L)
                        .name("City Rx")
                        .area("Bole")
                        .approved(true)
                        .licenseSuspended(false)
                        .latitude(9.01)
                        .longitude(38.75)
                        .build()));

        List<PharmacyResponseDTO> results = service.searchMedicine("paracetamol");

        assertEquals(1, results.size());
        assertEquals("City Rx", results.get(0).getName());
        assertEquals(5, results.get(0).getStockQuantity());
        assertEquals("paracetamol", results.get(0).getMedicineName());
    }

    @Test
    void searchMedicineWithArea_filtersByArea() {
        PharmacyInventory item = PharmacyInventory.builder()
                .id(20L)
                .pharmacyId(2L)
                .medicineName("paracetamol")
                .quantity(3)
                .build();
        when(inventoryRepository.findByMedicineNameIgnoreCase("paracetamol")).thenReturn(List.of(item));
        when(pharmacyRepository.findById(2L)).thenReturn(Optional.of(
                Pharmacy.builder()
                        .id(2L)
                        .name("City Rx")
                        .area("Bole Atlas")
                        .licenseSuspended(false)
                        .latitude(9.01)
                        .longitude(38.75)
                        .build()));

        assertEquals(1, service.searchMedicineWithArea("paracetamol", "bole").size());
        assertTrue(service.searchMedicineWithArea("paracetamol", "piassa").isEmpty());
    }
}
