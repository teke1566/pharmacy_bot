package com.tenahub.bot.controller;

import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.repository.MedicineReservationRepository;
import com.tenahub.bot.repository.PharmacyRepository;
import com.tenahub.bot.service.AdminAuditTrailService;
import com.tenahub.bot.service.AdminInboxService;
import com.tenahub.bot.service.AdminService;
import com.tenahub.bot.service.LicenseComplianceService;
import com.tenahub.bot.service.MiniAppActorResolver;
import com.tenahub.bot.service.MiniAppAuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMiniAppControllerTest {

    @Mock
    private AdminService adminService;
    @Mock
    private AdminInboxService adminInboxService;
    @Mock
    private AdminAuditTrailService adminAuditTrailService;
    @Mock
    private LicenseComplianceService licenseComplianceService;
    @Mock
    private PharmacyRepository pharmacyRepository;
    @Mock
    private MedicineReservationRepository reservationRepository;
    @Mock
    private MiniAppActorResolver miniAppActorResolver;

    private AdminMiniAppController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminMiniAppController(
                adminService,
                adminInboxService,
                adminAuditTrailService,
                licenseComplianceService,
                pharmacyRepository,
                reservationRepository,
                miniAppActorResolver);
        ReflectionTestUtils.setField(controller, "adminChatId", 55L);
    }

    @Test
    void checkAccess_allowsConfiguredAdmin() {
        when(miniAppActorResolver.resolveAdminIdForAccessCheck(55L, null)).thenReturn(55L);

        ResponseEntity<?> response = controller.checkAccess(55L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(true, body.get("isAdmin"));
        assertEquals(55L, body.get("telegramId"));
    }

    @Test
    void checkAccess_deniesNonAdmin() {
        when(miniAppActorResolver.resolveAdminIdForAccessCheck(99L, null)).thenReturn(99L);

        ResponseEntity<?> response = controller.checkAccess(99L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(false, body.get("isAdmin"));
    }

    @Test
    void cancelReservation_usesAdminResolver() {
        when(miniAppActorResolver.requireAdminTelegramId(55L, null)).thenReturn(55L);

        ResponseEntity<?> response = controller.cancelReservation(12L, 55L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        MiniAppOperationResponseDTO body = (MiniAppOperationResponseDTO) response.getBody();
        assertEquals(true, body.isSuccess());
        verify(adminService).adminCancelReservation(12L);
        verify(adminAuditTrailService).record("RESERVATION_CANCELLED", "RESERVATION", 12L, 55L, "Cancelled via admin mini app");
    }

    @Test
    void cancelReservation_propagatesAdminAuthError() {
        when(miniAppActorResolver.requireAdminTelegramId(99L, null))
                .thenThrow(new MiniAppAuthException("Access denied — admin only"));

        assertThrows(MiniAppAuthException.class, () -> controller.cancelReservation(12L, 99L, null));
    }
}
