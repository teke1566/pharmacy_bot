package com.tenahub.bot.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class EthiopiaLocationTranslator {

    private static final Map<String, String> ENGLISH_TO_AMHARIC = buildMappings();
    private static final Map<String, String> AMHARIC_TO_ENGLISH = buildReverseMappings();

    private EthiopiaLocationTranslator() {
    }

    public static String toDisplayValue(String value, BotLanguage language) {
        if (language != BotLanguage.AMHARIC || value == null || value.isBlank()) {
            return value;
        }

        return ENGLISH_TO_AMHARIC.getOrDefault(normalize(value), value);
    }

    public static String toDisplayAddress(String value, BotLanguage language) {
        if (language != BotLanguage.AMHARIC || value == null || value.isBlank()) {
            return value;
        }

        return Arrays.stream(value.split("\\s*,\\s*"))
                .map(part -> toDisplayValue(part, language))
                .collect(Collectors.joining(", "));
    }

    public static String toCanonicalValue(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        return AMHARIC_TO_ENGLISH.getOrDefault(normalize(value), value);
    }

    public static String toCanonicalAddress(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        return Arrays.stream(value.split("\\s*,\\s*"))
                .map(EthiopiaLocationTranslator::toCanonicalValue)
                .collect(Collectors.joining(", "));
    }

    private static Map<String, String> buildMappings() {
        Map<String, String> mappings = new HashMap<>();

        mappings.put("addis ababa", "አዲስ አበባ");
        mappings.put("afar", "አፋር");
        mappings.put("amhara", "አማራ");
        mappings.put("benishangul-gumuz", "ቤንሻንጉል-ጉሙዝ");
        mappings.put("central ethiopia", "ማዕከላዊ ኢትዮጵያ");
        mappings.put("dire dawa", "ድሬዳዋ");
        mappings.put("gambela", "ጋምቤላ");
        mappings.put("harari", "ሐረሪ");
        mappings.put("oromia", "ኦሮሚያ");
        mappings.put("sidama", "ሲዳማ");
        mappings.put("somali", "ሶማሌ");
        mappings.put("south ethiopia", "ደቡብ ኢትዮጵያ");
        mappings.put("south west ethiopia peoples' region", "ደቡብ ምዕራብ ኢትዮጵያ ህዝቦች ክልል");
        mappings.put("tigray", "ትግራይ");

        mappings.put("adama", "አዳማ");
        mappings.put("adigrat", "አዲግራት");
        mappings.put("alamata", "አላማጣ");
        mappings.put("aleta wendo", "አለታ ወንዶ");
        mappings.put("ambo", "አምቦ");
        mappings.put("arba minch", "አርባ ምንጭ");
        mappings.put("areka", "አረካ");
        mappings.put("asayita", "አሳይታ");
        mappings.put("asella", "አሰላ");
        mappings.put("asosa", "አሶሳ");
        mappings.put("awash", "አዋሽ");
        mappings.put("axum", "አክሱም");
        mappings.put("bahir dar", "ባህር ዳር");
        mappings.put("bale robe", "ባሌ ሮቤ");
        mappings.put("bambasi", "ባምባሲ");
        mappings.put("bishoftu", "ቢሾፍቱ");
        mappings.put("bonga", "ቦንጋ");
        mappings.put("burayu", "ቡራዩ");
        mappings.put("butajira", "ቡታጅራ");
        mappings.put("debre birhan", "ደብረ ብርሃን");
        mappings.put("debre markos", "ደብረ ማርቆስ");
        mappings.put("debre tabor", "ደብረ ታቦር");
        mappings.put("degahbur", "ደጋህቡር");
        mappings.put("dessie", "ደሴ");
        mappings.put("dilla", "ዲላ");
        mappings.put("dukem", "ዱከም");
        mappings.put("gelan", "ገላን");
        mappings.put("gilgel beles", "ጊልገል በለስ");
        mappings.put("goba", "ጎባ");
        mappings.put("gode", "ጎዴ");
        mappings.put("gondar", "ጎንደር");
        mappings.put("harar", "ሐረር");
        mappings.put("hawassa", "ሐዋሳ");
        mappings.put("holeta", "ሆለታ");
        mappings.put("hosaena", "ሆሳዕና");
        mappings.put("humera", "ሁመራ");
        mappings.put("itang", "ኢታንግ");
        mappings.put("jigjiga", "ጅግጅጋ");
        mappings.put("jimma", "ጅማ");
        mappings.put("jinka", "ጂንካ");
        mappings.put("karat", "ካራት");
        mappings.put("kebri dehar", "ቀብሪ ደሃር");
        mappings.put("kobo", "ቆቦ");
        mappings.put("kombolcha", "ኮምቦልቻ");
        mappings.put("logiya", "ሎጊያ");
        mappings.put("mekelle", "መቀሌ");
        mappings.put("metema", "መተማ");
        mappings.put("mizan teferi", "ሚዛን ተፈሪ");
        mappings.put("mojo", "ሞጆ");
        mappings.put("nekemte", "ነቀምት");
        mappings.put("sawla", "ሳውላ");
        mappings.put("sebeta", "ሰበታ");
        mappings.put("sekota", "ሰቆጣ");
        mappings.put("semera", "ሰመራ");
        mappings.put("shashemene", "ሻሸመኔ");
        mappings.put("shire", "ሽሬ");
        mappings.put("sululta", "ሱሉልታ");
        mappings.put("tepi", "ቴፒ");
        mappings.put("warder", "ዋርደር");
        mappings.put("welkite", "ወልቂጤ");
        mappings.put("wolaita sodo", "ወላይታ ሶዶ");
        mappings.put("woldia", "ወልዲያ");
        mappings.put("worabe", "ወራቤ");
        mappings.put("yirgalem", "ይርጋለም");
        mappings.put("ziway", "ዝዋይ");

        mappings.put("addis ketema", "አዲስ ከተማ");
        mappings.put("akaky kaliti", "አቃቂ ቃሊቲ");
        mappings.put("gullele", "ጉለሌ");
        mappings.put("kirkos", "ቂርቆስ");
        mappings.put("kolfe keranio", "ኮልፌ ቀራኒዮ");
        mappings.put("lideta", "ልደታ");
        mappings.put("nifas silk lafto", "ንፋስ ስልክ ላፍቶ");
        mappings.put("yeka", "የካ");
        mappings.put("lemi kura", "ለሚ ኩራ");

        mappings.put("01 area", "01 አካባቢ");
        mappings.put("4 kilo", "4 ኪሎ");
        mappings.put("6 kilo", "6 ኪሎ");
        mappings.put("aboker", "አቦከር");
        mappings.put("abosto", "አቦስቶ");
        mappings.put("adama center", "አዳማ ማዕከል");
        mappings.put("adigrat center", "አዲግራት ማዕከል");
        mappings.put("administration area", "አስተዳደር አካባቢ");
        mappings.put("agip", "አጂፕ");
        mappings.put("airport", "አየር ማረፊያ");
        mappings.put("akaki", "አቃቂ");
        mappings.put("alamata center", "አላማጣ ማዕከል");
        mappings.put("alelu", "አሌሉ");
        mappings.put("alem bank", "አለም ባንክ");
        mappings.put("alem gena", "አለም ገና");
        mappings.put("aleta wendo center", "አለታ ወንዶ ማዕከል");
        mappings.put("ambo center", "አምቦ ማዕከል");
        mappings.put("arada", "አራዳ");
        mappings.put("arat kilo", "አራት ኪሎ");
        mappings.put("arba minch center", "አርባ ምንጭ ማዕከል");
        mappings.put("areka center", "አረካ ማዕከል");
        mappings.put("argob bari", "አርጎብ በር");
        mappings.put("asayita center", "አሳይታ ማዕከል");
        mappings.put("asella center", "አሰላ ማዕከል");
        mappings.put("ashewa meda", "አሸዋ ሜዳ");
        mappings.put("asko", "አስኮ");
        mappings.put("asosa center", "አሶሳ ማዕከል");
        mappings.put("awaro", "አዋሮ");
        mappings.put("awash center", "አዋሽ ማዕከል");
        mappings.put("axum center", "አክሱም ማዕከል");
        mappings.put("ayat", "አያት");
        mappings.put("ayat roundabout", "አያት አደባባይ");
        mappings.put("ayder", "አይደር");
        mappings.put("ayer tena", "አየር ጤና");
        mappings.put("azezo", "አዘዞ");
        mappings.put("babogaya", "ባቦጋያ");
        mappings.put("bahir dar center", "ባህር ዳር ማዕከል");
        mappings.put("bake jama", "ባቄ ጃማ");
        mappings.put("bambasi center", "ባምባሲ ማዕከል");
        mappings.put("bambis", "ባምቢስ");
        mappings.put("bishoftu center", "ቢሾፍቱ ማዕከል");
        mappings.put("bole", "ቦሌ");
        mappings.put("bole area", "ቦሌ አካባቢ");
        mappings.put("bole medhanialem", "ቦሌ መድሃኒዓለም");
        mappings.put("bole rwanda", "ቦሌ ሩዋንዳ");
        mappings.put("bonga center", "ቦንጋ ማዕከል");
        mappings.put("buanbuawuha", "ቡአንቡአውሃ");
        mappings.put("bulchana", "ቡልቻና");
        mappings.put("burayu center", "ቡራዩ ማዕከል");
        mappings.put("burka jato", "ቡርቃ ጃቶ");
        mappings.put("butajira center", "ቡታጅራ ማዕከል");
        mappings.put("cmc", "ሲኤምሲ");
        mappings.put("debre birhan center", "ደብረ ብርሃን ማዕከል");
        mappings.put("debre markos center", "ደብረ ማርቆስ ማዕከል");
        mappings.put("debre tabor center", "ደብረ ታቦር ማዕከል");
        mappings.put("degahbur center", "ደጋህቡር ማዕከል");
        mappings.put("dembela", "ደምበላ");
        mappings.put("dessie center", "ደሴ ማዕከል");
        mappings.put("dilla center", "ዲላ ማዕከል");
        mappings.put("dire dawa center", "ድሬዳዋ ማዕከል");
        mappings.put("dukem center", "ዱከም ማዕከል");
        mappings.put("entoto", "እንጦጦ");
        mappings.put("gambela center", "ጋምቤላ ማዕከል");
        mappings.put("geda", "ገዳ");
        mappings.put("gefersa", "ገፈርሳ");
        mappings.put("gelan center", "ገላን ማዕከል");
        mappings.put("gende kore", "ገንደ ኮሬ");
        mappings.put("gerji", "ገርጂ");
        mappings.put("gilgel beles center", "ጊልገል በለስ ማዕከል");
        mappings.put("goba center", "ጎባ ማዕከል");
        mappings.put("gode center", "ጎዴ ማዕከል");
        mappings.put("gofa", "ጎፋ");
        mappings.put("gondar center", "ጎንደር ማዕከል");
        mappings.put("gotera", "ጎተራ");
        mappings.put("gudumale", "ጉዱማሌ");
        mappings.put("gurd shola", "ጉርድ ሾላ");
        mappings.put("haik dar", "ሐይቅ ዳር");
        mappings.put("harar center", "ሐረር ማዕከል");
        mappings.put("hawassa center", "ሐዋሳ ማዕከል");
        mappings.put("hawelti", "ሐወልቲ");
        mappings.put("hayat", "ሐያት");
        mappings.put("hermata", "ሄርማታ");
        mappings.put("holeta center", "ሆለታ ማዕከል");
        mappings.put("hora", "ሆራ");
        mappings.put("hosaena center", "ሆሳዕና ማዕከል");
        mappings.put("humera center", "ሁመራ ማዕከል");
        mappings.put("itang center", "ኢታንግ ማዕከል");
        mappings.put("jegol", "ጀጎል");
        mappings.put("jemo", "ጄሞ");
        mappings.put("jigjiga center", "ጅግጅጋ ማዕከል");
        mappings.put("jimma center", "ጅማ ማዕከል");
        mappings.put("jinka center", "ጂንካ ማዕከል");
        mappings.put("jiren", "ጅሬን");
        mappings.put("karat center", "ካራት ማዕከል");
        mappings.put("kazanchis", "ካዛንቺስ");
        mappings.put("kebele 03", "ቀበሌ 03");
        mappings.put("kebele 14", "ቀበሌ 14");
        mappings.put("kebele 16", "ቀበሌ 16");
        mappings.put("kebri dehar center", "ቀብሪ ደሃር ማዕከል");
        mappings.put("kedamay weyane", "ቀዳማይ ወያኔ");
        mappings.put("kera", "ቄራ");
        mappings.put("kezira", "ከዚራ");
        mappings.put("kobo center", "ቆቦ ማዕከል");
        mappings.put("kombolcha center", "ኮምቦልቻ ማዕከል");
        mappings.put("kotebe", "ኮተቤ");
        mappings.put("kuriftu area", "ኩሪፍቱ አካባቢ");
        mappings.put("lafto", "ላፍቶ");
        mappings.put("lake side", "ሐይቅ ዳርቻ");
        mappings.put("lamberet", "ላምበሬት");
        mappings.put("lebu", "ለቡ");
        mappings.put("legehare", "ለገሃር");
        mappings.put("logiya center", "ሎጊያ ማዕከል");
        mappings.put("market area", "ገበያ አካባቢ");
        mappings.put("mebrat hail", "መብራት ሐይል");
        mappings.put("megala", "መጋላ");
        mappings.put("megenagna", "መገናኛ");
        mappings.put("mekanisa", "መካኒሳ");
        mappings.put("mekelle center", "መቀሌ ማዕከል");
        mappings.put("menaharia", "መናሃሪያ");
        mappings.put("menehariya", "መነሃሪያ");
        mappings.put("meri", "መሪ");
        mappings.put("merkato", "መርካቶ");
        mappings.put("metema center", "መተማ ማዕከል");
        mappings.put("mexico", "ሜክሲኮ");
        mappings.put("mizan center", "ሚዛን ማዕከል");
        mappings.put("mojo center", "ሞጆ ማዕከል");
        mappings.put("nekemte center", "ነቀምት ማዕከል");
        mappings.put("piassa", "ፒያሳ");
        mappings.put("piazza", "ፒያዛ");
        mappings.put("pinyudo road area", "ፒንዩዶ መንገድ አካባቢ");
        mappings.put("poly area", "ፖሊ አካባቢ");
        mappings.put("quiha", "ቁሓ");
        mappings.put("robe center", "ሮቤ ማዕከል");
        mappings.put("sabian", "ሳቢያን");
        mappings.put("sarbet", "ሳር ቤት");
        mappings.put("sawla center", "ሳውላ ማዕከል");
        mappings.put("sebeta center", "ሰበታ ማዕከል");
        mappings.put("sebeta hawas", "ሰበታ ሐዋስ");
        mappings.put("sekota center", "ሰቆጣ ማዕከል");
        mappings.put("semera center", "ሰመራ ማዕከል");
        mappings.put("semit", "ሰሚት");
        mappings.put("seto", "ሴቶ");
        mappings.put("shashemene center", "ሻሸመኔ ማዕከል");
        mappings.put("shecha", "ሸቻ");
        mappings.put("sheik ali jowhar", "ሼክ አሊ ጆሐር");
        mappings.put("shire center", "ሽሬ ማዕከል");
        mappings.put("shiromeda", "ሽሮ ሜዳ");
        mappings.put("sidist kilo", "ስድስት ኪሎ");
        mappings.put("sikela", "ሲቀላ");
        mappings.put("stadium", "ስታዲየም");
        mappings.put("sululta center", "ሱሉልታ ማዕከል");
        mappings.put("summit", "ሳሚት");
        mappings.put("sunshine", "ሰንሻይን");
        mappings.put("tabor", "ታቦር");
        mappings.put("tepi center", "ቴፒ ማዕከል");
        mappings.put("torhailoch", "ጦር ሃይሎች");
        mappings.put("tulu dimtu", "ቱሉ ዲምቱ");
        mappings.put("warder center", "ዋርደር ማዕከል");
        mappings.put("welkite center", "ወልቂጤ ማዕከል");
        mappings.put("wello sefer", "ወሎ ሰፈር");
        mappings.put("wolaita sodo center", "ወላይታ ሶዶ ማዕከል");
        mappings.put("woldia center", "ወልዲያ ማዕከል");
        mappings.put("worabe center", "ወራቤ ማዕከል");
        mappings.put("yerer", "የረር");
        mappings.put("yirgalem center", "ይርጋለም ማዕከል");
        mappings.put("zenzelima", "ዘንዘሊማ");
        mappings.put("ziway center", "ዝዋይ ማዕከል");

        return Collections.unmodifiableMap(mappings);
    }

    private static String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static Map<String, String> buildReverseMappings() {
        Map<String, String> reverse = new HashMap<>();

        ENGLISH_TO_AMHARIC.forEach((english, amharic) -> reverse.put(normalize(amharic), english));

        return Collections.unmodifiableMap(reverse);
    }
}