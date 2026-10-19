package com.simo.chatbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatBotTest {

    private ChatBot bot;
    private Bank bank;

    @BeforeEach
    void setUp() {
        bank = Bank.demo();
        bot = new ChatBot(IntentClassifier.fromResource(), bank);
    }

    @Test
    void classifiesPhrasesItHasNeverSeen() {
        IntentClassifier c = IntentClassifier.fromResource();
        Map<String, String> unseen = Map.of(
                "how much money is in my account right now", "balance",
                "please send £40 to 87654321", "transfer",
                "my card has been stolen", "block_card",
                "show what i spent recently", "transactions",
                "when does the branch open on saturday", "hours",
                "hello there", "greeting",
                "ok thanks bye", "goodbye");
        unseen.forEach((text, intent) -> assertEquals(intent, c.classify(text).label(), text));
    }

    @Test
    void leaveOneOutAccuracyIsHigh() {
        List<IntentClassifier.Example> all = IntentClassifier.loadExamples();
        int correct = 0;
        for (int i = 0; i < all.size(); i++) {
            List<IntentClassifier.Example> rest = new ArrayList<>(all);
            IntentClassifier.Example held = rest.remove(i);
            if (new IntentClassifier(rest).classify(held.text()).label().equals(held.intent())) correct++;
        }
        double acc = (double) correct / all.size();
        assertTrue(acc > 0.8, "leave-one-out accuracy " + acc);
    }

    @Test
    void extractsAmountsAndAccounts() {
        assertEquals(Optional.of("12345678"), Entities.account("send 20 to 12345678"));
        assertEquals(Optional.of(new BigDecimal("20.00")), Entities.amount("send 20 to 12345678"));
        assertEquals(Optional.of(new BigDecimal("15.50")), Entities.amount("pay £15.50 please"));
        assertEquals(Optional.of(new BigDecimal("30.00")), Entities.amount("30 quid"));
        assertEquals(Optional.empty(), Entities.amount("send money to 12345678"));
    }

    @Test
    void transferAsksForMissingDetailsThenConfirms() {
        assertTrue(bot.reply("I want to send some money").contains("Who should I send it to"));
        assertTrue(bot.reply("12345678").contains("How much"));
        assertTrue(bot.reply("£50").contains("Send £50.00 to Savings pot?"));
        assertTrue(bot.reply("yes").contains("new balance is £1200.00"));
        assertEquals(new BigDecimal("1200.00"), bank.balance());
    }

    @Test
    void transferCanBeCancelledAndChecksFunds() {
        bot.reply("transfer £20 to 87654321");
        assertTrue(bot.reply("no").contains("cancelled"));
        assertEquals(new BigDecimal("1250.00"), bank.balance());

        bot.reply("send £5000 to 87654321");
        assertTrue(bot.reply("yes").contains("only have"));
    }

    @Test
    void blocksCardOnlyAfterConfirmation() {
        assertTrue(bot.reply("I lost my card").contains("Shall I go ahead"));
        assertTrue(!bank.cardBlocked());
        bot.reply("yes");
        assertTrue(bank.cardBlocked());
    }

    @Test
    void admitsWhenItDoesNotUnderstand() {
        assertTrue(bot.reply("xyzzy plugh").contains("not sure"));
    }

    @Test
    void transfersToASavedPayeeByName() {
        assertTrue(bot.reply("send £20 to Yusuf").contains("Send £20.00 to Yusuf (flatmate)?"));
        bot.reply("yes");
        assertEquals(new BigDecimal("1230.00"), bank.balance());

        assertTrue(bot.reply("transfer some money").contains("Who should I send it to"));
        assertTrue(bot.reply("my savings pot").contains("How much would you like to send to Savings pot?"));
    }

    @Test
    void unblocksAFoundCardAfterConfirmation() {
        assertTrue(bot.reply("unblock my card").contains("isn't blocked"));
        bot.reply("my card was stolen");
        bot.reply("yes");
        assertTrue(bank.cardBlocked());
        assertTrue(bot.reply("good news, I found my card").contains("Shall I unblock"));
        assertTrue(bank.cardBlocked());
        assertTrue(bot.reply("yes").contains("active again"));
        assertTrue(!bank.cardBlocked());
    }
}
