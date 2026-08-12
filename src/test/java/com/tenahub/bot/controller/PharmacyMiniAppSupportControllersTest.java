package com.tenahub.bot.controller;

import com.tenahub.bot.dto.PharmacyMiniAppInventoryItemDTO;
import com.tenahub.bot.entity.Pharmacy;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.InventoryService;
import com.tenahub.bot.service.MiniAppActorResolver;
import com.tenahub.bot.service.PharmacyPerformanceService;
import com.tenahub.bot.service.PharmacyService;
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

    @Test
    void inventoryGet_usesResolverThenService() {
        when(miniAppActorResolver.requirePharmacyTelegramId(9001L, null)).thenReturn(9001L);
        when(inventoryService.getPharmacyMiniAppInventory(9001L))
                .thenReturn(List.of(PharmacyMiniAppInventoryItemDTO.builder().itemId(1L).build()));

        PharmacyMiniAppInventoryController controller =
                new PharmacyMiniAppInventoryController(inventoryService, miniAppActorResolver);
        ResponseEntity<?> response = controller.getInventory(9001L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(inventoryService).getPharmacyMiniAppInventory(9001L);
    }

    @Test
    void profileGet_usesResolverThenRepository() {
        when(miniAppActorResolver.requirePharmacyTelegramId(9001L, null)).thenReturn(9001L);
        when(pharmacyRepository.findByTelegramId(9001L))
                .thenReturn(Optional.of(Pharmacy.builder().id(3L).telegramId(9001L).name("City").build()));

        PharmacyMiniAppProfileController controller =
                new PharmacyMiniAppProfileController(pharmacyRepository, pharmacyService, miniAppActorResolver);
        ResponseEntity<?> response = controller.getProfile(9001L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(pharmacyRepository).findByTelegramId(9001L);
    }

    @Test
    void performanceGet_usesResolverThenService() {
        when(miniAppActorResolver.requirePharmacyTelegramId(9001L, null)).thenReturn(9001L);
        when(pharmacyPerformanceService.buildPerformanceCard(9001L)).thenReturn("card");

        PharmacyMiniAppPerformanceController controller =
                new PharmacyMiniAppPerformanceController(pharmacyPerformanceService, inventoryService, miniAppActorResolver);
        ResponseEntity<?> response = controller.getPerformance(9001L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("card", body.get("performance"));
        verify(pharmacyPerformanceService).buildPerformanceCard(9001L);
    }
}
