package com.simo.chatbot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Loads the training phrases and works out what a customer is asking for. */
public class IntentClassifier {

    public record Example(String intent, String text) { }

    private final NaiveBayes model = new NaiveBayes();

    public IntentClassifier(List<Example> examples) {
        for (Example e : examples) model.learn(e.intent(), Tokenizer.tokens(e.text()));
    }

    public static IntentClassifier fromResource() {
        return new IntentClassifier(loadExamples());
    }

    public static List<Example> loadExamples() {
        InputStream in = IntentClassifier.class.getResourceAsStream("/intents.txt");
        if (in == null) throw new IllegalStateException("intents.txt is missing from resources");
        List<Example> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] parts = line.split("\t", 2);
                out.add(new Example(parts[0].trim(), parts[1].trim()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    public NaiveBayes.Prediction classify(String text) {
        return model.predict(Tokenizer.tokens(text));
    }
}
