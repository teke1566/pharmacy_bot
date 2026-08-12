package com.tenahub.bot.util;

import java.util.List;

/**
 * Caps Telegram reservation card dumps so status buttons do not flood the chat.
 */
public final class ReservationListLimiter {

    public static final int TELEGRAM_RESERVATION_CARD_LIMIT = 5;
    public static final String MORE_CALLBACK_PREFIX = "res_more_";

    private ReservationListLimiter() {
    }

    /**
     * Returns items starting at {@code offset}, up to {@code limit} items.
     */
    public static <T> List<T> page(List<T> items, int offset, int limit) {
        if (items == null || items.isEmpty() || limit <= 0) {
            return List.of();
        }
        if (offset < 0) {
            offset = 0;
        }
        if (offset >= items.size()) {
            return List.of();
        }
        int end = Math.min(items.size(), offset + limit);
        return List.copyOf(items.subList(offset, end));
    }

    public static <T> List<T> page(List<T> items, int offset) {
        return page(items, offset, TELEGRAM_RESERVATION_CARD_LIMIT);
    }

    /**
     * Returns the first {@code limit} items (newest-first lists stay newest-first).
     */
    public static <T> List<T> latest(List<T> items, int limit) {
        return page(items, 0, limit);
    }

    public static <T> List<T> latest(List<T> items) {
        return latest(items, TELEGRAM_RESERVATION_CARD_LIMIT);
    }

    public static int hiddenCount(int total, int limit) {
        return hiddenCount(total, 0, limit);
    }

    public static int hiddenCount(int total, int offset, int limit) {
        if (total <= 0 || limit <= 0) {
            return 0;
        }
        if (offset < 0) {
            offset = 0;
        }
        int shownThrough = Math.min(total, offset + limit);
        return Math.max(0, total - shownThrough);
    }

    public static int hiddenCount(int total) {
        return hiddenCount(total, 0, TELEGRAM_RESERVATION_CARD_LIMIT);
    }

    public static String moreMessage(int hidden, String noun) {
        if (hidden <= 0) {
            return null;
        }
        String label = noun == null || noun.isBlank() ? "reservations" : noun.trim();
        return "…and " + hidden + " more " + label + ".";
    }

    public static String overflowText(int hidden, String noun) {
        if (hidden <= 0) {
            return null;
        }
        String label = noun == null || noun.isBlank() ? "reservations" : noun.trim();
        return hidden + " more " + label;
    }

    public static String moreCallback(String scope, String kind, int nextOffset) {
        return MORE_CALLBACK_PREFIX + scope + "_" + kind + "_" + nextOffset;
    }

    public static boolean isMoreCallback(String data) {
        return data != null && data.startsWith(MORE_CALLBACK_PREFIX);
    }

    /**
     * Parses {@code res_more_{scope}_{kind}_{offset}}.
     * Kind may contain underscores (e.g. RX_UPLOAD); offset is the trailing integer.
     */
    public static MoreCallback parseMoreCallback(String data) {
        if (!isMoreCallback(data)) {
            return null;
        }
        String payload = data.substring(MORE_CALLBACK_PREFIX.length());
        String[] parts = payload.split("_", 2);
        if (parts.length < 2) {
            return null;
        }
        String scope = parts[0];
        String rest = parts[1];
        int lastUnderscore = rest.lastIndexOf('_');
        if (lastUnderscore <= 0 || lastUnderscore >= rest.length() - 1) {
            return null;
        }
        String kind = rest.substring(0, lastUnderscore);
        try {
            int offset = Integer.parseInt(rest.substring(lastUnderscore + 1));
            if (offset < 0) {
                return null;
            }
            return new MoreCallback(scope, kind, offset);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Caps a LinkedHashMap of groups while preserving insertion order (newest-first).
     */
    public static <K, V> List<java.util.Map.Entry<K, V>> latestEntries(
            java.util.LinkedHashMap<K, V> groups,
            int limit) {
        return pageEntries(groups, 0, limit);
    }

    public static <K, V> List<java.util.Map.Entry<K, V>> latestEntries(
            java.util.LinkedHashMap<K, V> groups) {
        return latestEntries(groups, TELEGRAM_RESERVATION_CARD_LIMIT);
    }

    public static <K, V> List<java.util.Map.Entry<K, V>> pageEntries(
            java.util.LinkedHashMap<K, V> groups,
            int offset,
            int limit) {
        if (groups == null || groups.isEmpty() || limit <= 0) {
            return List.of();
        }
        return page(List.copyOf(groups.entrySet()), offset, limit);
    }

    public static <K, V> List<java.util.Map.Entry<K, V>> pageEntries(
            java.util.LinkedHashMap<K, V> groups,
            int offset) {
        return pageEntries(groups, offset, TELEGRAM_RESERVATION_CARD_LIMIT);
    }

    public record MoreCallback(String scope, String kind, int offset) {
    }
}
