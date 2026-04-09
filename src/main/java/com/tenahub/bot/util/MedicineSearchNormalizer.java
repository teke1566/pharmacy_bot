package com.tenahub.bot.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class MedicineSearchNormalizer {

   private static final Map<String, String> AMHARIC_TO_ENGLISH = Map.ofEntries(
        Map.entry("ኢንሱሊን", "insulin"),
        Map.entry("ፓራሲታሞል", "paracetamol"),
        Map.entry("ፐራሲታሞል", "paracetamol"),
        Map.entry("ፓራሰታሞል", "paracetamol"),
        Map.entry("አሞክሲሲሊን", "amoxicillin"),
        Map.entry("አዚትሮማይሲን", "azithromycin"),
        Map.entry("ኢቡፕሮፌን", "ibuprofen"),
        Map.entry("ሜትፎርሚን", "metformin"),
        Map.entry("አስፕሪን", "aspirin"),
        Map.entry("ኦሜፕራዞል", "omeprazole"),
        Map.entry("ዲክሎፍናክ", "diclofenac"),
        Map.entry("ሴፍትሪያክሶን", "ceftriaxone"),
        Map.entry("ኦአርኤስ", "ors"),
        Map.entry("ዚንክ", "zinc"),

        Map.entry("ሳሊን", "saline"),
        Map.entry("ጂንታማይሲን", "gentamicin"),
        Map.entry("ሴፍክሲም", "cefixime"),
        Map.entry("ሴፋሌክሲን", "cephalexin"),
        Map.entry("ሲፕሮፍሎክሳሲን", "ciprofloxacin"),
        Map.entry("ዶክሲሳይክሊን", "doxycycline"),
        Map.entry("ክሎራምፈኒኮል", "chloramphenicol"),
        Map.entry("ኒስታቲን", "nystatin"),
        Map.entry("ፍሉኮናዞል", "fluconazole"),
        Map.entry("አልበንዳዞል", "albendazole"),
        Map.entry("ሜትሮኒዳዞል", "metronidazole"),
        Map.entry("ሎፔራሚድ", "loperamide"),
        Map.entry("ራኒቲዲን", "ranitidine"),
        Map.entry("ፋሞቲዲን", "famotidine"),
        Map.entry("ሳልቡታሞል", "salbutamol"),
        Map.entry("ሀይድሮኮርቲዞን", "hydrocortisone"),
        Map.entry("ፕሬድኒሶሎን", "prednisolone"),
        Map.entry("ክሎርፌኒራሚን", "chlorpheniramine"),
        Map.entry("ሴቲሪዚን", "cetirizine"),
        Map.entry("ሎራታዲን", "loratadine"),
        Map.entry("አምሎዲፒን", "amlodipine"),
        Map.entry("ኤናላፕሪል", "enalapril"),
        Map.entry("ሎሳርታን", "losartan"),
        Map.entry("አቴኖሎል", "atenolol"),
        Map.entry("ሲምቫስታቲን", "simvastatin"),
        Map.entry("ኢንሱሊን ግላርጂን", "insulin glargine"),
        Map.entry("ኢንሱሊን ሬግዩላር", "regular insulin"),

        // More added
        Map.entry("ክላቪላኒክ አሲድ", "clavulanic acid"),
        Map.entry("አሞክሲክላቭ", "amoxiclav"),
        Map.entry("ኮትሪሞክሳዞል", "co-trimoxazole"),
        Map.entry("ትሪሜቶፕሪም", "trimethoprim"),
        Map.entry("ሱልፋሜቶክሳዞል", "sulfamethoxazole"),
        Map.entry("ክሊንዳማይሲን", "clindamycin"),
        Map.entry("ኤሪትሮማይሲን", "erythromycin"),
        Map.entry("ክላሪትሮማይሲን", "clarithromycin"),
        Map.entry("ሌቮፍሎክሳሲን", "levofloxacin"),
        Map.entry("ሞክሲፍሎክሳሲን", "moxifloxacin"),
        Map.entry("ፔኒሲሊን", "penicillin"),
        Map.entry("ቤንዚል ፔኒሲሊን", "benzylpenicillin"),
        Map.entry("ፊኖክሲሜቲል ፔኒሲሊን", "phenoxymethylpenicillin"),
        Map.entry("ፒፔራሲሊን", "piperacillin"),
        Map.entry("ታዞባክታም", "tazobactam"),
        Map.entry("ሜሮፔኔም", "meropenem"),
        Map.entry("ኢሚፔኔም", "imipenem"),
        Map.entry("ቫንኮማይሲን", "vancomycin"),
        Map.entry("ሊኔዞሊድ", "linezolid"),

        Map.entry("አርቴሱኔት", "artesunate"),
        Map.entry("አርቴሜተር", "artemether"),
        Map.entry("ሉሜፋንትሪን", "lumefantrine"),
        Map.entry("ኩዊኒን", "quinine"),
        Map.entry("ክሎሮኩዊን", "chloroquine"),
        Map.entry("ፕሪማኩዊን", "primaquine"),

        Map.entry("ሜቤንዳዞል", "mebendazole"),
        Map.entry("ፕራዚኳንቴል", "praziquantel"),
        Map.entry("አይቨርሜክቲን", "ivermectin"),

        Map.entry("ኬቶኮናዞል", "ketoconazole"),
        Map.entry("ክሎትሪማዞል", "clotrimazole"),
        Map.entry("ሚኮናዞል", "miconazole"),
        Map.entry("ቴርቢናፊን", "terbinafine"),
        Map.entry("አሲክሎቪር", "acyclovir"),

        Map.entry("ካልሲየም", "calcium"),
        Map.entry("ፎሊክ አሲድ", "folic acid"),
        Map.entry("ብረት", "iron"),
        Map.entry("ቫይታሚን ኤ", "vitamin a"),
        Map.entry("ቫይታሚን ቢ", "vitamin b"),
        Map.entry("ቫይታሚን ሲ", "vitamin c"),
        Map.entry("ቫይታሚን ዲ", "vitamin d"),
        Map.entry("ቫይታሚን ኢ", "vitamin e"),
        Map.entry("መልቲቫይታሚን", "multivitamin"),

        Map.entry("ግሊቤንክላሚድ", "glibenclamide"),
        Map.entry("ግሊሜፒራይድ", "glimepiride"),
        Map.entry("ኢንሱሊን ኤንፒኤች", "nph insulin"),

        Map.entry("ፉሮሴማይድ", "furosemide"),
        Map.entry("ሀይድሮክሎሮትያዛይድ", "hydrochlorothiazide"),
        Map.entry("ስፒሮኖላክቶን", "spironolactone"),
        Map.entry("ካፕቶፕሪል", "captopril"),
        Map.entry("ሊሲኖፕሪል", "lisinopril"),
        Map.entry("ኒፊዲፒን", "nifedipine"),
        Map.entry("ፕሮፕራኖሎል", "propranolol"),
        Map.entry("ሜቶፕሮሎል", "metoprolol"),
        Map.entry("አቶርቫስታቲን", "atorvastatin"),

        Map.entry("ግሊሰሪን", "glycerin"),
        Map.entry("ላክቱሎዝ", "lactulose"),
        Map.entry("ቢሳኮዲል", "bisacodyl"),
        Map.entry("ሴና", "senna"),
        Map.entry("ዶምፔሪዶን", "domperidone"),
        Map.entry("ኦንዳንሴትሮን", "ondansetron"),
        Map.entry("አሉሚኒየም ሀይድሮክሳይድ", "aluminium hydroxide"),
        Map.entry("ማግኒዥየም ሀይድሮክሳይድ", "magnesium hydroxide"),

        Map.entry("ቤንዛቲን ፔኒሲሊን", "benzathine penicillin"),
        Map.entry("ሊዶኬን", "lidocaine"),
        Map.entry("ዲያዜፓም", "diazepam"),
        Map.entry("ፌኖባርቢታል", "phenobarbital"),
        Map.entry("ፋኒቶይን", "phenytoin"),
        Map.entry("ካርባማዜፒን", "carbamazepine"),
        Map.entry("ሶዲየም ቫልፕሮኤት", "sodium valproate"),

        Map.entry("ሃሎፐሪዶል", "haloperidol"),
        Map.entry("አሚትሪፕቲሊን", "amitriptyline"),
        Map.entry("ፍሉኦክሴቲን", "fluoxetine"),

        Map.entry("ቤታሜታዞን", "betamethasone"),
        Map.entry("ዴክሳሜታዞን", "dexamethasone"),
        Map.entry("ፕሬድኒዞን", "prednisone"),

        Map.entry("አድሬናሊን", "adrenaline"),
        Map.entry("ኤፒኔፍሪን", "epinephrine"),
        Map.entry("አትሮፒን", "atropine"),

        Map.entry("ደክስትሮዝ", "dextrose"),
        Map.entry("ግሉኮዝ", "glucose"),
        Map.entry("ሪንገር ላክቴት", "ringer lactate"),

        Map.entry("ማንኒቶል", "mannitol"),
        Map.entry("ሄፓሪን", "heparin"),
        Map.entry("ዋርፋሪን", "warfarin"),

        Map.entry("ቲታነስ ቶክሶይድ", "tetanus toxoid"),
        Map.entry("ቲቲ", "tt vaccine"),
        Map.entry("ኢንሱሊን ሊስፕሮ", "insulin lispro"),
        Map.entry("ኢንሱሊን አስፓርት", "insulin aspart")
);

    private static final Map<String, String> ENGLISH_ALIAS_TO_CANONICAL = Map.ofEntries(
            Map.entry("acetaminophen", "paracetamol"),
            Map.entry("tylenol", "paracetamol"),
            Map.entry("panadol", "paracetamol"),
            Map.entry("augmentin", "amoxiclav"),
            Map.entry("glucophage", "metformin"),
            Map.entry("brufen", "ibuprofen"),
            Map.entry("flagyl", "metronidazole"),
            Map.entry("ventolin", "salbutamol"),
            Map.entry("zithromax", "azithromycin")
    );

    private static final Map<String, String> ENGLISH_TO_AMHARIC = buildEnglishToAmharic();
    private static final Map<String, String> SEARCH_ALIAS_TO_CANONICAL = buildSearchAliasToCanonical();
    private static final Map<String, List<String>> CANONICAL_TO_SEARCH_FORMS = buildCanonicalToSearchForms();

    private MedicineSearchNormalizer() {
    }

    private static Map<String, String> buildEnglishToAmharic() {
        Map<String, String> reverse = new HashMap<>();
        AMHARIC_TO_ENGLISH.forEach((amharic, english) -> reverse.putIfAbsent(english, amharic));
        return Map.copyOf(reverse);
    }

    private static Map<String, String> buildSearchAliasToCanonical() {
        Map<String, String> aliases = new LinkedHashMap<>();

        AMHARIC_TO_ENGLISH.forEach((alias, canonical) -> aliases.put(normalizeSearchKey(alias), canonical));
        ENGLISH_ALIAS_TO_CANONICAL.forEach((alias, canonical) -> aliases.put(normalizeSearchKey(alias), canonical));
        ENGLISH_TO_AMHARIC.keySet().forEach(canonical -> aliases.put(normalizeSearchKey(canonical), canonical));

        return Map.copyOf(aliases);
    }

    private static Map<String, List<String>> buildCanonicalToSearchForms() {
        Map<String, Set<String>> forms = new LinkedHashMap<>();

        SEARCH_ALIAS_TO_CANONICAL.forEach((alias, canonical) ->
                forms.computeIfAbsent(canonical, ignored -> new LinkedHashSet<>()).add(alias));

        return forms.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue())
                ));
    }

    public static String normalizeSearchKey(String input) {
        if (input == null) {
            return "";
        }

        return input.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    public static String normalizeToEnglishCanonical(String input) {
        String cleaned = normalizeSearchKey(input);
        if (cleaned.isBlank()) {
            return "";
        }

        String mapped = SEARCH_ALIAS_TO_CANONICAL.get(cleaned);
        if (mapped != null) {
            return mapped;
        }

        return cleaned;
    }

    public static String normalizeCommaSeparatedMedicines(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        return Arrays.stream(input.split(","))
                .map(MedicineSearchNormalizer::normalizeToEnglishCanonical)
                .filter(m -> !m.isBlank())
                .distinct()
                .collect(Collectors.joining(","));
    }

    public static String toAmharicDisplay(String input) {
        String canonical = normalizeToEnglishCanonical(input);
        if (canonical.isBlank()) {
            return "";
        }

        return ENGLISH_TO_AMHARIC.getOrDefault(canonical, canonical);
    }

    public static String toDisplayName(String input, BotLanguage language) {
        if (language == BotLanguage.AMHARIC) {
            return toAmharicDisplay(input);
        }

        return normalizeToEnglishCanonical(input);
    }

    public static List<String> searchFormsForCanonical(String canonical) {
        if (canonical == null || canonical.isBlank()) {
            return List.of();
        }

        return CANONICAL_TO_SEARCH_FORMS.getOrDefault(normalizeToEnglishCanonical(canonical), List.of());
    }
}
