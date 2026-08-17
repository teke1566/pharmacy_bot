package com.tenahub.bot.controller;

import com.tenahub.bot.dto.MiniAppOperationResponseDTO;
import com.tenahub.bot.dto.MiniAppMedicineSummaryDTO;
import com.tenahub.bot.dto.MiniAppReservationCardDTO;
import com.tenahub.bot.dto.MiniAppReservationConfirmRequestDTO;
import com.tenahub.bot.dto.MiniAppReservationConfirmResponseDTO;
import com.tenahub.bot.dto.MiniAppPharmacyReportRequestDTO;
import com.tenahub.bot.dto.PharmacyResponseDTO;
import com.tenahub.bot.service.AdminInboxService;
import com.tenahub.bot.service.MiniAppActorResolver;
import com.tenahub.bot.service.MiniAppAuthException;
import com.tenahub.bot.service.MiniAppService;
import com.tenahub.bot.service.TelegramWebAppAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MiniAppControllerTest {

    @Mock
    private MiniAppService miniAppService;
    @Mock
    private TelegramWebAppAuthService telegramWebAppAuthService;
    @Mock
    private MiniAppActorResolver miniAppActorResolver;
    @Mock
    private AdminInboxService adminInboxService;

    private MiniAppController controller;

    @BeforeEach
    void setUp() {
        controller = new MiniAppController();
        ReflectionTestUtils.setField(controller, "miniAppService", miniAppService);
        ReflectionTestUtils.setField(controller, "telegramWebAppAuthService", telegramWebAppAuthService);
        ReflectionTestUtils.setField(controller, "miniAppActorResolver", miniAppActorResolver);
        ReflectionTestUtils.setField(controller, "adminInboxService", adminInboxService);
    }

    @Test
    void search_prefersSortAndFilterOverAliases() {
        when(miniAppService.search(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        ResponseEntity<?> response = controller.search(
                "paracetamol",
                9.01,
                38.75,
                42L,
                "Cheapest",
                "Nearest",
                "Verified only",
                "No prescription",
                77L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(miniAppService).search(
                eq("paracetamol"),
                eq(77L),
                eq(9.01),
                eq(38.75),
                eq(42L),
                eq("Cheapest"),
                eq("Verified only"));
    }

    @Test
    void search_usesSortByAndFilterByWhenPrimaryValuesMissing() {
        when(miniAppService.search(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(PharmacyResponseDTO.builder().name("A").build()));

        ResponseEntity<?> response = controller.search(
                "ibuprofen",
                null,
                null,
                99L,
                null,
                "Highest Rated",
                null,
                "Prescription required",
                null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(miniAppService).search(
                eq("ibuprofen"),
                isNull(),
                eq(null),
                eq(null),
                eq(99L),
                eq("Highest Rated"),
                eq("Prescription required"));
    }

    @Test
    void searchMedicineCatalog_forwardsQueryParams() {
        when(miniAppService.searchMedicineCatalog(any(), any(), any()))
                .thenReturn(List.of(MiniAppMedicineSummaryDTO.builder().medicineId(5L).medicineName("insulin").build()));

        ResponseEntity<?> response = controller.searchMedicineCatalog("insulin", 9.01, 38.75);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(miniAppService).searchMedicineCatalog(eq("insulin"), eq(9.01), eq(38.75));
    }

    @Test
    void searchAnalogues_forwardsQueryParams() {
        when(miniAppService.searchAnalogues(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        ResponseEntity<?> response = controller.searchAnalogues(
                "insulin glargine",
                "11",
                9.01,
                38.75,
                42L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(miniAppService).searchAnalogues(
                eq("insulin glargine"),
                eq(11L),
                eq(9.01),
                eq(38.75),
                eq(42L));
    }

    @Test
    void searchAnalogues_ignoresNonNumericMedicineId() {
        when(miniAppService.searchAnalogues(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        ResponseEntity<?> response = controller.searchAnalogues(
                "insulin",
                "insulin",
                null,
                null,
                null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(miniAppService).searchAnalogues(
                eq("insulin"),
                isNull(),
                isNull(),
                isNull(),
                isNull());
    }

    @Test
    void searchMultipleMedicines_forwardsQueryParams() {
        when(miniAppService.searchMultipleMedicines(any(), any(), any(), any()))
                .thenReturn(List.of());

        ResponseEntity<?> response = controller.searchMultipleMedicines(
                "insulin,paracetamol",
                9.01,
                38.75,
                42L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(miniAppService).searchMultipleMedicines(
                eq(List.of("insulin", "paracetamol")),
                eq(9.01),
                eq(38.75),
                eq(42L));
    }

    @Test
    void searchMultipleMedicines_requiresMedicinesAndCoordinates() {
        ResponseEntity<?> missingNames = controller.searchMultipleMedicines("  ", 9.01, 38.75, null);
        ResponseEntity<?> missingCoords = controller.searchMultipleMedicines("insulin", null, 38.75, null);

        assertEquals(HttpStatus.BAD_REQUEST, missingNames.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, missingCoords.getStatusCode());
        verify(miniAppService, never()).searchMultipleMedicines(any(), any(), any(), any());
    }

    @Test
    void search_returnsBadRequestOnRuntimeException() {
        when(miniAppService.search(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("medicine is required"));

        ResponseEntity<?> response = controller.search(
                "",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Error searching pharmacies: medicine is required", response.getBody());
    }

    @Test
    void getActiveReservations_requiresInitDataUserId() {
        when(miniAppActorResolver.currentInitData()).thenReturn(null);
        when(telegramWebAppAuthService.requireUserId(eq("init"), isNull(), isNull(), isNull())).thenReturn(42L);
        when(miniAppService.getActiveReservations(42L))
                .thenReturn(List.of(MiniAppReservationCardDTO.builder().reservationId(1L).build()));

        ResponseEntity<?> response = controller.getActiveReservations(7L, null, null, "init");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(miniAppService).getActiveReservations(42L);
    }

    @Test
    void getReservationHistory_requiresInitDataUserId() {
        when(miniAppActorResolver.currentInitData()).thenReturn(null);
        when(telegramWebAppAuthService.requireUserId(eq("init"), isNull(), isNull(), isNull())).thenReturn(42L);
        when(miniAppService.getReservationHistory(42L)).thenReturn(List.of());

        ResponseEntity<?> response = controller.getReservationHistory(7L, null, null, "init");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(miniAppService).getReservationHistory(42L);
    }

    @Test
    void hideReservationFromHistory_succeedsForMatchingUser() {
        when(miniAppActorResolver.currentInitData()).thenReturn(null);
        when(telegramWebAppAuthService.requireUserId(eq("init"), isNull(), isNull(), isNull())).thenReturn(42L);
        when(miniAppService.hideReservationFromHistory(9L, 42L))
                .thenReturn(MiniAppOperationResponseDTO.builder().success(true).message("ok").build());

        ResponseEntity<?> response = controller.hideReservationFromHistory(9L, 42L, null, null, "init");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(miniAppService).hideReservationFromHistory(9L, 42L);
    }

    @Test
    void hideReservationFromHistory_rejectsUserIdMismatch() {
        when(miniAppActorResolver.currentInitData()).thenReturn(null);
        when(telegramWebAppAuthService.requireUserId(eq("init"), isNull(), isNull(), isNull())).thenReturn(42L);

        ResponseEntity<?> response = controller.hideReservationFromHistory(9L, 99L, null, null, "init");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(miniAppService, never()).hideReservationFromHistory(any(), any());
    }

    @Test
    void hideReservationFromHistory_missingInitDataThrowsAuthError() {
        when(miniAppActorResolver.currentInitData()).thenReturn(null);
        when(telegramWebAppAuthService.requireUserId(isNull(), isNull(), isNull(), isNull()))
                .thenThrow(new MiniAppAuthException("Telegram initData is required"));

        assertThrows(MiniAppAuthException.class,
                () -> controller.hideReservationFromHistory(9L, 42L, null, null, null));
    }

    @Test
    void clearReservationHistory_succeedsForMatchingUser() {
        when(miniAppActorResolver.currentInitData()).thenReturn(null);
        when(telegramWebAppAuthService.requireUserId(eq("init"), isNull(), isNull(), isNull())).thenReturn(42L);
        when(miniAppService.clearReservationHistory(42L))
                .thenReturn(MiniAppOperationResponseDTO.builder().success(true).build());

        ResponseEntity<?> response = controller.clearReservationHistory(42L, null, null, "init");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(miniAppService).clearReservationHistory(42L);
    }

    @Test
    void clearReservationHistory_rejectsUserIdMismatch() {
        when(miniAppActorResolver.currentInitData()).thenReturn(null);
        when(telegramWebAppAuthService.requireUserId(eq("init"), isNull(), isNull(), isNull())).thenReturn(42L);

        ResponseEntity<?> response = controller.clearReservationHistory(99L, null, null, "init");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(miniAppService, never()).clearReservationHistory(any());
    }

    @Test
    void cancelReservation_ownerPath() {
        when(miniAppActorResolver.currentInitData()).thenReturn(null);
        when(telegramWebAppAuthService.requireUserId(eq("init"), isNull(), isNull(), isNull())).thenReturn(42L);
        when(miniAppService.cancelReservation(5L, 42L))
                .thenReturn(MiniAppOperationResponseDTO.builder().success(true).build());

        ResponseEntity<?> response = controller.cancelReservation(5L, 42L, null, null, "init");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(miniAppService).cancelReservation(5L, 42L);
    }

    @Test
    void cancelReservation_missingInitDataThrowsAuthError() {
        when(miniAppActorResolver.currentInitData()).thenReturn(null);
        when(telegramWebAppAuthService.requireUserId(isNull(), isNull(), isNull(), isNull()))
                .thenThrow(new MiniAppAuthException("Telegram initData is required"));

        assertThrows(MiniAppAuthException.class, () -> controller.cancelReservation(5L, 42L, null, null, null));
    }

    @Test
    void confirmReservation_forwardsBodyAndInitData() {
        MiniAppReservationConfirmRequestDTO request = MiniAppReservationConfirmRequestDTO.builder()
                .pharmacyId(3L)
                .medicineId(8L)
                .quantity(1)
                .build();
        when(miniAppService.confirmReservation(request))
                .thenReturn(MiniAppReservationConfirmResponseDTO.builder().reservationId(11L).build());

        ResponseEntity<?> response = controller.confirmReservation(request, "init-from-header");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("init-from-header", request.getTelegramInitData());
        verify(miniAppService).confirmReservation(request);
    }

    @Test
    void reportPharmacy_createsInboxIssue() {
        when(miniAppActorResolver.currentInitData()).thenReturn(null);
        when(telegramWebAppAuthService.requireUserId(eq("init-data"), isNull(), isNull(), isNull())).thenReturn(42L);
        MiniAppPharmacyReportRequestDTO request = MiniAppPharmacyReportRequestDTO.builder()
                .issueType("wrong_hours")
                .note("Closes earlier")
                .pharmacyName("Bole Pharmacy")
                .medicineName("Paracetamol")
                .build();

        ResponseEntity<?> response = controller.reportPharmacy(9L, request, null, null, "init-data");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(adminInboxService).createIssueItem(
                eq(42L),
                eq(9L),
                eq("Paracetamol"),
                eq("wrong_hours"),
                org.mockito.ArgumentMatchers.contains("Bole Pharmacy"));
    }
}
