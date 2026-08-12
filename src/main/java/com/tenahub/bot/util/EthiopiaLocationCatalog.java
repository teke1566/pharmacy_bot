package com.tenahub.bot.util;

import com.tenahub.bot.dto.EthiopiaLocationOption;

import java.util.List;
import java.util.stream.Collectors;

public class EthiopiaLocationCatalog {
    private EthiopiaLocationCatalog() {
        /* This utility class should not be instantiated */
    }


   private static final List<EthiopiaLocationOption> OPTIONS = List.of(

        /* =========================
           ADDIS ABABA
           Region = Addis Ababa
           City   = Addis Ababa
           Area   = final selectable area
           ========================= */

        // Arada
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Piassa", 9.0415, 38.7469),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Arat Kilo", 9.0362, 38.7617),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Sidist Kilo", 9.0460, 38.7610),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "4 Kilo", 9.0468, 38.7608),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "6 Kilo", 9.0485, 38.7619),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Shiromeda", 9.0488, 38.7456),

        // Addis Ketema
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Merkato", 9.0320, 38.7360),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Kera", 9.0001, 38.7398),

        // Akaky Kaliti
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Alem Bank", 8.9557, 38.6489),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Lafto", 8.9781, 38.7225),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Akaki", 8.8920, 38.7880),

        // Bole
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Bole", 8.9806, 38.7578),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Bole Medhanialem", 8.9918, 38.7895),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Bole Rwanda", 8.9892, 38.7765),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Gerji", 8.9970, 38.8085),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Megenagna", 9.0108, 38.7995),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Wello Sefer", 9.0107, 38.7682),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Bambis", 9.0205, 38.7700),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Stadium", 9.0228, 38.7526),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Airport", 8.9890, 38.7990),

        // Gullele
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Asko", 9.0506, 38.6867),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Entoto", 9.0800, 38.7400),

        // Kirkos
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Mexico", 9.0054, 38.7636),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Kazanchis", 9.0167, 38.7618),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Gotera", 8.9913, 38.7613),

        // Kolfe Keranio
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Jemo", 8.9307, 38.7098),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Lebu", 8.9441, 38.6949),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Ayer Tena", 8.9762, 38.7011),

        // Lideta
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Lideta", 9.0030, 38.7349),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Sarbet", 8.9992, 38.7480),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Torhailoch", 9.0039, 38.7223),

        // Nifas Silk Lafto
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Gofa", 8.9673, 38.7390),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Mekanisa", 8.9658, 38.7267),

        // Yeka
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "CMC", 9.0300, 38.8210),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Ayat", 9.0458, 38.8496),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Summit", 8.9997, 38.8574),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Hayat", 8.9988, 38.8540),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Kotebe", 9.0237, 38.8507),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Gurd Shola", 9.0101, 38.7929),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Yeka", 9.0308, 38.8017),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Lamberet", 9.0620, 38.7253),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Semit", 9.0403, 38.8048),

        // Lemi Kura
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Lemi Kura", 9.0500, 38.8900),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Ayat Roundabout", 9.0465, 38.8505),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Sunshine", 9.0170, 38.8420),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Meri", 9.0400, 38.8700),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Tulu Dimtu", 8.9620, 38.8350),
        new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Yerer", 9.0500, 38.9000),

        /* =========================
           OROMIA
           ========================= */
        new EthiopiaLocationOption("Oromia", "Adama", "Adama Center", 8.5409, 39.2716),
        new EthiopiaLocationOption("Oromia", "Adama", "01 Area", 8.5460, 39.2730),
        new EthiopiaLocationOption("Oromia", "Adama", "Bole Area", 8.5350, 39.2800),
        new EthiopiaLocationOption("Oromia", "Adama", "Geda", 8.5510, 39.2800),
        new EthiopiaLocationOption("Oromia", "Adama", "Dembela", 8.5340, 39.2650),
        new EthiopiaLocationOption("Oromia", "Adama", "Mebrat Hail", 8.5450, 39.2760),

        new EthiopiaLocationOption("Oromia", "Bishoftu", "Bishoftu Center", 8.7520, 38.9785),
        new EthiopiaLocationOption("Oromia", "Bishoftu", "Hora", 8.7570, 38.9850),
        new EthiopiaLocationOption("Oromia", "Bishoftu", "Babogaya", 8.7710, 38.9790),
        new EthiopiaLocationOption("Oromia", "Bishoftu", "Kuriftu Area", 8.7540, 38.9855),

        new EthiopiaLocationOption("Oromia", "Jimma", "Jimma Center", 7.6736, 36.8344),
        new EthiopiaLocationOption("Oromia", "Jimma", "Merkato", 7.6780, 36.8320),
        new EthiopiaLocationOption("Oromia", "Jimma", "Jiren", 7.6900, 36.8500),
        new EthiopiaLocationOption("Oromia", "Jimma", "Agip", 7.6810, 36.8410),
        new EthiopiaLocationOption("Oromia", "Jimma", "Hermata", 7.6640, 36.8250),
        new EthiopiaLocationOption("Oromia", "Jimma", "Seto", 7.6760, 36.8380),

        new EthiopiaLocationOption("Oromia", "Shashemene", "Shashemene Center", 7.2001, 38.6002),
        new EthiopiaLocationOption("Oromia", "Shashemene", "Abosto", 7.1910, 38.5920),
        new EthiopiaLocationOption("Oromia", "Shashemene", "Bulchana", 7.2090, 38.6090),
        new EthiopiaLocationOption("Oromia", "Shashemene", "Alelu", 7.2030, 38.6030),

        new EthiopiaLocationOption("Oromia", "Nekemte", "Nekemte Center", 9.0862, 36.5461),
        new EthiopiaLocationOption("Oromia", "Nekemte", "Bake Jama", 9.0910, 36.5490),
        new EthiopiaLocationOption("Oromia", "Nekemte", "Burka Jato", 9.0865, 36.5480),

        new EthiopiaLocationOption("Oromia", "Ambo", "Ambo Center", 8.9833, 37.8500),
        new EthiopiaLocationOption("Oromia", "Ambo", "Awaro", 8.9750, 37.8430),

        new EthiopiaLocationOption("Oromia", "Sebeta", "Sebeta Center", 8.9167, 38.6167),
        new EthiopiaLocationOption("Oromia", "Sebeta", "Alem Gena", 8.9000, 38.6000),
        new EthiopiaLocationOption("Oromia", "Sebeta", "Sebeta Hawas", 8.9210, 38.6210),

        new EthiopiaLocationOption("Oromia", "Burayu", "Burayu Center", 9.0200, 38.6500),
        new EthiopiaLocationOption("Oromia", "Burayu", "Gefersa", 9.0610, 38.6590),
        new EthiopiaLocationOption("Oromia", "Burayu", "Ashewa Meda", 9.0160, 38.6460),

        new EthiopiaLocationOption("Oromia", "Asella", "Asella Center", 7.9500, 39.1200),
        new EthiopiaLocationOption("Oromia", "Bale Robe", "Robe Center", 7.1270, 40.0080),
        new EthiopiaLocationOption("Oromia", "Mojo", "Mojo Center", 8.5860, 39.1210),
        new EthiopiaLocationOption("Oromia", "Holeta", "Holeta Center", 9.0620, 38.4960),
        new EthiopiaLocationOption("Oromia", "Sululta", "Sululta Center", 9.1830, 38.7500),
        new EthiopiaLocationOption("Oromia", "Dukem", "Dukem Center", 8.8000, 38.9100),
        new EthiopiaLocationOption("Oromia", "Gelan", "Gelan Center", 8.7550, 38.7850),
        new EthiopiaLocationOption("Oromia", "Ziway", "Ziway Center", 7.9330, 38.7160),
        new EthiopiaLocationOption("Oromia", "Goba", "Goba Center", 7.0160, 39.9830),

        /* =========================
           AMHARA
           ========================= */
        new EthiopiaLocationOption("Amhara", "Bahir Dar", "Bahir Dar Center", 11.5742, 37.3614),
        new EthiopiaLocationOption("Amhara", "Bahir Dar", "Kebele 14", 11.5800, 37.3600),
        new EthiopiaLocationOption("Amhara", "Bahir Dar", "Kebele 16", 11.5700, 37.3700),
        new EthiopiaLocationOption("Amhara", "Bahir Dar", "Zenzelima", 11.5810, 37.3480),
        new EthiopiaLocationOption("Amhara", "Bahir Dar", "Poly Area", 11.5980, 37.3900),

        new EthiopiaLocationOption("Amhara", "Gondar", "Gondar Center", 12.6030, 37.4521),
        new EthiopiaLocationOption("Amhara", "Gondar", "Azezo", 12.6100, 37.4340),
        new EthiopiaLocationOption("Amhara", "Gondar", "Piazza", 12.6070, 37.4670),

        new EthiopiaLocationOption("Amhara", "Dessie", "Dessie Center", 11.1333, 39.6333),
        new EthiopiaLocationOption("Amhara", "Dessie", "Buanbuawuha", 11.1360, 39.6340),

        new EthiopiaLocationOption("Amhara", "Debre Markos", "Debre Markos Center", 10.3500, 37.7333),
        new EthiopiaLocationOption("Amhara", "Debre Tabor", "Debre Tabor Center", 11.8500, 38.0170),
        new EthiopiaLocationOption("Amhara", "Woldia", "Woldia Center", 11.8330, 39.6000),
        new EthiopiaLocationOption("Amhara", "Kombolcha", "Kombolcha Center", 11.0830, 39.7330),
        new EthiopiaLocationOption("Amhara", "Debre Birhan", "Debre Birhan Center", 9.6830, 39.5330),
        new EthiopiaLocationOption("Amhara", "Kobo", "Kobo Center", 12.1500, 39.6330),
        new EthiopiaLocationOption("Amhara", "Sekota", "Sekota Center", 12.6300, 39.0470),
        new EthiopiaLocationOption("Amhara", "Metema", "Metema Center", 12.9500, 36.1500),

        /* =========================
           TIGRAY
           ========================= */
        new EthiopiaLocationOption("Tigray", "Mekelle", "Mekelle Center", 13.4967, 39.4753),
        new EthiopiaLocationOption("Tigray", "Mekelle", "Ayder", 13.5000, 39.4800),
        new EthiopiaLocationOption("Tigray", "Mekelle", "Quiha", 13.4700, 39.4700),
        new EthiopiaLocationOption("Tigray", "Mekelle", "Kedamay Weyane", 13.4930, 39.4700),
        new EthiopiaLocationOption("Tigray", "Mekelle", "Hawelti", 13.5050, 39.4920),

        new EthiopiaLocationOption("Tigray", "Axum", "Axum Center", 14.1211, 38.7234),
        new EthiopiaLocationOption("Tigray", "Adigrat", "Adigrat Center", 14.2670, 39.4500),
        new EthiopiaLocationOption("Tigray", "Shire", "Shire Center", 14.1030, 38.2820),
        new EthiopiaLocationOption("Tigray", "Humera", "Humera Center", 14.2890, 36.6120),
        new EthiopiaLocationOption("Tigray", "Alamata", "Alamata Center", 12.4220, 39.5600),

        /* =========================
           SIDAMA
           ========================= */
        new EthiopiaLocationOption("Sidama", "Hawassa", "Hawassa Center", 7.0621, 38.4772),
        new EthiopiaLocationOption("Sidama", "Hawassa", "Tabor", 7.0700, 38.4800),
        new EthiopiaLocationOption("Sidama", "Hawassa", "Haik Dar", 7.0600, 38.4900),
        new EthiopiaLocationOption("Sidama", "Hawassa", "Menaharia", 7.0580, 38.4760),
        new EthiopiaLocationOption("Sidama", "Hawassa", "Lake Side", 7.0500, 38.4740),
        new EthiopiaLocationOption("Sidama", "Hawassa", "Gudumale", 7.0660, 38.4820),
        new EthiopiaLocationOption("Sidama", "Yirgalem", "Yirgalem Center", 6.7500, 38.4200),
        new EthiopiaLocationOption("Sidama", "Aleta Wendo", "Aleta Wendo Center", 6.6000, 38.4700),

        /* =========================
           DIRE DAWA
           ========================= */
        new EthiopiaLocationOption("Dire Dawa", "Dire Dawa", "Dire Dawa Center", 9.6009, 41.8501),
        new EthiopiaLocationOption("Dire Dawa", "Dire Dawa", "Kezira", 9.6020, 41.8600),
        new EthiopiaLocationOption("Dire Dawa", "Dire Dawa", "Sabian", 9.6100, 41.8450),
        new EthiopiaLocationOption("Dire Dawa", "Dire Dawa", "Megala", 9.6040, 41.8660),
        new EthiopiaLocationOption("Dire Dawa", "Dire Dawa", "Legehare", 9.6010, 41.8520),
        new EthiopiaLocationOption("Dire Dawa", "Dire Dawa", "Gende Kore", 9.6070, 41.8480),

        /* =========================
           HARARI
           ========================= */
        new EthiopiaLocationOption("Harari", "Harar", "Harar Center", 9.3149, 42.1181),
        new EthiopiaLocationOption("Harari", "Harar", "Jegol", 9.3130, 42.1260),
        new EthiopiaLocationOption("Harari", "Harar", "Aboker", 9.3160, 42.1350),
        new EthiopiaLocationOption("Harari", "Harar", "Argob Bari", 9.3090, 42.1200),

        /* =========================
           SOMALI
           ========================= */
        new EthiopiaLocationOption("Somali", "Jigjiga", "Jigjiga Center", 9.3500, 42.8000),
        new EthiopiaLocationOption("Somali", "Jigjiga", "Kebele 03", 9.3520, 42.8010),
        new EthiopiaLocationOption("Somali", "Jigjiga", "Sheik Ali Jowhar", 9.3570, 42.8050),
        new EthiopiaLocationOption("Somali", "Gode", "Gode Center", 5.9500, 43.4500),
        new EthiopiaLocationOption("Somali", "Kebri Dehar", "Kebri Dehar Center", 6.7330, 44.2700),
        new EthiopiaLocationOption("Somali", "Degahbur", "Degahbur Center", 8.2200, 43.5700),
        new EthiopiaLocationOption("Somali", "Warder", "Warder Center", 6.9700, 45.5500),

        /* =========================
           AFAR
           ========================= */
        new EthiopiaLocationOption("Afar", "Semera", "Semera Center", 11.7930, 41.0050),
        new EthiopiaLocationOption("Afar", "Semera", "Administration Area", 11.7920, 41.0030),
        new EthiopiaLocationOption("Afar", "Awash", "Awash Center", 8.9830, 40.1670),
        new EthiopiaLocationOption("Afar", "Logiya", "Logiya Center", 11.7900, 40.6660),
        new EthiopiaLocationOption("Afar", "Asayita", "Asayita Center", 11.5700, 41.4400),

        /* =========================
           BENISHANGUL-GUMUZ
           ========================= */
        new EthiopiaLocationOption("Benishangul-Gumuz", "Asosa", "Asosa Center", 10.0670, 34.5330),
        new EthiopiaLocationOption("Benishangul-Gumuz", "Asosa", "Market Area", 10.0650, 34.5300),
        new EthiopiaLocationOption("Benishangul-Gumuz", "Gilgel Beles", "Gilgel Beles Center", 10.3500, 36.7830),
        new EthiopiaLocationOption("Benishangul-Gumuz", "Bambasi", "Bambasi Center", 9.7500, 34.7300),

        /* =========================
           GAMBELA
           ========================= */
        new EthiopiaLocationOption("Gambela", "Gambela", "Gambela Center", 8.2500, 34.5830),
        new EthiopiaLocationOption("Gambela", "Gambela", "Pinyudo Road Area", 8.2540, 34.5890),
        new EthiopiaLocationOption("Gambela", "Itang", "Itang Center", 8.3000, 34.2500),

        /* =========================
           CENTRAL ETHIOPIA
           ========================= */
        new EthiopiaLocationOption("Central Ethiopia", "Hosaena", "Hosaena Center", 7.5500, 37.8500),
        new EthiopiaLocationOption("Central Ethiopia", "Hosaena", "Menehariya", 7.5520, 37.8530),
        new EthiopiaLocationOption("Central Ethiopia", "Butajira", "Butajira Center", 8.1160, 38.3670),
        new EthiopiaLocationOption("Central Ethiopia", "Worabe", "Worabe Center", 8.0000, 38.0000),
        new EthiopiaLocationOption("Central Ethiopia", "Welkite", "Welkite Center", 8.2830, 37.7830),

        /* =========================
           SOUTH ETHIOPIA
           ========================= */
        new EthiopiaLocationOption("South Ethiopia", "Wolaita Sodo", "Wolaita Sodo Center", 6.8600, 37.7600),
        new EthiopiaLocationOption("South Ethiopia", "Wolaita Sodo", "Arada", 6.8620, 37.7580),
        new EthiopiaLocationOption("South Ethiopia", "Arba Minch", "Arba Minch Center", 6.0333, 37.5500),
        new EthiopiaLocationOption("South Ethiopia", "Arba Minch", "Sikela", 6.0400, 37.5500),
        new EthiopiaLocationOption("South Ethiopia", "Arba Minch", "Shecha", 6.0300, 37.5600),
        new EthiopiaLocationOption("South Ethiopia", "Dilla", "Dilla Center", 6.4100, 38.3100),
        new EthiopiaLocationOption("South Ethiopia", "Jinka", "Jinka Center", 5.7900, 36.5700),
        new EthiopiaLocationOption("South Ethiopia", "Sawla", "Sawla Center", 6.3000, 36.8800),
        new EthiopiaLocationOption("South Ethiopia", "Karat", "Karat Center", 5.2500, 37.4800),
        new EthiopiaLocationOption("South Ethiopia", "Areka", "Areka Center", 7.0700, 37.7000),

        /* =========================
           SOUTH WEST ETHIOPIA PEOPLES' REGION
           ========================= */
        new EthiopiaLocationOption("South West Ethiopia Peoples' Region", "Bonga", "Bonga Center", 7.2700, 36.2400),
        new EthiopiaLocationOption("South West Ethiopia Peoples' Region", "Mizan Teferi", "Mizan Center", 7.0000, 35.5800),
        new EthiopiaLocationOption("South West Ethiopia Peoples' Region", "Tepi", "Tepi Center", 7.2000, 35.4500)
);
    

    

    public static List<String> getRegions() {
        return OPTIONS.stream()
                .map(EthiopiaLocationOption::getRegion)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    public static List<String> getCitiesByRegion(String region) {
        return OPTIONS.stream()
                .filter(o -> o.getRegion().equalsIgnoreCase(region))
                .map(EthiopiaLocationOption::getCity)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    public static List<String> getAreasByRegionAndCity(String region, String city) {
        return OPTIONS.stream()
                .filter(o -> o.getRegion().equalsIgnoreCase(region))
                .filter(o -> o.getCity().equalsIgnoreCase(city))
                .map(EthiopiaLocationOption::getArea)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    public static List<String> getAreasByCity(String city) {
        return OPTIONS.stream()
                .filter(o -> o.getCity().equalsIgnoreCase(city))
                .map(EthiopiaLocationOption::getArea)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    public static EthiopiaLocationOption find(String region, String city, String area) {
        return OPTIONS.stream()
                .filter(o -> o.getRegion().equalsIgnoreCase(region))
                .filter(o -> o.getCity().equalsIgnoreCase(city))
                .filter(o -> o.getArea().equalsIgnoreCase(area))
                .findFirst()
                .orElse(null);
    }

   

    

    public static boolean isAddisAbabaRegion(String region) {
    return region != null && region.equalsIgnoreCase("Addis Ababa");
}

public static boolean isAddisAbabaCity(String city) {
    return city != null && city.equalsIgnoreCase("Addis Ababa");
}

public static List<String> getAddisAbabaSubCities() {
    return List.of(
            "Addis Ketema",
            "Akaky Kaliti",
            "Arada",
            "Bole",
            "Gullele",
            "Kirkos",
            "Kolfe Keranio",
            "Lideta",
            "Nifas Silk Lafto",
            "Yeka",
            "Lemi Kura"
    );
}

public static List<String> getAddisAreasBySubCity(String subCity) {
    if (subCity == null) {
        return List.of();
    }

    return switch (subCity.trim().toLowerCase()) {
        case "arada" -> List.of(
                "Piassa",
                "Arat Kilo",
                "Sidist Kilo",
                "4 Kilo",
                "6 Kilo",
                "Shiromeda"
        );
        case "addis ketema" -> List.of(
                "Merkato",
                "Kera"
        );
        case "akaky kaliti", "akaki kaliti" -> List.of(
                "Alem Bank",
                "Lafto",
                "Akaki"
        );
        case "bole" -> List.of(
                "Bole",
                "Bole Medhanialem",
                "Bole Rwanda",
                "Gerji",
                "Megenagna",
                "Wello Sefer",
                "Bambis",
                "Stadium",
                "Airport"
        );
        case "gullele" -> List.of(
                "Asko",
                "Entoto"
        );
        case "kirkos" -> List.of(
                "Mexico",
                "Kazanchis",
                "Gotera"
        );
        case "kolfe keranio" -> List.of(
                "Jemo",
                "Lebu",
                "Ayer Tena"
        );
        case "lideta" -> List.of(
                "Lideta",
                "Sarbet",
                "Torhailoch"
        );
        case "nifas silk-lafto", "nifas silk lafto" -> List.of(
                "Gofa",
                "Mekanisa"
        );
        case "yeka" -> List.of(
                "CMC",
                "Ayat",
                "Summit",
                "Hayat",
                "Kotebe",
                "Gurd Shola",
                "Yeka",
                "Lamberet",
                "Semit"
        );
        case "lemi kura" -> List.of(
                "Lemi Kura",
                "Ayat Roundabout",
                "Sunshine",
                "Meri",
                "Tulu Dimtu",
                "Yerer"
        );
        default -> List.of();
    };
}
public static EthiopiaLocationOption findNearest(double latitude, double longitude) {
    EthiopiaLocationOption nearest = null;
    double minDistanceKm = Double.MAX_VALUE;

    for (EthiopiaLocationOption option : OPTIONS) {
        double distanceKm = haversineKm(
                latitude,
                longitude,
                option.getLatitude(),
                option.getLongitude()
        );

        if (distanceKm < minDistanceKm) {
            minDistanceKm = distanceKm;
            nearest = option;
        }
    }

    return nearest;
}

public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
    return haversineKm(lat1, lon1, lat2, lon2);
}

private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
    final double earthRadiusKm = 6371.0;

    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);

    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);

    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

    return earthRadiusKm * c;
}
public static String findAddisSubCityByArea(String area) {
    if (area == null) {
        return null;
    }

    for (String subCity : getAddisAbabaSubCities()) {
        boolean found = getAddisAreasBySubCity(subCity).stream()
                .anyMatch(a -> a.equalsIgnoreCase(area));

        if (found) {
            return subCity;
        }
    }

    return null;
}


}