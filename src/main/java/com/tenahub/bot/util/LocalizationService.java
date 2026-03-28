package com.tenahub.bot.util;

import com.tenahub.bot.entity.BotTranslation;
import com.tenahub.bot.repository.BotTranslationRepository;
import com.tenahub.bot.service.UserLocationService;
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

    private final UserLocationService userLocationService;
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
            if (existing.isEmpty()) {
                Map<String, String> defaults = STATIC_TEXTS.getOrDefault(lang, STATIC_TEXTS.get(BotLanguage.ENGLISH));
                List<BotTranslation> toSave = defaults.entrySet().stream()
                        .map(e -> BotTranslation.builder()
                                .languageCode(lang.getCode())
                                .translationKey(e.getKey())
                                .value(e.getValue())
                                .build())
                        .collect(Collectors.toList());
                translationRepository.saveAll(toSave);
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
        Map<String, String> localized = cache.isEmpty()
                ? STATIC_TEXTS.getOrDefault(language, STATIC_TEXTS.get(BotLanguage.ENGLISH))
                : cache.getOrDefault(language, cache.get(BotLanguage.ENGLISH));
        String template = localized != null ? localized.get(key) : null;
        if (template == null) {
            Map<String, String> englishMap = cache.isEmpty()
                    ? STATIC_TEXTS.get(BotLanguage.ENGLISH)
                    : cache.get(BotLanguage.ENGLISH);
            template = englishMap != null ? englishMap.getOrDefault(key, key) : key;
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
                Map.entry("feedback_prompt", "📝 Please type your feedback.\n\nWe will use it to improve TenaHub."),
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
                Map.entry("reg_phone_prompt", "🏥 <b>Pharmacy Registration</b>\n\nStep 2/7\n📞 Enter phone number\nExample: 0912345678\n\nOr tap <b>Share Phone Number</b> below."),
                Map.entry("reg_license_step", "📄 Now upload your pharmacy license (photo, PDF, DOC, or other document)."),
                Map.entry("reg_license_expiry_step", "📅 Enter your license expiry date in <b>YYYY-MM-DD</b> format.\nExample: 2027-12-31\n\nOr tap <b>📅 Pick Date</b> from the bottom keyboard."),
                Map.entry("share_phone_number_button", "📱 Share Phone Number"),
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
                Map.entry("card_open_now", "🟢 Open now"),
                Map.entry("card_closed", "🔴 Closed"),
                Map.entry("card_out_of_stock", "❌ Out of stock"),
                Map.entry("card_available", "✅ Available: %d left"),
                Map.entry("card_status_open", "Open now"),
                Map.entry("card_status_closed", "Closed"),
                Map.entry("card_stock_out", "Out of stock"),
                Map.entry("card_stock_left", "%d left"),
                Map.entry("card_hours_not_set", "Not set"),
                Map.entry("card_price_not_set", "not set"),
                Map.entry("card_navigate_btn", "🧭 Navigate"),
                Map.entry("card_reserve_btn", "📦 Reserve"),
                Map.entry("card_close_reserve_btn", "📦 Close Reserve"),
                Map.entry("card_details_btn", "ℹ️ Details"),
                Map.entry("card_rate_btn", "⭐ Rate"),
                Map.entry("card_save_btn", "❤️ Save"),
                Map.entry("card_saved_btn", "✅ Saved"),
                Map.entry("card_hide_details_btn", "🔽 Hide Details"),
                Map.entry("card_report_btn", "⚠️ Report issue"),
                Map.entry("card_reserve_matched_btn", "📦 Reserve Matched"),
                Map.entry("card_reserve_all_later_btn", "🧺 Reserve All Later")
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
                Map.entry("feedback_prompt", "📝 እባክዎ አስተያየትዎን ይጻፉ።\n\nአገልግሎቱን ለማሻሻል እንጠቀምበታለን።"),
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
                Map.entry("reg_phone_prompt", "🏥 <b>ፋርማሲ ምዝገባ</b>\n\nደረጃ 2/7\n📞 ስልክ ቁጥር ያስገቡ\nምሳሌ: 0912345678\n\nወይም ከታch <b>ስልክ ቁጥር አጋራ</b> ይንኩ።"),
                Map.entry("reg_license_step", "📄 አሁን የፋርማሲዎ ፈቃድ ያስሰቅሉ (ፎቶ፣ PDF፣ DOC ወይም ሌላ ሰነድ)።"),
                Map.entry("reg_license_expiry_step", "📅 የፈቃዱ ማብቂያ ቀን <b>YYYY-MM-DD</b> ቅርጸት ያስገቡ።\nምሳሌ: 2027-12-31\n\nወይም ከታch <b>📅 ቀን ምረጥ</b> ይንኩ።"),
                Map.entry("share_phone_number_button", "📱 ስልክ ቁጥር አጋራ"),
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
                Map.entry("card_open_now", "🟢 ክፍት ነው"),
                Map.entry("card_closed", "🔴 ተዘግቷል"),
                Map.entry("card_out_of_stock", "❌ አልቋል"),
                Map.entry("card_available", "✅ ይገኛል: %d ቀርቷል"),
                Map.entry("card_status_open", "ክፍት ነው"),
                Map.entry("card_status_closed", "ተዘግቷል"),
                Map.entry("card_stock_out", "አልቋል"),
                Map.entry("card_stock_left", "%d ቀርቷል"),
                Map.entry("card_hours_not_set", "አልተዋቀረም"),
                Map.entry("card_price_not_set", "አልተወሰነም"),
                Map.entry("card_navigate_btn", "🧭 አቅጣጫ"),
                Map.entry("card_reserve_btn", "📦 ቦታ አስይዝ"),
                Map.entry("card_close_reserve_btn", "📦 ማስያዣ ዝጋ"),
                Map.entry("card_details_btn", "ℹ️ ዝርዝር"),
                Map.entry("card_rate_btn", "⭐ ደምድብ"),
                Map.entry("card_save_btn", "❤️ አስቀምጥ"),
                Map.entry("card_saved_btn", "✅ ተቀምጧล"),
                Map.entry("card_hide_details_btn", "🔽 ዝርዝር ደብቅ"),
                Map.entry("card_report_btn", "⚠️ ችግር ሪፖርት አድርግ"),
                Map.entry("card_reserve_matched_btn", "📦 ተዛምዶ ፋርማሲ ያስይዙ"),
                Map.entry("card_reserve_all_later_btn", "🧺 ሁሉን ቆጣቢ")
        );
    }
}