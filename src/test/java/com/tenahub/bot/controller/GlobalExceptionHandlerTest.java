package com.tenahub.bot.controller;

import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.service.MiniAppAuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        ReflectionTestUtils.setField(handler, "maxFileSize", "15MB");
        ReflectionTestUtils.setField(handler, "maxRequestSize", "30MB");
    }

    @Test
    void handleMiniAppAuth_returnsUnauthorized() {
        ResponseEntity<MiniAppOperationResponseDTO> response =
                handler.handleMiniAppAuth(new MiniAppAuthException("Telegram initData is required"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(false, response.getBody().isSuccess());
        assertEquals("Telegram initData is required", response.getBody().getMessage());
    }

    @Test
    void handleMaxUploadSizeExceeded_returnsPayloadTooLarge() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/miniapp/reservations/prescriptions");

        ResponseEntity<String> response = handler.handleMaxUploadSizeExceeded(
                new MaxUploadSizeExceededException(15 * 1024 * 1024), request);

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        assertTrue(response.getBody().contains("15MB"));
        assertTrue(response.getBody().contains("30MB"));
    }
}
