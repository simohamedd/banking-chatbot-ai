package com.simo.chatbot;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pulls amounts and account numbers out of a message. */
public final class Entities {

    private static final Pattern ACCOUNT = Pattern.compile("\\b(\\d{8})\\b");
    /** Digits grouped in thousands with commas ("1,000", "12,500"), not followed by another digit. */
    private static final String GROUPED = "\\d{1,3}(?:,\\d{3})+(?!\\d)";
    private static final String PENCE = "(?:\\.\\d{1,2})?";
    private static final Pattern AMOUNT = Pattern.compile(
            "£\\s?((?:" + GROUPED + "|\\d+)" + PENCE + ")"
            + "|\\b((?:" + GROUPED + "|\\d+)" + PENCE + ")\\s?(?:pounds?|quid|gbp)\\b"
            + "|\\b((?:" + GROUPED + "|\\d{1,6})" + PENCE + ")\\b",
            Pattern.CASE_INSENSITIVE);

    private Entities() {
    }

    public static Optional<String> account(String text) {
        Matcher m = ACCOUNT.matcher(text);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    /**
     * The first amount in the message that isn't an 8-digit account number. Thousands separators are
     * understood ("£1,250.50" is 1250.50); a comma followed by a space ("20, 30") still separates two numbers.
     */
    public static Optional<BigDecimal> amount(String text) {
        Matcher m = AMOUNT.matcher(text);
        while (m.find()) {
            String v = m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3);
            if (v.length() == 8 && !v.contains(".") && !v.contains(",")) continue;
            return Optional.of(new BigDecimal(v.replace(",", "")).setScale(2, java.math.RoundingMode.HALF_UP));
        }
        return Optional.empty();
    }
}
