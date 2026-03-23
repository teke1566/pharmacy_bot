package com.tenahub.bot.util;

import com.tenahub.bot.registration.RegistrationStep;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TelegramClient {

    private final RestTemplate restTemplate = new RestTemplate();

    private final String botToken = "";
    private final String apiUrl = "https://api.telegram.org/bot" + botToken;

    /* ---------------- BASIC SEND ---------------- */

    public void sendMessage(Long chatId, String text) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "HTML");

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("Telegram sendMessage error: " + e.getMessage());
        }
    }

    public void sendPhoto(Long chatId, String fileId, String caption) {
        try {
            String url = apiUrl + "/sendPhoto";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("photo", fileId);
            body.put("caption", caption);
            body.put("parse_mode", "HTML");

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("Telegram sendPhoto error: " + e.getMessage());
        }
    }

    public void sendDocument(Long chatId, String fileId, String caption) {
        try {
            String url = apiUrl + "/sendDocument";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("document", fileId);
            body.put("caption", caption);
            body.put("parse_mode", "HTML");

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("Telegram sendDocument error: " + e.getMessage());
        }
    }

    public void sendLocation(Long chatId, double latitude, double longitude) {
        try {
            String url = apiUrl + "/sendLocation";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("latitude", latitude);
            body.put("longitude", longitude);

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendLocation error: " + e.getMessage());
        }
    }

    public void sendDocumentBytes(Long chatId, byte[] content, String filename, String caption) {
        try {
            String url = apiUrl + "/sendDocument";

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("chat_id", String.valueOf(chatId));
            body.add("caption", caption);

            ByteArrayResource resource = new ByteArrayResource(content) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            body.add("document", resource);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity =
                    new HttpEntity<>(body, headers);

            restTemplate.postForObject(url, requestEntity, String.class);
        } catch (Exception e) {
            System.out.println("sendDocumentBytes error: " + e.getMessage());
        }
    }

    /* ---------------- CALLBACK ---------------- */

    public void answerCallback(String callbackId) {
        try {
            String url = apiUrl + "/answerCallbackQuery";

            Map<String, Object> body = new HashMap<>();
            body.put("callback_query_id", callbackId);

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("Callback answer error: " + e.getMessage());
        }
    }

    public void editMessageRemoveButtons(Long chatId, Integer messageId) {
        try {
            String url = apiUrl + "/editMessageReplyMarkup";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("message_id", messageId);
            body.put("reply_markup", Map.of("inline_keyboard", List.of()));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("Remove buttons error: " + e.getMessage());
        }
    }


    public void editInlineKeyboard(Long chatId, Integer messageId, List<List<Map<String, Object>>> keyboard) {
        try {
            String url = apiUrl + "/editMessageReplyMarkup";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("message_id", messageId);
            body.put("reply_markup", Map.of("inline_keyboard", keyboard));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("editInlineKeyboard error: " + e.getMessage());
        }
    }
   public Integer sendSearchFilterKeyboardWithMessageId(Long chatId, String activeFilter) {
    try {
        String url = apiUrl + "/sendMessage";

        String nearestText = "Nearest";
        String cheapestText = "Cheapest";
        String highestRatedText = "Highest Rated";
        String openNowText = "Open Now";
        String inStockText = "In Stock Only";

        if (activeFilter != null) {
            String normalized = activeFilter.trim().toLowerCase();

            if (normalized.equals("nearest")) {
                nearestText = "✅ Nearest";
            } else if (normalized.equals("cheapest")) {
                cheapestText = "✅ Cheapest";
            } else if (normalized.equals("highest rated")) {
                highestRatedText = "✅ Highest Rated";
            } else if (normalized.equals("open now")) {
                openNowText = "✅ Open Now";
            } else if (normalized.equals("in stock only")) {
                inStockText = "✅ In Stock Only";
            }
        }

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                "🔎 <b>Search Filters</b>\n\n" +
                "Active: <b>" + (activeFilter == null ? "Nearest" : activeFilter) + "</b>\n" +
                "Choose a filter below."
        );
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> keyboard = List.of(
                List.of(
                        Map.of("text", "📍 " + nearestText),
                        Map.of("text", "💰 " + cheapestText)
                ),
                List.of(
                        Map.of("text", "⭐ " + highestRatedText),
                        Map.of("text", "🟢 " + openNowText)
                ),
                List.of(
                        Map.of("text", "📦 " + inStockText),
                        Map.of("text", "❌ Clear Filters")
                ),
                List.of(
                        Map.of("text", "🔙 Back"),
                        Map.of("text", "🏠 Home")
                )
        );

        body.put("reply_markup", persistentReplyKeyboard(keyboard));

        Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);

        if (response != null && Boolean.TRUE.equals(response.get("ok"))) {
            Object resultObj = response.get("result");
            if (resultObj instanceof Map<?, ?> result) {
                Object messageIdObj = result.get("message_id");
                if (messageIdObj instanceof Number number) {
                    return number.intValue();
                }
            }
        }
    } catch (Exception e) {
        System.out.println("sendSearchFilterKeyboardWithMessageId error: " + e.getMessage());
    }

    return null;
}
public void sendPharmacyVenue(Long chatId,
                              String pharmacyName,
                              String address,
                              Double latitude,
                              Double longitude) {
    try {
        if (latitude == null || longitude == null) {
            return;
        }

        String url = apiUrl + "/sendVenue";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("latitude", latitude);
        body.put("longitude", longitude);
        body.put("title", pharmacyName == null || pharmacyName.isBlank() ? "Pharmacy" : pharmacyName);
        body.put("address", address == null || address.isBlank() ? "Pharmacy location" : address);

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendPharmacyVenue error: " + e.getMessage());
    }
}
    public void editMessageTextWithInlineKeyboard(Long chatId,
                                                  Integer messageId,
                                                  String text,
                                                  List<List<Map<String, Object>>> keyboard) {
        try {
            String url = apiUrl + "/editMessageText";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("message_id", messageId);
            body.put("text", text);
            body.put("parse_mode", "HTML");
            body.put("reply_markup", Map.of("inline_keyboard", keyboard));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("editMessageTextWithInlineKeyboard error: " + e.getMessage());
        }
    }

    /* ---------------- COMMON KEYBOARD HELPERS ---------------- */

    private Map<String, Object> persistentReplyKeyboard(List<List<Map<String, Object>>> keyboard) {
        Map<String, Object> markup = new HashMap<>();
        markup.put("keyboard", keyboard);
        markup.put("resize_keyboard", true);
        markup.put("one_time_keyboard", false);
        markup.put("is_persistent", true);
        return markup;
    }

    private List<List<Map<String, Object>>> buildTwoColumnKeyboard(List<String> values) {
        List<List<Map<String, Object>>> keyboard = new ArrayList<>();

        for (int i = 0; i < values.size(); i += 2) {
            List<Map<String, Object>> row = new ArrayList<>();
            row.add(Map.of("text", values.get(i)));

            if (i + 1 < values.size()) {
                row.add(Map.of("text", values.get(i + 1)));
            }

            keyboard.add(row);
        }

        return keyboard;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }

    private String capitalizeMedicine(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase();
    }

    /* ---------------- START / DASHBOARD ---------------- */

    public void sendStartKeyboard(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text",
                    "👋 Welcome to TenaHub\n\n" +
                    "Find nearby pharmacies instantly.\n\n" +
                    "⚠️ If this is your first time, please share your location using the 📎 attachment button once.\n\n" +
                    "After that you can use the Share Location button below.\n\n" +
                    "🏥 Pharmacy owners can register below."
            );

            Map<String, Object> locationBtn = Map.of(
                    "text", "📍 Share Location",
                    "request_location", true
            );

            Map<String, Object> registerBtn = Map.of(
                    "text", "🏥 Register Pharmacy"
            );

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(locationBtn),
                    List.of(registerBtn)
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendStartKeyboard error: " + e.getMessage());
        }
    }

    public void sendUserDashboard(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text",
                    "👋 <b>Welcome to TenaHub</b>\n\n" +
                    "Find nearby pharmacies and reserve medicines easily.\n\n" +
                    "Choose an option below:"
            );
            body.put("parse_mode", "HTML");

   List<List<Map<String, Object>>> keyboard = List.of(
        List.of(Map.of("text", "🔎 Search Medicines")),
        List.of(Map.of("text", "🔎🛒 Search Multiple Meds")),
        List.of(Map.of("text", "📦 My Reservations"), Map.of("text", "🕘 Recent Searches")),
        List.of(Map.of("text", "👤 Account"), Map.of("text", "🔔 My Alerts")),
        List.of(Map.of("text", "📍 Share Location"), Map.of("text", "🏥 Register Pharmacy")),
        List.of(Map.of("text", "❓ How to Use"), Map.of("text", "📖 Information")),
        List.of(Map.of("text", "📝 Leave Feedback"), Map.of("text", "🌐 Language"))
);
            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendUserDashboard error: " + e.getMessage());
        }
    }

    public void sendPharmacyDashboard(Long chatId, String pharmacyName) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text",
                    "🏥 <b>Welcome back, " + pharmacyName + "</b>\n\n" +
                    "Status: Approved ✅\n\n" +
                    "Choose an action below:"
            );
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "📦 Inventory")),
                    List.of(Map.of("text", "📦 Reservations")),
                    List.of(Map.of("text", "⚙️ Profile")),
                    List.of(Map.of("text", "⬅️ Back"), Map.of("text", "🏠 Home"))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendPharmacyDashboard error: " + e.getMessage());
        }
    }

    public void sendPendingPharmacyHome(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text",
                    "⏳ <b>Your pharmacy registration is under review</b>\n\n" +
                    "Please wait for admin approval."
            );
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "🔄 Refresh"), Map.of("text", "🏠 Main"))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendPendingPharmacyHome error: " + e.getMessage());
        }
    }
public void sendExactPharmacyLocationRequest(Long chatId) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                "📍 <b>Exact Pharmacy Location</b>\n\n" +
                "Please tap the button below to share the exact pharmacy location."
        );
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> keyboard = List.of(
                List.of(Map.of("text", "📍 Send Pharmacy Location", "request_location", true)),
                List.of(Map.of("text", "⬅️ Back"), Map.of("text", "🏠 Main")),
                List.of(Map.of("text", "❌ Cancel"))
        );

        body.put("reply_markup", persistentReplyKeyboard(keyboard));
        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendExactPharmacyLocationRequest error: " + e.getMessage());
    }
}
    public void sendAdminDashboard(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text",
                    "🛠 <b>Admin Dashboard</b>\n\n" +
                    "Choose an action below:"
            );
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "🆕 Pending Registrations")),
                    List.of(Map.of("text", "📄 License Updates")),
                    List.of(Map.of("text", "📦 Reservation Oversight")),
                    List.of(Map.of("text", "📊 System Summary")),
                    List.of(Map.of("text", "🏠 Home"), Map.of("text", "🔄 Refresh"))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendAdminDashboard error: " + e.getMessage());
        }
    }

    /* ---------------- LOCATION ---------------- */

    public void sendLocationRequest(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", "📍 Please share your location to find nearby pharmacies.");

            Map<String, Object> locationBtn = Map.of(
                    "text", "📍 Share Location",
                    "request_location", true
            );

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(locationBtn),
                    List.of(Map.of("text", "⬅️ Back"), Map.of("text", "🏠 Main")),
                    List.of(Map.of("text", "🏥 Register Pharmacy"))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendLocationRequest error: " + e.getMessage());
        }
    }

public void sendLocationChoiceMenu(Long chatId) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                "📍 <b>Pharmacy Location</b>\n\n" +
                "Step 6/7\n" +
                "Choose how you want to set the pharmacy location.\n\n" +
                "1. Share exact pharmacy location\n" +
                "2. Paste Google Maps link / coordinates\n" +
                "3. Select region → city/sub-city → area"
        );
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> keyboard = List.of(
                List.of(Map.of("text", "📍 Share Exact Pharmacy Location", "request_location", true)),
                List.of(Map.of("text", "🔗 Paste Google Maps Link")),
                List.of(Map.of("text", "🗺 Select Ethiopia Region")),
                List.of(Map.of("text", "⬅️ Back"), Map.of("text", "🏠 Main")),
                List.of(Map.of("text", "❌ Cancel"))
        );

        body.put("reply_markup", persistentReplyKeyboard(keyboard));
        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendLocationChoiceMenu error: " + e.getMessage());
    }
}
public void sendRegionSelectionKeyboard(Long chatId) {
    sendReplyKeyboardText(
            chatId,
            "🗺 <b>Select region:</b>",
            EthiopiaLocationCatalog.getRegions(),
            2,
            true
    );
}

public void sendCitySelectionKeyboard(Long chatId, String region) {
    sendReplyKeyboardText(
            chatId,
            "🏙 <b>Select City in " + region + " Region</b>",
            EthiopiaLocationCatalog.getCitiesByRegion(region),
            2,
            true
    );
}

public void sendAddisSubCityKeyboard(Long chatId) {
    sendReplyKeyboardText(
            chatId,
            "🏙 <b>Select Sub-City in Addis Ababa</b>",
            EthiopiaLocationCatalog.getAddisAbabaSubCities(),
            2,
            true
    );
}

public void sendAreaSelectionKeyboard(Long chatId, String title, List<String> areas) {
    sendReplyKeyboardText(
            chatId,
            title,
            areas,
            2,
            true
    );
}

private void sendReplyKeyboardText(Long chatId,
                                   String text,
                                   List<String> options,
                                   int columns,
                                   boolean includeNav) {
    try {
        String url = apiUrl + "/sendMessage";

        List<List<Map<String, Object>>> keyboard = new ArrayList<>();
        List<Map<String, Object>> currentRow = new ArrayList<>();

        for (String option : options) {
            currentRow.add(Map.of("text", option));
            if (currentRow.size() == columns) {
                keyboard.add(currentRow);
                currentRow = new ArrayList<>();
            }
        }

        if (!currentRow.isEmpty()) {
            keyboard.add(currentRow);
        }

        if (includeNav) {
            keyboard.add(List.of(
                    Map.of("text", "⬅️ Back"),
                    Map.of("text", "🏠 Main")
            ));
            keyboard.add(List.of(
                    Map.of("text", "❌ Cancel")
            ));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("parse_mode", "HTML");
        body.put("reply_markup", persistentReplyKeyboard(keyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendReplyKeyboardText error: " + e.getMessage());
    }
}
public void sendRegionKeyboard(Long chatId) {
    sendSelectionKeyboard(chatId, "🗺 <b>Select region:</b>", EthiopiaLocationCatalog.getRegions(), 2);
}



public void sendSubCityKeyboard(Long chatId, String city, List<String> subCities) {
    sendSelectionKeyboard(chatId, "🏙 <b>Select Sub-City in " + city + "</b>", subCities, 2);
}


private void sendSelectionKeyboard(Long chatId, String text, List<String> values, int columns) {
    try {
        String url = apiUrl + "/sendMessage";

        List<List<Map<String, Object>>> keyboard = new ArrayList<>();
        List<Map<String, Object>> row = new ArrayList<>();

        for (String value : values) {
            row.add(Map.of("text", value));
            if (row.size() == columns) {
                keyboard.add(row);
                row = new ArrayList<>();
            }
        }

        if (!row.isEmpty()) {
            keyboard.add(row);
        }

        keyboard.add(List.of(
                Map.of("text", "⬅️ Back"),
                Map.of("text", "🏠 Main")
        ));
        keyboard.add(List.of(
                Map.of("text", "❌ Cancel")
        ));

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("parse_mode", "HTML");
        body.put("reply_markup", persistentReplyKeyboard(keyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendSelectionKeyboard error: " + e.getMessage());
    }
}
    public void sendRegionKeyboard(Long chatId, List<String> regions) {
        try {
            String url = apiUrl + "/sendMessage";

            List<List<Map<String, Object>>> keyboard = buildTwoColumnKeyboard(regions);
            keyboard.add(List.of(Map.of("text", "⬅️ Back"), Map.of("text", "🏠 Main")));

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", "🗺 Select region:");
            body.put("reply_markup", persistentReplyKeyboard(keyboard));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendRegionKeyboard error: " + e.getMessage());
        }
    }

    public void sendCityKeyboard(Long chatId, String region, List<String> cities) {
        try {
            String url = apiUrl + "/sendMessage";

            List<List<Map<String, Object>>> keyboard = buildTwoColumnKeyboard(cities);
            keyboard.add(List.of(Map.of("text", "⬅️ Back"), Map.of("text", "🏠 Main")));
            keyboard.add(List.of(Map.of("text", "❌ Cancel")));

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", "🏙 Select city in " + region + ":");
            body.put("reply_markup", persistentReplyKeyboard(keyboard));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendCityKeyboard error: " + e.getMessage());
        }
    }

    public void sendAreaKeyboard(Long chatId, String city, List<String> areas) {
        try {
            String url = apiUrl + "/sendMessage";

            List<List<Map<String, Object>>> keyboard = buildTwoColumnKeyboard(areas);
            keyboard.add(List.of(Map.of("text", "⬅️ Back"), Map.of("text", "🏠 Main")));
            keyboard.add(List.of(Map.of("text", "❌ Cancel")));

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", "📌 Select area in " + city + ":");
            body.put("reply_markup", persistentReplyKeyboard(keyboard));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendAreaKeyboard error: " + e.getMessage());
        }
    }

    /* ---------------- REGISTRATION FLOW ---------------- */

    public void sendRegistrationStepMessage(Long chatId, String text, RegistrationStep step) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "HTML");
            body.put("reply_markup", buildRegistrationReplyKeyboard(step));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendRegistrationStepMessage error: " + e.getMessage());
        }
    }

    private Map<String, Object> buildRegistrationReplyKeyboard(RegistrationStep step) {
        List<List<Map<String, Object>>> keyboard = new ArrayList<>();

        if (step == RegistrationStep.PHONE) {
            keyboard.add(List.of(
                    Map.of("text", "📱 Share Phone Number", "request_contact", true)
            ));
        }

        keyboard.add(List.of(Map.of("text", "⬅️ Back"), Map.of("text", "🏠 Main")));
        keyboard.add(List.of(Map.of("text", "❌ Cancel")));

        return persistentReplyKeyboard(keyboard);
    }

    public void sendRegistrationNamePrompt(Long chatId) {
        sendRegistrationStepMessage(
                chatId,
                "🏥 <b>Pharmacy Registration</b>\n\nStep 1/7\nPlease enter your pharmacy name.",
                RegistrationStep.NAME
        );
    }

    public void sendRegistrationRegionPrompt(Long chatId) {
        sendRegistrationStepMessage(
                chatId,
                "🏥 <b>Pharmacy Registration</b>\n\nStep 2/7\n🗺 Select your region in Ethiopia.",
                RegistrationStep.CITY
        );
    }

    public void sendRegistrationCityPrompt(Long chatId) {
        sendRegistrationStepMessage(
                chatId,
                "🏥 <b>Pharmacy Registration</b>\n\nStep 2/7\n📍 Enter your city",
                RegistrationStep.CITY
        );
    }

    public void sendRegistrationAreaPrompt(Long chatId) {
        sendRegistrationStepMessage(
                chatId,
                "🏥 <b>Pharmacy Registration</b>\n\nStep 3/7\n📌 Enter your area",
                RegistrationStep.AREA
        );
    }

    public void sendRegistrationPhonePrompt(Long chatId) {
        sendRegistrationStepMessage(
                chatId,
                "🏥 <b>Pharmacy Registration</b>\n\nStep 2/7\n📞 Enter phone number\nExample: 0912345678\n\nOr tap <b>Share Phone Number</b> below.",
                RegistrationStep.PHONE
        );
    }

    public void sendRegistrationLocationChoice(Long chatId) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                "📍 <b>Pharmacy Location</b>\n\n" +
                "Step 6/7\n" +
                "Choose how you want to set the pharmacy location.\n\n" +
                "1. Share exact pharmacy location\n" +
                "2. Paste Google Maps link / coordinates\n" +
                "3. Select region → city/sub-city → area"
        );
        body.put("parse_mode", "HTML");

        // IMPORTANT:
        // Do NOT use request_location here.
        // First let the user tap the text button,
        // then in handleTextMessage() set exact-location mode,
        // then send a second keyboard that requests location.
        List<List<Map<String, Object>>> keyboard = List.of(
                List.of(Map.of("text", "📍 Share Exact Pharmacy Location")),
                List.of(Map.of("text", "🔗 Paste Google Maps Link")),
                List.of(Map.of("text", "🗺 Select Ethiopia Region")),
                List.of(Map.of("text", "⬅️ Back"), Map.of("text", "🏠 Main")),
                List.of(Map.of("text", "❌ Cancel"))
        );

        body.put("reply_markup", persistentReplyKeyboard(keyboard));
        restTemplate.postForObject(url, body, String.class);
    } catch (Exception e) {
        System.out.println("sendRegistrationLocationChoice error: " + e.getMessage());
    }
}
public void sendUserReservationItemReadOnly(Long chatId,
                                            Long reservationId,
                                            String pharmacyName,
                                            String pharmacyAddress,
                                            String medicine,
                                            Integer quantity,
                                            String status) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                "🆔 <b>ID:</b> " + reservationId + "\n" +
                "🏥 <b>Pharmacy:</b> " + pharmacyName + "\n" +
                "📍 <b>Address:</b> " + pharmacyAddress + "\n" +
                "💊 <b>Medicine:</b> " + medicine + "\n" +
                "🔢 <b>Quantity:</b> " + quantity + "\n" +
                "📌 <b>Status:</b> " + status
        );
        body.put("parse_mode", "HTML");

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendUserReservationItemReadOnly error: " + e.getMessage());
    }
}
public void sendPharmacyPendingReservationCard(Long chatId,
                                               Long reservationId,
                                               Long userId,
                                               String medicineName,
                                               Integer quantity,
                                               String customerPhone,
                                               String customerName) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                "📦 <b>Pending Reservation</b>\n\n" +
                "🆔 <b>ID:</b> " + reservationId + "\n" +
                "💊 <b>Medicine:</b> " + medicineName + "\n" +
                "🔢 <b>Quantity:</b> " + quantity + "\n" +
                "👤 <b>Customer:</b> " + customerName + "\n" +
                "📱 <b>Phone:</b> " + customerPhone + "\n" +
                "👤 <b>User ID:</b> " + userId
        );
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> keyboard = List.of(
                List.of(
                        Map.of("text", "✅ Approve", "callback_data", "approve_res_" + reservationId),
                        Map.of("text", "❌ Reject", "callback_data", "reject_res_" + reservationId)
                )
        );

        body.put("reply_markup", Map.of("inline_keyboard", keyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendPharmacyPendingReservationCard error: " + e.getMessage());
    }
}
public void sendPharmacyApprovedReservationCard(Long chatId,
                                                Long reservationId,
                                                Long userId,
                                                String medicineName,
                                                Integer quantity,
                                                String customerPhone,
                                                String customerName,
                                                String holdUntil) {
    try {
        String url = apiUrl + "/sendMessage";

        String holdLine = (holdUntil != null && !holdUntil.isBlank())
                ? "⏳ <b>Hold Until:</b> " + holdUntil + "\n"
                : "";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                "✅ <b>Approved Reservation</b>\n\n" +
                "🆔 <b>ID:</b> " + reservationId + "\n" +
                "💊 <b>Medicine:</b> " + medicineName + "\n" +
                "🔢 <b>Quantity:</b> " + quantity + "\n" +
                "👤 <b>Customer:</b> " + customerName + "\n" +
                "📱 <b>Phone:</b> " + customerPhone + "\n" +
                "👤 <b>User ID:</b> " + userId + "\n" +
                holdLine
        );
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> keyboard = List.of(
                List.of(
                        Map.of("text", "📦 Fulfilled", "callback_data", "fulfill_res_" + reservationId)
                )
        );

        body.put("reply_markup", Map.of("inline_keyboard", keyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendPharmacyApprovedReservationCard error: " + e.getMessage());
    }
}
public void sendPharmacyReservationReadOnlyCard(Long chatId,
                                                String title,
                                                Long reservationId,
                                                Long userId,
                                                String medicineName,
                                                Integer quantity,
                                                String customerPhone,
                                                String customerName,
                                                String status,
                                                String extraLine) {
    try {
        String url = apiUrl + "/sendMessage";

        String extra = (extraLine != null && !extraLine.isBlank())
                ? extraLine + "\n"
                : "";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                title + "\n\n" +
                "🆔 <b>ID:</b> " + reservationId + "\n" +
                "💊 <b>Medicine:</b> " + medicineName + "\n" +
                "🔢 <b>Quantity:</b> " + quantity + "\n" +
                "👤 <b>Customer:</b> " + customerName + "\n" +
                "📱 <b>Phone:</b> " + customerPhone + "\n" +
                "👤 <b>User ID:</b> " + userId + "\n" +
                extra +
                "📌 <b>Status:</b> " + status
        );
        body.put("parse_mode", "HTML");

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendPharmacyReservationReadOnlyCard error: " + e.getMessage());
    }
}
public void sendPharmacyDetailsWithPhoto(Long chatId,
                                         String photoFileId,
                                         String name,
                                         String fullAddress,
                                         String phone,
                                         Double distance,
                                         Long pharmacyId,
                                         String medicineName,
                                         Double rating,
                                         BigDecimal price,
                                         Integer stockQuantity,
                                         boolean outOfStock,
                                         boolean openNow,
                                         String openTime,
                                         String closeTime,
                                         String lastStockUpdate,
                                         Double latitude,
                                         Double longitude) {
    try {
        String url = apiUrl + "/sendPhoto";

        String ratingText = rating == null ? "N/A" : String.format("%.1f", rating);
        String priceText = price == null ? "not set" : price.stripTrailingZeros().toPlainString() + " ETB";
        String distanceText = distance == null ? "N/A" : String.format("%.2f", distance) + " km";
        String stockText = outOfStock ? "Out of stock" : (stockQuantity == null ? 0 : stockQuantity) + " left";
        String hoursText = (openTime != null && closeTime != null)
                ? openTime + " - " + closeTime
                : "Not set";

        String caption =
                "ℹ️ <b>Pharmacy Details</b>\n\n" +
                "🏥 <b>Name:</b> " + name + "\n" +
                "💊 <b>Medicine:</b> " + medicineName + "\n" +
                "📍 <b>Address:</b> " + fullAddress + "\n" +
                "📞 <b>Phone:</b> " + (phone == null ? "N/A" : phone) + "\n" +
                "📏 <b>Distance:</b> " + distanceText + "\n" +
                "⭐ <b>Rating:</b> " + ratingText + "/5\n" +
                "💰 <b>Price:</b> " + priceText + "\n" +
                "🕒 <b>Hours:</b> " + hoursText + "\n" +
                "📌 <b>Status:</b> " + (openNow ? "Open now" : "Closed") + "\n" +
                "📦 <b>Stock:</b> " + stockText + "\n" +
                "🕘 <b>Last Stock Update:</b> " + (lastStockUpdate == null ? "N/A" : lastStockUpdate);

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("photo", photoFileId);
        body.put("caption", caption);
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> keyboard = new ArrayList<>();
        keyboard.add(List.of(
                Map.of("text", "🧭 Navigate", "url", navigateUrl),
                Map.of("text", "📞 Call", "callback_data", "call_" + pharmacyId)
        ));
        keyboard.add(List.of(
                Map.of("text", "📦 Reserve", "callback_data", "toggle_reserve_" + pharmacyId + "_" + medicineName),
                Map.of("text", "🔽 Hide Details", "callback_data", "hide_details_" + pharmacyId + "_" + medicineName)
        ));

        body.put("reply_markup", Map.of("inline_keyboard", keyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendPharmacyDetailsWithPhoto error: " + e.getMessage());
    }
}
public void sendUserReservationItemWithCancel(Long chatId,
                                              Long reservationId,
                                              String pharmacyName,
                                              String pharmacyAddress,
                                              String medicine,
                                              Integer quantity,
                                              String status) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                "🆔 <b>ID:</b> " + reservationId + "\n" +
                "🏥 <b>Pharmacy:</b> " + pharmacyName + "\n" +
                "📍 <b>Address:</b> " + pharmacyAddress + "\n" +
                "💊 <b>Medicine:</b> " + medicine + "\n" +
                "🔢 <b>Quantity:</b> " + quantity + "\n" +
                "📌 <b>Status:</b> " + status
        );
        body.put("parse_mode", "HTML");

        Map<String, Object> cancelButton = new HashMap<>();
        cancelButton.put("text", "❌ Cancel");
        cancelButton.put("callback_data", "cancel_res_" + reservationId);

        Map<String, Object> replyMarkup = new HashMap<>();
        replyMarkup.put("inline_keyboard", List.of(List.of(cancelButton)));

        body.put("reply_markup", replyMarkup);

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendUserReservationItemWithCancel error: " + e.getMessage());
    }
}

public void sendMessage(Long chatId, String text, String parseMode) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("parse_mode", parseMode);

        restTemplate.postForObject(url, body, String.class);
    } catch (Exception e) {
        System.out.println("sendMessage error: " + e.getMessage());
    }
}
public void sendAccountOverview(Long chatId, String text) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> keyboard = List.of(
                List.of(Map.of("text", "📦 My Reservations"), Map.of("text", "📍 Share Location")),
                List.of(Map.of("text", "🏥 Register Pharmacy"), Map.of("text", "🔄 Refresh")),
                List.of(Map.of("text", "🏠 Main"))
        );

        body.put("reply_markup", persistentReplyKeyboard(keyboard));
        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendAccountOverview error: " + e.getMessage());
    }
}

public void sendAccountMenu(Long chatId, boolean isRegisteredPharmacy) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", "⚙️ <b>Account Actions</b>");
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> keyboard;

        if (isRegisteredPharmacy) {
            keyboard = List.of(
                    List.of(Map.of("text", "📦 My Reservations")),
                    List.of(Map.of("text", "📍 Share Location"), Map.of("text", "⚙️ Profile")),
                    List.of(Map.of("text", "🏠 Main"))
            );
        } else {
          keyboard = List.of(
        List.of(Map.of("text", "📦 My Reservations"), Map.of("text", "❤️ Favorite Pharmacies")),
        List.of(Map.of("text", "📍 Share Location"), Map.of("text", "🏥 Register Pharmacy")),
        List.of(Map.of("text", "🏠 Main"))
);
        }

        body.put("reply_markup", persistentReplyKeyboard(keyboard));
        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendAccountMenu error: " + e.getMessage());
    }
}
    public void sendRegistrationGoogleMapHelp(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text",
                    "🔗 <b>Paste Google Maps Link</b>\n\n" +
                    "Paste a valid Google Maps link or coordinates.\n\n" +
                    "Example:\n" +
                    "https://maps.google.com/?q=8.9806,38.7578\n\n" +
                    "or\n" +
                    "8.9806,38.7578"
            );
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "📍 Share Exact Pharmacy Location")),
                    List.of(Map.of("text", "🗺 Select Ethiopia Region")),
                    List.of(Map.of("text", "⬅️ Back"), Map.of("text", "🏠 Main")),
                    List.of(Map.of("text", "❌ Cancel"))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendRegistrationGoogleMapHelp error: " + e.getMessage());
        }
    }

    public void sendRegistrationExactLocationHelp(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text",
                    "📍 <b>Share Exact Pharmacy Location</b>\n\n" +
                    "Tap below to send the exact pharmacy location."
            );
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "📍 Send Pharmacy Location", "request_location", true)),
                    List.of(Map.of("text", "🔗 Paste Google Maps Link")),
                    List.of(Map.of("text", "🗺 Select Ethiopia Region")),
                    List.of(Map.of("text", "⬅️ Back"), Map.of("text", "🏠 Main")),
                    List.of(Map.of("text", "❌ Cancel"))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendRegistrationExactLocationHelp error: " + e.getMessage());
        }
    }

    public void sendEthiopiaRegionKeyboard(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", "🗺 <b>Select Region in Ethiopia</b>");
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "Addis Ababa"), Map.of("text", "Oromia")),
                    List.of(Map.of("text", "Amhara"), Map.of("text", "Tigray")),
                    List.of(Map.of("text", "SNNPR"), Map.of("text", "Sidama")),
                    List.of(Map.of("text", "Afar"), Map.of("text", "Somali")),
                    List.of(Map.of("text", "Benishangul-Gumuz"), Map.of("text", "Gambela")),
                    List.of(Map.of("text", "Harari"), Map.of("text", "Dire Dawa")),
                    List.of(Map.of("text", "⬅️ Back"), Map.of("text", "🏠 Main")),
                    List.of(Map.of("text", "❌ Cancel"))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendEthiopiaRegionKeyboard error: " + e.getMessage());
        }
    }

    public void sendAddisAbabaCityKeyboard(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", "🏙 <b>Select City in Addis Ababa Region</b>");
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "Addis Ababa")),
                    List.of(Map.of("text", "⬅️ Back"), Map.of("text", "🏠 Main")),
                    List.of(Map.of("text", "❌ Cancel"))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendAddisAbabaCityKeyboard error: " + e.getMessage());
        }
    }

    public void sendAddisAbabaSubCityKeyboard(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", "🏙 <b>Select Sub-City in Addis Ababa</b>");
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "Addis Ketema"), Map.of("text", "Akaky Kaliti")),
                    List.of(Map.of("text", "Arada"), Map.of("text", "Bole")),
                    List.of(Map.of("text", "Gullele"), Map.of("text", "Kirkos")),
                    List.of(Map.of("text", "Kolfe Keranio"), Map.of("text", "Lideta")),
                    List.of(Map.of("text", "Nifas Silk Lafto"), Map.of("text", "Yeka")),
                    List.of(Map.of("text", "Lemi Kura")),
                    List.of(Map.of("text", "⬅️ Back"), Map.of("text", "🏠 Main")),
                    List.of(Map.of("text", "❌ Cancel"))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendAddisAbabaSubCityKeyboard error: " + e.getMessage());
        }
    }

    public void sendAddisAbabaAreaBySubCityKeyboard(Long chatId, String subCity, List<String> areas) {
        try {
            String url = apiUrl + "/sendMessage";

            List<List<Map<String, Object>>> keyboard = buildTwoColumnKeyboard(areas);
            keyboard.add(List.of(Map.of("text", "⬅️ Back"), Map.of("text", "🏠 Main")));
            keyboard.add(List.of(Map.of("text", "❌ Cancel")));

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", "📍 <b>Select Area in " + subCity + "</b>");
            body.put("parse_mode", "HTML");
            body.put("reply_markup", persistentReplyKeyboard(keyboard));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendAddisAbabaAreaBySubCityKeyboard error: " + e.getMessage());
        }
    }

    public void sendSimpleRegionCityKeyboard(Long chatId, String region, List<String> cities) {
        try {
            String url = apiUrl + "/sendMessage";

            List<List<Map<String, Object>>> keyboard = buildTwoColumnKeyboard(cities);
            keyboard.add(List.of(Map.of("text", "⬅️ Back"), Map.of("text", "🏠 Main")));
            keyboard.add(List.of(Map.of("text", "❌ Cancel")));

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", "🏙 <b>Select City in " + region + "</b>");
            body.put("parse_mode", "HTML");
            body.put("reply_markup", persistentReplyKeyboard(keyboard));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendSimpleRegionCityKeyboard error: " + e.getMessage());
        }
    }

    public void sendSimpleAreaKeyboard(Long chatId, String city, List<String> areas) {
        try {
            String url = apiUrl + "/sendMessage";

            List<List<Map<String, Object>>> keyboard = buildTwoColumnKeyboard(areas);
            keyboard.add(List.of(Map.of("text", "⬅️ Back"), Map.of("text", "🏠 Main")));
            keyboard.add(List.of(Map.of("text", "❌ Cancel")));

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", "📍 <b>Select Area in " + city + "</b>");
            body.put("parse_mode", "HTML");
            body.put("reply_markup", persistentReplyKeyboard(keyboard));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendSimpleAreaKeyboard error: " + e.getMessage());
        }
    }

    /* ---------------- MEDICINES ---------------- */

    public void sendMedicinePicker(Long chatId, List<String> selected) {
        try {
            String url = apiUrl + "/sendMessage";
            Map<String, Object> body = buildMedicinePickerBody(chatId, selected);
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMedicinePicker error: " + e.getMessage());
        }
    }

    public void editMedicinePicker(Long chatId, Integer messageId, List<String> selected) {
        try {
            String url = apiUrl + "/editMessageText";

            String selectedText = selected == null || selected.isEmpty()
                    ? "None"
                    : String.join(", ", selected);

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("message_id", messageId);
            body.put("text",
                    "💊 <b>Select medicines</b>\n\n "+
                    "Step 5/7\n\n" +
                    "Selected:\n" + selectedText
            );
            body.put("parse_mode", "HTML");
            body.put("reply_markup", buildMedicineReplyMarkup(selected));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("editMedicinePicker error: " + e.getMessage());
        }
    }

    private Map<String, Object> buildMedicinePickerBody(Long chatId, List<String> selected) {
        String selectedText = selected == null || selected.isEmpty()
                ? "None"
                : String.join(", ", selected);

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                "💊 <b>Select medicines</b>\n\n" +
                "Selected:\n" + selectedText
        );
        body.put("parse_mode", "HTML");
        body.put("reply_markup", buildMedicineReplyMarkup(selected));

        return body;
    }

    private Map<String, Object> buildMedicineReplyMarkup(List<String> selected) {
        List<String> current = selected == null ? List.of() : selected;

        List<List<Map<String, Object>>> keyboard = List.of(
                List.of(medButton("insulin", current), medButton("paracetamol", current)),
                List.of(medButton("amoxicillin", current), medButton("ibuprofen", current)),
                List.of(medButton("ceftriaxone", current), medButton("metformin", current)),
                List.of(
                        Map.of("text", "➕ Add Custom", "callback_data", "med_custom"),
                        Map.of("text", "✅ Done", "callback_data", "med_done")
                ),
                List.of(
                        Map.of("text", "🗑 Clear", "callback_data", "med_clear"),
                        Map.of("text", "❌ Cancel", "callback_data", "med_cancel")
                )
        );

        return Map.of("inline_keyboard", keyboard);
    }

    private Map<String, Object> medButton(String medicine, List<String> selected) {
        boolean chosen = selected.contains(medicine);

        String label = chosen
                ? "✅ " + capitalizeMedicine(medicine)
                : capitalizeMedicine(medicine);

        return Map.of(
                "text", label,
                "callback_data", "med_toggle_" + medicine
        );
    }

    public void sendMedicineSuggestions(Long chatId, List<String> suggestions, String rawInput) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", "💊 Select suggested medicine or use your typed value");
            body.put("parse_mode", "HTML");

            List<List<Map<String, String>>> keyboard = suggestions.stream()
                    .map(s -> List.of(
                            Map.of("text", capitalizeMedicine(s), "callback_data", "med_pick_" + s.toLowerCase())
                    ))
                    .collect(Collectors.toList());

            keyboard.add(List.of(
                    Map.of("text", "✅ Use \"" + rawInput + "\"", "callback_data", "med_pick_" + rawInput.toLowerCase())
            ));

            keyboard.add(List.of(
                    Map.of("text", "❌ Cancel", "callback_data", "med_custom_cancel")
            ));

            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMedicineSuggestions error: " + e.getMessage());
        }
    }

    /* ---------------- HOURS ---------------- */

    public void sendHourPicker(Long chatId, String title, String type) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", title);
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(
                            Map.of("text", "00", "callback_data", "time_" + type + "_hour_00"),
                            Map.of("text", "01", "callback_data", "time_" + type + "_hour_01"),
                            Map.of("text", "02", "callback_data", "time_" + type + "_hour_02"),
                            Map.of("text", "03", "callback_data", "time_" + type + "_hour_03")
                    ),
                    List.of(
                            Map.of("text", "04", "callback_data", "time_" + type + "_hour_04"),
                            Map.of("text", "05", "callback_data", "time_" + type + "_hour_05"),
                            Map.of("text", "06", "callback_data", "time_" + type + "_hour_06"),
                            Map.of("text", "07", "callback_data", "time_" + type + "_hour_07")
                    ),
                    List.of(
                            Map.of("text", "08", "callback_data", "time_" + type + "_hour_08"),
                            Map.of("text", "09", "callback_data", "time_" + type + "_hour_09"),
                            Map.of("text", "10", "callback_data", "time_" + type + "_hour_10"),
                            Map.of("text", "11", "callback_data", "time_" + type + "_hour_11")
                    ),
                    List.of(
                            Map.of("text", "12", "callback_data", "time_" + type + "_hour_12"),
                            Map.of("text", "13", "callback_data", "time_" + type + "_hour_13"),
                            Map.of("text", "14", "callback_data", "time_" + type + "_hour_14"),
                            Map.of("text", "15", "callback_data", "time_" + type + "_hour_15")
                    ),
                    List.of(
                            Map.of("text", "16", "callback_data", "time_" + type + "_hour_16"),
                            Map.of("text", "17", "callback_data", "time_" + type + "_hour_17"),
                            Map.of("text", "18", "callback_data", "time_" + type + "_hour_18"),
                            Map.of("text", "19", "callback_data", "time_" + type + "_hour_19")
                    ),
                    List.of(
                            Map.of("text", "20", "callback_data", "time_" + type + "_hour_20"),
                            Map.of("text", "21", "callback_data", "time_" + type + "_hour_21"),
                            Map.of("text", "22", "callback_data", "time_" + type + "_hour_22"),
                            Map.of("text", "23", "callback_data", "time_" + type + "_hour_23")
                    )
            );

            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendHourPicker error: " + e.getMessage());
        }
    }

    public void sendMinutePicker(Long chatId, String title, String type, int hour) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", title);
            body.put("parse_mode", "HTML");

            String hh = String.format("%02d", hour);

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(
                            Map.of("text", "00", "callback_data", "time_" + type + "_minute_" + hh + "_00"),
                            Map.of("text", "15", "callback_data", "time_" + type + "_minute_" + hh + "_15"),
                            Map.of("text", "30", "callback_data", "time_" + type + "_minute_" + hh + "_30"),
                            Map.of("text", "45", "callback_data", "time_" + type + "_minute_" + hh + "_45")
                    )
            );

            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMinutePicker error: " + e.getMessage());
        }
    }
    public void sendUserReservationItem(Long chatId,
                                    Long reservationId,
                                    String medicine,
                                    Integer quantity,
                                    String status) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                "🆔 <b>ID:</b> " + reservationId + "\n" +
                "💊 <b>Medicine:</b> " + medicine + "\n" +
                "🔢 <b>Quantity:</b> " + quantity + "\n" +
                "📌 <b>Status:</b> " + status
        );
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> inlineKeyboard = List.of(
                List.of(
                        Map.of(
                                "text", "❌ Cancel",
                                "callback_data", "user_cancel_res_" + reservationId
                        )
                )
        );

        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendUserReservationItem error: " + e.getMessage());
    }
}

    /* ---------------- RESERVATION ---------------- */

    public void sendPhoneRequestKeyboard(Long chatId, String message) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", message);

            Map<String, Object> contactBtn = Map.of(
                    "text", "📱 Share Phone Number",
                    "request_contact", true
            );

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(contactBtn),
                    List.of(Map.of("text", "⬅️ Back"), Map.of("text", "🏠 Main")),
                    List.of(Map.of("text", "❌ Cancel"))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendPhoneRequestKeyboard error: " + e.getMessage());
        }
    }

    public void sendMessageRemoveKeyboard(Long chatId, String text) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "HTML");
            body.put("reply_markup", Map.of("remove_keyboard", true));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMessageRemoveKeyboard error: " + e.getMessage());
        }
    }

    public void sendReservationQuantityPicker(Long chatId, String medicineName) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text",
                    "📦 <b>Reserve Medicine</b>\n\n" +
                    "💊 Medicine: " + medicineName + "\n\n" +
                    "Select quantity:"
            );
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(
                            Map.of("text", "1", "callback_data", "res_qty_1"),
                            Map.of("text", "2", "callback_data", "res_qty_2"),
                            Map.of("text", "3", "callback_data", "res_qty_3")
                    ),
                    List.of(
                            Map.of("text", "5", "callback_data", "res_qty_5"),
                            Map.of("text", "10", "callback_data", "res_qty_10")
                    ),
                    List.of(Map.of("text", "✍️ Other", "callback_data", "res_qty_other")),
                    List.of(
                            Map.of("text", "⬅️ Back", "callback_data", "res_back"),
                            Map.of("text", "🏠 Main", "callback_data", "res_main")
                    ),
                    List.of(Map.of("text", "❌ Cancel", "callback_data", "res_cancel"))
            );

            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendReservationQuantityPicker error: " + e.getMessage());
        }
    }
public void sendReservationRequestToPharmacy(Long pharmacyChatId,
                                             Long reservationId,
                                             Long userId,
                                             String medicineName,
                                             Integer quantity,
                                             String customerPhone,
                                             String customerName) {
    try {
        String url = apiUrl + "/sendMessage";

        System.out.println("SEND RESERVATION -> pharmacyChatId=" + pharmacyChatId
                + ", reservationId=" + reservationId
                + ", medicine=" + medicineName);

        if (pharmacyChatId == null || pharmacyChatId <= 0) {
            throw new RuntimeException("Invalid pharmacy chat id: " + pharmacyChatId);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", pharmacyChatId);
        body.put("text",
                "📦 <b>New Reservation Request</b>\n\n" +
                "🆔 Reservation ID: " + reservationId + "\n" +
                "💊 Medicine: " + medicineName + "\n" +
                "🔢 Quantity: " + quantity + "\n" +
                "👤 Full Name: " + customerName + "\n" +
                "📱 Phone: " + customerPhone + "\n" +
                "👤 User ID: " + userId + "\n\n" +
                "Choose an action:"
        );
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> keyboard = List.of(
                List.of(
                        Map.of("text", "✅ Approve", "callback_data", "approve_res_" + reservationId),
                        Map.of("text", "❌ Reject", "callback_data", "reject_res_" + reservationId)
                ),
                List.of(
                        Map.of("text", "📦 Fulfilled", "callback_data", "fulfill_res_" + reservationId)
                )
        );

        body.put("reply_markup", Map.of("inline_keyboard", keyboard));

        restTemplate.postForObject(url, body, String.class);

        System.out.println("SEND RESERVATION SUCCESS -> pharmacyChatId=" + pharmacyChatId);

    } catch (Exception e) {
        System.out.println("sendReservationRequestToPharmacy error: " + e.getMessage());
        throw e;
    }
}
public void sendUserReservationItemReadOnly(Long chatId,
                                            Long reservationId,
                                            String pharmacyName,
                                            String pharmacyAddress,
                                            String medicine,
                                            Integer quantity,
                                            String status,
                                            String holdUntil) {
    try {
        String url = apiUrl + "/sendMessage";

        String holdLine = (holdUntil != null && !holdUntil.isBlank())
                ? "⏳ <b>Hold Until:</b> " + holdUntil + "\n"
                : "";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                "🆔 <b>ID:</b> " + reservationId + "\n" +
                "🏥 <b>Pharmacy:</b> " + pharmacyName + "\n" +
                "📍 <b>Address:</b> " + pharmacyAddress + "\n" +
                "💊 <b>Medicine:</b> " + medicine + "\n" +
                "🔢 <b>Quantity:</b> " + quantity + "\n" +
                holdLine +
                "📌 <b>Status:</b> " + status
        );
        body.put("parse_mode", "HTML");

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendUserReservationItemReadOnly error: " + e.getMessage());
    }
}
public void sendUserReservationItemWithCancel(Long chatId,
                                              Long reservationId,
                                              String pharmacyName,
                                              String pharmacyAddress,
                                              String medicine,
                                              Integer quantity,
                                              String status,
                                              String holdUntil) {
    try {
        String url = apiUrl + "/sendMessage";

        String holdLine = (holdUntil != null && !holdUntil.isBlank())
                ? "⏳ <b>Hold Until:</b> " + holdUntil + "\n"
                : "";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                "🆔 <b>ID:</b> " + reservationId + "\n" +
                "🏥 <b>Pharmacy:</b> " + pharmacyName + "\n" +
                "📍 <b>Address:</b> " + pharmacyAddress + "\n" +
                "💊 <b>Medicine:</b> " + medicine + "\n" +
                "🔢 <b>Quantity:</b> " + quantity + "\n" +
                holdLine +
                "📌 <b>Status:</b> " + status
        );
        body.put("parse_mode", "HTML");

        Map<String, Object> cancelButton = new HashMap<>();
        cancelButton.put("text", "❌ Cancel");
        cancelButton.put("callback_data", "cancel_res_" + reservationId);

        Map<String, Object> replyMarkup = new HashMap<>();
        replyMarkup.put("inline_keyboard", List.of(List.of(cancelButton)));

        body.put("reply_markup", replyMarkup);

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendUserReservationItemWithCancel error: " + e.getMessage());
    }
}
    public void editReservationToFulfilledOnly(Long chatId, Integer messageId, Long reservationId) {
        try {
            String url = apiUrl + "/editMessageReplyMarkup";

            Map<String, Object> fulfillBtn = Map.of(
                    "text", "📦 Fulfilled",
                    "callback_data", "fulfill_res_" + reservationId
            );

            List<List<Map<String, Object>>> keyboard = List.of(List.of(fulfillBtn));

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("message_id", messageId);
            body.put("reply_markup", Map.of("inline_keyboard", keyboard));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("editReservationToFulfilledOnly error: " + e.getMessage());
        }
    }

    public void sendReservationManagementMenu(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", "📦 <b>Reservation Management</b>\n\nChoose an action:");
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "📦 View Reservations")),
                    List.of(Map.of("text", "📦 Pending Reservations")),
                    List.of(Map.of("text", "✅ Approved Reservations")),
                    List.of(Map.of("text", "📦 Mark Fulfilled")),
                    List.of(Map.of("text", "📜 Reservation History")),
                    List.of(Map.of("text", "🏠 Home"), Map.of("text", "🔙 Back")),
                    List.of(Map.of("text", "❌ Cancel"))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendReservationManagementMenu error: " + e.getMessage());
        }
    }

    /* ---------------- PHARMACY RESULT ---------------- */
public void sendPharmacyResult(Long chatId,
                               String name,
                               String area,
                               String phone,
                               Double distance,
                               Double latitude,
                               Double longitude,
                               Long pharmacyId,
                               Double rating,
                               boolean canRate,
                               boolean isFavorite,
                               Integer stockQuantity,
                               boolean outOfStock,
                               String medicineName,
                               BigDecimal price,
                               boolean openNow,
                               String openTime,
                               String closeTime) {
    try {
        String url = apiUrl + "/sendMessage";

        String ratingText = rating == null ? "N/A" : String.format("%.1f", rating);
        String priceText = price == null
                ? "not set"
                : price.stripTrailingZeros().toPlainString() + " ETB";

        String distanceText = distance == null
                ? "N/A"
                : String.format("%.2f", distance) + " km away";

        String phoneText = (phone == null || phone.isBlank()) ? "N/A" : phone.trim();

        String hoursText;
        if (openTime != null && closeTime != null) {
            hoursText = (openNow ? "🟢 Open now" : "🔴 Closed") + " • " + openTime + " - " + closeTime;
        } else {
            hoursText = openNow ? "🟢 Open now" : "🔴 Closed";
        }

        String stockText = outOfStock
                ? "❌ Out of stock"
                : "✅ Available: " + (stockQuantity == null ? 0 : stockQuantity) + " left";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                "🏥 <b>" + name + "</b>\n" +
                "📍 " + (area == null ? "N/A" : area) + "\n" +
                "📏 " + distanceText + "\n" +
                "📞 " + phoneText + "\n" +
                "⭐ Rating: " + ratingText + "/5\n" +
                "💰 Price: " + priceText + "\n" +
                "🕒 " + hoursText + "\n" +
                "📦 " + stockText
        );
        body.put("parse_mode", "HTML");

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        List<Map<String, Object>> row1 = new ArrayList<>();
        row1.add(Map.of("text", "🧭 Navigate", "url", navigateUrl));
        if (phone != null && !phone.isBlank()) {
            row1.add(Map.of("text", "📞 Call", "callback_data", "call_" + pharmacyId));
        }
        inlineKeyboard.add(row1);

        List<Map<String, Object>> row2 = new ArrayList<>();
        if (!outOfStock) {
            row2.add(Map.of("text", "📦 Reserve", "callback_data", "toggle_reserve_" + pharmacyId + "_" + medicineName));
        }
        row2.add(Map.of("text", "ℹ️ Details", "callback_data", "details_" + pharmacyId + "_" + medicineName));
        inlineKeyboard.add(row2);

      
        List<Map<String, Object>> row3 = new ArrayList<>();

if (canRate) {
    row3.add(Map.of("text", "⭐ Rate", "callback_data", "show_rate_" + pharmacyId + "_" + medicineName));
}

if (isFavorite) {
    row3.add(Map.of("text", "✅ Saved", "callback_data", "fav_remove_" + pharmacyId));
} else {
    row3.add(Map.of("text", "❤️ Save", "callback_data", "fav_add_" + pharmacyId));
}

if (!row3.isEmpty()) {
    inlineKeyboard.add(row3);
}

        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendPharmacyResult error: " + e.getMessage());
    }
}
public void editPharmacyMessageToCompact(Long chatId,
                                         Integer messageId,
                                         String name,
                                         String area,
                                         String phone,
                                         Double distance,
                                         Double latitude,
                                         Double longitude,
                                         Long pharmacyId,
                                         Double rating,
                                         boolean canRate,
                                         Integer stockQuantity,
                                         boolean outOfStock,
                                         String medicineName,
                                         BigDecimal price,
                                         boolean openNow,
                                         String openTime,
                                         String closeTime) {
    try {
        String url = apiUrl + "/editMessageText";

        String ratingText = rating == null ? "N/A" : String.format("%.1f", rating);
        String priceText = price == null
                ? "not set"
                : price.stripTrailingZeros().toPlainString() + " ETB";

        String distanceText = distance == null
                ? "N/A"
                : String.format("%.2f", distance) + " km away";

        String phoneText = (phone == null || phone.isBlank()) ? "N/A" : phone.trim();

        String hoursText;
        if (openTime != null && closeTime != null) {
            hoursText = (openNow ? "🟢 Open now" : "🔴 Closed") + " • " + openTime + " - " + closeTime;
        } else {
            hoursText = openNow ? "🟢 Open now" : "🔴 Closed";
        }

        String stockText = outOfStock
                ? "❌ Out of stock"
                : "✅ Available: " + (stockQuantity == null ? 0 : stockQuantity) + " left";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("parse_mode", "HTML");
        body.put("text",
                "🏥 <b>" + name + "</b>\n" +
                "📍 " + (area == null ? "N/A" : area) + "\n" +
                "📏 " + distanceText + "\n" +
                "📞 " + phoneText + "\n" +
                "⭐ Rating: " + ratingText + "/5\n" +
                "💰 Price: " + priceText + "\n" +
                "🕒 " + hoursText + "\n" +
                "📦 " + stockText
        );

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        List<Map<String, Object>> row1 = new ArrayList<>();
        row1.add(Map.of("text", "🧭 Navigate", "url", navigateUrl));
        if (phone != null && !phone.isBlank()) {
            row1.add(Map.of("text", "📞 Call", "callback_data", "call_" + pharmacyId));
        }
        inlineKeyboard.add(row1);

        List<Map<String, Object>> row2 = new ArrayList<>();
        if (!outOfStock) {
            row2.add(Map.of("text", "📦 Reserve", "callback_data", "toggle_reserve_" + pharmacyId + "_" + medicineName));
        }
        row2.add(Map.of("text", "ℹ️ Details", "callback_data", "details_" + pharmacyId + "_" + medicineName));
        inlineKeyboard.add(row2);

        if (canRate) {
            inlineKeyboard.add(List.of(
                    Map.of("text", "⭐ Rate", "callback_data", "show_rate_" + pharmacyId + "_" + medicineName)
            ));
        }

        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("editPharmacyMessageToCompact error: " + e.getMessage());
    }
}
public void editPharmacyMessageToggleReserve(Long chatId,
                                             Integer messageId,
                                             String name,
                                             String area,
                                             String phone,
                                             Double latitude,
                                             Double longitude,
                                             Long pharmacyId,
                                             Double rating,
                                             boolean canRate,
                                             Integer stockQuantity,
                                             boolean outOfStock,
                                             String medicineName,
                                             BigDecimal price,
                                             boolean openNow,
                                             String openTime,
                                             String closeTime,
                                             boolean reserveOpen) {
    try {
        String url = apiUrl + "/editMessageReplyMarkup";

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        List<Map<String, Object>> row1 = new ArrayList<>();
        row1.add(Map.of("text", "🧭 Navigate", "url", navigateUrl));

        if (phone != null && !phone.isBlank()) {
            row1.add(Map.of("text", "📞 Call", "callback_data", "call_" + pharmacyId));
        }

        inlineKeyboard.add(row1);

        List<Map<String, Object>> row2 = new ArrayList<>();
        if (!outOfStock) {
            row2.add(Map.of(
                    "text", reserveOpen ? "📦 Close Reserve" : "📦 Reserve",
                    "callback_data", reserveOpen
                            ? "close_reserve_" + pharmacyId + "_" + medicineName
                            : "toggle_reserve_" + pharmacyId + "_" + medicineName
            ));
        }

        row2.add(Map.of("text", "ℹ️ Details", "callback_data", "details_" + pharmacyId + "_" + medicineName));
        inlineKeyboard.add(row2);

        if (reserveOpen && !outOfStock) {
            inlineKeyboard.add(List.of(
                    Map.of("text", "1", "callback_data", "res_qty_pick_" + pharmacyId + "_" + medicineName + "_1"),
                    Map.of("text", "2", "callback_data", "res_qty_pick_" + pharmacyId + "_" + medicineName + "_2"),
                    Map.of("text", "3", "callback_data", "res_qty_pick_" + pharmacyId + "_" + medicineName + "_3")
            ));

            inlineKeyboard.add(List.of(
                    Map.of("text", "5", "callback_data", "res_qty_pick_" + pharmacyId + "_" + medicineName + "_5"),
                    Map.of("text", "10", "callback_data", "res_qty_pick_" + pharmacyId + "_" + medicineName + "_10")
            ));

            inlineKeyboard.add(List.of(
                    Map.of("text", "✍️ Other", "callback_data", "res_qty_custom_" + pharmacyId + "_" + medicineName)
            ));
        }

        if (canRate) {
            inlineKeyboard.add(List.of(
                    Map.of("text", "⭐ Rate", "callback_data", "show_rate_" + pharmacyId + "_" + medicineName)
            ));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("message is not modified")) {
            System.out.println("Reserve toggle skipped: already in same state.");
            return;
        }
        System.out.println("editPharmacyMessageToggleReserve error: " + e.getMessage());
    }
}
public void editPharmacyMessageAskCustomQuantity(Long chatId,
                                                 Integer messageId,
                                                 String name,
                                                 String area,
                                                 String phone,
                                                 Double distance,
                                                 Double latitude,
                                                 Double longitude,
                                                 Long pharmacyId,
                                                 Double rating,
                                                 Integer stockQuantity,
                                                 boolean outOfStock,
                                                 String medicineName,
                                                 BigDecimal price,
                                                 boolean openNow,
                                                 String openTime,
                                                 String closeTime) {
    try {
        String url = apiUrl + "/editMessageText";

        String ratingText = rating == null ? "N/A" : String.format("%.1f", rating);
        String priceText = price == null ? "not set" : price.stripTrailingZeros().toPlainString() + " ETB";
        String distanceText = distance == null ? "N/A" : String.format("%.2f", distance) + " km away";
        String phoneText = (phone == null || phone.isBlank()) ? "N/A" : phone.trim();

        String hoursText;
        if (openTime != null && closeTime != null) {
            hoursText = (openNow ? "🟢 Open now" : "🔴 Closed") + " • " + openTime + " - " + closeTime;
        } else {
            hoursText = openNow ? "🟢 Open now" : "🔴 Closed";
        }

        String stockText = outOfStock
                ? "❌ Out of stock"
                : "✅ Available: " + (stockQuantity == null ? 0 : stockQuantity) + " left";

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("parse_mode", "HTML");
        body.put("text",
                "🏥 <b>" + name + "</b>\n" +
                "📍 " + (area == null ? "N/A" : area) + "\n" +
                "📏 " + distanceText + "\n" +
                "📞 " + phoneText + "\n" +
                "⭐ Rating: " + ratingText + "/5\n" +
                "💰 Price: " + priceText + "\n" +
                "🕒 " + hoursText + "\n" +
                "📦 " + stockText + "\n\n" +
                "✍️ <b>Enter quantity as a number</b>\n" +
                "Example: 4"
        );

        List<List<Map<String, Object>>> keyboard = new ArrayList<>();
        keyboard.add(List.of(
                Map.of("text", "🧭 Navigate", "url", navigateUrl),
                Map.of("text", "📞 Call", "callback_data", "call_" + pharmacyId)
        ));
        keyboard.add(List.of(
                Map.of("text", "⬅️ Back", "callback_data", "close_reserve_" + pharmacyId + "_" + medicineName),
                Map.of("text", "🏠 Main", "callback_data", "res_main")
        ));

        body.put("reply_markup", Map.of("inline_keyboard", keyboard));

        restTemplate.postForObject(url, body, String.class);
    } catch (Exception e) {
        System.out.println("editPharmacyMessageAskCustomQuantity error: " + e.getMessage());
    }
}
public void editPharmacyMessageAskName(Long chatId,
                                       Integer messageId,
                                       String name,
                                       String area,
                                       String phone,
                                       Double distance,
                                       Double latitude,
                                       Double longitude,
                                       Long pharmacyId,
                                       Double rating,
                                       Integer stockQuantity,
                                       boolean outOfStock,
                                       String medicineName,
                                       BigDecimal price,
                                       boolean openNow,
                                       String openTime,
                                       String closeTime,
                                       Integer quantity) {
    try {
        String url = apiUrl + "/editMessageText";

        String ratingText = rating == null ? "N/A" : String.format("%.1f", rating);
        String priceText = price == null ? "not set" : price.stripTrailingZeros().toPlainString() + " ETB";
        String distanceText = distance == null ? "N/A" : String.format("%.2f", distance) + " km away";
        String phoneText = (phone == null || phone.isBlank()) ? "N/A" : phone.trim();

        String hoursText;
        if (openTime != null && closeTime != null) {
            hoursText = (openNow ? "🟢 Open now" : "🔴 Closed") + " • " + openTime + " - " + closeTime;
        } else {
            hoursText = openNow ? "🟢 Open now" : "🔴 Closed";
        }

        String stockText = outOfStock
                ? "❌ Out of stock"
                : "✅ Available: " + (stockQuantity == null ? 0 : stockQuantity) + " left";

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("parse_mode", "HTML");
        body.put("text",
                "🏥 <b>" + name + "</b>\n" +
                "📍 " + (area == null ? "N/A" : area) + "\n" +
                "📏 " + distanceText + "\n" +
                "📞 " + phoneText + "\n" +
                "⭐ Rating: " + ratingText + "/5\n" +
                "💰 Price: " + priceText + "\n" +
                "🕒 " + hoursText + "\n" +
                "📦 " + stockText + "\n\n" +
                "📦 <b>Reserve Medicine</b>\n" +
                "💊 Medicine: " + medicineName + "\n" +
                "🔢 Quantity: " + quantity + "\n\n" +
                "👤 <b>Please enter your full name</b>\n" +
                "Example:\nTeketsel Beyene"
        );

        List<List<Map<String, Object>>> keyboard = new ArrayList<>();
        keyboard.add(List.of(
                Map.of("text", "🧭 Navigate", "url", navigateUrl),
                Map.of("text", "📞 Call", "callback_data", "call_" + pharmacyId)
        ));
        keyboard.add(List.of(
                Map.of("text", "⬅️ Back", "callback_data", "toggle_reserve_" + pharmacyId + "_" + medicineName),
                Map.of("text", "🏠 Main", "callback_data", "res_main")
        ));

        body.put("reply_markup", Map.of("inline_keyboard", keyboard));

        restTemplate.postForObject(url, body, String.class);
    } catch (Exception e) {
        System.out.println("editPharmacyMessageAskName error: " + e.getMessage());
    }
}
public void editPharmacyMessageAskPhone(Long chatId,
                                        Integer messageId,
                                        String name,
                                        String area,
                                        String phone,
                                        Double distance,
                                        Double latitude,
                                        Double longitude,
                                        Long pharmacyId,
                                        Double rating,
                                        Integer stockQuantity,
                                        boolean outOfStock,
                                        String medicineName,
                                        BigDecimal price,
                                        boolean openNow,
                                        String openTime,
                                        String closeTime,
                                        Integer quantity,
                                        String customerName) {
    try {
        String url = apiUrl + "/editMessageText";

        String ratingText = rating == null ? "N/A" : String.format("%.1f", rating);
        String priceText = price == null ? "not set" : price.stripTrailingZeros().toPlainString() + " ETB";
        String distanceText = distance == null ? "N/A" : String.format("%.2f", distance) + " km away";
        String phoneText = (phone == null || phone.isBlank()) ? "N/A" : phone.trim();

        String hoursText;
        if (openTime != null && closeTime != null) {
            hoursText = (openNow ? "🟢 Open now" : "🔴 Closed") + " • " + openTime + " - " + closeTime;
        } else {
            hoursText = openNow ? "🟢 Open now" : "🔴 Closed";
        }

        String stockText = outOfStock
                ? "❌ Out of stock"
                : "✅ Available: " + (stockQuantity == null ? 0 : stockQuantity) + " left";

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("parse_mode", "HTML");
        body.put("text",
                "🏥 <b>" + name + "</b>\n" +
                "📍 " + (area == null ? "N/A" : area) + "\n" +
                "📏 " + distanceText + "\n" +
                "📞 " + phoneText + "\n" +
                "⭐ Rating: " + ratingText + "/5\n" +
                "💰 Price: " + priceText + "\n" +
                "🕒 " + hoursText + "\n" +
                "📦 " + stockText + "\n\n" +
                "📦 <b>Reserve Medicine</b>\n" +
                "💊 Medicine: " + medicineName + "\n" +
                "🔢 Quantity: " + quantity + "\n" +
                "👤 Name: " + customerName + "\n\n" +
                "📱 <b>Please type your phone number</b>\n" +
                "Example:\n0912345678"
        );

        List<List<Map<String, Object>>> keyboard = new ArrayList<>();
        keyboard.add(List.of(
                Map.of("text", "🧭 Navigate", "url", navigateUrl),
                Map.of("text", "📞 Call", "callback_data", "call_" + pharmacyId)
        ));
        keyboard.add(List.of(
                Map.of("text", "⬅️ Back", "callback_data", "toggle_reserve_" + pharmacyId + "_" + medicineName),
                Map.of("text", "🏠 Main", "callback_data", "res_main")
        ));

        body.put("reply_markup", Map.of("inline_keyboard", keyboard));

        restTemplate.postForObject(url, body, String.class);
    } catch (Exception e) {
        System.out.println("editPharmacyMessageAskPhone error: " + e.getMessage());
    }
}
public void editPharmacyMessageToDetails(Long chatId,
                                         Integer messageId,
                                         String name,
                                         String fullAddress,
                                         String formattedAddress,
                                            String landmark,
                                            String plusCode,
                                         String phone,
                                         Double distance,
                                         Double latitude,
                                         Double longitude,
                                         Long pharmacyId,
                                         Double rating,
                                         boolean canRate,
                                         Integer stockQuantity,
                                         boolean outOfStock,
                                         String medicineName,
                                         BigDecimal price,
                                         boolean openNow,
                                         String openTime,
                                         String closeTime,
                                         String lastStockUpdate) {
    try {
        String url = apiUrl + "/editMessageText";

        String ratingText = rating == null ? "N/A" : String.format("%.1f", rating);
        String priceText = price == null
                ? "not set"
                : price.stripTrailingZeros().toPlainString() + " ETB";

        String distanceText = distance == null
                ? "N/A"
                : String.format("%.2f", distance) + " km";

        String phoneText = (phone == null || phone.isBlank()) ? "N/A" : phone.trim();

        String hoursValue;
        if (openTime != null && closeTime != null) {
            hoursValue = openTime + " - " + closeTime;
        } else {
            hoursValue = "Not set";
        }

        String statusText = openNow ? "Open now" : "Closed";
        String stockText = outOfStock
                ? "Out of stock"
                : (stockQuantity == null ? 0 : stockQuantity) + " left";

        String stockUpdatedText = (lastStockUpdate == null || lastStockUpdate.isBlank())
                ? "N/A"
                : lastStockUpdate;

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("parse_mode", "HTML");
        body.put("text",
                "ℹ️ <b>Pharmacy Details</b>\n\n" +
                "🏥 <b>Name:</b> " + name + "\n" +
                "💊 <b>Medicine:</b> " + medicineName + "\n" +
                "📍 <b>Address:</b> " + fullAddress + "\n" +
                 (formattedAddress != null && !formattedAddress.isBlank()
        ? "📍 <b>Exact Address:</b> " + formattedAddress + "\n"
        : "")
+ (landmark != null && !landmark.isBlank()
        ? "🏢 <b>Landmark:</b> " + landmark + "\n"
        : "")
+ (plusCode != null && !plusCode.isBlank()
        ? "➕ <b>Plus Code:</b> " + plusCode + "\n"
        : "")+
                "📞 <b>Phone:</b> " + phoneText + "\n" +
                "📏 <b>Distance:</b> " + distanceText + "\n" +
                "⭐ <b>Rating:</b> " + ratingText + "/5\n" +
                "💰 <b>Price:</b> " + priceText + "\n" +
                "🕒 <b>Hours:</b> " + hoursValue + "\n" +
                "📌 <b>Status:</b> " + statusText + "\n" +
                "📦 <b>Stock:</b> " + stockText + "\n" +
                "🕘 <b>Last Stock Update:</b> " + stockUpdatedText
        );

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        List<Map<String, Object>> row1 = new ArrayList<>();
        row1.add(Map.of("text", "📍 Open Map", "url", navigateUrl));
        if (phone != null && !phone.isBlank()) {
            row1.add(Map.of("text", "📞 Call", "callback_data", "call_" + pharmacyId));
        }
        inlineKeyboard.add(row1);

        List<Map<String, Object>> row2 = new ArrayList<>();
        if (!outOfStock) {
            row2.add(Map.of("text", "📦 Reserve", "callback_data", "toggle_reserve_" + pharmacyId + "_" + medicineName));
        }
        row2.add(Map.of("text", "🔽 Hide Details", "callback_data", "hide_details_" + pharmacyId + "_" + medicineName));
        inlineKeyboard.add(row2);

        if (canRate) {
            inlineKeyboard.add(List.of(
                    Map.of("text", "⭐ Rate", "callback_data", "show_rate_" + pharmacyId + "_" + medicineName)
            ));
        }

        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("editPharmacyMessageToDetails error: " + e.getMessage());
    }
}
public void sendLandmarkChoiceKeyboard(Long chatId) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", "🏢 Send landmark now or skip.");
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> keyboard = List.of(
                List.of(Map.of("text", "⏭ Skip Landmark")),
                List.of(Map.of("text", "⬅️ Back"), Map.of("text", "🏠 Main")),
                List.of(Map.of("text", "❌ Cancel"))
        );

        body.put("reply_markup", persistentReplyKeyboard(keyboard));
        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendLandmarkChoiceKeyboard error: " + e.getMessage());
    }
}
public Integer sendPharmacyMapPreview(Long chatId,
                                      Double latitude,
                                      Double longitude,
                                      String pharmacyName,
                                      String address) {
    try {
        if (latitude == null || longitude == null) {
            return null;
        }

        String url = apiUrl + "/sendVenue";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("latitude", latitude);
        body.put("longitude", longitude);
        body.put("title", pharmacyName == null || pharmacyName.isBlank() ? "Pharmacy Location" : pharmacyName);
        body.put("address", address == null || address.isBlank() ? "Pharmacy Address" : address);

        Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);

        if (response != null && Boolean.TRUE.equals(response.get("ok"))) {
            Object resultObj = response.get("result");
            if (resultObj instanceof Map<?, ?> result) {
                Object messageIdObj = result.get("message_id");
                if (messageIdObj instanceof Number number) {
                    return number.intValue();
                }
            }
        }
    } catch (Exception e) {
        System.out.println("sendPharmacyMapPreview error: " + e.getMessage());
    }

    return null;
}
public void restoreNormalPharmacyButtonsAfterRating(Long chatId,
                                                    Integer messageId,
                                                    Long pharmacyId,
                                                    String medicineName,
                                                    Double latitude,
                                                    Double longitude,
                                                    String phone,
                                                    boolean outOfStock) {
    try {
        String url = apiUrl + "/editMessageReplyMarkup";

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        List<Map<String, Object>> row1 = new ArrayList<>();
        row1.add(Map.of("text", "🧭 Navigate", "url", navigateUrl));

        if (phone != null && !phone.isBlank()) {
            row1.add(Map.of("text", "📞 Call", "callback_data", "call_" + pharmacyId));
        }

        inlineKeyboard.add(row1);

        List<Map<String, Object>> row2 = new ArrayList<>();
        if (!outOfStock) {
           row2.add(Map.of("text", "📦 Reserve", "callback_data", "toggle_reserve_" + pharmacyId + "_" + medicineName));
        }
        row2.add(Map.of("text", "ℹ️ Details", "callback_data", "details_" + pharmacyId + "_" + medicineName));
        inlineKeyboard.add(row2);

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("restoreNormalPharmacyButtonsAfterRating error: " + e.getMessage());
    }
}
public void restoreRateButtonAfterCancel(Long chatId,
                                         Integer messageId,
                                         Long pharmacyId,
                                         String medicineName,
                                         Double latitude,
                                         Double longitude,
                                         String phone,
                                         boolean outOfStock) {
    try {
        String url = apiUrl + "/editMessageReplyMarkup";

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        List<Map<String, Object>> row1 = new ArrayList<>();
        row1.add(Map.of("text", "🧭 Navigate", "url", navigateUrl));

        if (phone != null && !phone.isBlank()) {
            row1.add(Map.of("text", "📞 Call", "callback_data", "call_" + pharmacyId));
        }

        inlineKeyboard.add(row1);

        List<Map<String, Object>> row2 = new ArrayList<>();
        if (!outOfStock) {
            row2.add(Map.of("text", "📦 Reserve", "callback_data", "toggle_reserve_" + pharmacyId + "_" + medicineName));
        }
        row2.add(Map.of("text", "ℹ️ Details", "callback_data", "details_" + pharmacyId + "_" + medicineName));
        inlineKeyboard.add(row2);

        inlineKeyboard.add(List.of(
                Map.of("text", "⭐ Rate", "callback_data", "show_rate_" + pharmacyId + "_" + medicineName)
        ));

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("restoreRateButtonAfterCancel error: " + e.getMessage());
    }
}
public void togglePharmacyReservePicker(Long chatId,
                                        Integer messageId,
                                        String name,
                                        String area,
                                        String phone,
                                        Double distance,
                                        Double latitude,
                                        Double longitude,
                                        Long pharmacyId,
                                        Double rating,
                                        boolean canRate,
                                        Integer stockQuantity,
                                        boolean outOfStock,
                                        String medicineName,
                                        BigDecimal price,
                                        boolean openNow,
                                        String openTime,
                                        String closeTime) {
    try {
        String url = apiUrl + "/editMessageReplyMarkup";

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        List<Map<String, Object>> row1 = new ArrayList<>();
        row1.add(Map.of("text", "🧭 Navigate", "url", navigateUrl));
        if (phone != null && !phone.isBlank()) {
            row1.add(Map.of("text", "📞 Call", "callback_data", "call_" + pharmacyId));
        }
        inlineKeyboard.add(row1);

        List<Map<String, Object>> row2 = new ArrayList<>();
        row2.add(Map.of("text", "📦 Close Reserve", "callback_data", "close_reserve_" + pharmacyId + "_" + medicineName));
        row2.add(Map.of("text", "ℹ️ Details", "callback_data", "details_" + pharmacyId + "_" + medicineName));
        inlineKeyboard.add(row2);

        if (!outOfStock) {
            inlineKeyboard.add(List.of(
                    Map.of("text", "1", "callback_data", "res_qty_pick_" + pharmacyId + "_" + medicineName + "_1"),
                    Map.of("text", "2", "callback_data", "res_qty_pick_" + pharmacyId + "_" + medicineName + "_2"),
                    Map.of("text", "3", "callback_data", "res_qty_pick_" + pharmacyId + "_" + medicineName + "_3")
            ));

            inlineKeyboard.add(List.of(
                    Map.of("text", "5", "callback_data", "res_qty_pick_" + pharmacyId + "_" + medicineName + "_5"),
                    Map.of("text", "10", "callback_data", "res_qty_pick_" + pharmacyId + "_" + medicineName + "_10")
            ));

            inlineKeyboard.add(List.of(
                    Map.of("text", "✍️ Other", "callback_data", "res_qty_custom_" + pharmacyId + "_" + medicineName)
            ));
        }

        if (canRate) {
            inlineKeyboard.add(List.of(
                    Map.of("text", "⭐ Rate", "callback_data", "show_rate_" + pharmacyId + "_" + medicineName)
            ));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("togglePharmacyReservePicker error: " + e.getMessage());
    }
}
public void restorePharmacyCardButtons(Long chatId,
                                       Integer messageId,
                                       Long pharmacyId,
                                       String medicineName,
                                       Double latitude,
                                       Double longitude,
                                       boolean outOfStock,
                                       boolean canRate) {
    try {
        String url = apiUrl + "/editMessageReplyMarkup";
        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        inlineKeyboard.add(List.of(
                Map.of("text", "🧭 Navigate", "url", navigateUrl),
                Map.of("text", "📞 Call", "callback_data", "call_" + pharmacyId)
        ));

        List<Map<String, Object>> row2 = new ArrayList<>();
        if (!outOfStock) {
            row2.add(Map.of("text", "📦 Reserve", "callback_data", "toggle_reserve_" + pharmacyId + "_" + medicineName));
        }
        row2.add(Map.of("text", "ℹ️ Details", "callback_data", "details_" + pharmacyId + "_" + medicineName));
        inlineKeyboard.add(row2);

        if (canRate) {
            inlineKeyboard.add(List.of(
                    Map.of("text", "⭐ Rate", "callback_data", "show_rate_" + pharmacyId + "_" + medicineName)
            ));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("restorePharmacyCardButtons error: " + e.getMessage());
    }
}
public void editPharmacyMessageToRatingPicker(Long chatId,
                                              Integer messageId,
                                              Long pharmacyId,
                                              String medicineName,
                                              Double latitude,
                                              Double longitude,
                                              String phone,
                                              boolean outOfStock) {
    try {
        String url = apiUrl + "/editMessageReplyMarkup";

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        List<Map<String, Object>> row1 = new ArrayList<>();
        row1.add(Map.of("text", "🧭 Navigate", "url", navigateUrl));

        if (phone != null && !phone.isBlank()) {
            row1.add(Map.of("text", "📞 Call", "callback_data", "call_" + pharmacyId));
        }

        inlineKeyboard.add(row1);

        List<Map<String, Object>> row2 = new ArrayList<>();
        if (!outOfStock) {
           row2.add(Map.of("text", "📦 Reserve", "callback_data", "toggle_reserve_" + pharmacyId + "_" + medicineName));
        }
        row2.add(Map.of("text", "ℹ️ Details", "callback_data", "details_" + pharmacyId + "_" + medicineName));
        inlineKeyboard.add(row2);

        inlineKeyboard.add(List.of(
                Map.of("text", "⭐ 1", "callback_data", "rate_" + pharmacyId + "_" + medicineName + "_1"),
                Map.of("text", "⭐ 2", "callback_data", "rate_" + pharmacyId + "_" + medicineName + "_2"),
                Map.of("text", "⭐ 3", "callback_data", "rate_" + pharmacyId + "_" + medicineName + "_3"),
                Map.of("text", "⭐ 4", "callback_data", "rate_" + pharmacyId + "_" + medicineName + "_4"),
                Map.of("text", "⭐ 5", "callback_data", "rate_" + pharmacyId + "_" + medicineName + "_5")
        ));

        inlineKeyboard.add(List.of(
                Map.of("text", "❌ Cancel", "callback_data", "cancel_rate_" + pharmacyId + "_" + medicineName)
        ));

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("editPharmacyMessageToRatingPicker error: " + e.getMessage());
    }
}
public void restorePharmacyButtonsAfterRating(Long chatId,
                                              Integer messageId,
                                              Long pharmacyId,
                                              Double latitude,
                                              Double longitude) {
    try {
        String url = apiUrl + "/editMessageReplyMarkup";

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        List<List<Map<String, Object>>> inlineKeyboard = List.of(
                List.of(
                        Map.of("text", "🧭 Navigate", "url", navigateUrl),
                        Map.of("text", "✅ Rated", "callback_data", "rated_done")
                )
        );

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("restorePharmacyButtonsAfterRating error: " + e.getMessage());
    }
}
public void restoreNormalPharmacyButtons(Long chatId,
                                         Integer messageId,
                                         Long pharmacyId,
                                         String medicineName,
                                         Double latitude,
                                         Double longitude,
                                         boolean outOfStock) {
    try {
        String url = apiUrl + "/editMessageReplyMarkup";

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        if (!outOfStock) {
            inlineKeyboard.add(List.of(
                    Map.of("text", "🧭 Navigate", "url", navigateUrl),
                    Map.of("text", "📦 Reserve", "callback_data", "toggle_reserve_" + pharmacyId + "_" + medicineName)
            ));
        } else {
            inlineKeyboard.add(List.of(
                    Map.of("text", "🧭 Navigate", "url", navigateUrl)
            ));
        }

        inlineKeyboard.add(List.of(
                Map.of("text", "ℹ️ Details", "callback_data", "details_" + pharmacyId + "_" + medicineName),
                Map.of("text", "⭐ Rate", "callback_data", "show_rate_" + pharmacyId + "_" + medicineName)
        ));

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("restoreNormalPharmacyButtons error: " + e.getMessage());
    }
}
public void restorePharmacyButtonsAfterRating(Long chatId,
                                              Integer messageId,
                                              Long pharmacyId,
                                              String medicineName,
                                              Double latitude,
                                              Double longitude,
                                              boolean outOfStock) {
    try {
        String url = apiUrl + "/editMessageReplyMarkup";

        String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        if (!outOfStock) {
            inlineKeyboard.add(List.of(
                    Map.of("text", "🧭 Navigate", "url", navigateUrl),
                    Map.of("text", "📦 Reserve", "callback_data", "reserve_" + pharmacyId + "_" + medicineName)
            ));
        } else {
            inlineKeyboard.add(List.of(
                    Map.of("text", "🧭 Navigate", "url", navigateUrl)
            ));
        }

        inlineKeyboard.add(List.of(
                Map.of("text", "ℹ️ Details", "callback_data", "details_" + pharmacyId + "_" + medicineName),
                Map.of("text", "✅ Rated", "callback_data", "rated_done")
        ));

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("restorePharmacyButtonsAfterRating error: " + e.getMessage());
    }
}
public void sendRatingPicker(Long chatId, Long pharmacyId) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", "⭐ <b>Rate this pharmacy</b>\n\nChoose a rating:");
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> keyboard = List.of(
                List.of(
                        Map.of("text", "⭐ 1", "callback_data", "rate_" + pharmacyId + "_1"),
                        Map.of("text", "⭐ 2", "callback_data", "rate_" + pharmacyId + "_2"),
                        Map.of("text", "⭐ 3", "callback_data", "rate_" + pharmacyId + "_3"),
                        Map.of("text", "⭐ 4", "callback_data", "rate_" + pharmacyId + "_4"),
                        Map.of("text", "⭐ 5", "callback_data", "rate_" + pharmacyId + "_5")
                ),
                List.of(
                        Map.of("text", "❌ Cancel", "callback_data", "cancel_rate")
                )
        );

        body.put("reply_markup", Map.of("inline_keyboard", keyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendRatingPicker error: " + e.getMessage());
    }
}
public void sendPharmacyDetails(Long chatId,
                                String name,
                                String area,
                                String phone,
                                Double distance,
                                Double rating,
                                BigDecimal price,
                                Integer stockQuantity,
                                boolean outOfStock,
                                boolean openNow,
                                String openTime,
                                String closeTime,
                                String medicineName) {
    try {
        String url = apiUrl + "/sendMessage";

        String ratingText = rating == null ? "N/A" : String.format("%.1f", rating);
        String priceText = price == null
                ? "not set"
                : price.stripTrailingZeros().toPlainString() + " ETB";
        String distanceText = distance == null
                ? "N/A"
                : String.format("%.2f", distance) + " km";
        String stockText = outOfStock
                ? "Out of stock"
                : ((stockQuantity == null ? 0 : stockQuantity) + " left");
        String hoursText = (openTime != null && closeTime != null)
                ? openTime + " - " + closeTime
                : "Not set";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                "ℹ️ <b>Pharmacy Details</b>\n\n" +
                "🏥 <b>Name:</b> " + name + "\n" +
                "💊 <b>Medicine:</b> " + medicineName + "\n" +
                "📍 <b>Area:</b> " + (area == null ? "N/A" : area) + "\n" +
                "📞 <b>Phone:</b> " + (phone == null ? "N/A" : phone) + "\n" +
                "📏 <b>Distance:</b> " + distanceText + "\n" +
                "⭐ <b>Rating:</b> " + ratingText + "/5\n" +
                "💰 <b>Price:</b> " + priceText + "\n" +
                "🕒 <b>Hours:</b> " + hoursText + "\n" +
                "📌 <b>Status:</b> " + (openNow ? "Open now" : "Closed") + "\n" +
                "📦 <b>Stock:</b> " + stockText
        );
        body.put("parse_mode", "HTML");

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendPharmacyDetails error: " + e.getMessage());
    }
}
    public void lockRatingKeepNavigation(Long chatId, Integer messageId, double lat, double lon) {
        try {
            String mapLink = "https://www.google.com/maps?q=" + lat + "," + lon;

            Map<String, Object> mapBtn = Map.of("text", "🧭 Navigate", "url", mapLink);
            List<List<Map<String, Object>>> keyboard = List.of(List.of(mapBtn));

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("message_id", messageId);
            body.put("reply_markup", Map.of("inline_keyboard", keyboard));

            restTemplate.postForObject(apiUrl + "/editMessageReplyMarkup", body, String.class);
        } catch (Exception e) {
            System.out.println("lockRatingKeepNavigation error: " + e.getMessage());
        }
    }

    /* ---------------- PROFILE / INVENTORY ---------------- */

 public void sendUpdateMenu(Long chatId) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", "⚙️ Pharmacy Profile Update\n\nSelect what you want to update:");

        List<List<Map<String, Object>>> keyboard = List.of(
                List.of(Map.of("text", "📞 Update Phone")),
                List.of(Map.of("text", "📄 Update License")),
                List.of(Map.of("text", "⏰ Update Hours")),
                List.of(Map.of("text", "💊 Update Medicines")),
                List.of(Map.of("text", "📍 Update Location")),
                List.of(Map.of("text", "🖼 Update Pharmacy Photo")),
                List.of(Map.of("text", "🏠 Home"), Map.of("text", "🔙 Back")),
                List.of(Map.of("text", "❌ Cancel"))
        );

        body.put("reply_markup", persistentReplyKeyboard(keyboard));
        restTemplate.postForObject(url, body, String.class);
    } catch (Exception e) {
        System.out.println("sendUpdateMenu error: " + e.getMessage());
    }
}
    public void sendInventoryMenu(Long chatId) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", "📦 <b>Inventory Management</b>\n\nChoose an action:");
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> keyboard = List.of(
                List.of(
                        Map.of("text", "➕ Add / Update Stock"),
                        Map.of("text", "💰 Update Price")
                ),
                List.of(
                        Map.of("text", "📉 Mark Out of Stock"),
                        Map.of("text", "📋 View Inventory")
                ),
                List.of(
                        Map.of("text", "📤 Export Inventory"),
                        Map.of("text", "📥 Import Inventory CSV")
                ),
                List.of(
                        Map.of("text", "📊 Inventory Summary"),
                        Map.of("text", "⚠️ Low Stock Alert")
                ),
                List.of(
                        Map.of("text", "📈 Demand Insights"),
                        Map.of("text", "🎯 Set Low Stock Threshold")
                ),
                List.of(
                        Map.of("text", "🔙 Back"),
                        Map.of("text", "🏠 Home")
                ),
                List.of(
                        Map.of("text", "❌ Cancel")
                )
        );

        body.put("reply_markup", persistentReplyKeyboard(keyboard));
        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendInventoryMenu error: " + e.getMessage());
    }
}
    public void sendSummaryMenu(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", "📊 <b>Inventory Summary</b>\n\nChoose a period:");
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "📊 Daily Summary")),
                    List.of(Map.of("text", "📊 Weekly Summary")),
                    List.of(Map.of("text", "📊 Monthly Summary")),
                    List.of(Map.of("text", "📊 Yearly Summary")),
                    List.of(Map.of("text", "⚠️ Low Stock Alert")),
                    List.of(Map.of("text", "📈 Demand Insights")),
                    List.of(Map.of("text", "🎯 Set Low Stock Threshold")),
                    List.of(Map.of("text", "📥 Import Inventory CSV")),
                    List.of(Map.of("text", "🏠 Home"), Map.of("text", "🔙 Back")),
                    List.of(Map.of("text", "❌ Cancel"))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendSummaryMenu error: " + e.getMessage());
        }
    }
    public void sendPharmacyPhoto(Long chatId, String photoFileId, String caption) {
    try {
        if (photoFileId == null || photoFileId.isBlank()) {
            return;
        }

        String url = apiUrl + "/sendPhoto";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("photo", photoFileId);
        body.put("caption", caption);
        body.put("parse_mode", "HTML");

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendPharmacyPhoto error: " + e.getMessage());
    }
}
public Integer sendPharmacyPhotoWithMessageId(Long chatId, String photoFileId, String caption) {
    try {
        if (photoFileId == null || photoFileId.isBlank()) {
            return null;
        }

        String url = apiUrl + "/sendPhoto";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("photo", photoFileId);
        body.put("caption", caption);
        body.put("parse_mode", "HTML");

        Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);

        if (response != null && Boolean.TRUE.equals(response.get("ok"))) {
            Object resultObj = response.get("result");
            if (resultObj instanceof Map<?, ?> result) {
                Object messageIdObj = result.get("message_id");
                if (messageIdObj instanceof Number number) {
                    return number.intValue();
                }
            }
        }
    } catch (Exception e) {
        System.out.println("sendPharmacyPhotoWithMessageId error: " + e.getMessage());
    }

    return null;
}
public void deleteMessage(Long chatId, Integer messageId) {
    try {
        if (chatId == null || messageId == null) {
            return;
        }

        String url = apiUrl + "/deleteMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("deleteMessage error: " + e.getMessage());
    }
}
    public void sendLowStockAlert(Long chatId, String medicineName, Integer quantity, Integer threshold) {
        try {
            String text = "⚠️ <b>Low Stock Alert</b>\n\n"
                    + "💊 Medicine: " + medicineName + "\n"
                    + "📦 Current quantity: " + (quantity == null ? 0 : quantity) + "\n"
                    + "🎯 Threshold: " + (threshold == null ? 0 : threshold) + "\n\n"
                    + "Please restock soon.";

            sendMessage(chatId, text);
        } catch (Exception e) {
            System.out.println("sendLowStockAlert error: " + e.getMessage());
        }
    }

    /* ---------------- MULTI MEDICINE ---------------- */

    public void sendMultiMedicineStartMenu(Long chatId, boolean hasSavedLocation) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text",
                    "🔎🛒 <b>Multi-Medicine Search</b>\n\n" +
                    "Find one pharmacy matching several medicines.\n\n" +
                    "Choose location option first."
            );
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = new ArrayList<>();

            if (hasSavedLocation) {
                keyboard.add(List.of(Map.of("text", "📍 Use Saved Location", "callback_data", "multi_loc_saved")));
            }

            keyboard.add(List.of(Map.of("text", "📌 Share Current Location", "callback_data", "multi_loc_share")));
            keyboard.add(List.of(Map.of("text", "🏠 Main", "callback_data", "multi_cancel")));

            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMultiMedicineStartMenu error: " + e.getMessage());
        }
    }

    public void sendMultiMedicinePanel(Long chatId, List<String> selected) {
        try {
            String url = apiUrl + "/sendMessage";

            StringBuilder text = new StringBuilder("🧺 <b>Selected Medicines</b>\n\n");

            if (selected == null || selected.isEmpty()) {
                text.append("No medicines selected yet.");
            } else {
                for (int i = 0; i < selected.size(); i++) {
                    text.append(i + 1).append(". ").append(selected.get(i)).append("\n");
                }
            }

            text.append("\nChoose next action:");

            List<List<Map<String, Object>>> keyboard = new ArrayList<>();
            keyboard.add(List.of(Map.of("text", "🔍 Search Pharmacies", "callback_data", "multi_search")));
            keyboard.add(List.of(
                    Map.of("text", "➕ Add More", "callback_data", "multi_add_more"),
                    Map.of("text", "🗑 Clear", "callback_data", "multi_clear")
            ));

            if (selected != null) {
                for (String med : selected) {
                    keyboard.add(List.of(
                            Map.of("text", "❌ Remove " + med, "callback_data", "multi_remove_" + med.toLowerCase())
                    ));
                }
            }

            keyboard.add(List.of(
                    Map.of("text", "📍 Change Location", "callback_data", "multi_change_location"),
                    Map.of("text", "🏠 Main", "callback_data", "multi_cancel")
            ));

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text.toString());
            body.put("parse_mode", "HTML");
            body.put("reply_markup", Map.of("inline_keyboard", keyboard));

            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMultiMedicinePanel error: " + e.getMessage());
        }
    }

    public void sendMultiMedicineModeKeyboard(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", "🧺 Multi-medicine mode is active.");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "🔍 Search Pharmacies")),
                    List.of(Map.of("text", "➕ Add More"), Map.of("text", "🗑 Clear")),
                    List.of(Map.of("text", "📍 Change Location"), Map.of("text", "🏠 Main")),
                    List.of(Map.of("text", "⬅️ Back"))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMultiMedicineModeKeyboard error: " + e.getMessage());
        }
    }

    public void sendMultiMedicineLocationKeyboard(Long chatId, boolean hasSavedLocation) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text",
                    "🔎🛒 Multi-Medicine Search\n\n" +
                    "Choose location option first."
            );

            List<List<Map<String, Object>>> keyboard = new ArrayList<>();

            if (hasSavedLocation) {
                keyboard.add(List.of(
                        Map.of("text", "📍 Use Saved Location"),
                        Map.of("text", "📌 Share Current Location")
                ));
            } else {
                keyboard.add(List.of(
                        Map.of("text", "📌 Share Current Location")
                ));
            }

            keyboard.add(List.of(
                    Map.of("text", "🗺 Select Ethiopia Region"),
                    Map.of("text", "🏠 Main")
            ));

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMultiMedicineLocationKeyboard error: " + e.getMessage());
        }
    }

    public void sendMultiMedicineChangeLocationMenu(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text",
                    "📍 <b>Change Location</b>\n\n" +
                    "Choose how to update your search location."
            );
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "📍 Share Exact Location", "request_location", true)),
                    List.of(Map.of("text", "🗺 Select Ethiopia Region")),
                    List.of(Map.of("text", "⬅️ Back"), Map.of("text", "🏠 Main"))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMultiMedicineChangeLocationMenu error: " + e.getMessage());
        }
    }

    public void sendMultiMedicineExactLocationRequest(Long chatId) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text",
                    "📍 <b>Share Current Location</b>\n\n" +
                    "Tap the button below to send your exact location for multi-medicine search."
            );
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "📍 Share Exact Location", "request_location", true)),
                    List.of(Map.of("text", "⬅️ Back"), Map.of("text", "🏠 Main"))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMultiMedicineExactLocationRequest error: " + e.getMessage());
        }
    }

    public void sendMultiMedicinePharmacyResult(Long chatId, com.tenahub.bot.dto.MultiMedicinePharmacyResultDTO r) {
        try {
            sendLocation(chatId, r.getLatitude(), r.getLongitude());

            String mapLink = "https://www.google.com/maps?q=" + r.getLatitude() + "," + r.getLongitude();

            String text = "🏥 <b>" + r.getName() + "</b>\n"
                    + "📍 " + r.getArea() + "\n"
                    + "📏 " + String.format("%.2f km away", r.getDistance()) + "\n"
                    + "📞 " + r.getPhone() + "\n"
                    + "⭐ Rating: " + String.format("%.1f", r.getRating()) + "/5\n"
                    + "🕒 " + (r.isOpenNow() ? "Open now ✅" : "Closed now") + "\n\n"
                    + "✅ Matched: " + r.getMatchedCount() + "/" + (r.getMatchedMedicines().size() + r.getMissingMedicines().size()) + "\n"
                    + "💊 Available: " + (r.getMatchedMedicines().isEmpty() ? "None" : String.join(", ", r.getMatchedMedicines())) + "\n"
                    + "❌ Missing: " + (r.getMissingMedicines().isEmpty() ? "None" : String.join(", ", r.getMissingMedicines()));

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = new ArrayList<>();
            keyboard.add(List.of(Map.of("text", "🧭 Navigate", "url", mapLink)));

            if (r.getMatchedCount() > 0) {
                keyboard.add(List.of(
                        Map.of("text", "📦 Reserve Matched", "callback_data", "multi_reserve_" + r.getPharmacyId())
                ));

                if (r.getMatchedCount() > 1) {
                    keyboard.add(List.of(
                            Map.of("text", "🧺 Reserve All Later", "callback_data", "multi_reserve_all_later_" + r.getPharmacyId())
                    ));
                }
            }

            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            restTemplate.postForObject(apiUrl + "/sendMessage", body, String.class);
        } catch (Exception e) {
            System.out.println("sendMultiMedicinePharmacyResult error: " + e.getMessage());
        }
    }

    public void sendMatchedMedicineReservePicker(Long chatId, Long pharmacyId, List<String> matchedMedicines) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text",
                    "📦 <b>Reserve Matched Medicine</b>\n\n" +
                    "Select one medicine to reserve:"
            );
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = new ArrayList<>();

            for (String medicine : matchedMedicines) {
                keyboard.add(List.of(
                        Map.of("text", "💊 " + medicine,
                                "callback_data", "multi_pick_reserve_" + pharmacyId + "_" + medicine.toLowerCase())
                ));
            }

            keyboard.add(List.of(
                    Map.of("text", "❌ Cancel", "callback_data", "multi_pick_reserve_cancel")
            ));

            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendMatchedMedicineReservePicker error: " + e.getMessage());
        }
    }

    /* ---------------- ADMIN PHOTO ACTIONS ---------------- */

    public Integer sendPhotoWithButtons(Long chatId, String fileId, String caption, Long registrationId) {
        try {
            String url = apiUrl + "/sendPhoto";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("photo", fileId);
            body.put("caption", caption);
            body.put("parse_mode", "HTML");

            Map<String, Object> approveBtn = Map.of(
                    "text", "✅ Approve",
                    "callback_data", "approve_" + registrationId
            );

            Map<String, Object> rejectBtn = Map.of(
                    "text", "❌ Reject",
                    "callback_data", "reject_" + registrationId
            );

            body.put("reply_markup", Map.of(
                    "inline_keyboard", List.of(List.of(approveBtn, rejectBtn))
            ));

            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);

            if (response != null && Boolean.TRUE.equals(response.get("ok"))) {
                Object resultObj = response.get("result");

                if (resultObj instanceof Map<?, ?> result) {
                    Object messageIdObj = result.get("message_id");
                    if (messageIdObj instanceof Number number) {
                        return number.intValue();
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Telegram sendPhotoWithButtons error: " + e.getMessage());
        }

        return null;
    }

    public Integer sendPhotoWithLicenseUpdateButtons(Long chatId,
                                                     String fileId,
                                                     String caption,
                                                     Long pharmacyTelegramId) {
        try {
            String url = apiUrl + "/sendPhoto";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("photo", fileId);
            body.put("caption", caption);
            body.put("parse_mode", "HTML");

            Map<String, Object> approveBtn = Map.of(
                    "text", "✅ Approve",
                    "callback_data", "approve_license_" + pharmacyTelegramId
            );

            Map<String, Object> rejectBtn = Map.of(
                    "text", "❌ Reject",
                    "callback_data", "reject_license_" + pharmacyTelegramId
            );

            body.put("reply_markup", Map.of(
                    "inline_keyboard", List.of(List.of(approveBtn, rejectBtn))
            ));

            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);

            if (response != null && Boolean.TRUE.equals(response.get("ok"))) {
                Object resultObj = response.get("result");

                if (resultObj instanceof Map<?, ?> result) {
                    Object messageIdObj = result.get("message_id");
                    if (messageIdObj instanceof Number number) {
                        return number.intValue();
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("sendPhotoWithLicenseUpdateButtons error: " + e.getMessage());
        }

        return null;
    }

    public void editAdminLicenseSummaryButtonClosed(Long chatId, Integer messageId, Long pharmacyId) {
        editInlineKeyboard(
                chatId,
                messageId,
                List.of(
                        List.of(
                                Map.of("text", "👁 View License", "callback_data", "view_license_" + pharmacyId)
                        )
                )
        );
    }

    public void editAdminLicenseSummaryButtonOpen(Long chatId,
                                                  Integer messageId,
                                                  Long pharmacyId,
                                                  Integer detailMessageId) {
        editInlineKeyboard(
                chatId,
                messageId,
                List.of(
                        List.of(
                                Map.of("text", "❌ Close License",
                                        "callback_data", "close_license_" + detailMessageId + "_" + pharmacyId)
                        )
                )
        );
    }

    public void editAdminRegistrationSummaryButtonClosed(Long chatId, Integer messageId, Long registrationId) {
        editInlineKeyboard(
                chatId,
                messageId,
                List.of(
                        List.of(
                                Map.of("text", "👁 View", "callback_data", "view_reg_" + registrationId)
                        )
                )
        );
    }

    public void editAdminRegistrationSummaryButtonOpen(Long chatId,
                                                       Integer messageId,
                                                       Long registrationId,
                                                       Integer detailMessageId) {
        editInlineKeyboard(
                chatId,
                messageId,
                List.of(
                        List.of(
                                Map.of("text", "❌ Close",
                                        "callback_data", "close_reg_" + detailMessageId + "_" + registrationId)
                        )
                )
        );
    }

    public void sendAdminPendingRegistrationsPage(
            Long chatId,
            org.springframework.data.domain.Page<com.tenahub.bot.entity.PharmacyRegistration> pageData,
            int page) {
        try {
            if (pageData == null || pageData.isEmpty()) {
                sendMessage(chatId, "🆕 No pending registrations.");
                return;
            }

            String header = "🆕 <b>Pending Registrations</b>\n\nPage " + (page + 1) + " of " + pageData.getTotalPages();

            Map<String, Object> headerBody = new HashMap<>();
            headerBody.put("chat_id", chatId);
            headerBody.put("text", header);
            headerBody.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> navKeyboard = new ArrayList<>();
            List<Map<String, Object>> navRow = new ArrayList<>();
            navRow.add(Map.of("text", "🔄 Refresh", "callback_data", "admin_reg_page_" + page));

            if (pageData.hasNext()) {
                navRow.add(Map.of("text", "➡️ View More", "callback_data", "admin_reg_page_" + (page + 1)));
            }

            navKeyboard.add(navRow);
            headerBody.put("reply_markup", Map.of("inline_keyboard", navKeyboard));

            restTemplate.postForObject(apiUrl + "/sendMessage", headerBody, String.class);

            for (com.tenahub.bot.entity.PharmacyRegistration reg : pageData.getContent()) {
                String text = "🆔 <b>ID:</b> " + reg.getId() + "\n"
                        + "🏥 <b>Name:</b> " + safe(reg.getName()) + "\n"
                        + "🏙️ <b>City:</b> " + safe(reg.getCity()) + "\n"
                        + "📍 <b>Area:</b> " + safe(reg.getArea()) + "\n"
                        + "📞 <b>Phone:</b> " + safe(reg.getPhone()) + "\n"
                        + "👤 <b>Telegram ID:</b> " + reg.getTelegramId();

                Map<String, Object> body = new HashMap<>();
                body.put("chat_id", chatId);
                body.put("text", text);
                body.put("parse_mode", "HTML");
                body.put("reply_markup", Map.of(
                        "inline_keyboard",
                        List.of(
                                List.of(
                                        Map.of("text", "👁 View", "callback_data", "view_reg_" + reg.getId()),
                                        Map.of("text", "🗑 Delete", "callback_data", "delete_reg_" + reg.getId())
                                )
                        )
                ));

                restTemplate.postForObject(apiUrl + "/sendMessage", body, String.class);
            }
        } catch (Exception e) {
            System.out.println("sendAdminPendingRegistrationsPage error: " + e.getMessage());
        }
    }

    public void sendAdminPendingLicenseUpdatesPage(
            Long chatId,
            org.springframework.data.domain.Page<com.tenahub.bot.entity.Pharmacy> pageData,
            int page) {
        try {
            if (pageData == null || pageData.isEmpty()) {
                sendMessage(chatId, "📄 No pending license updates.");
                return;
            }

            String header = "📄 <b>Pending License Updates</b>\n\nPage " + (page + 1) + " of " + pageData.getTotalPages();

            Map<String, Object> headerBody = new HashMap<>();
            headerBody.put("chat_id", chatId);
            headerBody.put("text", header);
            headerBody.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> navKeyboard = new ArrayList<>();
            List<Map<String, Object>> navRow = new ArrayList<>();
            navRow.add(Map.of("text", "🔄 Refresh", "callback_data", "admin_license_page_" + page));

            if (pageData.hasNext()) {
                navRow.add(Map.of("text", "➡️ View More", "callback_data", "admin_license_page_" + (page + 1)));
            }

            navKeyboard.add(navRow);
            headerBody.put("reply_markup", Map.of("inline_keyboard", navKeyboard));

            restTemplate.postForObject(apiUrl + "/sendMessage", headerBody, String.class);

            for (com.tenahub.bot.entity.Pharmacy pharmacy : pageData.getContent()) {
                String text = "🆔 <b>Pharmacy ID:</b> " + pharmacy.getId() + "\n"
                        + "🏥 <b>Name:</b> " + safe(pharmacy.getName()) + "\n"
                        + "🏙️ <b>City:</b> " + safe(pharmacy.getCity()) + "\n"
                        + "📍 <b>Area:</b> " + safe(pharmacy.getArea()) + "\n"
                        + "📞 <b>Phone:</b> " + safe(pharmacy.getPhone()) + "\n"
                        + "👤 <b>Telegram ID:</b> " + pharmacy.getTelegramId();

                Map<String, Object> body = new HashMap<>();
                body.put("chat_id", chatId);
                body.put("text", text);
                body.put("parse_mode", "HTML");
                body.put("reply_markup", Map.of(
                        "inline_keyboard",
                        List.of(
                                List.of(Map.of("text", "👁 View License", "callback_data", "view_license_" + pharmacy.getId()))
                        )
                ));

                restTemplate.postForObject(apiUrl + "/sendMessage", body, String.class);
            }
        } catch (Exception e) {
            System.out.println("sendAdminPendingLicenseUpdatesPage error: " + e.getMessage());
        }
    }

    public void sendAdminPendingRegistrations(Long chatId, List<com.tenahub.bot.entity.PharmacyRegistration> registrations) {
        try {
            if (registrations == null || registrations.isEmpty()) {
                sendMessage(chatId, "🆕 No pending registrations.");
                return;
            }

            sendMessage(chatId, "🆕 <b>Pending Registrations</b>");

            for (var reg : registrations) {
                String text = "🆔 <b>ID:</b> " + reg.getId() + "\n"
                        + "🏥 <b>Name:</b> " + safe(reg.getName()) + "\n"
                        + "🏙️ <b>City:</b> " + safe(reg.getCity()) + "\n"
                        + "📍 <b>Area:</b> " + safe(reg.getArea()) + "\n"
                        + "📞 <b>Phone:</b> " + safe(reg.getPhone()) + "\n"
                        + "👤 <b>Telegram ID:</b> " + reg.getTelegramId();

                Map<String, Object> body = new HashMap<>();
                body.put("chat_id", chatId);
                body.put("text", text);
                body.put("parse_mode", "HTML");
                body.put("reply_markup", Map.of(
                        "inline_keyboard",
                        List.of(
                                List.of(
                                        Map.of("text", "👁 View", "callback_data", "view_reg_" + reg.getId())
                                )
                        )
                ));

                restTemplate.postForObject(apiUrl + "/sendMessage", body, String.class);
            }
        } catch (Exception e) {
            System.out.println("sendAdminPendingRegistrations error: " + e.getMessage());
        }
    }

    public void sendAdminPendingLicenseUpdates(Long chatId, List<com.tenahub.bot.entity.Pharmacy> pharmacies) {
        try {
            if (pharmacies == null || pharmacies.isEmpty()) {
                sendMessage(chatId, "📄 No pending license updates.");
                return;
            }

            sendMessage(chatId, "📄 <b>Pending License Updates</b>");

            for (com.tenahub.bot.entity.Pharmacy pharmacy : pharmacies) {
                String text = "🆔 <b>Pharmacy ID:</b> " + pharmacy.getId() + "\n"
                        + "🏥 <b>Name:</b> " + safe(pharmacy.getName()) + "\n"
                        + "🏙️ <b>City:</b> " + safe(pharmacy.getCity()) + "\n"
                        + "📍 <b>Area:</b> " + safe(pharmacy.getArea()) + "\n"
                        + "📞 <b>Phone:</b> " + safe(pharmacy.getPhone()) + "\n"
                        + "👤 <b>Telegram ID:</b> " + pharmacy.getTelegramId();

                Map<String, Object> body = new HashMap<>();
                body.put("chat_id", chatId);
                body.put("text", text);
                body.put("parse_mode", "HTML");
                body.put("reply_markup", Map.of(
                        "inline_keyboard",
                        List.of(
                                List.of(
                                        Map.of("text", "👁 View License", "callback_data", "view_license_" + pharmacy.getId())
                                )
                        )
                ));

                restTemplate.postForObject(apiUrl + "/sendMessage", body, String.class);
            }
        } catch (Exception e) {
            System.out.println("sendAdminPendingLicenseUpdates error: " + e.getMessage());
        }
    }

    public void sendAdminReservationOversight(Long chatId, String text) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(
                            Map.of("text", "⏳ More Pending", "callback_data", "admin_more_pending_res"),
                            Map.of("text", "✅ More Approved", "callback_data", "admin_more_approved_res")
                    ),
                    List.of(
                            Map.of("text", "📦 More Fulfilled", "callback_data", "admin_more_fulfilled_res"),
                            Map.of("text", "❌ More Rejected", "callback_data", "admin_more_rejected_res")
                    ),
                    List.of(
                            Map.of("text", "⌛ More Expired", "callback_data", "admin_more_expired_res")
                    )
            );

            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendAdminReservationOversight error: " + e.getMessage());
        }
    }

    public void sendAdminSystemSummary(Long chatId, String text) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(
                            Map.of("text", "📦 More Reservations", "callback_data", "admin_more_reservations"),
                            Map.of("text", "🏥 More Pharmacies", "callback_data", "admin_more_pharmacies")
                    ),
                    List.of(
                            Map.of("text", "💊 More Medicines", "callback_data", "admin_more_top_medicines"),
                            Map.of("text", "⚠️ More Low Stock", "callback_data", "admin_more_low_stock")
                    )
            );

            body.put("reply_markup", Map.of("inline_keyboard", keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendAdminSystemSummary error: " + e.getMessage());
        }
    }

    public void sendRejectedRegistrationResumeMenu(Long chatId, String reason) {
        try {
            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text",
                    "❌ <b>Your pharmacy registration was rejected.</b>\n\n" +
                    "Reason: " + reason + "\n\n" +
                    "Your previous data was saved.\n" +
                    "Choose an option below:"
            );
            body.put("parse_mode", "HTML");

            List<List<Map<String, Object>>> keyboard = List.of(
                    List.of(Map.of("text", "🔁 Resume Registration")),
                    List.of(Map.of("text", "🆕 Start Fresh")),
                    List.of(Map.of("text", "🏠 Main"))
            );

            body.put("reply_markup", persistentReplyKeyboard(keyboard));
            restTemplate.postForObject(url, body, String.class);
        } catch (Exception e) {
            System.out.println("sendRejectedRegistrationResumeMenu error: " + e.getMessage());
        }
    }
    public void sendSearchFilterButtons(Long chatId, String medicineName, String activeFilter) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                "🔎 <b>Filters for:</b> " + medicineName + "\n" +
                "Active: <b>" + activeFilter + "</b>"
        );
        body.put("parse_mode", "HTML");

        Map<String, Object> nearest = new HashMap<>();
        nearest.put("text", "📍 Nearest");
        nearest.put("callback_data", "filter_nearest");

        Map<String, Object> cheapest = new HashMap<>();
        cheapest.put("text", "💰 Cheapest");
        cheapest.put("callback_data", "filter_cheapest");

        Map<String, Object> rated = new HashMap<>();
        rated.put("text", "⭐ Highest Rated");
        rated.put("callback_data", "filter_rated");

        Map<String, Object> openNow = new HashMap<>();
        openNow.put("text", "🟢 Open Now");
        openNow.put("callback_data", "filter_open");

        Map<String, Object> stockOnly = new HashMap<>();
        stockOnly.put("text", "📦 In Stock Only");
        stockOnly.put("callback_data", "filter_stock");

        Map<String, Object> clear = new HashMap<>();
        clear.put("text", "❌ Clear Filters");
        clear.put("callback_data", "filter_clear");

        Map<String, Object> inlineKeyboard = new HashMap<>();
        inlineKeyboard.put("inline_keyboard", List.of(
                List.of(nearest, cheapest),
                List.of(rated, openNow),
                List.of(stockOnly),
                List.of(clear)
        ));

        body.put("reply_markup", inlineKeyboard);

        restTemplate.postForObject(url, body, String.class);
    } catch (Exception e) {
        System.out.println("sendSearchFilterButtons error: " + e.getMessage());
    }
}
public void sendSearchFilterKeyboard(Long chatId, String activeFilter) {
    try {
        String url = apiUrl + "/sendMessage";

        String nearestText = "Nearest";
        String cheapestText = "Cheapest";
        String highestRatedText = "Highest Rated";
        String openNowText = "Open Now";
        String inStockText = "In Stock Only";

        if (activeFilter != null) {
            String normalized = activeFilter.trim().toLowerCase();

            if (normalized.equals("nearest")) {
                nearestText = "✅ Nearest";
            } else if (normalized.equals("cheapest")) {
                cheapestText = "✅ Cheapest";
            } else if (normalized.equals("highest rated")) {
                highestRatedText = "✅ Highest Rated";
            } else if (normalized.equals("open now")) {
                openNowText = "✅ Open Now";
            } else if (normalized.equals("in stock only")) {
                inStockText = "✅ In Stock Only";
            }
        }

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                "🔎 <b>Search Filters</b>\n\n" +
                "Active: <b>" + (activeFilter == null ? "Nearest" : activeFilter) + "</b>\n" +
                "Choose a filter below."
        );
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> keyboard = List.of(
                List.of(
                        Map.of("text", "📍 " + nearestText),
                        Map.of("text", "💰 " + cheapestText)
                ),
                List.of(
                        Map.of("text", "⭐ " + highestRatedText),
                        Map.of("text", "🟢 " + openNowText)
                ),
                List.of(
                        Map.of("text", "📦 " + inStockText),
                        Map.of("text", "❌ Clear Filters")
                ),
                List.of(
                        Map.of("text", "🔙 Back"),
                        Map.of("text", "🏠 Home")
                )
        );

        body.put("reply_markup", persistentReplyKeyboard(keyboard));
        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendSearchFilterKeyboard error: " + e.getMessage());
    }
}

public void sendAlternativeMedicineSuggestions(Long chatId, String searchedMedicine, List<String> alternatives) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put(
                "text",
                "❌ <b>No exact match found for:</b> " + searchedMedicine + "\n\n" +
                "💡 <b>Did you mean one of these?</b>"
        );
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        for (String alt : alternatives) {
            inlineKeyboard.add(List.of(
                    Map.of(
                            "text", "💊 " + alt,
                            "callback_data", "alt_med_" + alt.toLowerCase()
                    )
            ));
        }

        inlineKeyboard.add(List.of(
                Map.of("text", "❌ Cancel", "callback_data", "alt_med_cancel")
        ));

        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendAlternativeMedicineSuggestions error: " + e.getMessage());
    }
}
public void sendFavoritePharmacyCard(Long chatId, com.tenahub.bot.entity.Pharmacy pharmacy) {
    try {
        String url = apiUrl + "/sendMessage";

        String phoneText = (pharmacy.getPhone() == null || pharmacy.getPhone().isBlank())
                ? "N/A"
                : pharmacy.getPhone().trim();

        Double ratingValue = pharmacy.getRating();
        String ratingText = String.format("%.1f", ratingValue == null ? 0.0 : ratingValue);

        Double lat = pharmacy.getLatitude();
        Double lon = pharmacy.getLongitude();

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                "❤️ <b>" + safe(pharmacy.getName()) + "</b>\n" +
                "📍 " + safe(pharmacy.getArea()) + ", " + safe(pharmacy.getCity()) + "\n" +
                "📞 " + phoneText + "\n" +
                "⭐ Rating: " + ratingText + "/5"
        );
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();
        List<Map<String, Object>> row1 = new ArrayList<>();

        if (lat != null && lon != null) {
            String navigateUrl = "https://www.google.com/maps/search/?api=1&query=" + lat + "," + lon;
            row1.add(Map.of("text", "🧭 Navigate", "url", navigateUrl));
        }

        row1.add(Map.of("text", "🗑 Remove", "callback_data", "fav_remove_" + pharmacy.getId()));
        inlineKeyboard.add(row1);

        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendFavoritePharmacyCard error: " + e.getMessage());
    }
}
public void sendRecentSearches(Long chatId, List<String> searches) {
    try {
        String url = apiUrl + "/sendMessage";

        if (searches == null || searches.isEmpty()) {
            sendMessage(chatId, "🕘 <b>Recent Searches</b>\n\nNo recent searches found.");
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", "🕘 <b>Recent Searches</b>\n\nTap one to search again:");
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        for (String medicine : searches) {
            inlineKeyboard.add(List.of(
                    Map.of("text", "💊 " + medicine, "callback_data", "recent_search_" + medicine.toLowerCase())
            ));
        }

        inlineKeyboard.add(List.of(
                Map.of("text", "🏠 Home", "callback_data", "recent_search_home")
        ));

        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);
    } catch (Exception e) {
        System.out.println("sendRecentSearches error: " + e.getMessage());
    }
}
public void sendNoResultWithAlertOption(Long chatId, String medicineName) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                "❌ <b>No pharmacies found for:</b> " + medicineName + "\n\n" +
                "You can create an alert and get notified when it becomes available."
        );
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> inlineKeyboard = List.of(
                List.of(Map.of("text", "🔔 Notify me when available", "callback_data", "alert_create_" + medicineName.toLowerCase())),
                List.of(Map.of("text", "🏠 Home", "callback_data", "alert_home"))
        );

        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);
    } catch (Exception e) {
        System.out.println("sendNoResultWithAlertOption error: " + e.getMessage());
    }
}
public void sendMyAlerts(Long chatId, List<com.tenahub.bot.entity.MedicineAvailabilityAlert> alerts) {
    try {
        if (alerts == null || alerts.isEmpty()) {
            sendMessage(chatId, "🔔 <b>My Alerts</b>\n\nNo active alerts found.", "HTML");
            return;
        }

        sendMessage(
                chatId,
                "🔔 <b>My Alerts</b>\n\nManage your active alerts below.",
                "HTML"
        );

        java.time.format.DateTimeFormatter formatter =
                java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a");

        for (com.tenahub.bot.entity.MedicineAvailabilityAlert alert : alerts) {
            String locationText = (alert.getLatitude() != null && alert.getLongitude() != null)
                    ? "Saved nearby location"
                    : "Any nearby pharmacy";

            String createdText = alert.getCreatedAt() == null
                    ? "N/A"
                    : alert.getCreatedAt().format(formatter);

            String text =
                    "🔔 <b>Alert</b>\n\n" +
                    "💊 <b>Medicine:</b> " + alert.getMedicineName() + "\n" +
                    "📍 <b>Location:</b> " + locationText + "\n" +
                    "🕒 <b>Created:</b> " + createdText;

            String url = apiUrl + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "HTML");
            body.put("reply_markup", Map.of(
                    "inline_keyboard",
                    List.of(
                            List.of(
                                    Map.of("text", "🔎 Search Now", "callback_data", "alert_search_" + alert.getMedicineName().toLowerCase()),
                                    Map.of("text", "❌ Remove Alert", "callback_data", "alert_remove_" + alert.getId())
                            )
                    )
            ));

            restTemplate.postForObject(url, body, String.class);
        }

        Map<String, Object> footerBody = new HashMap<>();
        footerBody.put("chat_id", chatId);
        footerBody.put("text", "⚙️ Alert actions");
        footerBody.put("reply_markup", Map.of(
                "inline_keyboard",
                List.of(
                        List.of(Map.of("text", "🗑 Remove All Alerts", "callback_data", "alert_remove_all")),
                        List.of(Map.of("text", "🏠 Home", "callback_data", "alert_home"))
                )
        ));

        restTemplate.postForObject(apiUrl + "/sendMessage", footerBody, String.class);

    } catch (Exception e) {
        System.out.println("sendMyAlerts error: " + e.getMessage());
    }
}

public void sendAlternativeMedicineSuggestionsWithNotify(Long chatId, String searchedMedicine, List<String> alternatives) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                "❌ <b>No exact match found for:</b> " + searchedMedicine + "\n\n" +
                "💡 <b>Did you mean one of these?</b>"
        );
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();

        for (String alt : alternatives) {
            inlineKeyboard.add(List.of(
                    Map.of("text", "💊 " + alt, "callback_data", "alt_med_" + alt.toLowerCase())
            ));
        }

        inlineKeyboard.add(List.of(
                Map.of("text", "🔔 Notify Me for " + searchedMedicine,
                        "callback_data", "alert_create_" + searchedMedicine.toLowerCase())
        ));

        inlineKeyboard.add(List.of(
                Map.of("text", "❌ Cancel", "callback_data", "alt_med_cancel")
        ));

        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendAlternativeMedicineSuggestionsWithNotify error: " + e.getMessage());
    }
}
public void sendNoMedicineFoundWithNotify(Long chatId, String medicineName) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                "❌ <b>No pharmacies found for:</b> " + medicineName + "\n\n" +
                "You can create an alert and get notified when it becomes available."
        );
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> inlineKeyboard = List.of(
                List.of(
                        Map.of("text", "🔔 Notify me when available", "callback_data", "alert_create_" + medicineName.toLowerCase())
                ),
                List.of(
                        Map.of("text", "🏠 Home", "callback_data", "alert_home")
                )
        );

        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));
        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendNoMedicineFoundWithNotify error: " + e.getMessage());
    }
}

public void sendAllResultsOutOfStockNotice(Long chatId, String medicineName) {
    try {
        String url = apiUrl + "/sendMessage";

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text",
                "⚠️ <b>" + medicineName + "</b> was found, but nearby pharmacies are currently out of stock.\n\n" +
                "🔔 You can create an alert and get notified when stock is updated."
        );
        body.put("parse_mode", "HTML");

        List<List<Map<String, Object>>> inlineKeyboard = List.of(
                List.of(
                        Map.of("text", "🔔 Notify Me", "callback_data", "alert_create_" + medicineName.toLowerCase())
                ),
                List.of(
                        Map.of("text", "🏠 Home", "callback_data", "alert_home")
                )
        );

        body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

        restTemplate.postForObject(url, body, String.class);

    } catch (Exception e) {
        System.out.println("sendAllResultsOutOfStockNotice error: " + e.getMessage());
    }
}
}