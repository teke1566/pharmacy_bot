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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        when(localizationService.getLanguage(any())).thenReturn(BotLanguage.ENGLISH);
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
}
