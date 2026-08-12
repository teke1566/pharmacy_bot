package com.tenahub.bot.service.impl;

import com.tenahub.bot.dto.PharmacyResponseDTO;
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

    private MiniAppServiceImpl miniAppService;

    @BeforeEach
    void setUp() {
        miniAppService = new MiniAppServiceImpl();
        ReflectionTestUtils.setField(miniAppService, "pharmacyService", pharmacyService);
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
