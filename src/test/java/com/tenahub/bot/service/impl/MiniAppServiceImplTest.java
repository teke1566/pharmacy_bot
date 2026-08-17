package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.MiniAppMedicineSummaryDTO;
import com.tenahub.bot.dto.PharmacyResponseDTO;
import com.tenahub.bot.entity.Medicine;
import com.tenahub.bot.entity.PharmacyInventory;
import com.tenahub.bot.repository.MedicineRepository;
import com.tenahub.bot.repository.PharmacyInventoryRepository;
import com.tenahub.bot.service.PharmacyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MiniAppServiceImplTest {

    @Mock
    private PharmacyService pharmacyService;
    @Mock
    private PharmacyInventoryRepository pharmacyInventoryRepository;
    @Mock
    private MedicineRepository medicineRepository;

    private MiniAppServiceImpl miniAppService;

    @BeforeEach
    void setUp() {
        miniAppService = new MiniAppServiceImpl();
        ReflectionTestUtils.setField(miniAppService, "pharmacyService", pharmacyService);
        ReflectionTestUtils.setField(miniAppService, "pharmacyInventoryRepository", pharmacyInventoryRepository);
        ReflectionTestUtils.setField(miniAppService, "medicineRepository", medicineRepository);
    }

    @Test
    void search_appliesVerifiedOnlyFilter() {
        PharmacyResponseDTO verified = dto("Verified", true, false, true, 1.0, 45.0, "10.00", false);
        PharmacyResponseDTO notVerified = dto("Unverified", false, false, true, 2.0, 44.0, "11.00", false);

        when(pharmacyService.searchMedicine("paracetamol")).thenReturn(List.of(notVerified, verified));

        List<PharmacyResponseDTO> result = miniAppService.search("paracetamol", null, null, 12L, null, "Verified only");

        assertEquals(1, result.size());
        assertTrue(result.get(0).isVerified());
        assertEquals("Verified", result.get(0).getName());
        verify(pharmacyService).searchMedicine("paracetamol");
    }

    @Test
    void search_appliesPrescriptionRequiredFilter() {
        PharmacyResponseDTO rx = dto("Rx Pharmacy", true, true, true, 1.5, 40.0, "8.50", false);
        PharmacyResponseDTO noRx = dto("NoRx Pharmacy", true, false, true, 1.2, 41.0, "8.00", false);

        when(pharmacyService.searchMedicine("amoxicillin")).thenReturn(List.of(noRx, rx));

        List<PharmacyResponseDTO> result = miniAppService.search("amoxicillin", null, null, 12L, null, "Prescription required");

        assertEquals(1, result.size());
        assertTrue(result.get(0).isRequiresPrescription());
        assertEquals("Rx Pharmacy", result.get(0).getName());
    }

    @Test
    void search_appliesNoPrescriptionFilter() {
        PharmacyResponseDTO rx = dto("Rx Pharmacy", true, true, true, 1.5, 40.0, "8.50", false);
        PharmacyResponseDTO noRx = dto("NoRx Pharmacy", true, false, true, 1.2, 41.0, "8.00", false);

        when(pharmacyService.searchMedicine("ibuprofen")).thenReturn(List.of(rx, noRx));

        List<PharmacyResponseDTO> result = miniAppService.search("ibuprofen", null, null, 12L, "No prescription", null);

        assertEquals(1, result.size());
        assertFalse(result.get(0).isRequiresPrescription());
        assertEquals("NoRx Pharmacy", result.get(0).getName());
    }

    @Test
    void search_appliesInStockFilter() {
        PharmacyResponseDTO inStock = dto("In stock", true, false, true, 1.0, 45.0, "10.00", false);
        inStock.setStockQuantity(8);
        PharmacyResponseDTO empty = dto("Empty", true, false, true, 1.1, 44.0, "9.00", true);
        empty.setStockQuantity(0);

        when(pharmacyService.searchMedicine("paracetamol")).thenReturn(List.of(empty, inStock));

        List<PharmacyResponseDTO> result = miniAppService.search("paracetamol", null, null, 12L, null, "inStock");

        assertEquals(1, result.size());
        assertEquals("In stock", result.get(0).getName());
        assertFalse(result.get(0).isOutOfStock());
    }

    @Test
    void search_openNowWithoutSort_defaultsToNearestAndFiltersClosed() {
        PharmacyResponseDTO nearestOpen = dto("Nearest Open", true, false, true, 0.8, 40.0, "10.00", false);
        PharmacyResponseDTO fartherOpen = dto("Farther Open", true, false, true, 2.4, 50.0, "10.00", false);
        PharmacyResponseDTO closed = dto("Closed", true, false, false, 0.3, 60.0, "10.00", false);

        when(pharmacyService.searchMedicineNearby(eq("cetirizine"), anyDouble(), anyDouble(), anyLong()))
                .thenReturn(List.of(fartherOpen, closed, nearestOpen));

        List<PharmacyResponseDTO> result = miniAppService.search("cetirizine", 9.0, 38.0, 7L, null, "Open now");

        assertEquals(2, result.size());
        assertEquals("Nearest Open", result.get(0).getName());
        assertEquals("Farther Open", result.get(1).getName());
        assertTrue(result.stream().allMatch(PharmacyResponseDTO::isOpenNow));
    }

    @Test
    void search_withCoordinatesAndNullUserId_usesZeroUserIdFallback() {
        when(pharmacyService.searchMedicineNearby(eq("azithromycin"), eq(8.98), eq(38.79), eq(0L)))
                .thenReturn(List.of());

        List<PharmacyResponseDTO> result = miniAppService.search("azithromycin", 8.98, 38.79, null, null, null);

        assertTrue(result.isEmpty());
        verify(pharmacyService).searchMedicineNearby("azithromycin", 8.98, 38.79, 0L);
    }

    @Test
    void searchMultipleMedicines_forwardsToPharmacyServiceWithZeroUserIdFallback() {
        when(pharmacyService.searchMultipleMedicinesNearby(eq(List.of("insulin", "paracetamol")), eq(9.01), eq(38.75), eq(0L)))
                .thenReturn(List.of());

        var result = miniAppService.searchMultipleMedicines(List.of("insulin", "paracetamol"), 9.01, 38.75, null);

        assertTrue(result.isEmpty());
        verify(pharmacyService).searchMultipleMedicinesNearby(List.of("insulin", "paracetamol"), 9.01, 38.75, 0L);
    }

    @Test
    void searchMedicineCatalog_groupsByCatalogIdAndOmitsZeroPrice() {
        Medicine insulin = Medicine.builder()
                .id(5L)
                .name("insulin")
                .canonicalName("insulin")
                .prescriptionRequired(false)
                .build();
        PharmacyInventory rxRow = PharmacyInventory.builder()
                .id(1L)
                .pharmacyId(10L)
                .catalogMedicineId(5L)
                .quantity(3)
                .outOfStock(false)
                .price(new BigDecimal("0"))
                .requiresPrescription(true)
                .build();
        PharmacyInventory priced = PharmacyInventory.builder()
                .id(2L)
                .pharmacyId(11L)
                .catalogMedicineId(5L)
                .quantity(4)
                .outOfStock(false)
                .price(new BigDecimal("120.00"))
                .requiresPrescription(false)
                .build();
        PharmacyInventory nullPrice = PharmacyInventory.builder()
                .id(3L)
                .pharmacyId(12L)
                .catalogMedicineId(5L)
                .quantity(1)
                .outOfStock(false)
                .price(null)
                .build();

        when(medicineRepository.searchByName("insulin", "insulin")).thenReturn(List.of(insulin));
        when(pharmacyInventoryRepository.findByCatalogMedicineIdIn(List.of(5L)))
                .thenReturn(List.of(rxRow, priced, nullPrice));

        List<MiniAppMedicineSummaryDTO> result = miniAppService.searchMedicineCatalog("insulin", null, null);

        assertEquals(1, result.size());
        assertEquals(5L, result.get(0).getMedicineId());
        assertEquals(new BigDecimal("120.00"), result.get(0).getPrice());
        assertEquals(3, result.get(0).getAvailablePharmacies());
        assertTrue(result.get(0).isPrescriptionRequired());
        assertFalse(result.get(0).isOutOfStock());
    }

    @Test
    void searchAnalogues_returnsOtherCatalogMedicinesSharingIngredient() {
        Medicine selected = Medicine.builder()
                .id(11L)
                .name("Insulin Glargine")
                .canonicalName("insulin glargine")
                .activeIngredient("insulin")
                .prescriptionRequired(true)
                .build();
        Medicine analogue = Medicine.builder()
                .id(22L)
                .name("Insulin Lispro")
                .canonicalName("insulin lispro")
                .activeIngredient("insulin")
                .prescriptionRequired(true)
                .build();
        PharmacyInventory analogueRow = PharmacyInventory.builder()
                .id(220L)
                .pharmacyId(2L)
                .catalogMedicineId(22L)
                .medicineName("Insulin Lispro")
                .quantity(6)
                .outOfStock(false)
                .price(new BigDecimal("380.00"))
                .requiresPrescription(true)
                .build();
        PharmacyInventory analogueRow2 = PharmacyInventory.builder()
                .id(221L)
                .pharmacyId(3L)
                .catalogMedicineId(22L)
                .medicineName("Insulin Lispro")
                .quantity(2)
                .outOfStock(false)
                .price(new BigDecimal("395.00"))
                .requiresPrescription(true)
                .build();

        when(medicineRepository.findById(11L)).thenReturn(java.util.Optional.of(selected));
        when(medicineRepository.findByActiveIngredientIgnoreCase("insulin"))
                .thenReturn(List.of(selected, analogue));
        when(pharmacyInventoryRepository.findByCatalogMedicineIdIn(List.of(22L)))
                .thenReturn(List.of(analogueRow, analogueRow2));

        List<MiniAppMedicineSummaryDTO> result = miniAppService.searchAnalogues(
                "insulin glargine", 11L, null, null, 7L);

        assertEquals(1, result.size());
        assertEquals("Insulin Lispro", result.get(0).getMedicineName());
        assertEquals(22L, result.get(0).getMedicineId());
        assertEquals(2, result.get(0).getAvailablePharmacies());
        assertEquals(new BigDecimal("380.00"), result.get(0).getPrice());
        assertTrue(result.get(0).isPrescriptionRequired());
        assertFalse(result.get(0).isOutOfStock());
    }

    @Test
    void searchAnalogues_paracetamolOnly_returnsEmpty() {
        Medicine selected = Medicine.builder()
                .id(1L)
                .name("paracetamol")
                .canonicalName("paracetamol")
                .activeIngredient("paracetamol")
                .build();

        when(medicineRepository.findById(1L)).thenReturn(java.util.Optional.of(selected));
        when(medicineRepository.findByActiveIngredientIgnoreCase("paracetamol"))
                .thenReturn(List.of(selected));

        List<MiniAppMedicineSummaryDTO> result = miniAppService.searchAnalogues(
                "paracetamol", 1L, null, null, 7L);

        assertTrue(result.isEmpty());
    }

    @Test
    void searchAnalogues_excludesOutOfStockSibling() {
        Medicine selected = Medicine.builder()
                .id(11L)
                .name("Insulin Glargine")
                .canonicalName("insulin glargine")
                .activeIngredient("insulin")
                .build();
        Medicine analogue = Medicine.builder()
                .id(22L)
                .name("Insulin Lispro")
                .canonicalName("insulin lispro")
                .activeIngredient("insulin")
                .build();
        PharmacyInventory outOfStock = PharmacyInventory.builder()
                .id(220L)
                .pharmacyId(2L)
                .catalogMedicineId(22L)
                .quantity(0)
                .outOfStock(true)
                .price(new BigDecimal("380.00"))
                .build();

        when(medicineRepository.findById(11L)).thenReturn(java.util.Optional.of(selected));
        when(medicineRepository.findByActiveIngredientIgnoreCase("insulin"))
                .thenReturn(List.of(selected, analogue));
        when(pharmacyInventoryRepository.findByCatalogMedicineIdIn(List.of(22L)))
                .thenReturn(List.of(outOfStock));

        List<MiniAppMedicineSummaryDTO> result = miniAppService.searchAnalogues(
                "insulin glargine", 11L, null, null, 7L);

        assertTrue(result.isEmpty());
    }

    @Test
    void searchAnalogues_excludesSameDisplayName() {
        Medicine selected = Medicine.builder()
                .id(1L)
                .name("paracetamol")
                .canonicalName("paracetamol")
                .activeIngredient("paracetamol")
                .build();
        Medicine lookalike = Medicine.builder()
                .id(2L)
                .name("Paracetamol")
                .canonicalName("paracetamol 500mg")
                .activeIngredient("paracetamol")
                .strength("500mg")
                .build();

        when(medicineRepository.findById(1L)).thenReturn(java.util.Optional.of(selected));
        when(medicineRepository.findByActiveIngredientIgnoreCase("paracetamol"))
                .thenReturn(List.of(selected, lookalike));

        List<MiniAppMedicineSummaryDTO> result = miniAppService.searchAnalogues(
                "paracetamol", 1L, null, null, 7L);

        assertTrue(result.isEmpty());
    }

    private PharmacyResponseDTO offerDto(String pharmacyName,
                                         Long pharmacyId,
                                         Long medicineId,
                                         String medicineName,
                                         String price) {
        return PharmacyResponseDTO.builder()
                .id(pharmacyId)
                .name(pharmacyName)
                .medicineId(medicineId)
                .medicineName(medicineName)
                .verified(true)
                .requiresPrescription(true)
                .openNow(true)
                .distance(1.0)
                .rating(40.0)
                .price(new BigDecimal(price))
                .outOfStock(false)
                .build();
    }

    private PharmacyResponseDTO dto(String name,
                                    boolean verified,
                                    boolean requiresPrescription,
                                    boolean openNow,
                                    double distance,
                                    double rating,
                                    String price,
                                    boolean outOfStock) {
        return PharmacyResponseDTO.builder()
                .name(name)
                .verified(verified)
                .requiresPrescription(requiresPrescription)
                .openNow(openNow)
                .distance(distance)
                .rating(rating)
                .price(new BigDecimal(price))
                .outOfStock(outOfStock)
                .build();
    }
}
