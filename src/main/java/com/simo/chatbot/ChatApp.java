package com.simo.chatbot;

import java.util.Scanner;

/** Chat with the banking assistant in the terminal. Run with: mvn -q compile exec:java */
public class ChatApp {
    public static void main(String[] args) {
        ChatBot bot = new ChatBot(IntentClassifier.fromResource(), Bank.demo());
        System.out.println("Bank assistant. Try: \"what's my balance\", \"send £20 to 12345678\", \"I lost my card\". Type quit to leave.");
        Scanner in = new Scanner(System.in);
        while (true) {
            System.out.print("\nYou: ");
            if (!in.hasNextLine()) return;
            String line = in.nextLine();
            if (line.equalsIgnoreCase("quit")) return;
            System.out.println("Bot: " + bot.reply(line));
        }
    }
}
