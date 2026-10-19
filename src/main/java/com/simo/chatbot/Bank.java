package com.simo.chatbot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** A tiny in-memory bank for the chatbot to talk to. */
public class Bank {

    public record Payment(LocalDate date, String description, BigDecimal amount) { }

    private final String ownAccount;
    private BigDecimal balance;
    private boolean cardBlocked;
    private final List<Payment> payments = new ArrayList<>();
    private final Map<String, String> payees = new LinkedHashMap<>();

    public Bank(String ownAccount, BigDecimal openingBalance) {
        this.ownAccount = ownAccount;
        this.balance = openingBalance;
    }

    public static Bank demo() {
        Bank b = new Bank("10203040", new BigDecimal("1250.00"));
        LocalDate today = LocalDate.now();
        b.payments.add(new Payment(today.minusDays(1), "Tesco", new BigDecimal("-32.18")));
        b.payments.add(new Payment(today.minusDays(2), "Salary", new BigDecimal("2100.00")));
        b.payments.add(new Payment(today.minusDays(3), "TfGM travel", new BigDecimal("-5.80")));
        b.payments.add(new Payment(today.minusDays(4), "Netflix", new BigDecimal("-10.99")));
        b.payees.put("12345678", "Savings pot");
        b.payees.put("87654321", "Yusuf (flatmate)");
        return b;
    }

    public BigDecimal balance() { return balance; }
    public boolean cardBlocked() { return cardBlocked; }
    public String ownAccount() { return ownAccount; }
    public String payeeName(String account) { return payees.getOrDefault(account, "account " + account); }

    /** Finds a saved payee whose name appears in the message, for example "Yusuf" in "send £20 to yusuf". */
    public Optional<String> payeeMentionedIn(String text) {
        String lower = " " + text.toLowerCase(Locale.UK).replaceAll("[^a-z0-9]+", " ") + " ";
        for (var e : payees.entrySet()) {
            for (String word : e.getValue().toLowerCase(Locale.UK).split("[^a-z0-9]+")) {
                if (word.length() >= 3 && lower.contains(" " + word + " ")) return Optional.of(e.getKey());
            }
        }
        return Optional.empty();
    }

    public List<Payment> recent(int n) {
        return payments.stream().sorted((a, b) -> b.date().compareTo(a.date())).limit(n).toList();
    }

    public void transfer(String to, BigDecimal amount) {
        if (amount.signum() <= 0) throw new IllegalArgumentException("The amount needs to be more than £0");
        if (to.equals(ownAccount)) throw new IllegalArgumentException("That's your own account");
        if (amount.compareTo(balance) > 0) throw new IllegalArgumentException("You only have £" + balance + " available");
        balance = balance.subtract(amount);
        payments.add(new Payment(LocalDate.now(), "Transfer to " + payeeName(to), amount.negate()));
    }

    public void blockCard() {
        cardBlocked = true;
    }
}
