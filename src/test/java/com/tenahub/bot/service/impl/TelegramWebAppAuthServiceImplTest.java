package com.tenahub.bot.service.impl;

import com.tenahub.bot.service.MiniAppAuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TelegramWebAppAuthServiceImplTest {

    private static final String BOT_TOKEN = "123456:TEST-bot-token";

    private TelegramWebAppAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TelegramWebAppAuthServiceImpl();
        ReflectionTestUtils.setField(service, "botToken", BOT_TOKEN);
    }

    @Test
    void parseUserId_acceptsValidInitData() {
        String initData = signedInitData(42L, Instant.now().getEpochSecond());

        assertEquals(42L, service.parseUserId(initData));
        assertEquals(42L, service.requireUserId(null, initData));
        assertEquals(42L, service.resolveUserId(initData, 99L));
    }

    @Test
    void parseUserId_rejectsTamperedHash() {
        String initData = signedInitData(42L, Instant.now().getEpochSecond()) + "ff";

        MiniAppAuthException error = assertThrows(MiniAppAuthException.class, () -> service.parseUserId(initData));
        assertEquals("Telegram initData signature is invalid", error.getMessage());
    }

    @Test
    void parseUserId_rejectsExpiredAuthDate() {
        String initData = signedInitData(42L, Instant.now().getEpochSecond() - (25 * 60 * 60));

        MiniAppAuthException error = assertThrows(MiniAppAuthException.class, () -> service.parseUserId(initData));
        assertEquals("Telegram initData has expired", error.getMessage());
    }

    @Test
    void requireUserId_rejectsMissingInitData() {
        MiniAppAuthException error = assertThrows(MiniAppAuthException.class, () -> service.requireUserId(null, "  "));
        assertEquals("Telegram initData is required", error.getMessage());
    }

    @Test
    void resolveUserId_fallsBackToClaimedId() {
        assertEquals(77L, service.resolveUserId(null, 77L));
    }

    private String signedInitData(long userId, long authDate) {
        String userJson = "{\"id\":" + userId + ",\"first_name\":\"Ada\"}";
        TreeMap<String, String> fields = new TreeMap<>();
        fields.put("auth_date", String.valueOf(authDate));
        fields.put("query_id", "AAE");
        fields.put("user", userJson);

        StringBuilder dataCheck = new StringBuilder();
        for (var entry : fields.entrySet()) {
            if (!dataCheck.isEmpty()) {
                dataCheck.append('\n');
            }
            dataCheck.append(entry.getKey()).append('=').append(entry.getValue());
        }

        String hash = hmacHex(hmac("WebAppData", BOT_TOKEN), dataCheck.toString());
        StringBuilder query = new StringBuilder();
        for (var entry : fields.entrySet()) {
            if (!query.isEmpty()) {
                query.append('&');
            }
            query.append(urlEncode(entry.getKey())).append('=').append(urlEncode(entry.getValue()));
        }
        query.append("&hash=").append(hash);
        return query.toString();
    }

    private byte[] hmac(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String hmacHex(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
