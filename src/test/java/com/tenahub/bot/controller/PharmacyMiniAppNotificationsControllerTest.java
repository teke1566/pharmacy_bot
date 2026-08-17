package com.tenahub.bot.controller;

import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.dto.PharmacyNotificationDTO;
import com.tenahub.bot.service.MiniAppActorResolver;
import com.tenahub.bot.service.PharmacyNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PharmacyMiniAppNotificationsControllerTest {

    @Mock
    private PharmacyNotificationService pharmacyNotificationService;
    @Mock
    private MiniAppActorResolver miniAppActorResolver;

    private PharmacyMiniAppNotificationsController controller;

    @BeforeEach
    void setUp() {
        controller = new PharmacyMiniAppNotificationsController(pharmacyNotificationService, miniAppActorResolver);
        when(miniAppActorResolver.requirePharmacyTelegramId(9001L, null)).thenReturn(9001L);
    }

    @Test
    void list_usesResolver() {
        when(pharmacyNotificationService.list(9001L, false)).thenReturn(List.of(
                PharmacyNotificationDTO.builder().notificationId(1L).type("RESERVATION_PENDING").build()));

        ResponseEntity<?> response = controller.list(9001L, null, false);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(pharmacyNotificationService).list(9001L, false);
    }

    @Test
    void markRead_otherPharmacyForbidden() {
        when(pharmacyNotificationService.markRead(9001L, 7L))
                .thenThrow(new RuntimeException("Notification does not belong to this pharmacy"));

        ResponseEntity<?> response = controller.markRead(7L, 9001L, null);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        MiniAppOperationResponseDTO body = (MiniAppOperationResponseDTO) response.getBody();
        assertEquals(false, body.isSuccess());
    }

    @Test
    void unreadCount_ok() {
        when(pharmacyNotificationService.unreadCount(9001L)).thenReturn(3L);

        ResponseEntity<?> response = controller.unreadCount(9001L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(3L, body.get("unreadCount"));
    }
}
