package com.tenahub.bot.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReservationListLimiterTest {

    @Test
    void latest_returnsFirstFive() {
        List<Integer> items = List.of(1, 2, 3, 4, 5, 6, 7);

        assertEquals(List.of(1, 2, 3, 4, 5), ReservationListLimiter.latest(items));
        assertEquals(5, ReservationListLimiter.TELEGRAM_RESERVATION_CARD_LIMIT);
    }

    @Test
    void latest_returnsAllWhenUnderLimit() {
        List<String> items = List.of("a", "b");

        assertEquals(List.of("a", "b"), ReservationListLimiter.latest(items, 5));
    }

    @Test
    void latest_handlesNullAndEmpty() {
        assertTrue(ReservationListLimiter.latest(null).isEmpty());
        assertTrue(ReservationListLimiter.latest(List.of()).isEmpty());
        assertTrue(ReservationListLimiter.latest(List.of(1, 2), 0).isEmpty());
    }

    @Test
    void page_returnsSliceFromOffset() {
        List<Integer> items = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9);

        assertEquals(List.of(6, 7, 8, 9), ReservationListLimiter.page(items, 5));
        assertEquals(List.of(6, 7), ReservationListLimiter.page(items, 5, 2));
        assertTrue(ReservationListLimiter.page(items, 20).isEmpty());
    }

    @Test
    void hiddenCount_returnsRemainder() {
        assertEquals(14, ReservationListLimiter.hiddenCount(19));
        assertEquals(0, ReservationListLimiter.hiddenCount(5));
        assertEquals(0, ReservationListLimiter.hiddenCount(3));
        assertEquals(0, ReservationListLimiter.hiddenCount(0));
        assertEquals(9, ReservationListLimiter.hiddenCount(19, 5, 5));
        assertEquals(0, ReservationListLimiter.hiddenCount(19, 15, 5));
    }

    @Test
    void moreMessage_formatsHiddenCount() {
        assertEquals("…and 14 more cancelled reservations.",
                ReservationListLimiter.moreMessage(14, "cancelled reservations"));
        assertNull(ReservationListLimiter.moreMessage(0, "reservations"));
        assertEquals("14 more cancelled reservations",
                ReservationListLimiter.overflowText(14, "cancelled reservations"));
    }

    @Test
    void moreCallback_roundTrips() {
        String data = ReservationListLimiter.moreCallback("user", "CANCELLED", 5);
        assertEquals("res_more_user_CANCELLED_5", data);
        assertTrue(ReservationListLimiter.isMoreCallback(data));

        ReservationListLimiter.MoreCallback parsed = ReservationListLimiter.parseMoreCallback(data);
        assertEquals("user", parsed.scope());
        assertEquals("CANCELLED", parsed.kind());
        assertEquals(5, parsed.offset());

        ReservationListLimiter.MoreCallback rx = ReservationListLimiter.parseMoreCallback(
                ReservationListLimiter.moreCallback("pharm", "RX_UPLOAD", 10));
        assertEquals("pharm", rx.scope());
        assertEquals("RX_UPLOAD", rx.kind());
        assertEquals(10, rx.offset());
        assertFalse(ReservationListLimiter.isMoreCallback("cancel_res_1"));
        assertNull(ReservationListLimiter.parseMoreCallback("res_more_bad"));
    }

    @Test
    void latestEntries_capsLinkedHashMapGroups() {
        LinkedHashMap<String, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 1; i <= 7; i++) {
            groups.put("g" + i, List.of(i));
        }

        List<Map.Entry<String, List<Integer>>> latest = ReservationListLimiter.latestEntries(groups);

        assertEquals(5, latest.size());
        assertEquals("g1", latest.get(0).getKey());
        assertEquals("g5", latest.get(4).getKey());
        assertEquals(2, ReservationListLimiter.hiddenCount(groups.size()));

        List<Map.Entry<String, List<Integer>>> page = ReservationListLimiter.pageEntries(groups, 5);
        assertEquals(2, page.size());
        assertEquals("g6", page.get(0).getKey());
    }
}
