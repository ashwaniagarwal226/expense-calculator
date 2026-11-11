package com.expense.calculator.bank;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Best-effort payee name from a raw narration, e.g.
 * {@code UPI-SWIGGY LIMITED-swiggy.upi@icici-ICIC0DC0099-412345678901-PAYMENT} -> {@code SWIGGY LIMITED},
 * {@code POS 512345XXXXXX1234 AMAZON PAY INDIA} -> {@code AMAZON PAY INDIA}.
 * Used for "top merchants", recurring-payment detection and as the default keyword when the user
 * creates a rule from a transaction.
 */
public final class MerchantExtractor {

    private static final Set<String> NOISE = Set.of(
            "UPI", "NEFT", "IMPS", "RTGS", "POS", "ACH", "ECS", "NACH", "DR", "CR", "MB", "IB", "BIL", "BILLPAY",
            "INF", "INFT", "ONL", "TPT", "TRF", "TRANSFER", "TO", "FROM", "BY", "PAYMENT", "PAY", "SENT", "VIA",
            "MMT", "P2A", "P2M", "PAYTM", "REF", "NA", "ME", "DEBIT", "CREDIT", "CARD");
    private static final Pattern SPLIT = Pattern.compile("[-/:|*]+");
    private static final Pattern IFSC = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");
    private static final Pattern MASKED_OR_NUMERIC = Pattern.compile("^[0-9X]+$");

    private MerchantExtractor() {
    }

    public static String extract(String narration) {
        if (narration == null || narration.isBlank()) return "UNKNOWN";
        String upper = narration.toUpperCase(Locale.ROOT).trim();
        for (String part : SPLIT.split(upper)) {
            String candidate = cleanWords(part);
            if (candidate.length() >= 3) {
                return candidate.length() > 40 ? candidate.substring(0, 40).trim() : candidate;
            }
        }
        return upper.length() > 40 ? upper.substring(0, 40).trim() : upper;
    }

    private static String cleanWords(String part) {
        List<String> words = new ArrayList<>();
        for (String word : part.trim().split("\\s+")) {
            String w = word.replaceAll("[^A-Z0-9&.]", "");
            boolean junk = w.isEmpty() || w.contains("@") || word.contains("@") || IFSC.matcher(w).matches()
                    || MASKED_OR_NUMERIC.matcher(w).matches() || w.matches("^[0-9].*[0-9]$");
            if (junk) {
                if (!words.isEmpty()) break; // payee name ended
                continue;
            }
            if (words.isEmpty() && NOISE.contains(w)) continue; // leading "UPI", "POS", "NEFT CR" ...
            words.add(w);
        }
        while (!words.isEmpty() && NOISE.contains(words.get(words.size() - 1))) {
            words.remove(words.size() - 1); // trailing "PAYMENT", "DR" ...
        }
        return String.join(" ", words).replaceAll("^[.&]+|[.&]+$", "");
    }
}
