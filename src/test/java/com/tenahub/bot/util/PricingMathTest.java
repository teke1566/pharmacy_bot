package com.tenahub.bot.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PricingMathTest {

    @Test
    void grossMarginAndMarkup() {
        BigDecimal sell = new BigDecimal("600.00");
        BigDecimal cost = new BigDecimal("450.00");
        assertEquals(new BigDecimal("150.00"), PricingMath.grossProfit(sell, cost));
        assertEquals(0, new BigDecimal("25.0000").compareTo(PricingMath.grossMarginPercent(sell, cost)));
        assertEquals(0, new BigDecimal("33.3333").compareTo(PricingMath.markupPercent(sell, cost)));
    }

    @Test
    void percentChangeAndDiscounts() {
        assertEquals(0, new BigDecimal("8.3333").compareTo(
                PricingMath.percentChange(new BigDecimal("600"), new BigDecimal("650"))));
        assertEquals(new BigDecimal("540.00"),
                PricingMath.applyPercentageDiscount(new BigDecimal("600"), new BigDecimal("10")));
        assertEquals(new BigDecimal("550.00"),
                PricingMath.applyFixedDiscount(new BigDecimal("600"), new BigDecimal("50")));
    }

    @Test
    void nullSafe() {
        assertNull(PricingMath.grossMarginPercent(null, new BigDecimal("1")));
        assertNull(PricingMath.markupPercent(new BigDecimal("1"), BigDecimal.ZERO));
    }
}
