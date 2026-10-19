package com.simo.chatbot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Lower-cases text, splits it into words, drops filler words and trims simple English endings. */
public final class Tokenizer {

    private static final Set<String> STOP = Set.of(
            "a", "an", "the", "to", "of", "in", "on", "is", "it", "my", "me", "i", "you", "can", "please",
            "and", "or", "for", "do", "does", "are", "am", "be", "what", "how", "s", "that", "this", "some");

    private Tokenizer() {
    }

    public static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        for (String raw : text.toLowerCase(Locale.UK).split("[^a-z0-9£]+")) {
            if (raw.isEmpty() || STOP.contains(raw)) continue;
            if (raw.matches("£?\\d+(\\.\\d+)?")) { out.add("<num>"); continue; }
            out.add(stem(raw));
        }
        return out;
    }

    static String stem(String w) {
        if (w.length() > 5 && w.endsWith("ing")) return w.substring(0, w.length() - 3);
        if (w.length() > 4 && w.endsWith("ed")) return w.substring(0, w.length() - 2);
        if (w.length() > 4 && w.endsWith("ies")) return w.substring(0, w.length() - 3) + "y";
        if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss")) return w.substring(0, w.length() - 1);
        return w;
    }
}
