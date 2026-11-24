package com.simo.chatbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EntitiesTest {

    private static Optional<BigDecimal> money(String value) {
        return Optional.of(new BigDecimal(value));
    }

    @Test
    void readsAmountsWithThousandsSeparatorsInFull() {
        assertEquals(money("1000.00"), Entities.amount("send £1,000 to Yusuf"));
        assertEquals(money("1250.00"), Entities.amount("1,250 pounds"));
        assertEquals(money("2500.50"), Entities.amount("pay 2,500.50 to 12345678"));
        assertEquals(money("12500.75"), Entities.amount("£12,500.75"));
        assertEquals(money("1000000.00"), Entities.amount("£ 1,000,000 please"));
        assertEquals(money("3000.00"), Entities.amount("3,000 quid"));
    }

    @Test
    void groupedAmountIsNeverMistakenForAnAccountNumber() {
        assertEquals(money("12345678.00"), Entities.amount("£12,345,678"));
        assertEquals(Optional.of("87654321"), Entities.account("send £1,000 to 87654321"));
        assertEquals(money("1000.00"), Entities.amount("send £1,000 to 87654321"));
    }

    @Test
    void commasThatAreNotThousandsSeparatorsStillSplitNumbers() {
        assertEquals(money("20.00"), Entities.amount("20, 30"));
        assertEquals(money("20.00"), Entities.amount("£20,30"));
        assertEquals(money("1.00"), Entities.amount("£1,0000"));
    }

    @Test
    void plainAmountsAndAccountsWorkAsBefore() {
        assertEquals(money("20.00"), Entities.amount("send 20 to 12345678"));
        assertEquals(money("15.50"), Entities.amount("pay £15.50 please"));
        assertEquals(money("1000.00"), Entities.amount("£1000"));
        assertEquals(Optional.empty(), Entities.amount("send money to 12345678"));
    }

    @Test
    void readsHowManyTransactionsWereAskedFor() {
        assertEquals(Optional.of(3), Entities.transactionCount("show my last 3 transactions"));
        assertEquals(Optional.of(10), Entities.transactionCount("my 10 most recent payments"));
        assertEquals(Optional.of(4), Entities.transactionCount("past 4 purchases please"));
        assertEquals(Optional.of(1), Entities.transactionCount("last 1 payment"));
        assertEquals(Optional.empty(), Entities.transactionCount("show my recent transactions"));
        assertEquals(Optional.empty(), Entities.transactionCount("last 0 payments"));
    }

    @Test
    void transferConfirmsTheFullGroupedAmount() {
        ChatBot bot = new ChatBot(IntentClassifier.fromResource(), Bank.demo());
        String reply = bot.reply("send £1,000 to Yusuf");
        assertTrue(reply.contains("Send £1000.00 to Yusuf (flatmate)?"), reply);
    }
}
