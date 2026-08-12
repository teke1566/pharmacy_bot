package com.tenahub.bot.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenahub.bot.service.MiniAppAuthException;
import com.tenahub.bot.service.TelegramWebAppAuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Service
public class TelegramWebAppAuthServiceImpl implements TelegramWebAppAuthService {

    private static final long MAX_AUTH_AGE_SECONDS = 24 * 60 * 60;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${telegram.bot-token}")
    private String botToken;

    @Override
    public Long requireUserId(String... initDataCandidates) {
        String initData = firstNonBlank(initDataCandidates);
        if (initData == null) {
            throw new MiniAppAuthException("Telegram initData is required");
        }
        return parseUserId(initData);
    }

    @Override
    public Long resolveUserId(String initData, Long claimedUserId) {
        if (initData != null && !initData.isBlank()) {
            return parseUserId(initData);
        }
        if (claimedUserId != null && claimedUserId > 0) {
            return claimedUserId;
        }
        return null;
    }

    @Override
    public Long parseUserId(String initData) {
        if (initData == null || initData.isBlank()) {
            throw new MiniAppAuthException("Telegram initData is required");
        }
        if (botToken == null || botToken.isBlank()) {
            throw new MiniAppAuthException("Telegram bot token is not configured");
        }

        Map<String, String> fields = parseInitData(initData);
        String hash = fields.remove("hash");
        if (hash == null || hash.isBlank()) {
            throw new MiniAppAuthException("Telegram initData hash is missing");
        }

        String authDateValue = fields.get("auth_date");
        if (authDateValue == null || authDateValue.isBlank()) {
            throw new MiniAppAuthException("Telegram initData auth_date is missing");
        }
        long authDate;
        try {
            authDate = Long.parseLong(authDateValue);
        } catch (NumberFormatException ex) {
            throw new MiniAppAuthException("Telegram initData auth_date is invalid");
        }
        if (Instant.now().getEpochSecond() - authDate > MAX_AUTH_AGE_SECONDS) {
            throw new MiniAppAuthException("Telegram initData has expired");
        }

        String dataCheckString = buildDataCheckString(fields);
        String expectedHash = hmacHex(hmac("WebAppData", botToken), dataCheckString);
        if (!constantTimeEquals(expectedHash, hash.toLowerCase(Locale.ROOT))) {
            throw new MiniAppAuthException("Telegram initData signature is invalid");
        }

        String userJson = fields.get("user");
        if (userJson == null || userJson.isBlank()) {
            throw new MiniAppAuthException("Telegram initData user is missing");
        }
        try {
            JsonNode user = OBJECT_MAPPER.readTree(userJson);
            long userId = user.path("id").asLong(0);
            if (userId <= 0) {
                throw new MiniAppAuthException("Telegram initData user id is invalid");
            }
            return userId;
        } catch (MiniAppAuthException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MiniAppAuthException("Telegram initData user is invalid");
        }
    }

    private Map<String, String> parseInitData(String initData) {
        Map<String, String> fields = new TreeMap<>();
        for (String pair : initData.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            int separator = pair.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = urlDecode(pair.substring(0, separator));
            String value = urlDecode(pair.substring(separator + 1));
            fields.put(key, value);
        }
        return fields;
    }

    private String buildDataCheckString(Map<String, String> fields) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            parts.add(entry.getKey() + "=" + entry.getValue());
        }
        return String.join("\n", parts);
    }

    private byte[] hmac(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new MiniAppAuthException("Unable to validate Telegram initData");
        }
    }

    private String hmacHex(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new MiniAppAuthException("Unable to validate Telegram initData");
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = actual.getBytes(StandardCharsets.UTF_8);
        if (left.length != right.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < left.length; i++) {
            result |= left[i] ^ right[i];
        }
        return result == 0;
    }

    private String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
