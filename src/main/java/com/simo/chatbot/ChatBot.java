package com.simo.chatbot;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Holds a conversation: classifies each message, fills in missing details over several turns,
 * and asks for confirmation before moving money or blocking a card.
 */
public class ChatBot {

    private static final double MIN_CONFIDENCE = 0.25;
    // A side question asked mid-conversation has to be a clearer match, so a stray reply isn't mistaken for one.
    private static final double SIDE_QUESTION_CONFIDENCE = 0.4;
    private static final java.util.Set<String> SIDE_QUESTIONS = java.util.Set.of("balance", "transactions", "hours");
    private static final int DEFAULT_TRANSACTIONS = 5;
    private static final int MAX_TRANSACTIONS = 20;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM");

    // A yes or no only counts as a whole word at the start of the reply, so "yusuf" or "now what?" are not answers.
    private static final Pattern YES = Pattern.compile("^(yes|y|yeah|yep|yup|sure|ok|okay|go ahead|confirm(ed)?|do it)\\b.*");
    private static final Pattern NO = Pattern.compile("^(no|n|nope|nah|cancel|stop|don't|do not|not now)\\b.*");

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

        NaiveBayes.Prediction p = classifier.classify(text);

        if (pending != null) {
            if (isSideQuestion(text, p)) return answer(p.label(), text) + "\n" + pendingQuestion();
            String r = continuePending(text);
            if (r != null) return r;
        }

        if (p.confidence() < MIN_CONFIDENCE) {
            return "I'm not sure I understood. I can check your balance, show recent transactions, "
                    + "make a transfer, block or unblock a card, or tell you branch opening hours.";
        }
        return switch (p.label()) {
            case "greeting" -> "Hello! How can I help with your account today?";
            case "goodbye" -> "Thanks for banking with us. Goodbye!";
            case "balance", "transactions", "hours" -> answer(p.label(), text);
            case "block_card" -> {
                if (bank.cardBlocked()) yield "Your card is already blocked.";
                pending = "block";
                yield "I can block your card now. You won't be able to use it until a replacement arrives. Shall I go ahead? (yes/no)";
            }
            case "unblock_card" -> {
                if (!bank.cardBlocked()) yield "Your card isn't blocked, so there's nothing to undo.";
                pending = "unblock";
                yield "Glad you found it. Shall I unblock your card so you can use it again? (yes/no)";
            }
            case "transfer" -> startTransfer(text);
            default -> "I'm not sure how to help with that yet.";
        };
    }

    /** Answers a question that only reads information, so it is safe to answer mid-conversation. */
    private String answer(String intent, String text) {
        return switch (intent) {
            case "balance" -> "Your balance is £" + bank.balance() + ".";
            case "transactions" -> transactions(text);
            case "hours" -> "Branches are open Monday to Friday 9am to 5pm, and Saturday 9am to 1pm. They're closed on Sundays.";
            default -> throw new IllegalArgumentException(intent);
        };
    }

    /** Lists recent payments, as many as the customer asked for ("my last 3 transactions") up to a limit. */
    private String transactions(String text) {
        var asked = Entities.transactionCount(text);
        int n = asked.map(c -> Math.min(c, MAX_TRANSACTIONS)).orElse(DEFAULT_TRANSACTIONS);
        var list = bank.recent(n);
        if (list.isEmpty()) return "You don't have any transactions yet.";
        String heading = asked.isEmpty() ? "Your recent transactions:"
                : list.size() == 1 ? "Your last transaction:"
                : "Your last " + list.size() + " transactions:";
        if (asked.isPresent() && asked.get() > MAX_TRANSACTIONS)
            heading = "I can show up to " + MAX_TRANSACTIONS + " at a time. " + heading;
        return heading + "\n" + list.stream()
                .map(t -> String.format("  %-7s %-22s %s£%s", t.date().format(DAY), t.description(),
                        t.amount().signum() < 0 ? "-" : "+", t.amount().abs()))
                .collect(Collectors.joining("\n"));
    }

    /**
     * True when a reply to a pending question is really a quick question of its own, like "what's my balance?".
     * Yes/no answers and replies carrying an amount, account or payee are always treated as answers.
     */
    private boolean isSideQuestion(String text, NaiveBayes.Prediction p) {
        if (!SIDE_QUESTIONS.contains(p.label()) || p.confidence() < SIDE_QUESTION_CONFIDENCE) return false;
        if (isYes(text) || isNo(text)) return false;
        return Entities.amount(text).isEmpty() && Entities.account(text).isEmpty()
                && bank.payeeMentionedIn(text).isEmpty();
    }

    /** The question the bot is waiting on, repeated after answering a side question. */
    private String pendingQuestion() {
        return switch (pending) {
            case "block" -> "Shall I still block your card? (yes/no)";
            case "unblock" -> "Shall I still unblock your card? (yes/no)";
            case "transfer" -> toAccount == null
                    ? "Who should I send it to? Give a saved payee's name or an 8-digit account number."
                    : "How much would you like to send to " + bank.payeeName(toAccount) + "?";
            case "transfer-confirm" -> "Send £" + amount + " to " + bank.payeeName(toAccount) + "? (yes/no)";
            default -> "";
        };
    }

    private String startTransfer(String text) {
        pending = "transfer";
        toAccount = Entities.account(text).or(() -> bank.payeeMentionedIn(text)).orElse(null);
        amount = Entities.amount(text).orElse(null);
        return nextTransferQuestion();
    }

    private String nextTransferQuestion() {
        // Check each detail as soon as we have it, and ask again for just that one if it won't work.
        String problem = "";
        if (toAccount != null) {
            var p = bank.recipientProblem(toAccount);
            if (p.isPresent()) { problem = p.get() + ". "; toAccount = null; }
        }
        if (amount != null) {
            var p = bank.amountProblem(amount);
            if (p.isPresent()) { problem += p.get() + ". "; amount = null; }
        }
        if (toAccount == null) return problem + "Who should I send it to? Give a saved payee's name or an 8-digit account number.";
        if (amount == null) return problem + "How much would you like to send to " + bank.payeeName(toAccount) + "?";
        pending = "transfer-confirm";
        return "Send £" + amount + " to " + bank.payeeName(toAccount) + "? (yes/no)";
    }

    private String continuePending(String text) {
        boolean yes = isYes(text);
        boolean no = isNo(text);
        switch (pending) {
            case "block" -> {
                if (yes) { bank.blockCard(); clear(); return "Done. Your card is blocked and a replacement will arrive in 3 to 5 working days."; }
                if (no) { clear(); return "OK, I've left your card active."; }
                return "Please answer yes or no: shall I block your card?";
            }
            case "unblock" -> {
                if (yes) { bank.unblockCard(); clear(); return "Done. Your card is active again."; }
                if (no) { clear(); return "OK, your card stays blocked."; }
                return "Please answer yes or no: shall I unblock your card?";
            }
            case "transfer" -> {
                if (no) { clear(); return "OK, I've cancelled that transfer."; }
                // Fill in whatever is still missing, so "£50 to Yusuf" answers both questions at once.
                if (toAccount == null) toAccount = Entities.account(text).or(() -> bank.payeeMentionedIn(text)).orElse(null);
                if (amount == null) amount = Entities.amount(text).orElse(null);
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

    static boolean isYes(String text) {
        return YES.matcher(normalise(text)).matches();
    }

    static boolean isNo(String text) {
        return NO.matcher(normalise(text)).matches();
    }

    private static String normalise(String text) {
        return text.trim().toLowerCase(Locale.UK).replace('\u2019', '\'');
    }

    private void clear() {
        pending = null;
        toAccount = null;
        amount = null;
    }
}
