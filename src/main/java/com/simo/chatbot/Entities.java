package com.simo.chatbot;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pulls amounts and account numbers out of a message. */
public final class Entities {

    private static final Pattern ACCOUNT = Pattern.compile("\\b(\\d{8})\\b");
    private static final Pattern AMOUNT = Pattern.compile(
            "£\\s?(\\d+(?:\\.\\d{1,2})?)|\\b(\\d+(?:\\.\\d{1,2})?)\\s?(?:pounds?|quid|gbp)\\b|\\b(\\d{1,6}(?:\\.\\d{1,2})?)\\b",
            Pattern.CASE_INSENSITIVE);

    private Entities() {
    }

    public static Optional<String> account(String text) {
        Matcher m = ACCOUNT.matcher(text);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    /** The first amount in the message that isn't an 8-digit account number. */
    public static Optional<BigDecimal> amount(String text) {
        Matcher m = AMOUNT.matcher(text);
        while (m.find()) {
            String v = m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3);
            if (v.length() == 8 && !v.contains(".")) continue;
            return Optional.of(new BigDecimal(v).setScale(2, java.math.RoundingMode.HALF_UP));
        }
        return Optional.empty();
    }
}
