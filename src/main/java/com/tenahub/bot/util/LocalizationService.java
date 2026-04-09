package com.tenahub.bot.util;

import com.tenahub.bot.entity.BotTranslation;
import com.tenahub.bot.repository.BotTranslationRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LocalizationService {

        private static final String AM_HOME = "🏠 መነሻ";

    private final BotTranslationRepository translationRepository;

    /** Static fallback maps — used as seed data and as fallback when DB key is missing. */
    private static final Map<BotLanguage, Map<String, String>> STATIC_TEXTS = Map.of(
            BotLanguage.ENGLISH, englishTexts(),
            BotLanguage.AMHARIC, amharicTexts()
    );

    /** Runtime cache loaded from DB at startup; refreshable without restart. */
    private final Map<BotLanguage, Map<String, String>> cache = new HashMap<>();
        private final Map<Long, BotLanguage> languageOverrides = new ConcurrentHashMap<>();

    private static final Map<String, String> COMMANDS = buildCommandMap();

    @PostConstruct
    public void initTranslations() {
        for (BotLanguage lang : BotLanguage.values()) {
            List<BotTranslation> existing = translationRepository.findByLanguageCode(lang.getCode());
            Map<String, String> defaults = STATIC_TEXTS.getOrDefault(lang, STATIC_TEXTS.get(BotLanguage.ENGLISH));

            Map<String, String> existingByKey = existing.stream()
                    .collect(Collectors.toMap(BotTranslation::getTranslationKey, BotTranslation::getValue, (a, b) -> a));

            List<BotTranslation> missingRows = defaults.entrySet().stream()
                    .filter(e -> !existingByKey.containsKey(e.getKey()))
                    .map(e -> BotTranslation.builder()
                            .languageCode(lang.getCode())
                            .translationKey(e.getKey())
                            .value(e.getValue())
                            .build())
                    .collect(Collectors.toList());

            if (!missingRows.isEmpty()) {
                translationRepository.saveAll(missingRows);
            }
        }
        refreshCache();
    }

    public void refreshCache() {
        for (BotLanguage lang : BotLanguage.values()) {
            List<BotTranslation> rows = translationRepository.findByLanguageCode(lang.getCode());
            Map<String, String> map = rows.stream()
                    .collect(Collectors.toMap(BotTranslation::getTranslationKey, BotTranslation::getValue, (a, b) -> a));
            cache.put(lang, map);
        }
    }

    public BotLanguage getLanguage(Long chatId) {
                return languageOverrides.getOrDefault(chatId, BotLanguage.ENGLISH);
    }

    public void setLanguage(Long chatId, BotLanguage language) {
                if (chatId == null) {
                        return;
                }
                languageOverrides.put(chatId, language == null ? BotLanguage.ENGLISH : language);
    }

    public String text(Long chatId, String key, Object... args) {
        return text(getLanguage(chatId), key, args);
    }

    public String text(BotLanguage language, String key, Object... args) {
                Map<String, String> localized = new HashMap<>(
                                STATIC_TEXTS.getOrDefault(language, STATIC_TEXTS.get(BotLanguage.ENGLISH))
                );

                if (!cache.isEmpty()) {
                        Map<String, String> cached = cache.getOrDefault(language, cache.get(BotLanguage.ENGLISH));
                        if (cached != null) {
                                localized.putAll(cached);
                        }
                }

                String template = localized.get(key);
        if (template == null) {
                        Map<String, String> englishMap = new HashMap<>(STATIC_TEXTS.get(BotLanguage.ENGLISH));
                        if (!cache.isEmpty() && cache.get(BotLanguage.ENGLISH) != null) {
                                englishMap.putAll(cache.get(BotLanguage.ENGLISH));
                        }
                        template = englishMap.getOrDefault(key, key);
        }
        return args == null || args.length == 0 ? template : String.format(template, args);
    }

    public String resolveCommand(String text) {
        String normalized = normalizeLookup(text);
        if (normalized.isBlank()) {
            return "";
        }

        return COMMANDS.getOrDefault(normalized, basicNormalize(text));
    }

    public boolean matchesCommand(String text, String commandKey) {
        return commandKey.equals(resolveCommand(text));
    }

    private static Map<String, String> buildCommandMap() {
        Map<String, String> commands = new HashMap<>();

        register(commands, "search medicines",
                "search medicines", "search medicine", "🔎 search medicines", "🔎 search medicine",
                "መድሃኒት ፈልግ", "🔎 መድሃኒት ፈልግ");
        register(commands, "search multiple meds",
                "search multiple meds", "🔎🛒 search multiple meds",
                "ብዙ መድሃኒቶች ፈልግ", "🔎🛒 ብዙ መድሃኒቶች ፈልግ");
        register(commands, "my reservations",
                "my reservations", "📦 my reservations",
                "የእኔ ቦታ ማስያዣዎች", "📦 የእኔ ቦታ ማስያዣዎች");
        register(commands, "recent searches",
                "recent searches", "🕘 recent searches",
                "የቅርብ ፍለጋዎች", "🕘 የቅርብ ፍለጋዎች");
        register(commands, "reserve again",
                "reserve again", "🔁 reserve again",
                "እንደገና አስይዝ", "🔁 እንደገና አስይዝ");
        register(commands, "account",
                "account", "👤 account",
                "መለያ", "👤 መለያ");
        register(commands, "my alerts",
                "my alerts", "🔔 my alerts",
                "ማስጠንቀቂያዎቼ", "🔔 ማስጠንቀቂያዎቼ");
        register(commands, "share location",
                "share location", "📍 share location",
                "አካባቢ አጋራ", "📍 አካባቢ አጋራ");
        register(commands, "register pharmacy",
                "register pharmacy", "🏥 register pharmacy",
                "ፋርማሲ መዝግብ", "🏥 ፋርማሲ መዝግብ");
        register(commands, "how to use",
                "how to use", "❓ how to use",
                "እንዴት እጠቀም", "❓ እንዴት እጠቀም");
        register(commands, "information",
                "information", "📖 information",
                "መረጃ", "📖 መረጃ");
        register(commands, "leave feedback",
                "leave feedback", "📝 leave feedback",
                "አስተያየት ስጥ", "📝 አስተያየት ስጥ");
        register(commands, "language",
                "language", "🌐 language",
                "ቋንቋ", "🌐 ቋንቋ");
        register(commands, "home",
                "home", "🏠 home", "/home",
                "መነሻ", AM_HOME);
        register(commands, "main",
                "main", "🏠 main",
                "ዋና", "🏠 ዋና", AM_HOME);
        register(commands, "back",
                "back", "⬅️ back", "🔙 back",
                "ተመለስ", "⬅️ ተመለስ", "🔙 ተመለስ");
        register(commands, "cancel",
                "cancel", "❌ cancel", "/cancel",
                "ሰርዝ", "❌ ሰርዝ");
        register(commands, "refresh",
                "refresh", "🔄 refresh",
                "አድስ", "🔄 አድስ");
        register(commands, "pending",
                "pending", "⏳ pending",
                "በመጠባበቅ ላይ", "⏳ በመጠባበቅ ላይ");
        register(commands, "fulfilled",
                "fulfilled", "📦 fulfilled",
                "የተጠናቀቀ", "📦 የተጠናቀቀ");
        register(commands, "expired",
                "expired", "⌛ expired",
                "ያለፈበት", "⌛ ያለፈበት");
        register(commands, "cancelled",
                "cancelled", "❌ cancelled",
                "የተሰረዘ", "❌ የተሰረዘ");
        register(commands, "favorite pharmacies",
                "favorite pharmacies", "❤️ favorite pharmacies",
                "ተወዳጅ ፋርማሲዎች", "❤️ ተወዳጅ ፋርማሲዎች");
        register(commands, "use saved location",
                "use saved location", "📍 use saved location",
                "የተቀመጠ አካባቢ ተጠቀም", "📍 የተቀመጠ አካባቢ ተጠቀም");
        register(commands, "share exact location",
                "share exact location", "📍 share exact location",
                "ትክክለኛ አካባቢ አጋራ", "📍 ትክክለኛ አካባቢ አጋራ");
        register(commands, "share current location",
                "share current location", "📌 share current location",
                "የአሁኑን አካባቢ አጋራ", "📌 የአሁኑን አካባቢ አጋራ");
        register(commands, "select ethiopia region",
                "select ethiopia region", "🗺 select ethiopia region",
                "የኢትዮጵያ ክልል ምረጥ", "🗺 የኢትዮጵያ ክልል ምረጥ");
        register(commands, "change location",
                "change location", "📍 change location",
                "አካባቢ ቀይር", "📍 አካባቢ ቀይር");
        register(commands, "add more",
                "add more", "➕ add more",
                "ተጨማሪ ጨምር", "➕ ተጨማሪ ጨምር");
        register(commands, "clear",
                "clear", "🗑 clear",
                "አጽዳ", "🗑 አጽዳ");
        register(commands, "search pharmacies",
                "search pharmacies", "🔍 search pharmacies",
                "ፋርማሲዎችን ፈልግ", "🔍 ፋርማሲዎችን ፈልግ");
        register(commands, "share exact pharmacy location",
                "share exact pharmacy location", "📍 share exact pharmacy location",
                "ትክክለኛ የፋርማሲ አካባቢ አጋራ", "📍 ትክክለኛ የፋርማሲ አካባቢ አጋራ");
        register(commands, "paste google maps link",
                "paste google maps link", "🔗 paste google maps link",
                "የ google maps ሊንክ ለጥፍ", "🔗 የ google maps ሊንክ ለጥፍ");
        register(commands, "nearest",
                "nearest", "📍 nearest", "📍 ✅ nearest",
                "ቅርብ", "📍 ቅርብ", "📍 ✅ ቅርብ");
        register(commands, "cheapest",
                "cheapest", "💰 cheapest", "💰 ✅ cheapest",
                "ዝቅተኛ ዋጋ", "💰 ዝቅተኛ ዋጋ", "💰 ✅ ዝቅተኛ ዋጋ");
        register(commands, "highest rated",
                "highest rated", "⭐ highest rated", "⭐ ✅ highest rated",
                "ከፍተኛ ደረጃ", "⭐ ከፍተኛ ደረጃ", "⭐ ✅ ከፍተኛ ደረጃ");
        register(commands, "open now",
                "open now", "🟢 open now", "🟢 ✅ open now",
                "አሁን ክፍት", "🟢 አሁን ክፍት", "🟢 ✅ አሁን ክፍት");
        register(commands, "in stock only",
                "in stock only", "📦 in stock only", "📦 ✅ in stock only",
                "በስቶክ ያሉ ብቻ", "📦 በስቶክ ያሉ ብቻ", "📦 ✅ በስቶክ ያሉ ብቻ");
        register(commands, "clear filters",
                "clear filters", "❌ clear filters",
                "ማጣሪያዎችን አጥፋ", "❌ ማጣሪያዎችን አጥፋ");
        register(commands, "pick date",
                "pick date", "📅 pick date",
                "ቀን ምረጥ", "📅 ቀን ምረጥ");

        return commands;
    }

    private static void register(Map<String, String> commands, String canonical, String... aliases) {
        for (String alias : aliases) {
            commands.put(normalizeLookup(alias), canonical);
        }
    }

    private static String normalizeLookup(String text) {
        if (text == null) {
            return "";
        }

        return text.toLowerCase(Locale.ROOT)
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String basicNormalize(String text) {
        return normalizeLookup(text)
                .replaceAll("[^\\p{L}\\p{N} ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static Map<String, String> englishTexts() {
        return Map.ofEntries(
                Map.entry("start_welcome", "👋 <b>Welcome to TenaHub</b>\n\n⚠️ If this is your first time, please share your location using the 📎 attachment button once.\n\nAfter that you can use the Share Location button below.\n\n🏥 Pharmacy owners can register below."),
                Map.entry("share_location_button", "📍 Share Location"),
                Map.entry("register_pharmacy_button", "🏥 Register Pharmacy"),
                Map.entry("dashboard_welcome", "👋 <b>Welcome to TenaHub</b>\n\nFind nearby pharmacies and reserve medicines easily.\n\nChoose an option below:"),
                Map.entry("search_medicines_button", "🔎 Search Medicines"),
                Map.entry("search_multiple_meds_button", "🔎🛒 Search Multiple Meds"),
                Map.entry("my_reservations_button", "📦 My Reservations"),
                Map.entry("recent_searches_button", "🕘 Recent Searches"),
                Map.entry("reserve_again_button", "🔁 Reserve Again"),
                Map.entry("account_button", "👤 Account"),
                Map.entry("my_alerts_button", "🔔 My Alerts"),
                Map.entry("how_to_use_button", "❓ How to Use"),
                Map.entry("information_button", "📖 Information"),
                Map.entry("leave_feedback_button", "📝 Leave Feedback"),
                Map.entry("language_button", "🌐 Language"),
                Map.entry("pending_home_text", "⏳ <b>Your pharmacy registration is under review</b>\n\nPlease wait for admin approval."),
                Map.entry("refresh_button", "🔄 Refresh"),
                Map.entry("main_button", "🏠 Main"),
                Map.entry("home_button", "🏠 Home"),
                Map.entry("back_button", "⬅️ Back"),
                Map.entry("cancel_button", "❌ Cancel"),
                Map.entry("location_choice_title", "📍 <b>Pharmacy Location</b>\n\nStep 6/7\nChoose how you want to set the pharmacy location.\n\n1. Share exact pharmacy location\n2. Paste Google Maps link / coordinates\n3. Select region → city/sub-city → area"),
                Map.entry("share_exact_pharmacy_location_button", "📍 Share Exact Pharmacy Location"),
                Map.entry("paste_google_maps_link_button", "🔗 Paste Google Maps Link"),
                Map.entry("select_ethiopia_region_button", "🗺 Select Ethiopia Region"),
                Map.entry("account_actions_title", "⚙️ <b>Account Actions</b>"),
                Map.entry("favorite_pharmacies_button", "❤️ Favorite Pharmacies"),
                Map.entry("recent_searches_empty", "🕘 <b>Recent Searches</b>\n\nNo recent searches found."),
                Map.entry("recent_searches_title", "🕘 <b>Recent Searches</b>\n\nTop 5 recent medicines. Tap to search or reserve again:"),
                Map.entry("recent_search_item", "💊 %s"),
                Map.entry("recent_search_home_button", "🏠 Home"),
                Map.entry("reserve_again_empty", "🔁 <b>Reserve Again</b>\n\nNo reservation history found yet."),
                Map.entry("reserve_again_title", "🔁 <b>Reserve Again</b>\n\nOne-tap search from your reservation history:"),
                Map.entry("reserve_again_item", "🔁 %s"),
                Map.entry("my_reservations_menu_title", "📦 <b>My Reservations</b>\n\nChoose a section from bottom buttons:"),
                Map.entry("pending_button", "⏳ Pending"),
                Map.entry("fulfilled_button", "📦 Fulfilled"),
                Map.entry("expired_button", "⌛ Expired"),
                Map.entry("cancelled_button", "❌ Cancelled"),
                Map.entry("language_menu_text", "🌐 <b>Choose Language</b>\n\nCurrent language: <b>%s</b>"),
                Map.entry("language_name_english", "English"),
                Map.entry("language_name_amharic", "Amharic"),
                Map.entry("language_changed", "🌐 Language changed to <b>%s</b>."),
                Map.entry("how_to_use_text", "❓ <b>How to Use TenaHub</b>\n\n1. Share your location\n2. Search for a medicine\n3. View nearby pharmacies\n4. Tap Reserve if available\n5. Enter quantity, name, and phone\n6. Wait for pharmacy approval\n7. Pick up before the hold time expires"),
                Map.entry("information_text", "📖 <b>About TenaHub</b>\n\nTenaHub helps users find nearby pharmacies, check medicine availability, and reserve medicines before visiting.\n\nPharmacy owners can manage inventory, reservations, and profile information through the bot."),
                Map.entry("information_menu_title", "📖 <b>Information</b>\n\nChoose an option:"),
                Map.entry("about_tenahub_button", "ℹ️ About TenaHub"),
                Map.entry("contacts_button", "📞 Contacts"),
                Map.entry("contacts_text_title", "📞 <b>Contacts</b>"),
                Map.entry("contacts_website_label", "🌐 <b>Website:</b> %s"),
                Map.entry("contacts_phone_label", "📞 <b>Support Phone:</b> %s"),
                Map.entry("contacts_email_label", "📧 <b>Support Email:</b> %s"),
                Map.entry("contacts_telegram_label", "💬 <b>Telegram Support:</b> %s"),
                Map.entry("contacts_partnership_label", "🤝 <b>Partnership:</b> %s"),
                Map.entry("feedback_prompt", "📝 Please type your feedback.\n\nWe will use it to improve TenaHub."),
                Map.entry("feedback_received", "✅ Thank you for your feedback! We appreciate it."),
                Map.entry("search_medicine_prompt", "🔎 Send medicine name to search."),
                Map.entry("invalid_medicine_selection", "⚠️ Invalid medicine selection."),
                Map.entry("share_location_first", "⚠️ Please share your location first."),
                Map.entry("pharmacies_with_medicine", "💊 <b>Pharmacies with %s</b>\n\nSorted by: <b>%s</b>"),
                Map.entry("nearest_filter_label", "Nearest"),
                Map.entry("cheapest_filter_label", "Cheapest"),
                Map.entry("highest_rated_filter_label", "Highest Rated"),
                Map.entry("open_now_filter_label", "Open Now"),
                Map.entry("in_stock_only_filter_label", "In Stock Only"),
                Map.entry("clear_filters_button", "❌ Clear Filters"),
                Map.entry("search_filters_title", "🔎 <b>Search Filters</b>\n\nActive: <b>%s</b>\nChoose a filter below."),
                Map.entry("multi_medicine_mode_text", "🧺 Multi-medicine mode is active."),
                Map.entry("multi_search_title", "🔎🛒 Multi-Medicine Search\n\nChoose location option first."),
                Map.entry("multi_search_intro", "🔎🛒 <b>Multi-Medicine Search</b>\n\nFind one pharmacy matching several medicines.\n\nChoose location option first."),
                Map.entry("multi_share_exact_location_prompt", "📍 <b>Share Current Location</b>\n\nTap the button below to send your exact location for multi-medicine search."),
                Map.entry("use_saved_location_button", "📍 Use Saved Location"),
                Map.entry("share_current_location_button", "📌 Share Current Location"),
                Map.entry("change_location_button", "📍 Change Location"),
                Map.entry("change_location_title", "📍 <b>Change Location</b>\n\nChoose how to update your search location."),
                Map.entry("share_exact_location_button", "📍 Share Exact Location"),
                Map.entry("search_pharmacies_button", "🔍 Search Pharmacies"),
                Map.entry("add_more_button", "➕ Add More"),
                Map.entry("clear_button", "🗑 Clear"),
                Map.entry("no_reservations_found", "📦 <b>My Reservations</b>\n\nNo reservations found."),
                Map.entry("pending_section_title", "📦 <b>My Reservations</b>\n\n⏳ Pending"),
                Map.entry("fulfilled_section_title", "📦 <b>My Reservations</b>\n\n📦 Fulfilled"),
                Map.entry("expired_section_title", "📦 <b>My Reservations</b>\n\n⌛ Expired"),
                Map.entry("cancelled_section_title", "📦 <b>My Reservations</b>\n\n❌ Cancelled"),
                Map.entry("unknown_reservation_section", "⚠️ Unknown reservation section."),
                Map.entry("account_overview_title", "👤 <b>Account Overview</b>"),
                Map.entry("telegram_id_label", "🆔 <b>Telegram ID:</b> %s"),
                Map.entry("saved_location_label", "📍 <b>Saved Location:</b> %s"),
                Map.entry("saved_location_missing", "Not saved"),
                Map.entry("saved_location_ok", "Saved ✅"),
                Map.entry("reservations_summary_title", "📦 <b>Reservations</b>"),
                Map.entry("reservation_count_line", "• %s: %d"),
                Map.entry("approved_status", "Approved"),
                Map.entry("pharmacy_status_label", "🏥 <b>Pharmacy Status:</b> %s"),
                Map.entry("registered_status", "Registered ✅"),
                Map.entry("not_registered_status", "Not Registered"),
                Map.entry("register_hint", "💡 You can register your pharmacy from the menu."),
                Map.entry("recent_reservations_title", "🕘 <b>Recent Reservations</b>"),
                Map.entry("no_recent_reservations", "• No reservations yet"),
                Map.entry("unknown_pharmacy", "Unknown Pharmacy"),
                Map.entry("quick_actions_hint", "Use the buttons below for quick actions."),
                Map.entry("alert_location_saved", "Saved nearby location"),
                Map.entry("alert_location_any", "Any nearby pharmacy"),
                Map.entry("alert_created", "🔔 <b>Alert created</b>\n\n💊 Medicine: %s\n📍 Location: %s\n\nYou will be notified when it becomes available."),
                Map.entry("alert_removed", "❌ Alert removed."),
                Map.entry("alert_all_removed", "🗑 All alerts removed."),
                Map.entry("fav_added", "❤️ Pharmacy saved to favorites."),
                Map.entry("fav_removed", "✅ Removed from favorites."),
                Map.entry("no_favorites", "❤️ No favorite pharmacies yet."),
                Map.entry("your_favorites_title", "❤️ <b>Your Favorite Pharmacies</b>"),
                Map.entry("session_expired", "⚠️ Session expired."),
                Map.entry("select_at_least_one_medicine", "⚠️ Select at least one medicine."),
                Map.entry("no_pharmacies_for_medicines", "❌ No pharmacies found for the selected medicines."),
                Map.entry("multi_results_title", "🏥 <b>Multi-Medicine Search Results</b>\n\nShowing pharmacies with the best match first."),
                Map.entry("send_first_medicine", "💊 Send the first medicine name.\n\nExample:\ninsulin"),
                Map.entry("share_location_then_medicine", "📍 Share your location, then send medicine name."),
                Map.entry("send_another_medicine", "💊 Send another medicine name."),
                Map.entry("reserve_all_later_text", "🧺 <b>Reserve All Later</b>\n\nMulti-medicine reservation in one request is not available yet.\n\nFor now, please use <b>Reserve Matched</b> and reserve one matched medicine at a time."),
                Map.entry("no_matched_medicines", "⚠️ No matched medicines available for reservation."),
                Map.entry("type_medicine_add", "✍️ Type the medicine name you want to add."),
                Map.entry("medicine_selection_cancelled", "❌ Medicine selection cancelled."),
                Map.entry("please_select_at_least_one", "⚠️ Please select at least one medicine."),
                Map.entry("medicines_updated", "✅ Medicines updated"),
                Map.entry("medicine_name_too_short", "⚠️ Medicine name is too short."),
                Map.entry("max_medicines_reached", "⚠️ Maximum 5 medicines allowed."),
                Map.entry("medicine_added", "✅ Added: %s\n\nCurrent medicines:\n%s"),
                Map.entry("medicines_cleared_prompt", "🗑 Selected medicines cleared.\n\nNow send the first medicine name."),
                Map.entry("add_at_least_one_medicine", "⚠️ Add at least one medicine first."),
                Map.entry("reservation_cancelled_msg", "❌ Reservation cancelled."),
                Map.entry("reservation_pick_cancelled", "❌ Reservation selection cancelled."),
                Map.entry("enter_quantity_prompt", "✍️ Enter quantity as a number.\n\nExample: 4"),
                Map.entry("enter_full_name_prompt", "👤 Please enter your full name.\n\nExample:\nTeketsel Beyene"),
                Map.entry("reservation_submitted", "✅ <b>Reservation submitted!</b>\n\n💊 Medicine: %s\n🔢 Quantity: %d\n👤 Name: %s\n📱 Phone: %s\n🕒 Waiting for pharmacy approval."),
                Map.entry("reservation_missing_details", "⚠️ Missing reservation details. Please start again."),
                Map.entry("reservation_cancel_success", "✅ Reservation cancelled.\n\n🆔 ID: %d\n💊 Medicine: %s\n🔢 Quantity: %d"),
                Map.entry("reservation_approved_user", "✅ Your reservation was approved.\n\n💊 Medicine: %s\n🔢 Quantity: %d\n⏳ Hold until: %s\n\nPlease arrive before the deadline."),
                Map.entry("reservation_rejected_user", "❌ Your reservation was rejected.\n\n💊 Medicine: %s\n🔢 Quantity: %d"),
                Map.entry("reservation_fulfilled_user", "📦 Your reservation has been fulfilled.\n\n💊 Medicine: %s\n🔢 Quantity: %d"),
                Map.entry("reservation_contact_sent", "✅ Reservation request sent to pharmacy.\n\n💊 Medicine: %s\n🔢 Quantity: %d\n👤 Name: %s\n📱 Phone: %s\n🕒 Waiting for pharmacy approval."),
                Map.entry("reservation_contact_saved", "✅ Reservation saved.\n\n💊 Medicine: %s\n🔢 Quantity: %d\n👤 Name: %s\n📱 Phone: %s\n\n⚠️ Could not notify the pharmacy automatically."),
                Map.entry("reservation_contact_failed", "⚠️ Could not create reservation.\n\n%s"),
                Map.entry("rating_thanks", "⭐ Thanks for rating %d/5"),
                Map.entry("location_received", "📍 Location received.\n\nNow send medicine name."),
                Map.entry("exact_location_saved_multi", "✅ Exact location saved for multi-medicine search.\n\nNow send the first medicine name."),
                Map.entry("please_share_exact_location", "📍 Please share your exact location."),
                Map.entry("exact_location_not_ethiopia", "✅ Exact location received.\n\n⚠️ This point does not match a nearby Ethiopia pharmacy area.\n\nUse one of these instead:\n• 🔗 Paste Google Maps Link\n• 🗺 Select Ethiopia Region"),
                Map.entry("reg_name_prompt", "🏥 <b>Pharmacy Registration</b>\n\nStep 1/7\nPlease enter your pharmacy name."),
                Map.entry("reg_region_prompt", "🏥 <b>Pharmacy Registration</b>\n\nStep 2/7\n🗺 Select your region in Ethiopia."),
                Map.entry("reg_city_prompt", "🏥 <b>Pharmacy Registration</b>\n\nStep 2/7\n📍 Enter your city"),
                Map.entry("reg_area_prompt", "🏥 <b>Pharmacy Registration</b>\n\nStep 3/7\n📌 Enter your area"),
                Map.entry("reg_phone_prompt", "🏥 <b>Pharmacy Registration</b>\n\nStep 2/7\n📞 Enter phone number\nExample: 0912345678\n\nOr tap <b>Share Phone Number</b> below."),
                Map.entry("reg_google_map_help_text", "🔗 <b>Paste Google Maps Link</b>\n\nPaste a valid Google Maps link or coordinates.\n\nExample:\nhttps://maps.google.com/?q=8.9806,38.7578\n\nor\n8.9806,38.7578"),
                Map.entry("reg_exact_location_help_text", "📍 <b>Share Exact Pharmacy Location</b>\n\nTap below to send the exact pharmacy location."),
                Map.entry("reg_license_step", "📄 Now upload your pharmacy license (photo, PDF, DOC, or other document)."),
                Map.entry("reg_license_expiry_step", "📅 Enter your license expiry date in <b>YYYY-MM-DD</b> format.\nExample: 2027-12-31\n\nOr tap <b>📅 Pick Date</b> from the bottom keyboard."),
                Map.entry("expiry_picker_reg_title", "📅 <b>Set License Expiry Date</b>"),
                Map.entry("expiry_picker_update_title", "📅 <b>Update License Expiry Date</b>"),
                Map.entry("expiry_picker_hint", "Tap a year ▶ month ▶ day — or type <b>YYYY-MM-DD</b> manually."),
                Map.entry("expiry_picker_confirmed", "✅ <b>Expiry Date Set</b> — %s"),
                Map.entry("license_update_expiry_prompt", "📅 Enter the new license expiry date in <b>YYYY-MM-DD</b> format.\nExample: 2027-12-31"),
                Map.entry("license_expiry_invalid", "⚠️ Invalid expiry date. Use <b>YYYY-MM-DD</b> and enter today or a future date."),
                Map.entry("license_update_received_pending", "📄 License and expiry date received.\nWaiting admin approval."),
                Map.entry("license_missing_expiry_suspended", "⛔ <b>License Compliance Required</b>\n\nYour pharmacy does not have a recorded license expiry date.\nYour account is suspended until you upload a license with expiry date and admin approves."),
                Map.entry("license_expiry_reminder", "⚠️ <b>License Expiry Reminder</b>\n\nYour pharmacy license will expire on <b>%s</b> (%d day(s) left).\n\nPlease update your license and expiry date before it expires."),
                Map.entry("license_expired_suspended", "⛔ <b>License Expired - Account Suspended</b>\n\nYour license expired on <b>%s</b>.\nYour pharmacy account is now suspended.\n\nUpdate your license and expiry date to reactivate."),
                Map.entry("share_phone_number_button", "📱 Share Phone Number"),
                Map.entry("send_pharmacy_location_button", "📍 Send Pharmacy Location"),
                Map.entry("reg_select_region_title", "🗺 <b>Select region:</b>"),
                Map.entry("reg_select_city_title", "🏙 <b>Select City in %s Region</b>"),
                Map.entry("reg_select_subcity_addis_title", "🏙 <b>Select Sub-City in Addis Ababa</b>"),
                Map.entry("reg_select_subcity_title", "🏙 <b>Select Sub-City in %s</b>"),
                Map.entry("reg_select_region_plain", "🗺 Select region:"),
                Map.entry("reg_select_city_plain", "🏙 Select city in %s:"),
                Map.entry("reg_select_area_plain", "📌 Select area in %s:"),
                Map.entry("reg_landmark_keyboard_text", "🏢 Send a landmark or tap Skip."),
                Map.entry("btn_skip_landmark", "⏭ Skip Landmark"),
                Map.entry("reg_plus_code_prompt", "➕ <b>Plus Code (Optional)</b>\n\nA Plus Code is a short location code (e.g. <code>7FG2+QW</code>).\n\nYou can find it in Google Maps by long-pressing your pharmacy location.\n\nSend your Plus Code or tap Skip."),
                Map.entry("btn_skip_plus_code", "⏭ Skip Plus Code"),
                Map.entry("reg_exact_address_prompt", "📍 <b>Exact Address (Optional)</b>\n\nType a descriptive street address for your pharmacy.\n\nExample: Bole Road, near Total Fuel Station\n\nSend the address or tap Skip."),
                Map.entry("btn_skip_exact_address", "⏭ Skip Exact Address"),
                Map.entry("reg_step3_open_hour", "Step 3/7\n⏰ Select opening hour"),
                Map.entry("reg_step4_close_hour", "Step 4/7\n🌙 Select closing hour"),
                Map.entry("opening_time_set", "✅ Opening time set to %s"),
                Map.entry("closing_time_set", "✅ Closing time set to %s"),
                Map.entry("opening_time_selected", "✅ Opening time selected: %s"),
                Map.entry("hours_updated", "✅ Working hours updated\n\nOpen: %s\nClose: %s"),
                Map.entry("reg_invalid_phone", "⚠️ Invalid phone number.\n\nPlease enter digits only.\nExample:\n0912345678\n\nOr tap <b>Share Phone Number</b> below."),
                Map.entry("reg_pending_submitted", "⏳ Your pharmacy registration is already submitted and waiting for admin approval."),
                Map.entry("already_registered", "🏥 You already have a registered pharmacy.\n\nUse /update to update your profile."),
                Map.entry("reg_previous_loaded", "🔁 Previous registration data loaded.\n\nReason: %s"),
                Map.entry("reg_license_after_location", "📄 After confirming location, please upload your pharmacy license (photo, PDF, DOC, or other document)."),
                Map.entry("reg_incomplete", "⚠️ Registration is incomplete.\n\nPlease finish all steps before uploading the license."),
                Map.entry("license_already_uploaded", "⚠️ License already uploaded."),
                Map.entry("contact_phone_unreadable", "⚠️ Could not read shared phone number."),
                Map.entry("location_unreadable", "⚠️ Could not read location."),
                Map.entry("pick_date_button", "📅 Pick Date"),
                // Pharmacy card labels
                Map.entry("card_km_away", "km away"),
                Map.entry("card_km", "km"),
                Map.entry("card_open_now", "🟢 Open now"),
                Map.entry("card_closed", "🔴 Closed"),
                Map.entry("card_temporarily_closed", "🔴 Temporarily closed"),
                Map.entry("card_temporarily_closed_reason", "🔴 Temporarily closed (%s)"),
                Map.entry("card_out_of_stock", "❌ Out of stock"),
                Map.entry("card_available", "✅ Available: %d left"),
                Map.entry("card_status_open", "Open now"),
                Map.entry("card_status_closed", "Closed"),
                Map.entry("card_stock_out", "Out of stock"),
                Map.entry("card_stock_left", "%d left"),
                Map.entry("card_hours_not_set", "Not set"),
                Map.entry("card_price_not_set", "not set"),
                Map.entry("card_navigate_btn", "🧭 Navigate"),
                Map.entry("card_open_map_btn", "📍 Open Map"),
                Map.entry("card_call_btn", "📞 Call"),
                Map.entry("card_reserve_btn", "📦 Reserve"),
                Map.entry("card_close_reserve_btn", "📦 Close Reserve"),
                Map.entry("card_details_btn", "ℹ️ Details"),
                Map.entry("card_rate_btn", "⭐ Rate"),
                Map.entry("card_save_btn", "❤️ Save"),
                Map.entry("card_saved_btn", "✅ Saved"),
                Map.entry("card_view_pharmacy_photo_btn", "🖼 View Pharmacy Photo"),
                Map.entry("card_view_medicine_photos_btn", "🖼 View Medicine Photos"),
                Map.entry("card_hide_details_btn", "🔽 Hide Details"),
                Map.entry("card_report_btn", "⚠️ Report issue"),
                Map.entry("card_reserve_matched_btn", "📦 Reserve Matched"),
                Map.entry("card_reserve_one_matched_btn", "📦 Reserve One Matched"),
                Map.entry("card_multi_reserve_btn", "🧺 Multi Reserve"),
                Map.entry("card_reserve_all_later_btn", "🧺 Reserve All Later"),
                Map.entry("multi_reserve_unavailable_title", "🚧 <b>Multi-Medicine Reservation</b>"),
                Map.entry("multi_reserve_unavailable_msg", "Multi-medicine reservation in one request is not available yet.\n\nFor now, choose <b>Reserve One Matched</b> to reserve one available medicine at a time."),
                Map.entry("issue_report_choose_type", "Choose issue type"),
                Map.entry("issue_type_price", "💰 Wrong Price"),
                Map.entry("issue_type_stock", "📦 Wrong Stock"),
                Map.entry("issue_type_location", "📍 Wrong Location"),
                Map.entry("issue_type_service", "🧾 Bad Service"),
                Map.entry("issue_type_stock_short", "⚠️ Stock not available"),
                Map.entry("issue_type_phone_short", "⚠️ Wrong phone"),
                Map.entry("issue_type_closed_short", "⚠️ Pharmacy closed"),
                Map.entry("issue_type_location_short", "📍 Wrong location"),
                Map.entry("issue_type_service_short", "🧾 Bad service"),
                Map.entry("issue_type_other", "✍️ Other"),
                Map.entry("issue_type_cancel", "❌ Cancel"),
                Map.entry("issue_menu_title", "⚠️ Report issue"),
                Map.entry("issue_menu_close", "🔽 Close"),
                Map.entry("issue_report_prompt_other", "✍️ Please type your issue details."),
                Map.entry("issue_report_received", "✅ Issue reported. Thank you."),
                Map.entry("issue_already_reported", "⚠️ You already reported this issue. Please wait before reporting again."),
                Map.entry("btn_other", "✍️ Other"),
                Map.entry("btn_back", "⬅️ Back"),
                Map.entry("btn_main", "🏠 Main"),
                Map.entry("btn_cancel", "❌ Cancel"),
                Map.entry("btn_rated", "✅ Rated"),
                Map.entry("btn_use_saved_location", "📍 Use Saved Location"),
                Map.entry("btn_share_current_location", "📌 Share Current Location"),
                Map.entry("btn_search_pharmacies", "🔍 Search Pharmacies"),
                Map.entry("btn_add_more", "➕ Add More"),
                Map.entry("btn_clear", "🗑 Clear"),
                Map.entry("btn_change_location", "📍 Change Location"),
                Map.entry("btn_notify_available", "🔔 Notify me when available"),
                Map.entry("btn_home", "🏠 Home"),
                Map.entry("medicine_suggestion_picker_title", "💊 <b>Suggested medicines for:</b> %s\nChoose one below or use your typed value."),
                Map.entry("medicine_suggestion_alternative_hint", "💡 Similar or alternative medicines are also included below."),
                Map.entry("medicine_suggestion_use_typed", "✅ Use \"%s\""),
                Map.entry("medicine_suggestion_no_exact", "❌ <b>No exact match found for:</b> %s"),
                Map.entry("medicine_suggestion_did_you_mean", "💊 <b>Did you mean one of these?</b>"),
                Map.entry("medicine_suggestion_alternatives_title", "💡 <b>Possible alternatives:</b>"),
                Map.entry("medicine_suggestion_notify_for", "🔔 Notify Me for %s"),
                Map.entry("medicine_no_pharmacies_found", "❌ <b>No pharmacies found for:</b> %s\n\nYou can create an alert and get notified when it becomes available."),
                Map.entry("btn_refresh", "🔄 Refresh"),
                Map.entry("btn_favorite_pharmacies", "❤️ Favorite Pharmacies"),
                Map.entry("btn_profile", "⚙️ Profile"),
                Map.entry("btn_remove", "🗑 Remove"),
                Map.entry("btn_remove_all_alerts", "🗑 Remove All Alerts"),
                Map.entry("btn_remove_alert", "❌ Remove Alert"),
                Map.entry("btn_search_now", "🔎 Search Now"),
                Map.entry("card_pharmacy_details_title", "Pharmacy Details"),
                Map.entry("card_name_label", "Name:"),
                Map.entry("card_medicine_label", "Medicine:"),
                Map.entry("card_address_label", "Address:"),
                Map.entry("card_exact_address_label", "Exact Address:"),
                Map.entry("card_landmark_label", "Landmark:"),
                Map.entry("card_plus_code_label", "Plus Code:"),
                Map.entry("card_phone_label", "Phone:"),
                Map.entry("card_distance_label", "Distance:"),
                Map.entry("card_rating_label", "Rating:"),
                Map.entry("card_price_label", "Price:"),
                Map.entry("card_hours_label", "Hours:"),
                Map.entry("card_status_label", "Status:"),
                Map.entry("card_stock_label", "Stock:"),
                Map.entry("card_last_stock_update_label", "Last Stock Update:"),
                Map.entry("reservation_blocked_temp_closed", "🚫 This pharmacy is temporarily closed.\n\nReservations are disabled right now.%n%s"),
                // Reservation history card
                Map.entry("res_hist_title", "📜 <b>Reservation History</b>"),
                Map.entry("res_hist_empty", "📜 <b>Reservation History</b>\n\nNo reservations found."),
                Map.entry("res_hist_section_pending", "⏳ Pending"),
                Map.entry("res_hist_section_approved", "✅ Approved"),
                Map.entry("res_hist_section_fulfilled", "📦 Fulfilled"),
                Map.entry("res_hist_section_cancelled", "❌ Cancelled"),
                Map.entry("res_hist_section_expired", "⌛ Expired"),
                Map.entry("res_hist_section_rejected", "🚫 Rejected"),
                Map.entry("res_hist_hold_until", "Hold Until"),
                Map.entry("res_hist_reason", "Reason"),
                Map.entry("res_status_pending", "Pending"),
                Map.entry("res_status_approved", "Approved"),
                Map.entry("res_status_fulfilled", "Fulfilled"),
                Map.entry("res_status_cancelled", "Cancelled"),
                Map.entry("res_status_expired", "Expired"),
                                Map.entry("res_status_rejected", "Rejected"),
                                Map.entry("res_card_id_label", "ID:"),
                                Map.entry("res_card_pharmacy_label", "Pharmacy:"),
                                Map.entry("res_card_quantity_label", "Quantity:"),
                                Map.entry("res_card_reserve_again_btn", "🔁 Reserve Again"),
                                Map.entry("res_section_reserve_latest_btn", "🔁 Reserve Again (Latest)")
        );
    }

    private static Map<String, String> amharicTexts() {
        return Map.ofEntries(
                Map.entry("start_welcome", "👋 <b>ወደ TenaHub እንኳን ደህና መጡ</b>\n\n⚠️ ለመጀመሪያ ጊዜ ከሆነ አካባቢዎን በ📎 አባሪ ቁልፍ አንድ ጊዜ ያጋሩ።\n\nከዚያ በኋላ ከታች ያለውን የአካባቢ ማጋሪያ ቁልፍ መጠቀም ይችላሉ።\n\n🏥 የፋርማሲ ባለቤቶችም ከታች መመዝገብ ይችላሉ።"),
                Map.entry("share_location_button", "📍 አካባቢ አጋራ"),
                Map.entry("register_pharmacy_button", "🏥 ፋርማሲ መዝግብ"),
                Map.entry("dashboard_welcome", "👋 <b>ወደ TenaHub እንኳን ደህና መጡ</b>\n\nአቅራቢያዎ ያሉ ፋርማሲዎችን ያግኙ እና መድሃኒቶችን በቀላሉ ያስይዙ።\n\nከታች አንዱን ይምረጡ:"),
                Map.entry("search_medicines_button", "🔎 መድሃኒት ፈልግ"),
                Map.entry("search_multiple_meds_button", "🔎🛒 ብዙ መድሃኒቶች ፈልግ"),
                Map.entry("my_reservations_button", "📦 የእኔ ቦታ ማስያዣዎች"),
                Map.entry("recent_searches_button", "🕘 የቅርብ ፍለጋዎች"),
                Map.entry("reserve_again_button", "🔁 እንደገና አስይዝ"),
                Map.entry("account_button", "👤 መለያ"),
                Map.entry("my_alerts_button", "🔔 ማስጠንቀቂያዎቼ"),
                Map.entry("how_to_use_button", "❓ እንዴት እጠቀም"),
                Map.entry("information_button", "📖 መረጃ"),
                Map.entry("leave_feedback_button", "📝 አስተያየት ስጥ"),
                Map.entry("language_button", "🌐 ቋንቋ"),
                Map.entry("pending_home_text", "⏳ <b>የፋርማሲዎ ምዝገባ በግምገማ ላይ ነው</b>\n\nእባክዎ የአስተዳዳሪ ፍቃድን ይጠብቁ።"),
                Map.entry("refresh_button", "🔄 አድስ"),
                        Map.entry("main_button", AM_HOME),
                        Map.entry("home_button", AM_HOME),
                Map.entry("back_button", "⬅️ ተመለስ"),
                Map.entry("cancel_button", "❌ ሰርዝ"),
                Map.entry("location_choice_title", "📍 <b>የፋርማሲ አካባቢ</b>\n\nደረጃ 6/7\nየፋርማሲውን አካባቢ እንዴት መያዝ እንደሚፈልጉ ይምረጡ።\n\n1. ትክክለኛ የፋርማሲ አካባቢ ያጋሩ\n2. የGoogle Maps ሊንክ / ኮኦርዲኔት ያስገቡ\n3. ክልል → ከተማ/ክፍለ ከተማ → አካባቢ ይምረጡ"),
                Map.entry("share_exact_pharmacy_location_button", "📍 ትክክለኛ የፋርማሲ አካባቢ አጋራ"),
                Map.entry("paste_google_maps_link_button", "🔗 የGoogle Maps ሊንክ ለጥፍ"),
                Map.entry("select_ethiopia_region_button", "🗺 የኢትዮጵያ ክልል ምረጥ"),
                Map.entry("account_actions_title", "⚙️ <b>የመለያ እርምጃዎች</b>"),
                Map.entry("favorite_pharmacies_button", "❤️ ተወዳጅ ፋርማሲዎች"),
                Map.entry("recent_searches_empty", "🕘 <b>የቅርብ ፍለጋዎች</b>\n\nየቅርብ ፍለጋ አልተገኘም።"),
                Map.entry("recent_searches_title", "🕘 <b>የቅርብ ፍለጋዎች</b>\n\nየቅርብ 5 መድሃኒቶች። ለመፈለግ ወይም እንደገና ለማስያዝ ይንኩ:"),
                Map.entry("recent_search_item", "💊 %s"),
                        Map.entry("recent_search_home_button", AM_HOME),
                Map.entry("reserve_again_empty", "🔁 <b>እንደገና አስይዝ</b>\n\nገና የቦታ ማስያዣ ታሪክ የለም።"),
                Map.entry("reserve_again_title", "🔁 <b>እንደገና አስይዝ</b>\n\nከቦታ ማስያዣ ታሪክዎ አንድ ጊዜ ንክኪ ፍለጋ:"),
                Map.entry("reserve_again_item", "🔁 %s"),
                Map.entry("my_reservations_menu_title", "📦 <b>የእኔ ቦታ ማስያዣዎች</b>\n\nከታች ካሉት ቁልፎች ክፍል ይምረጡ:"),
                Map.entry("pending_button", "⏳ በመጠባበቅ ላይ"),
                Map.entry("fulfilled_button", "📦 የተጠናቀቀ"),
                Map.entry("expired_button", "⌛ ያለፈበት"),
                Map.entry("cancelled_button", "❌ የተሰረዘ"),
                Map.entry("language_menu_text", "🌐 <b>ቋንቋ ይምረጡ</b>\n\nአሁን ያለው ቋንቋ: <b>%s</b>"),
                Map.entry("language_name_english", "English"),
                Map.entry("language_name_amharic", "አማርኛ"),
                Map.entry("language_changed", "🌐 ቋንቋው ወደ <b>%s</b> ተቀይሯል።"),
                Map.entry("how_to_use_text", "❓ <b>TenaHub እንዴት እንደሚጠቀሙ</b>\n\n1. አካባቢዎን ያጋሩ\n2. መድሃኒት ይፈልጉ\n3. አቅራቢያዎ ያሉ ፋርማሲዎችን ይመልከቱ\n4. ካለ ቦታ ያስይዙ\n5. ብዛት፣ ስም እና ስልክ ያስገቡ\n6. የፋርማሲ ማጽደቅን ይጠብቁ\n7. የተያዘው ጊዜ ከማለፉ በፊት ይውሰዱ"),
                Map.entry("information_text", "📖 <b>ስለ TenaHub</b>\n\nTenaHub ተጠቃሚዎች አቅራቢያቸው ያሉ ፋርማሲዎችን እንዲያገኙ፣ የመድሃኒት አቅርቦትን እንዲያዩ እና ከመሄዳቸው በፊት መድሃኒት እንዲያስይዙ ይረዳል።\n\nየፋርማሲ ባለቤቶችም በቦቱ ውስጥ ዕቃ ዝርዝር፣ ቦታ ማስያዣዎች እና መለያ መረጃ ማስተዳደር ይችላሉ።"),
                Map.entry("information_menu_title", "📖 <b>መረጃ</b>\n\nአንዱን ይምረጡ:"),
                Map.entry("about_tenahub_button", "ℹ️ ስለ TenaHub"),
                Map.entry("contacts_button", "📞 አድራሻዎች"),
                Map.entry("contacts_text_title", "📞 <b>አድራሻዎች</b>"),
                Map.entry("contacts_website_label", "🌐 <b>ድህረ ገጽ:</b> %s"),
                Map.entry("contacts_phone_label", "📞 <b>የድጋፍ ስልክ:</b> %s"),
                Map.entry("contacts_email_label", "📧 <b>የድጋፍ ኢሜይል:</b> %s"),
                Map.entry("contacts_telegram_label", "💬 <b>የTelegram ድጋፍ:</b> %s"),
                Map.entry("contacts_partnership_label", "🤝 <b>የቢዝነስ ትብብር:</b> %s"),
                Map.entry("feedback_prompt", "📝 እባክዎ አስተያየትዎን ይጻፉ።\n\nአገልግሎቱን ለማሻሻል እንጠቀምበታለን።"),
                Map.entry("feedback_received", "✅ አስተያየትዎ ደርሷل። እናመሰግናለን!"),
                Map.entry("search_medicine_prompt", "🔎 ለመፈለግ የመድሃኒት ስም ይላኩ።"),
                Map.entry("invalid_medicine_selection", "⚠️ የመድሃኒት ምርጫ ልክ አይደለም።"),
                Map.entry("share_location_first", "⚠️ እባክዎ መጀመሪያ አካባቢዎን ያጋሩ።"),
                Map.entry("pharmacies_with_medicine", "💊 <b>%s ያላቸው ፋርማሲዎች</b>\n\nየተደረደሩት በ: <b>%s</b>"),
                Map.entry("nearest_filter_label", "ቅርብ"),
                Map.entry("cheapest_filter_label", "ዝቅተኛ ዋጋ"),
                Map.entry("highest_rated_filter_label", "ከፍተኛ ደረጃ"),
                Map.entry("open_now_filter_label", "አሁን ክፍት"),
                Map.entry("in_stock_only_filter_label", "በስቶክ ያሉ ብቻ"),
                Map.entry("clear_filters_button", "❌ ማጣሪያዎችን አጥፋ"),
                Map.entry("search_filters_title", "🔎 <b>የፍለጋ ማጣሪያዎች</b>\n\nንቁ ማጣሪያ: <b>%s</b>\nከታች ይምረጡ።"),
                Map.entry("multi_medicine_mode_text", "🧺 የብዙ መድሃኒት ሁኔታ ንቁ ነው።"),
                Map.entry("multi_search_title", "🔎🛒 ብዙ መድሃኒት ፍለጋ\n\nመጀመሪያ የአካባቢ አማራጭ ይምረጡ።"),
                Map.entry("multi_search_intro", "🔎🛒 <b>ብዙ መድሃኒት ፍለጋ</b>\n\nበርካታ መድሃኒቶችን የሚያቀርብ አንድ ፋርማሲ ያግኙ።\n\nመጀመሪያ የአካባቢ አማራጭ ይምረጡ።"),
                Map.entry("multi_share_exact_location_prompt", "📍 <b>የአሁኑን አካባቢ አጋራ</b>\n\nለብዙ መድሃኒት ፍለጋ ትክክለኛ አካባቢዎን ለመላክ ከታች ያለውን ቁልፍ ይጫኑ።"),
                Map.entry("use_saved_location_button", "📍 የተቀመጠ አካባቢ ተጠቀም"),
                Map.entry("share_current_location_button", "📌 የአሁኑን አካባቢ አጋራ"),
                Map.entry("change_location_button", "📍 አካባቢ ቀይር"),
                Map.entry("change_location_title", "📍 <b>አካባቢ ቀይር</b>\n\nየፍለጋ አካባቢዎን እንዴት ማዘመን እንደሚፈልጉ ይምረጡ።"),
                Map.entry("share_exact_location_button", "📍 ትክክለኛ አካባቢ አጋራ"),
                Map.entry("search_pharmacies_button", "🔍 ፋርማሲዎችን ፈልግ"),
                Map.entry("add_more_button", "➕ ተጨማሪ ጨምር"),
                Map.entry("clear_button", "🗑 አጽዳ"),
                Map.entry("no_reservations_found", "📦 <b>የእኔ ቦታ ማስያዣዎች</b>\n\nምንም ቦታ ማስያዣ አልተገኘም።"),
                Map.entry("pending_section_title", "📦 <b>የእኔ ቦታ ማስያዣዎች</b>\n\n⏳ በመጠባበቅ ላይ"),
                Map.entry("fulfilled_section_title", "📦 <b>የእኔ ቦታ ማስያዣዎች</b>\n\n📦 የተጠናቀቀ"),
                Map.entry("expired_section_title", "📦 <b>የእኔ ቦታ ማስያዣዎች</b>\n\n⌛ ያለፈበት"),
                Map.entry("cancelled_section_title", "📦 <b>የእኔ ቦታ ማስያዣዎች</b>\n\n❌ የተሰረዘ"),
                Map.entry("unknown_reservation_section", "⚠️ ያልታወቀ የቦታ ማስያዣ ክፍል ነው።"),
                Map.entry("account_overview_title", "👤 <b>የመለያ አጠቃላይ እይታ</b>"),
                Map.entry("telegram_id_label", "🆔 <b>Telegram ID:</b> %s"),
                Map.entry("saved_location_label", "📍 <b>የተቀመጠ አካባቢ:</b> %s"),
                Map.entry("saved_location_missing", "አልተቀመጠም"),
                Map.entry("saved_location_ok", "ተቀምጧል ✅"),
                Map.entry("reservations_summary_title", "📦 <b>ቦታ ማስያዣዎች</b>"),
                Map.entry("reservation_count_line", "• %s: %d"),
                Map.entry("approved_status", "የጸደቀ"),
                Map.entry("pharmacy_status_label", "🏥 <b>የፋርማሲ ሁኔታ:</b> %s"),
                Map.entry("registered_status", "ተመዝግቧል ✅"),
                Map.entry("not_registered_status", "አልተመዘገበም"),
                Map.entry("register_hint", "💡 ፋርማሲዎን ከምናሌው መመዝገብ ይችላሉ።"),
                Map.entry("recent_reservations_title", "🕘 <b>የቅርብ ቦታ ማስያዣዎች</b>"),
                Map.entry("no_recent_reservations", "• እስካሁን ምንም ቦታ ማስያዣ የለም"),
                Map.entry("unknown_pharmacy", "ያልታወቀ ፋርማሲ"),
                Map.entry("quick_actions_hint", "ፈጣን እርምጃ ለማድረግ ከታች ያሉትን ቁልፎች ይጠቀሙ።"),
                Map.entry("alert_location_saved", "የተቀመጠ አካባቢ"),
                Map.entry("alert_location_any", "ማንኛውም ቅርብ ፋርማሲ"),
                Map.entry("alert_created", "🔔 <b>ማስጠንቀቂያ ተፈጥሯል</b>\n\n💊 መድሃኒት: %s\n📍 አካባቢ: %s\n\nሲገኝ ይገለጋሉ።"),
                Map.entry("alert_removed", "❌ ማስጠንቀቂያው ተሰርዟል።"),
                Map.entry("alert_all_removed", "🗑 ሁሉም ማስጠንቀቂያዎች ተሰርዘዋሉ።"),
                Map.entry("fav_added", "❤️ ፋርማሲ ወደ ተወዳጅ ተቀምጧል።"),
                Map.entry("fav_removed", "✅ ከተወዳጅ ተወግዷل።"),
                Map.entry("no_favorites", "❤️ እስካሁን ምንም ተወዳጅ ፋርማሲ የለም።"),
                Map.entry("your_favorites_title", "❤️ <b>ተወዳጅ ፋርማሲዎችዎ</b>"),
                Map.entry("session_expired", "⚠️ ክፍለ ጊዜ አልፏل።"),
                Map.entry("select_at_least_one_medicine", "⚠️ ቢያንስ አንድ መድሃኒት ይምረጡ።"),
                Map.entry("no_pharmacies_for_medicines", "❌ ለተመረጡት መድሃኒቶቹ ምንም ፋርማሲ አልተገኘም።"),
                Map.entry("multi_results_title", "🏥 <b>የብዙ መድሃኒት ፍለጋ ውጤቶች</b>\n\nከፍተኛ ተዛምዶ ካለው ፋርማሲ ተደርድሯل።"),
                Map.entry("send_first_medicine", "💊 የመጀመሪያ መድሃኒት ስም ይላኩ።\n\nምሳሌ:\ninsulin"),
                Map.entry("share_location_then_medicine", "📍 አካባቢዎን ያጋሩ፣ ከዚያ የመድሃኒት ስም ይላኩ።"),
                Map.entry("send_another_medicine", "💊 ሌላ የመድሃኒት ስም ይላኩ።"),
                Map.entry("reserve_all_later_text", "🧺 <b>ሁሉን ቆጣቢ</b>\n\nበአንድ ጥያቄ ብዙ መድሃኒቶችን ማስያዝ አሁን አልተቻለም።\n\nለEach ፋርማሲ ምርጫ <b>ተዛምዶ ፋርማሲ ያስይዙ</b> ይጠቀሙ።"),
                Map.entry("no_matched_medicines", "⚠️ ለቦታ ማስያዣ ምንም ተዛምዶ መድሃኒት አልተገኘም።"),
                Map.entry("type_medicine_add", "✍️ ማከል የሚፈልጉትን የመድሃኒት ስም ይጻፉ።"),
                Map.entry("medicine_selection_cancelled", "❌ የመድሃኒት ምርጫ ተሰርዟل።"),
                Map.entry("please_select_at_least_one", "⚠️ ቢያንስ አንድ መድሃኒት ይምረጡ።"),
                Map.entry("medicines_updated", "✅ መድሃኒቶቹ ተዘምነዋล"),
                Map.entry("medicine_name_too_short", "⚠️ የመድሃኒቱ ስም ያጠረ ነው።"),
                Map.entry("max_medicines_reached", "⚠️ ከፍተኛ 5 መድሃኒቶች ብቻ ይፈቀዳሉ።"),
                Map.entry("medicine_added", "✅ ታክሏล: %s\n\nአሁን ያሉ መድሃኒቶች:\n%s"),
                Map.entry("medicines_cleared_prompt", "🗑 የተመረጡ መድሃኒቶች ጸዱ።\n\nአሁን የመጀመሪያ መድሃኒት ስም ይላኩ።"),
                Map.entry("add_at_least_one_medicine", "⚠️ መጀመሪያ ቢያንስ አንድ መድሃኒት ያክሉ።"),
                Map.entry("reservation_cancelled_msg", "❌ ቦታ ማስያዣ ተሰርዟล።"),
                Map.entry("reservation_pick_cancelled", "❌ የቦታ ማስያዣ ምርጫ ተሰርዟล።"),
                Map.entry("enter_quantity_prompt", "✍️ ብዛቱን ቁጥር አድርጎ ያስገቡ።\n\nምሳሌ: 4"),
                Map.entry("enter_full_name_prompt", "👤 እባክዎ ሙሉ ስምዎን ያስገቡ።"),
                Map.entry("reservation_submitted", "✅ <b>ቦታ ማስያዣ ተቀብሏล!</b>\n\n💊 መድሃኒት: %s\n🔢 ብዛት: %d\n👤 ስም: %s\n📱 ስልክ: %s\n🕒 የፋርማሲ ማጽደቅን ይጠብቁ።"),
                Map.entry("reservation_missing_details", "⚠️ የቦታ ማስያዣ መረጃ ያልተሟla ነው። እንደገና ይጀምሩ።"),
                Map.entry("reservation_cancel_success", "✅ ቦታ ማስያዣ ተሰርዟல።\n\n🆔 ID: %d\n💊 መድሃኒት: %s\n🔢 ብዛት: %d"),
                Map.entry("reservation_approved_user", "✅ ቦታ ማስያዣዎ ጸድቋล።\n\n💊 መድሃኒት: %s\n🔢 ብዛት: %d\n⏳ እስከ: %s\n\nጊዜው ከማለፉ በፊት ይምጡ።"),
                Map.entry("reservation_rejected_user", "❌ ቦታ ማስያዣዎ ጸደቀ አለ።\n\n💊 መድሃኒት: %s\n🔢 ብዛት: %d"),
                Map.entry("reservation_fulfilled_user", "📦 ቦታ ማስያዣዎ ተሟልቷล።\n\n💊 መድሃኒት: %s\n🔢 ብዛት: %d"),
                Map.entry("reservation_contact_sent", "✅ ቦታ ማስያዣ ወደ ፋርማሲ ተልኳล።\n\n💊 መድሃኒት: %s\n🔢 ብዛት: %d\n👤 ስም: %s\n📱 ስልክ: %s\n🕒 የፋርማሲ ማጽደቅን ይጠብቁ።"),
                Map.entry("reservation_contact_saved", "✅ ቦታ ማስያዣ ተቀምጧล።\n\n💊 መድሃኒት: %s\n🔢 ብዛት: %d\n👤 ስም: %s\n📱 ስልክ: %s\n\n⚠️ ፋርማሲውን ማሳወቅ አልቻለም።"),
                Map.entry("reservation_contact_failed", "⚠️ ቦታ ማስያዣ መፍጠር አልቻለም።\n\n%s"),
                Map.entry("rating_thanks", "⭐ %d/5 ደረጃ ሰጡ፤ እናመሰግናለን!"),
                Map.entry("location_received", "📍 አካባቢ ደረሰ።\n\nአሁን የመድሃኒት ስም ይላኩ።"),
                Map.entry("exact_location_saved_multi", "✅ ትክክለኛ አካባቢ ለብዙ መድሃኒት ፍለጋ ተቀምጧล።\n\nአሁን የመጀመሪያ መድሃኒት ስም ይላኩ።"),
                Map.entry("please_share_exact_location", "📍 ትክክለኛ አካባቢዎን ያጋሩ።"),
                Map.entry("exact_location_not_ethiopia", "✅ ትክክለኛ አካባቢ ደረሰ።\n\n⚠️ ይህ ቦታ ቅርብ ኢትዮጵያ ፋርማሲ አካባቢን አይዛመድም።\n\nከዚህ ይምረጡ:\n• 🔗 Google Maps ሊንክ ለጥፍ\n• 🗺 የኢትዮጵያ ክልል ምረጥ"),
                Map.entry("reg_name_prompt", "🏥 <b>ፋርማሲ ምዝገባ</b>\n\nደረጃ 1/7\nእባክዎ የፋርማሲዎን ስም ያስገቡ።"),
                Map.entry("reg_region_prompt", "🏥 <b>ፋርማሲ ምዝገባ</b>\n\nደረጃ 2/7\n🗺 በኢትዮጵያ ያለውን ክልል ይምረጡ።"),
                Map.entry("reg_city_prompt", "🏥 <b>ፋርማሲ ምዝገባ</b>\n\nደረጃ 2/7\n📍 ከተማዎን ያስገቡ"),
                Map.entry("reg_area_prompt", "🏥 <b>ፋርማሲ ምዝገባ</b>\n\nደረጃ 3/7\n📌 አካባቢዎን ያስገቡ"),
                Map.entry("reg_phone_prompt", "🏥 <b>ፋርማሲ ምዝገባ</b>\n\nደረጃ 2/7\n📞 ስልክ ቁጥር ያስገቡ\nምሳሌ: 0912345678\n\nወይም ከታch <b>ስልክ ቁጥር አጋራ</b> ይንኩ።"),
                Map.entry("reg_google_map_help_text", "🔗 <b>የGoogle Maps ሊንክ ለጥፍ</b>\n\nየሚሰራ Google Maps ሊንክ ወይም ኮኦርዲኔት ያስገቡ።\n\nምሳሌ:\nhttps://maps.google.com/?q=8.9806,38.7578\n\nወይም\n8.9806,38.7578"),
                Map.entry("reg_exact_location_help_text", "📍 <b>ትክክለኛ የፋርማሲ አካባቢ አጋራ</b>\n\nከታች ያለውን ቁልፍ በመንካት ትክክለኛ የፋርማሲ አካባቢ ያጋሩ።"),
                Map.entry("reg_license_step", "📄 አሁን የፋርማሲዎ ፈቃድ ያስሰቅሉ (ፎቶ፣ PDF፣ DOC ወይም ሌላ ሰነድ)።"),
                Map.entry("reg_license_expiry_step", "📅 የፈቃዱ ማብቂያ ቀን <b>YYYY-MM-DD</b> ቅርጸት ያስገቡ።\nምሳሌ: 2027-12-31\n\nወይም ከታch <b>📅 ቀን ምረጥ</b> ይንኩ።"),
                Map.entry("expiry_picker_reg_title", "📅 <b>የፈቃድ ማብቂያ ቀን ያዘጋጁ</b>"),
                Map.entry("expiry_picker_update_title", "📅 <b>የፈቃድ ማብቂያ ቀን ያዘምኑ</b>"),
                Map.entry("expiry_picker_hint", "ዓ.ም ▶ ወር ▶ ቀን ይጫኑ — ወይም <b>YYYY-MM-DD</b> ያስገቡ።"),
                Map.entry("expiry_picker_confirmed", "✅ <b>ማብቂያ ቀን ተቀምጧል</b> — %s"),
                Map.entry("license_update_expiry_prompt", "📅 የአዲሱን ፈቃድ ማብቂያ ቀን በ <b>YYYY-MM-DD</b> ቅርጸት ያስገቡ።\nምሳሌ: 2027-12-31"),
                Map.entry("license_expiry_invalid", "⚠️ ልክ ያልሆነ የማብቂያ ቀን ነው። <b>YYYY-MM-DD</b> ይጠቀሙ እና የዛሬ ወይም ወደፊት ቀን ያስገቡ።"),
                Map.entry("license_update_received_pending", "📄 ፈቃድ እና የማብቂያ ቀን ደርሰዋል።\nየአስተዳዳሪ ማጽደቅ በመጠባበቅ ላይ ነው።"),
                Map.entry("license_missing_expiry_suspended", "⛔ <b>የፈቃድ ተገዢነት ያስፈልጋል</b>\n\nለፋርማሲዎ የተመዘገበ የፈቃድ ማብቂያ ቀን የለም።\nፈቃድ ከማብቂያ ቀን ጋር እስክታቀርቡ እና አስተዳዳሪው እስኪያጸድቅ ድረስ መለያዎ ታግዷል።"),
                Map.entry("license_expiry_reminder", "⚠️ <b>የፈቃድ ማብቂያ አስታዋሽ</b>\n\nየፋርማሲዎ ፈቃድ <b>%s</b> ይያዛል (%d ቀን ቀርቷል)።\n\nከማብቃቱ በፊት ፈቃድዎን እና የማብቂያ ቀኑን ያዘምኑ።"),
                Map.entry("license_expired_suspended", "⛔ <b>ፈቃድ አልፏል - መለያዎ ታግዷል</b>\n\nፈቃድዎ <b>%s</b> ላይ አልፏል።\nየፋርማሲዎ መለያ አሁን ታግዷል።\n\nእንደገና ለማንቃት ፈቃድዎን እና የማብቂያ ቀኑን ያዘምኑ።"),
                Map.entry("share_phone_number_button", "📱 ስልክ ቁጥር አጋራ"),
                Map.entry("send_pharmacy_location_button", "📍 የፋርማሲ አካባቢ ላክ"),
                Map.entry("reg_select_region_title", "🗺 <b>ክልል ይምረጡ:</b>"),
                Map.entry("reg_select_city_title", "🏙 <b>በ%s ክልል ውስጥ ከተማ ይምረጡ</b>"),
                Map.entry("reg_select_subcity_addis_title", "🏙 <b>በአዲስ አበባ ክፍለ ከተማ ይምረጡ</b>"),
                Map.entry("reg_select_subcity_title", "🏙 <b>በ%s ውስጥ ክፍለ ከተማ ይምረጡ</b>"),
                Map.entry("reg_select_region_plain", "🗺 ክልል ይምረጡ:"),
                Map.entry("reg_select_city_plain", "🏙 በ%s ውስጥ ከተማ ይምረጡ:"),
                Map.entry("reg_select_area_plain", "📌 በ%s ውስጥ አካባቢ ይምረጡ:"),
                Map.entry("reg_landmark_keyboard_text", "🏢 ምልክተኛ ቦታ ይላኩ ወይም ዝለል ይንኩ።"),
                Map.entry("btn_skip_landmark", "⏭ ምልክተኛ ዝለል"),
                Map.entry("reg_plus_code_prompt", "➕ <b>ፕለስ ኮድ (አይደለም ግዴታ)</b>\n\nፕለስ ኮድ አጭር የአካባቢ ኮድ ነው (ምሳሌ: <code>7FG2+QW</code>).\n\nGoogle Maps ላይ የፋርማሲዎን ቦታ ረዘም ሲነኩ ማግኘት ይችላሉ።\n\nፕለስ ኮድዎን ይላኩ ወይም ዝለል ይንኩ።"),
                Map.entry("btn_skip_plus_code", "⏭ ፕለስ ኮድ ዝለል"),
                Map.entry("reg_exact_address_prompt", "📍 <b>ትክክለኛ አድራሻ (አይደለም ግዴታ)</b>\n\nለፋርማሲዎ ዝርዝር የጎዳና አድራሻ ይጻፉ።\n\nምሳሌ: ቦሌ ጎዳና፣ Total ነዳጅ ቤት አካባቢ\n\nአድራሻዎን ይላኩ ወይም ዝለል ይንኩ።"),
                Map.entry("btn_skip_exact_address", "⏭ ትክክለኛ አድራሻ ዝለል"),
                Map.entry("reg_step3_open_hour", "ደረጃ 3/7\n⏰ የሚከፈትበት ሰዓት ይምረጡ"),
                Map.entry("reg_step4_close_hour", "ደረጃ 4/7\n🌙 የሚዘጋበት ሰዓት ይምረጡ"),
                Map.entry("opening_time_set", "✅ የሚከፈትበት ሰዓት %s ሆኗล"),
                Map.entry("closing_time_set", "✅ የሚዘጋበት ሰዓት %s ሆኗล"),
                Map.entry("opening_time_selected", "✅ የሚከፈትበት ሰዓት ተምርጧล: %s"),
                Map.entry("hours_updated", "✅ የሥራ ሰዓቶቹ ተዘምነዋล\n\nሚከፈትበት: %s\nሚዘጋበት: %s"),
                Map.entry("reg_invalid_phone", "⚠️ የስልክ ቁጥሩ ልክ አይደለም።\n\nቁጥሮቹን ብቻ ያስገቡ።\nምሳሌ:\n0912345678\n\nወይም ከታch <b>ስልክ ቁጥር አጋራ</b> ይንኩ።"),
                Map.entry("reg_pending_submitted", "⏳ የፋርማሲዎ ምዝገባ ቀደም ብሎ ቀርቧล፣ የአስተዳዳሪ ፍቃድ ይጠብቃล።"),
                Map.entry("already_registered", "🏥 ተመዝጋቢ ፋርማሲ አሎዎт።\n\nፕሮፋይልዎን ለማዘመን /update ይጠቀሙ።"),
                Map.entry("reg_previous_loaded", "🔁 ቀደምት ምዝገባ ዳታ ተጭኗல።\n\nምክንያት: %s"),
                Map.entry("reg_license_after_location", "📄 አካባቢ ካረጋገጡ በኋla የፋርማሲዎ ፈቃድ ያስሰቅሉ (ፎቶ፣ PDF፣ DOC ወይም ሌላ ሰነድ)།"),
                Map.entry("reg_incomplete", "⚠️ ምዝገባው ያልተሟla ነው።\n\nፈቃዱን ከመሰቀሉ በፊt ሁሉም ደረጃዎቹ ይሟሉ།"),
                Map.entry("license_already_uploaded", "⚠️ ፈቃዱ ቀደም ብሎ ተሰቅሏล།"),
                Map.entry("contact_phone_unreadable", "⚠️ ስልክ ቁጥሩ ሊነበብ አልቻሌ།"),
                Map.entry("location_unreadable", "⚠️ አካባቢው ሊነበብ አልቻሌ།"),
                Map.entry("pick_date_button", "📅 ቀን ምረጥ"),
                // Pharmacy card labels
                Map.entry("card_km_away", "ኪ.ሜ ርቀት"),
                Map.entry("card_km", "ኪ.ሜ"),
                Map.entry("card_open_now", "🟢 ክፍት ነው"),
                Map.entry("card_closed", "🔴 ተዘግቷል"),
                Map.entry("card_temporarily_closed", "🔴 ለጊዜው ተዘግቷል"),
                Map.entry("card_temporarily_closed_reason", "🔴 ለጊዜው ተዘግቷል (%s)"),
                Map.entry("card_out_of_stock", "❌ አልቋል"),
                Map.entry("card_available", "✅ ይገኛል: %d ቀርቷል"),
                Map.entry("card_status_open", "ክፍት ነው"),
                Map.entry("card_status_closed", "ተዘግቷል"),
                Map.entry("card_stock_out", "አልቋል"),
                Map.entry("card_stock_left", "%d ቀርቷል"),
                Map.entry("card_hours_not_set", "አልተዋቀረም"),
                Map.entry("card_price_not_set", "አልተወሰነም"),
                Map.entry("card_navigate_btn", "🧭 አቅጣጫ"),
                Map.entry("card_open_map_btn", "📍 ካርታ ክፈት"),
                Map.entry("card_call_btn", "📞 ደውል"),
                Map.entry("card_reserve_btn", "📦 ቦታ አስይዝ"),
                Map.entry("card_close_reserve_btn", "📦 ማስያዣ ዝጋ"),
                Map.entry("card_details_btn", "ℹ️ ዝርዝር"),
                Map.entry("card_rate_btn", "⭐ ደምድብ"),
                Map.entry("card_save_btn", "❤️ አስቀምጥ"),
                Map.entry("card_saved_btn", "✅ ተቀምጧล"),
                Map.entry("card_view_pharmacy_photo_btn", "🖼 የፋርማሲ ፎቶ እይ"),
                Map.entry("card_view_medicine_photos_btn", "🖼 የመድሃኒት ፎቶዎችን እይ"),
                Map.entry("card_hide_details_btn", "🔽 ዝርዝር ደብቅ"),
                Map.entry("card_report_btn", "⚠️ ችግር ሪፖርት አድርግ"),
                Map.entry("card_reserve_matched_btn", "📦 ተዛምዶ ፋርማሲ ያስይዙ"),
                Map.entry("card_reserve_one_matched_btn", "📦 አንድ ተዛምዶ ያስይዙ"),
                Map.entry("card_multi_reserve_btn", "🧺 ብዙ መድሃኒቶች ያስይዙ"),
                Map.entry("card_reserve_all_later_btn", "🧺 ሁሉን ቆጣቢ"),
                Map.entry("multi_reserve_unavailable_title", "🚧 <b>ብዙ መድሃኒቶች ያስያዣ</b>"),
                Map.entry("multi_reserve_unavailable_msg", "ብዙ መድሃኒቶችን በአንድ ጥያቄ ማስያዝ አሁን አልተቻለም።\n\nለአሁን <b>አንድ ተዛምዶ ያስይዙ</b> ይጠቀሙ በአንድ ጊዜ አንድ ጊዜ መድሃኒት ለማስያዝ።"),
                Map.entry("issue_report_choose_type", "የችግሩን አይነት ይምረጡ"),
                Map.entry("issue_type_price", "💰 የተሳሳተ ዋጋ"),
                Map.entry("issue_type_stock", "📦 የተሳሳተ ስቶክ"),
                Map.entry("issue_type_location", "📍 የተሳሳተ አካባቢ"),
                Map.entry("issue_type_service", "🧾 ደካማ አገልግሎት"),
                Map.entry("issue_type_stock_short", "⚠️ ስቶክ የለም"),
                Map.entry("issue_type_phone_short", "⚠️ የተሳሳተ ስልክ"),
                Map.entry("issue_type_closed_short", "⚠️ ፋርማሲው ዝጋ ነው"),
                Map.entry("issue_type_location_short", "📍 የተሳሳተ አካባቢ"),
                Map.entry("issue_type_service_short", "🧾 ደካማ አገልግሎት"),
                Map.entry("issue_type_other", "✍️ ሌላ"),
                Map.entry("issue_type_cancel", "❌ ሰርዝ"),
                Map.entry("issue_menu_title", "⚠️ ችግር ሪፖርት አድርግ"),
                Map.entry("issue_menu_close", "🔽 ዝጋ"),
                Map.entry("issue_report_prompt_other", "✍️ የችግሩን ዝርዝር ይጻፉ።"),
                Map.entry("issue_report_received", "✅ ሪፖርቱ ተልኳል። እናመሰግናለን።"),
                Map.entry("issue_already_reported", "⚠️ ይህን ችግር አስቀድመው ሪፖርት አድርገዋል። ድጋሚ ከማሪፖርትዎ በፊት ትንሽ ይጠብቁ。"),
                Map.entry("btn_other", "✍️ ሌላ"),
                Map.entry("btn_back", "⬅️ ተመለስ"),
                Map.entry("btn_main", "🏠 ዋናው ገጽ"),
                Map.entry("btn_cancel", "❌ ሰርዝ"),
                Map.entry("btn_rated", "✅ ደምድቧታል"),
                Map.entry("btn_use_saved_location", "📍 ተቀምጦ ያለ አካባቢ ጠቀም"),
                Map.entry("btn_share_current_location", "📌 አሁን ያለህ አካባቢ አጋራ"),
                Map.entry("btn_search_pharmacies", "🔍 ፋርማሲዎች ፈልግ"),
                Map.entry("btn_add_more", "➕ ተጨማሪ ጨምር"),
                Map.entry("btn_clear", "🗑 አጽዳ"),
                Map.entry("btn_change_location", "📍 አካባቢ ቀይር"),
                Map.entry("btn_notify_available", "🔔 ሲኖር አሳውቀኝ"),
                Map.entry("btn_home", "🏠 ዋናው ገጽ"),
                Map.entry("medicine_suggestion_picker_title", "💊 <b>ለዚህ የተጠቆሙ መድሃኒቶች:</b> %s\nከታች አንዱን ይምረጡ ወይም የጻፉትን ይጠቀሙ።"),
                Map.entry("medicine_suggestion_alternative_hint", "💡 ተመሳሳይ ወይም ተለዋጭ መድሃኒቶችም ከታች ተካትተዋል።"),
                Map.entry("medicine_suggestion_use_typed", "✅ \"%s\" ተጠቀም"),
                Map.entry("medicine_suggestion_no_exact", "❌ <b>ትክክለኛ ተዛማጅ አልተገኘም ለ:</b> %s"),
                Map.entry("medicine_suggestion_did_you_mean", "💊 <b>ከእነዚህ አንዱን ማለትዎ ነው?</b>"),
                Map.entry("medicine_suggestion_alternatives_title", "💡 <b>ሊተኩ የሚችሉ አማራጮች:</b>"),
                Map.entry("medicine_suggestion_notify_for", "🔔 ለ%s ሲኖር አሳውቀኝ"),
                Map.entry("medicine_no_pharmacies_found", "❌ <b>ለዚህ ምንም ፋርማሲ አልተገኘም:</b> %s\n\nሲገኝ እንዲያሳውቅዎ ማስጠንቀቂያ መፍጠር ይችላሉ።"),
                Map.entry("btn_refresh", "🔄 አድስ"),
                Map.entry("btn_favorite_pharmacies", "❤️ ተወዳጅ ፋርማሲዎች"),
                Map.entry("btn_profile", "⚙️ ፕሮፋይል"),
                Map.entry("btn_remove", "🗑 አስወግድ"),
                Map.entry("btn_remove_all_alerts", "🗑 ሁሉም ማስጣንቀቂያዎች አስወግድ"),
                Map.entry("btn_remove_alert", "❌ ማስጣንቀቂያ አስወግድ"),
                Map.entry("btn_search_now", "🔎 አሁን ፈልግ"),
                Map.entry("card_pharmacy_details_title", "የፋርማሲ ዝርዝር"),
                Map.entry("card_name_label", "ስም:"),
                Map.entry("card_medicine_label", "መድሃኒት:"),
                Map.entry("card_address_label", "አድራሻ:"),
                Map.entry("card_exact_address_label", "ትክክለኛ አድራሻ:"),
                Map.entry("card_landmark_label", "መለያ ቦታ:"),
                Map.entry("card_plus_code_label", "ፕላስ ኮድ:"),
                Map.entry("card_phone_label", "ስልክ:"),
                Map.entry("card_distance_label", "ርቀት:"),
                Map.entry("card_rating_label", "ደረጃ:"),
                Map.entry("card_price_label", "ዋጋ:"),
                Map.entry("card_hours_label", "ሰዓት:"),
                Map.entry("card_status_label", "ሁኔታ:"),
                Map.entry("card_stock_label", "ስቶክ:"),
                Map.entry("card_last_stock_update_label", "የመጨረሻ የስቶክ ዝማኔ:"),
                Map.entry("reservation_blocked_temp_closed", "🚫 ይህ ፋርማሲ ጊዜያዊ ዝግ ነው።\n\nአሁን ቦታ ማስያዝ አይቻልም።%n%s"),
                // Reservation history card
                Map.entry("res_hist_title", "📜 <b>የቦታ ማስያዣ ታሪክ</b>"),
                Map.entry("res_hist_empty", "📜 <b>የቦታ ማስያዣ ታሪክ</b>\n\nምንም ቦታ ማስያዣ አልተገኘም።"),
                Map.entry("res_hist_section_pending", "⏳ በመጠባበቅ ላይ"),
                Map.entry("res_hist_section_approved", "✅ የጸደቀ"),
                Map.entry("res_hist_section_fulfilled", "📦 የተጠናቀቀ"),
                Map.entry("res_hist_section_cancelled", "❌ የተሰረዘ"),
                Map.entry("res_hist_section_expired", "⌛ ያለፈበት"),
                Map.entry("res_hist_section_rejected", "🚫 የቀረ"),
                Map.entry("res_hist_hold_until", "እስከ"),
                Map.entry("res_hist_reason", "ምክንያት"),
                Map.entry("res_status_pending", "በመጠባበቅ ላይ"),
                Map.entry("res_status_approved", "ጸደቀ"),
                Map.entry("res_status_fulfilled", "ተጠናቀቀ"),
                Map.entry("res_status_cancelled", "ተሰረዘ"),
                Map.entry("res_status_expired", "ያለፈበት"),
                                Map.entry("res_status_rejected", "ቀረ"),
                                Map.entry("res_card_id_label", "መታወቂያ:"),
                                Map.entry("res_card_pharmacy_label", "ፋርማሲ:"),
                                Map.entry("res_card_quantity_label", "ብዛት:"),
                                Map.entry("res_card_reserve_again_btn", "🔁 እንደገና አስይዝ"),
                                Map.entry("res_section_reserve_latest_btn", "🔁 ቅርብ እንደገና አስይዝ")
        );
    }
}