package com.tenahub.bot.util;

import com.tenahub.bot.entity.MedicineReservation;
import com.tenahub.bot.entity.PrescriptionReviewStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramClientReservationNotifyTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private LocalizationService localizationService;

    private TelegramClient telegramClient;

    @BeforeEach
    void setUp() {
        telegramClient = new TelegramClient(restTemplate, localizationService);
        ReflectionTestUtils.setField(telegramClient, "apiUrl", "https://api.telegram.org/botTEST");
        lenient().when(localizationService.getLanguage(any())).thenReturn(BotLanguage.ENGLISH);
    }

    @Test
    void sendReservationRequestToPharmacy_escapesHtmlInCustomerName() {
        when(restTemplate.postForObject(any(String.class), any(), eq(String.class))).thenReturn("ok");

        assertDoesNotThrow(() -> telegramClient.sendReservationRequestToPharmacy(
                100L, 1L, 2L, "paracetamol", 1, "+251911000000", "A & B <test>", 20L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForObject(
                eq("https://api.telegram.org/botTEST/sendMessage"), bodyCaptor.capture(), eq(String.class));

        String text = (String) bodyCaptor.getValue().get("text");
        assertTrue(text.contains("A &amp; B &lt;test&gt;"));
        assertFalse(text.contains("A & B <test>"));
    }

    @Test
    void sendReservationRequestToPharmacy_rxAwaitingUpload_omitsApproveButtons() {
        when(restTemplate.postForObject(any(String.class), any(), eq(String.class))).thenReturn("ok");

        telegramClient.sendReservationRequestToPharmacy(
                100L, 11L, 12L, "amoxicillin", 2, "+251900000000", "Liya", 20L, true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForObject(
                eq("https://api.telegram.org/botTEST/sendMessage"), bodyCaptor.capture(), eq(String.class));

        Map<String, Object> body = bodyCaptor.getValue();
        String text = (String) body.get("text");
        assertTrue(text.contains("waiting for prescription upload"));
        assertFalse(body.containsKey("reply_markup"));
    }

    @Test
    void sendReservationRequestToPharmacy_retriesWithoutParseModeWhenHtmlFails() {
        HttpClientErrorException htmlError = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                HttpHeaders.EMPTY,
                "{\"ok\":false,\"description\":\"can't parse entities\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        when(restTemplate.postForObject(any(String.class), any(), eq(String.class)))
                .thenThrow(htmlError)
                .thenReturn("ok");

        assertDoesNotThrow(() -> telegramClient.sendReservationRequestToPharmacy(
                100L, 1L, 2L, "paracetamol", 1, "+251911000000", "A & B <test>", 20L));

        verify(restTemplate, times(2)).postForObject(
                eq("https://api.telegram.org/botTEST/sendMessage"), any(), eq(String.class));
    }

    @Test
    void sendPharmacyGroupedReservationCard_escapesHtmlCustomerName() {
        when(restTemplate.postForObject(any(String.class), any(), eq(String.class))).thenReturn("ok");
        MedicineReservation reservation = MedicineReservation.builder()
                .id(31L)
                .medicineName("paracetamol")
                .requestedQuantity(1)
                .customerName("A & B <test>")
                .customerPhone("0911 <x>")
                .prescriptionRequired(false)
                .prescriptionReviewStatus(PrescriptionReviewStatus.NOT_REQUIRED)
                .build();

        assertDoesNotThrow(() -> telegramClient.sendPharmacyGroupedReservationCard(
                200L, "abcdef12-group", List.of(reservation)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForObject(
                eq("https://api.telegram.org/botTEST/sendMessage"), bodyCaptor.capture(), eq(String.class));

        String text = (String) bodyCaptor.getValue().get("text");
        assertTrue(text.contains("A &amp; B &lt;test&gt;"));
        assertTrue(text.contains("0911 &lt;x&gt;"));
    }

    @Test
    void buildMiniAppUserReservationStatusUrl_includesSectionAndReservationId() {
        ReflectionTestUtils.setField(telegramClient, "miniAppBaseUrl", "https://tenahub-miniapp.vercel.app");

        String url = telegramClient.buildMiniAppUserReservationStatusUrl("active", 42L, null);

        assertTrue(url.contains("section=active"));
        assertTrue(url.contains("reservationId=42"));
        assertTrue(url.contains("startapp=a42"));
        assertTrue(url.contains("#/pharmacy-results"));
        assertTrue(url.startsWith("https://tenahub-miniapp.vercel.app/?startapp=a42"));
    }

    @Test
    void buildMiniAppUserReservationStatusUrl_includesBothReservationIdAndGroupId() {
        ReflectionTestUtils.setField(telegramClient, "miniAppBaseUrl", "https://tenahub-miniapp.vercel.app");

        String url = telegramClient.buildMiniAppUserReservationStatusUrl("active", 42L, "group-abc");

        assertTrue(url.contains("section=active"));
        assertTrue(url.contains("reservationId=42"));
        assertTrue(url.contains("groupId=group-abc"));
        assertTrue(url.contains("startapp=a42ggroup-abc"));
        assertTrue(url.startsWith("https://tenahub-miniapp.vercel.app/?"));
    }

    @Test
    void encodeReservationStartApp_historyPrefix() {
        assertEquals("h99", TelegramClient.encodeReservationStartApp("history", 99L, null));
        assertEquals("a7", TelegramClient.encodeReservationStartApp("active", 7L, null));
    }

    @Test
    void buildMiniAppPharmacyRegisterUrl_includesStartappPreg() {
        ReflectionTestUtils.setField(telegramClient, "miniAppBaseUrl", "https://tenahub-miniapp.vercel.app");

        String url = telegramClient.buildMiniAppPharmacyRegisterUrl();

        assertTrue(url.contains("startapp=preg"));
        assertTrue(url.contains("#/pharmacy/register"));
        assertTrue(url.startsWith("https://tenahub-miniapp.vercel.app/?startapp=preg"));
    }

    @Test
    void buildMiniAppPharmacyHomeUrl_includesPharmacyTelegramId() {
        ReflectionTestUtils.setField(telegramClient, "miniAppBaseUrl", "https://tenahub-miniapp.vercel.app");

        String url = telegramClient.buildMiniAppPharmacyHomeUrl(999L);

        assertTrue(url.contains("#/pharmacy"));
        assertTrue(url.contains("pharmacyTelegramId=999"));
        assertTrue(url.startsWith("https://tenahub-miniapp.vercel.app/?pharmacyTelegramId=999"));
    }

    @Test
    void buildMiniAppPharmacySearchUrl_includesMedicineAndFocusPharmacy() {
        ReflectionTestUtils.setField(telegramClient, "miniAppBaseUrl", "https://tenahub-miniapp.vercel.app");

        String url = telegramClient.buildMiniAppPharmacySearchUrl("Para cetamol", 12L, 55L);

        assertEquals(
                "https://tenahub-miniapp.vercel.app/?medicine=Para+cetamol&focusPharmacyId=12&medicineId=55#/pharmacy-results?medicine=Para+cetamol&focusPharmacyId=12&medicineId=55",
                url
        );
    }

    @Test
    void buildMiniAppPharmacySearchUrl_seeMoreOmitsFocusPharmacy() {
        ReflectionTestUtils.setField(telegramClient, "miniAppBaseUrl", "https://tenahub-miniapp.vercel.app");

        String url = telegramClient.buildMiniAppPharmacySearchUrl("Amoxicillin", null, 9L);

        assertEquals(
                "https://tenahub-miniapp.vercel.app/?medicine=Amoxicillin&medicineId=9#/pharmacy-results?medicine=Amoxicillin&medicineId=9",
                url
        );
    }

    @Test
    void buildMiniAppSingleReserveUrl_dualPlacesModeBeforeHash() {
        ReflectionTestUtils.setField(telegramClient, "miniAppBaseUrl", "https://tenahub-miniapp.vercel.app");

        String url = telegramClient.buildMiniAppSingleReserveUrl(12L, 55L);

        assertEquals(
                "https://tenahub-miniapp.vercel.app/?mode=single-reserve&pharmacyId=12&medicineId=55#/cart?mode=single-reserve&pharmacyId=12&medicineId=55",
                url
        );
    }

    @Test
    void buildMiniAppPharmacyPickupUrl_dualPlacesPharmacyTelegramId() {
        ReflectionTestUtils.setField(telegramClient, "miniAppBaseUrl", "https://tenahub-miniapp.vercel.app");
        ReflectionTestUtils.setField(
                telegramClient,
                "miniAppPharmacyPickupPagePath",
                "/#/pickup-scanner?pharmacyTelegramId={pharmacyTelegramId}");

        String url = telegramClient.buildMiniAppPharmacyPickupUrl(999L);

        assertTrue(url.startsWith("https://tenahub-miniapp.vercel.app/?pickup=1&pharmacyTelegramId=999"));
        assertTrue(url.contains("#/pickup-scanner?pharmacyTelegramId=999"));
    }

    @Test
    void sendMessageWithMiniAppButton_attachesWebAppKeyboard() {
        ReflectionTestUtils.setField(telegramClient, "miniAppBaseUrl", "https://tenahub-miniapp.vercel.app");
        when(restTemplate.postForObject(any(String.class), any(), eq(String.class))).thenReturn("ok");

        telegramClient.sendMessageWithMiniAppButton(
                55L,
                "Prescription submitted",
                "https://tenahub-miniapp.vercel.app/#/search?section=active&reservationId=10",
                "View reservation");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForObject(
                eq("https://api.telegram.org/botTEST/sendMessage"), bodyCaptor.capture(), eq(String.class));

        Map<String, Object> body = bodyCaptor.getValue();
        assertTrue(body.containsKey("reply_markup"));
        @SuppressWarnings("unchecked")
        Map<String, Object> markup = (Map<String, Object>) body.get("reply_markup");
        @SuppressWarnings("unchecked")
        List<List<Map<String, Object>>> keyboard = (List<List<Map<String, Object>>>) markup.get("inline_keyboard");
        assertTrue(keyboard.get(0).get(0).containsKey("web_app"));
    }

    @Test
    void setChatMenuButton_postsRoleWebAppMenu() {
        when(restTemplate.postForObject(any(String.class), any(), eq(String.class))).thenReturn("ok");

        telegramClient.setChatMenuButton(88L, "https://tenahub-miniapp.vercel.app/#/pharmacy?pharmacyTelegramId=88");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForObject(
                eq("https://api.telegram.org/botTEST/setChatMenuButton"), bodyCaptor.capture(), eq(String.class));

        Map<String, Object> body = bodyCaptor.getValue();
        assertTrue(body.get("chat_id").equals(88L));
        @SuppressWarnings("unchecked")
        Map<String, Object> menuButton = (Map<String, Object>) body.get("menu_button");
        assertTrue("web_app".equals(menuButton.get("type")));
    }

    @Test
    void sendRoleHomeMiniAppPrompt_pharmacyUsesPharmacyDashboardUrl() {
        ReflectionTestUtils.setField(telegramClient, "miniAppBaseUrl", "https://tenahub-miniapp.vercel.app");
        when(restTemplate.postForObject(any(String.class), any(), eq(String.class))).thenReturn("ok");

        telegramClient.sendRoleHomeMiniAppPrompt(77L, "pharmacy");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForObject(
                eq("https://api.telegram.org/botTEST/sendMessage"), bodyCaptor.capture(), eq(String.class));
        Map<String, Object> body = bodyCaptor.getValue();
        @SuppressWarnings("unchecked")
        Map<String, Object> markup = (Map<String, Object>) body.get("reply_markup");
        @SuppressWarnings("unchecked")
        List<List<Map<String, Object>>> keyboard = (List<List<Map<String, Object>>>) markup.get("inline_keyboard");
        @SuppressWarnings("unchecked")
        Map<String, Object> webApp = (Map<String, Object>) keyboard.get(0).get(0).get("web_app");
        assertTrue(String.valueOf(webApp.get("url")).contains("#/pharmacy"));
    }

    @Test
    void buildMiniAppPharmacyHomeUrl_dualPlacesQueryBeforeHash() {
        ReflectionTestUtils.setField(telegramClient, "miniAppBaseUrl", "https://tenahub-miniapp.vercel.app");

        String url = telegramClient.buildMiniAppPharmacyHomeUrl(77L);

        assertEquals(
                "https://tenahub-miniapp.vercel.app/?pharmacyTelegramId=77#/pharmacy?pharmacyTelegramId=77",
                url
        );
    }
}
