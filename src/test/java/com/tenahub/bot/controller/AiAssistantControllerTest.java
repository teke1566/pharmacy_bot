package com.tenahub.bot.controller;

import com.tenahub.bot.dto.AiChatDebugResponseDTO;
import com.tenahub.bot.dto.AiChatRequestDTO;
import com.tenahub.bot.dto.AiChatResponseDTO;
import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.service.AiAssistantService;
import com.tenahub.bot.service.MiniAppAuthException;
import com.tenahub.bot.service.TelegramWebAppAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAssistantControllerTest {

    @Mock
    private AiAssistantService aiAssistantService;
    @Mock
    private TelegramWebAppAuthService telegramWebAppAuthService;

    private AiAssistantController controller;

    @BeforeEach
    void setUp() {
        controller = new AiAssistantController(aiAssistantService, telegramWebAppAuthService);
        ReflectionTestUtils.setField(controller, "aiDebugEnabled", false);
        ReflectionTestUtils.setField(controller, "adminChatId", 55L);
    }

    @Test
    void chat_usesInitDataActor() {
        AiChatRequestDTO request = AiChatRequestDTO.builder().message("hello").build();
        when(telegramWebAppAuthService.requireUserId("init", null, null)).thenReturn(42L);
        when(aiAssistantService.chat(request, 42L, null, null))
                .thenReturn(AiChatResponseDTO.builder().answer("hi").build());

        ResponseEntity<?> response = controller.chat(request, 42L, null, null, "init");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(42L, request.getTelegramUserId());
        verify(aiAssistantService).chat(request, 42L, null, null);
    }

    @Test
    void chat_nullBodyThrowsAuthError() {
        assertThrows(MiniAppAuthException.class, () -> controller.chat(null, null, null, null, "init"));
    }

    @Test
    void chatDebug_disabledReturnsNotFound() {
        ResponseEntity<?> response = controller.chatDebug(
                AiChatRequestDTO.builder().message("debug").build(), null, null, null, "init");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        MiniAppOperationResponseDTO body = (MiniAppOperationResponseDTO) response.getBody();
        assertEquals(false, body.isSuccess());
    }

    @Test
    void chatDebug_enabledForwardsToService() {
        ReflectionTestUtils.setField(controller, "aiDebugEnabled", true);
        AiChatRequestDTO request = AiChatRequestDTO.builder().message("debug").build();
        when(telegramWebAppAuthService.requireUserId("init", null, null)).thenReturn(42L);
        when(aiAssistantService.chatDebug(eq(request), eq(42L), isNull(), isNull()))
                .thenReturn(AiChatDebugResponseDTO.builder().build());

        ResponseEntity<?> response = controller.chatDebug(request, 42L, null, null, "init");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(aiAssistantService).chatDebug(any(), eq(42L), isNull(), isNull());
    }
}
