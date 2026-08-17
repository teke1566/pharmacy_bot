package com.tenahub.bot.util;

/**
 * Deterministic demand labels for pharmacy performance / restock.
 */
public final class DemandLabeler {

    private DemandLabeler() {
    }

    public static String label(int searchCount, boolean outOfStock, boolean lowStock) {
        if (searchCount <= 0) {
            return "COLD";
        }
        if (searchCount >= 8 && (outOfStock || lowStock)) {
            return "HOT";
        }
        if (searchCount >= 4) {
            return "RISING";
        }
        if (searchCount >= 1) {
            return "STEADY";
        }
        return "COLD";
    }
}
