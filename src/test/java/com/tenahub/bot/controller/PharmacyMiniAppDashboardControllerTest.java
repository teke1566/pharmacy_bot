package com.tenahub.bot.controller;

import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.dto.PharmacyDashboardDTO;
import com.tenahub.bot.service.MiniAppActorResolver;
import com.tenahub.bot.service.PharmacyDashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PharmacyMiniAppDashboardControllerTest {

    @Mock
    private PharmacyDashboardService pharmacyDashboardService;
    @Mock
    private MiniAppActorResolver miniAppActorResolver;

    private PharmacyMiniAppDashboardController controller;

    @BeforeEach
    void setUp() {
        controller = new PharmacyMiniAppDashboardController(pharmacyDashboardService, miniAppActorResolver);
        when(miniAppActorResolver.requirePharmacyTelegramId(9001L, null)).thenReturn(9001L);
    }

    @Test
    void getDashboard_usesResolverAndReturnsDto() {
        PharmacyDashboardDTO dto = PharmacyDashboardDTO.builder()
                .pendingPrescriptions(1)
                .pendingReservations(2)
                .fulfilledToday(3)
                .todayRevenue(new BigDecimal("100"))
                .todaySaleCount(4)
                .totalItems(10)
                .inStock(7)
                .lowStock(2)
                .outOfStock(1)
                .expiringSoon(5)
                .unreadNotifications(6L)
                .build();
        when(pharmacyDashboardService.getDashboard(9001L)).thenReturn(dto);

        ResponseEntity<?> response = controller.getDashboard(9001L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(dto, response.getBody());
        verify(pharmacyDashboardService).getDashboard(9001L);
    }

    @Test
    void getDashboard_missingPharmacyReturnsNotFound() {
        when(pharmacyDashboardService.getDashboard(9001L))
                .thenThrow(new RuntimeException("Pharmacy not found"));

        ResponseEntity<?> response = controller.getDashboard(9001L, null);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        MiniAppOperationResponseDTO body = (MiniAppOperationResponseDTO) response.getBody();
        assertEquals(false, body.isSuccess());
    }
}
