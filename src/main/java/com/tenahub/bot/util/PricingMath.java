package com.tenahub.bot.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PricingMath {

    public static final int SCALE = 2;
    public static final int RATIO_SCALE = 4;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private PricingMath() {
    }

    public static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal money(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return money(new BigDecimal(value.trim()));
    }

    public static BigDecimal grossProfit(BigDecimal sellingPrice, BigDecimal purchaseCost) {
        if (sellingPrice == null || purchaseCost == null) {
            return null;
        }
        return money(sellingPrice.subtract(purchaseCost));
    }

    /** Gross margin % = ((sell - cost) / sell) * 100 */
    public static BigDecimal grossMarginPercent(BigDecimal sellingPrice, BigDecimal purchaseCost) {
        if (sellingPrice == null || purchaseCost == null || sellingPrice.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return sellingPrice.subtract(purchaseCost)
                .multiply(BigDecimal.valueOf(100))
                .divide(sellingPrice, RATIO_SCALE, ROUNDING);
    }

    /** Markup % = ((sell - cost) / cost) * 100 */
    public static BigDecimal markupPercent(BigDecimal sellingPrice, BigDecimal purchaseCost) {
        if (sellingPrice == null || purchaseCost == null || purchaseCost.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return sellingPrice.subtract(purchaseCost)
                .multiply(BigDecimal.valueOf(100))
                .divide(purchaseCost, RATIO_SCALE, ROUNDING);
    }

    public static BigDecimal percentChange(BigDecimal oldPrice, BigDecimal newPrice) {
        if (oldPrice == null || newPrice == null || oldPrice.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return newPrice.subtract(oldPrice)
                .multiply(BigDecimal.valueOf(100))
                .divide(oldPrice, RATIO_SCALE, ROUNDING);
    }

    public static BigDecimal applyPercentageDiscount(BigDecimal sellingPrice, BigDecimal percent) {
        if (sellingPrice == null || percent == null) {
            return sellingPrice;
        }
        BigDecimal discount = sellingPrice.multiply(percent).divide(BigDecimal.valueOf(100), SCALE, ROUNDING);
        return money(sellingPrice.subtract(discount));
    }

    public static BigDecimal applyFixedDiscount(BigDecimal sellingPrice, BigDecimal amount) {
        if (sellingPrice == null || amount == null) {
            return sellingPrice;
        }
        return money(sellingPrice.subtract(amount));
    }
}
