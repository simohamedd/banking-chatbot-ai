package com.simo.chatbot;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/**
 * Holds a conversation: classifies each message, fills in missing details over several turns,
 * and asks for confirmation before moving money or blocking a card.
 */
public class ChatBot {

    private static final double MIN_CONFIDENCE = 0.25;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM");

    private final IntentClassifier classifier;
    private final Bank bank;

    // Pending conversation state
    private String pending;
    private String toAccount;
    private BigDecimal amount;

    public ChatBot(IntentClassifier classifier, Bank bank) {
        this.classifier = classifier;
        this.bank = bank;
    }

    public String reply(String message) {
        String text = message.trim();
        if (text.isEmpty()) return "Sorry, I didn't catch that.";

        if (pending != null) {
            String r = continuePending(text);
            if (r != null) return r;
        }

        NaiveBayes.Prediction p = classifier.classify(text);
        if (p.confidence() < MIN_CONFIDENCE) {
            return "I'm not sure I understood. I can check your balance, show recent transactions, "
                    + "make a transfer, block a lost card, or tell you branch opening hours.";
        }
        return switch (p.label()) {
            case "greeting" -> "Hello! How can I help with your account today?";
            case "goodbye" -> "Thanks for banking with us. Goodbye!";
            case "balance" -> "Your balance is £" + bank.balance() + ".";
            case "transactions" -> "Your recent transactions:\n" + bank.recent(5).stream()
                    .map(t -> String.format("  %-7s %-22s %s£%s", t.date().format(DAY), t.description(),
                            t.amount().signum() < 0 ? "-" : "+", t.amount().abs()))
                    .collect(Collectors.joining("\n"));
            case "hours" -> "Branches are open Monday to Friday 9am to 5pm, and Saturday 9am to 1pm. They're closed on Sundays.";
            case "block_card" -> {
                if (bank.cardBlocked()) yield "Your card is already blocked.";
                pending = "block";
                yield "I can block your card now. You won't be able to use it until a replacement arrives. Shall I go ahead? (yes/no)";
            }
            case "transfer" -> startTransfer(text);
            default -> "I'm not sure how to help with that yet.";
        };
    }

    private String startTransfer(String text) {
        pending = "transfer";
        toAccount = Entities.account(text).orElse(null);
        amount = Entities.amount(text).orElse(null);
        return nextTransferQuestion();
    }

    private String nextTransferQuestion() {
        if (toAccount == null) return "Which account should I send it to? Please give the 8-digit account number.";
        if (amount == null) return "How much would you like to send to " + bank.payeeName(toAccount) + "?";
        pending = "transfer-confirm";
        return "Send £" + amount + " to " + bank.payeeName(toAccount) + "? (yes/no)";
    }

    private String continuePending(String text) {
        String lower = text.toLowerCase();
        boolean yes = lower.matches("(yes|y|yeah|yep|sure|ok|okay|go ahead|confirm).*");
        boolean no = lower.matches("(no|n|nope|cancel|stop|don't).*");
        switch (pending) {
            case "block" -> {
                if (yes) { bank.blockCard(); clear(); return "Done. Your card is blocked and a replacement will arrive in 3 to 5 working days."; }
                if (no) { clear(); return "OK, I've left your card active."; }
                return "Please answer yes or no: shall I block your card?";
            }
            case "transfer" -> {
                if (no) { clear(); return "OK, I've cancelled that transfer."; }
                if (toAccount == null) toAccount = Entities.account(text).orElse(null);
                else if (amount == null) amount = Entities.amount(text).orElse(null);
                return nextTransferQuestion();
            }
            case "transfer-confirm" -> {
                if (yes) {
                    try {
                        bank.transfer(toAccount, amount);
                        String done = "Sent £" + amount + " to " + bank.payeeName(toAccount) + ". Your new balance is £" + bank.balance() + ".";
                        clear();
                        return done;
                    } catch (IllegalArgumentException e) {
                        clear();
                        return "I couldn't make that transfer: " + e.getMessage() + ".";
                    }
                }
                if (no) { clear(); return "OK, I've cancelled that transfer."; }
                return "Please answer yes or no: send £" + amount + " to " + bank.payeeName(toAccount) + "?";
            }
            default -> { clear(); return null; }
        }
    }

    private void clear() {
        pending = null;
        toAccount = null;
        amount = null;
    }
}
