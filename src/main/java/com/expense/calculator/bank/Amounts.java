package com.expense.calculator.bank;

import java.math.BigDecimal;

/** Lenient parsing of statement amounts: "1,23,456.78", "₹ 500", "500.00 Dr", "(250.00)". */
public final class Amounts {

    private Amounts() {
    }

    /** @return absolute amount, or null if blank / zero / not a number. */
    public static BigDecimal parse(String raw) {
        if (raw == null) return null;
        String cleaned = raw.replaceAll("(?i)(INR|Rs\\.?|₹|Dr|Cr|[,\\s()])", "");
        if (cleaned.isEmpty() || cleaned.equals("-")) return null;
        try {
            BigDecimal value = new BigDecimal(cleaned).abs();
            return value.signum() == 0 ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
