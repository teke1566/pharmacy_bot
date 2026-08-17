package com.tenahub.bot.controller;

import com.tenahub.bot.dto.PharmacyMiniAppInventoryItemDTO;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.InventoryService;
import com.tenahub.bot.service.MiniAppActorResolver;
import com.tenahub.bot.service.PharmacyPerformanceService;
import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.dto.MiniAppPhotoDTO;
import com.tenahub.bot.service.PharmacyMiniAppMediaService;
import com.tenahub.bot.service.PharmacyService;
import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PharmacyMiniAppSupportControllersTest {

    @Mock
    private MiniAppActorResolver miniAppActorResolver;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private PharmacyPerformanceService pharmacyPerformanceService;
    @Mock
    private PharmacyRepository pharmacyRepository;
    @Mock
    private PharmacyService pharmacyService;
    @Mock
    private PharmacyMiniAppMediaService pharmacyMiniAppMediaService;
    @Mock
    private com.tenahub.bot.service.PharmacyPricingService pharmacyPricingService;
    @Mock
    private com.tenahub.bot.service.PharmacyAuthorizationService pharmacyAuthorizationService;

    private PharmacyMiniAppInventoryController inventoryController() {
        return new PharmacyMiniAppInventoryController(
                inventoryService, miniAppActorResolver, pharmacyPricingService, pharmacyAuthorizationService);
    }

    @Test
    void inventoryGet_usesResolverThenService() {
        when(miniAppActorResolver.requirePharmacyTelegramId(9001L, null)).thenReturn(9001L);
        when(inventoryService.getPharmacyMiniAppInventory(9001L, null, null, null, null))
                .thenReturn(List.of(PharmacyMiniAppInventoryItemDTO.builder().itemId(1L).build()));

        PharmacyMiniAppInventoryController controller = inventoryController();
        ResponseEntity<?> response = controller.getInventory(9001L, null, null, null, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(inventoryService).getPharmacyMiniAppInventory(9001L, null, null, null, null);
    }

    @Test
    void profileGet_usesResolverThenRepository() {
        when(miniAppActorResolver.requirePharmacyTelegramId(9001L, null)).thenReturn(9001L);
        when(pharmacyRepository.findByTelegramId(9001L))
                .thenReturn(Optional.of(Pharmacy.builder().id(3L).telegramId(9001L).name("City").build()));

        PharmacyMiniAppProfileController controller =
                new PharmacyMiniAppProfileController(pharmacyRepository, pharmacyService, pharmacyMiniAppMediaService, miniAppActorResolver);
        ResponseEntity<?> response = controller.getProfile(9001L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(pharmacyRepository).findByTelegramId(9001L);
    }

    @Test
    void profileLocationPut_usesPharmacyService() {
        when(miniAppActorResolver.requirePharmacyTelegramId(9001L, null)).thenReturn(9001L);

        PharmacyMiniAppProfileController controller =
                new PharmacyMiniAppProfileController(pharmacyRepository, pharmacyService, pharmacyMiniAppMediaService, miniAppActorResolver);
        ResponseEntity<?> response = controller.updateLocation(9001L, null, Map.of(
                "latitude", 11.79,
                "longitude", 41.0,
                "city", "Semera",
                "area", "Semera Center"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(pharmacyService).updateLocation(9001L, 11.79, 41.0, "Semera", "Semera Center", null, null);
    }

    @Test
    void profileLicensePost_usesMediaService() {
        when(miniAppActorResolver.requirePharmacyTelegramId(9001L, null)).thenReturn(9001L);
        MockMultipartFile license = new MockMultipartFile("license", "license.jpg", "image/jpeg", new byte[] {1, 2, 3});
        when(pharmacyMiniAppMediaService.submitLicenseUpdate(9001L, license, "2027-01-01"))
                .thenReturn(MiniAppOperationResponseDTO.builder().success(true).message("ok").build());

        PharmacyMiniAppProfileController controller =
                new PharmacyMiniAppProfileController(pharmacyRepository, pharmacyService, pharmacyMiniAppMediaService, miniAppActorResolver);
        ResponseEntity<?> response = controller.updateLicense(9001L, null, license, "2027-01-01");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(pharmacyMiniAppMediaService).submitLicenseUpdate(9001L, license, "2027-01-01");
    }

    @Test
    void profileLicensePost_alreadyPending_returnsBadRequest() {
        when(miniAppActorResolver.requirePharmacyTelegramId(9001L, null)).thenReturn(9001L);
        MockMultipartFile license = new MockMultipartFile("license", "license.jpg", "image/jpeg", new byte[] {1, 2, 3});
        when(pharmacyMiniAppMediaService.submitLicenseUpdate(9001L, license, "2027-01-01"))
                .thenThrow(new RuntimeException("A license update is already pending admin approval"));

        PharmacyMiniAppProfileController controller =
                new PharmacyMiniAppProfileController(pharmacyRepository, pharmacyService, pharmacyMiniAppMediaService, miniAppActorResolver);
        ResponseEntity<?> response = controller.updateLicense(9001L, null, license, "2027-01-01");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void profileLicensePost_missingFile_returnsBadRequest() {
        when(miniAppActorResolver.requirePharmacyTelegramId(9001L, null)).thenReturn(9001L);
        MockMultipartFile empty = new MockMultipartFile("license", "license.jpg", "image/jpeg", new byte[0]);
        when(pharmacyMiniAppMediaService.submitLicenseUpdate(9001L, empty, "2027-01-01"))
                .thenThrow(new RuntimeException("License photo is required"));

        PharmacyMiniAppProfileController controller =
                new PharmacyMiniAppProfileController(pharmacyRepository, pharmacyService, pharmacyMiniAppMediaService, miniAppActorResolver);
        ResponseEntity<?> response = controller.updateLicense(9001L, null, empty, "2027-01-01");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void pharmacyPhotosPost_usesMediaService() {
        when(miniAppActorResolver.requirePharmacyTelegramId(9001L, null)).thenReturn(9001L);
        MockMultipartFile file = new MockMultipartFile("file", "shop.jpg", "image/jpeg", new byte[] {1, 2, 3});
        when(pharmacyMiniAppMediaService.addPharmacyPhoto(9001L, file, null))
                .thenReturn(MiniAppPhotoDTO.builder().photoId(8L).mainPhoto(true).build());

        PharmacyMiniAppPhotosController controller =
                new PharmacyMiniAppPhotosController(pharmacyMiniAppMediaService, miniAppActorResolver);
        ResponseEntity<?> response = controller.addPharmacyPhoto(9001L, null, file, null);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(pharmacyMiniAppMediaService).addPharmacyPhoto(9001L, file, null);
    }

    @Test
    void pharmacyPhotosSetMainAndDelete_useMediaService() {
        when(miniAppActorResolver.requirePharmacyTelegramId(9001L, null)).thenReturn(9001L);
        when(pharmacyMiniAppMediaService.setMainPharmacyPhoto(9001L, 8L))
                .thenReturn(MiniAppPhotoDTO.builder().photoId(8L).mainPhoto(true).build());

        PharmacyMiniAppPhotosController controller =
                new PharmacyMiniAppPhotosController(pharmacyMiniAppMediaService, miniAppActorResolver);

        assertEquals(HttpStatus.OK, controller.setMainPharmacyPhoto(8L, 9001L, null).getStatusCode());
        assertEquals(HttpStatus.OK, controller.removePharmacyPhoto(8L, 9001L, null).getStatusCode());
        verify(pharmacyMiniAppMediaService).setMainPharmacyPhoto(9001L, 8L);
        verify(pharmacyMiniAppMediaService).removePharmacyPhoto(9001L, 8L);
    }

    @Test
    void medicinePhotosDelete_usesMediaService() {
        when(miniAppActorResolver.requirePharmacyTelegramId(9001L, null)).thenReturn(9001L);

        PharmacyMiniAppPhotosController controller =
                new PharmacyMiniAppPhotosController(pharmacyMiniAppMediaService, miniAppActorResolver);
        assertEquals(HttpStatus.OK, controller.removeMedicinePhoto(44L, 9L, 9001L, null).getStatusCode());
        verify(pharmacyMiniAppMediaService).removeMedicinePhoto(9001L, 44L, 9L);
    }

    @Test
    void performanceGet_usesResolverThenService() {
        when(miniAppActorResolver.requirePharmacyTelegramId(9001L, null)).thenReturn(9001L);
        when(pharmacyPerformanceService.getPerformanceReport(9001L, "weekly"))
                .thenReturn(com.tenahub.bot.dto.PharmacyPerformanceReportDTO.builder()
                        .period("weekly")
                        .healthScore(72)
                        .healthGrade("B")
                        .build());

        PharmacyMiniAppPerformanceController controller =
                new PharmacyMiniAppPerformanceController(pharmacyPerformanceService, inventoryService, miniAppActorResolver);
        ResponseEntity<?> response = controller.getPerformance(9001L, null, "weekly");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        com.tenahub.bot.dto.PharmacyPerformanceReportDTO body =
                (com.tenahub.bot.dto.PharmacyPerformanceReportDTO) response.getBody();
        assertEquals(72, body.getHealthScore());
        assertEquals("B", body.getHealthGrade());
        verify(pharmacyPerformanceService).getPerformanceReport(9001L, "weekly");
    }

    @Test
    void inventoryExpiryBucket_returnsJsonLots() {
        when(miniAppActorResolver.requirePharmacyTelegramId(9001L, null)).thenReturn(9001L);
        when(inventoryService.listExpiryBatches(9001L, "30")).thenReturn(List.of());

        PharmacyMiniAppInventoryController controller = inventoryController();
        ResponseEntity<?> response = controller.getExpiryAlert(9001L, null, "30");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(inventoryService).listExpiryBatches(9001L, "30");
    }

    @Test
    void inventoryAdjust_otherPharmacy_returnsForbidden() {
        when(miniAppActorResolver.requirePharmacyTelegramId(9001L, null)).thenReturn(9001L);
        when(inventoryService.adjustInventoryFromMiniApp(9001L, 5L, -1, "damaged", "DAMAGED", null, null))
                .thenThrow(new RuntimeException("Inventory item does not belong to this pharmacy"));

        PharmacyMiniAppInventoryController controller = inventoryController();
        ResponseEntity<?> response = controller.adjustItem(5L, 9001L, null, Map.of(
                "quantityChange", -1,
                "reason", "damaged",
                "type", "DAMAGED"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void inventoryRestockItems_returnsJson() {
        when(miniAppActorResolver.requirePharmacyTelegramId(9001L, null)).thenReturn(9001L);
        when(inventoryService.listRestockSuggestions(9001L)).thenReturn(List.of());

        PharmacyMiniAppInventoryController controller = inventoryController();
        ResponseEntity<?> response = controller.getRestockItems(9001L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(inventoryService).listRestockSuggestions(9001L);
    }
}
