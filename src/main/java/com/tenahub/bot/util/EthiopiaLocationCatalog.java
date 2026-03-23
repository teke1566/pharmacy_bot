package com.tenahub.bot.util;

import com.tenahub.bot.dto.EthiopiaLocationOption;

import java.util.List;
import java.util.stream.Collectors;

public class EthiopiaLocationCatalog {

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

            // Bole
            new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Bole", 8.9806, 38.7578),
            new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Bole Medhanialem", 8.9918, 38.7895),
            new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Bole Rwanda", 8.9892, 38.7765),
            new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Gerji", 8.9970, 38.8085),
            new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Megenagna", 9.0108, 38.7995),
            new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Wello Sefer", 9.0107, 38.7682),
            new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Bambis", 9.0205, 38.7700),
            new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Stadium", 9.0228, 38.7526),

            // Gullele
            new EthiopiaLocationOption("Addis Ababa", "Addis Ababa", "Asko", 9.0506, 38.6867),

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

            /* =========================
               OROMIA
               ========================= */
            new EthiopiaLocationOption("Oromia", "Adama", "Adama Center", 8.5409, 39.2716),
            new EthiopiaLocationOption("Oromia", "Adama", "01 Area", 8.5460, 39.2730),
            new EthiopiaLocationOption("Oromia", "Adama", "Bole Area", 8.5350, 39.2800),

            new EthiopiaLocationOption("Oromia", "Bishoftu", "Bishoftu Center", 8.7520, 38.9785),

            new EthiopiaLocationOption("Oromia", "Jimma", "Jimma Center", 7.6736, 36.8344),
            new EthiopiaLocationOption("Oromia", "Jimma", "Merkato", 7.6780, 36.8320),
            new EthiopiaLocationOption("Oromia", "Jimma", "Jiren", 7.6900, 36.8500),

            new EthiopiaLocationOption("Oromia", "Shashemene", "Shashemene Center", 7.2001, 38.6002),
            new EthiopiaLocationOption("Oromia", "Nekemte", "Nekemte Center", 9.0862, 36.5461),

            /* =========================
               AMHARA
               ========================= */
            new EthiopiaLocationOption("Amhara", "Bahir Dar", "Bahir Dar Center", 11.5742, 37.3614),
            new EthiopiaLocationOption("Amhara", "Bahir Dar", "Kebele 14", 11.5800, 37.3600),
            new EthiopiaLocationOption("Amhara", "Bahir Dar", "Kebele 16", 11.5700, 37.3700),

            new EthiopiaLocationOption("Amhara", "Gondar", "Gondar Center", 12.6030, 37.4521),
            new EthiopiaLocationOption("Amhara", "Dessie", "Dessie Center", 11.1333, 39.6333),
            new EthiopiaLocationOption("Amhara", "Debre Markos", "Debre Markos Center", 10.3500, 37.7333),

            /* =========================
               TIGRAY
               ========================= */
            new EthiopiaLocationOption("Tigray", "Mekelle", "Mekelle Center", 13.4967, 39.4753),
            new EthiopiaLocationOption("Tigray", "Mekelle", "Ayder", 13.5000, 39.4800),
            new EthiopiaLocationOption("Tigray", "Mekelle", "Quiha", 13.4700, 39.4700),

            new EthiopiaLocationOption("Tigray", "Axum", "Axum Center", 14.1211, 38.7234),

            /* =========================
               SIDAMA
               ========================= */
            new EthiopiaLocationOption("Sidama", "Hawassa", "Hawassa Center", 7.0621, 38.4772),
            new EthiopiaLocationOption("Sidama", "Hawassa", "Tabor", 7.0700, 38.4800),
            new EthiopiaLocationOption("Sidama", "Hawassa", "Haik Dar", 7.0600, 38.4900),

            /* =========================
               DIRE DAWA
               ========================= */
            new EthiopiaLocationOption("Dire Dawa", "Dire Dawa", "Dire Dawa Center", 9.6009, 41.8501),
            new EthiopiaLocationOption("Dire Dawa", "Dire Dawa", "Kezira", 9.6020, 41.8600),
            new EthiopiaLocationOption("Dire Dawa", "Dire Dawa", "Sabian", 9.6100, 41.8450),

            /* =========================
               HARARI
               ========================= */
            new EthiopiaLocationOption("Harari", "Harar", "Harar Center", 9.3149, 42.1181),

            /* =========================
               SOMALI
               ========================= */
            new EthiopiaLocationOption("Somali", "Jigjiga", "Jigjiga Center", 9.3500, 42.8000),

            /* =========================
               AFAR
               ========================= */
            new EthiopiaLocationOption("Afar", "Semera", "Semera Center", 11.7930, 41.0050),

            /* =========================
               BENISHANGUL-GUMUZ
               ========================= */
            new EthiopiaLocationOption("Benishangul-Gumuz", "Asosa", "Asosa Center", 10.0670, 34.5330),

            /* =========================
               GAMBELA
               ========================= */
            new EthiopiaLocationOption("Gambela", "Gambela", "Gambela Center", 8.2500, 34.5830),

            /* =========================
               SNNPR
               ========================= */
            new EthiopiaLocationOption("SNNPR", "Arba Minch", "Arba Minch Center", 6.0333, 37.5500),
            new EthiopiaLocationOption("SNNPR", "Wolaita Sodo", "Wolaita Sodo Center", 6.8600, 37.7600),
            new EthiopiaLocationOption("SNNPR", "Hosaena", "Hosaena Center", 7.5500, 37.8500)
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
                "Lafto"
        );
        case "bole" -> List.of(
                "Bole",
                "Bole Medhanialem",
                "Bole Rwanda",
                "Gerji",
                "Megenagna",
                "Wello Sefer",
                "Bambis",
                "Stadium"
        );
        case "gullele" -> List.of(
                "Asko"
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