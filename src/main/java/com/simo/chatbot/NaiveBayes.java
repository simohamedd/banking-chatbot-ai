package com.simo.chatbot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Multinomial naive Bayes text classifier with Laplace smoothing, written from scratch. */
public class NaiveBayes {

    public record Prediction(String label, double confidence) { }

    private final Map<String, Integer> docsPerLabel = new LinkedHashMap<>();
    private final Map<String, Map<String, Integer>> wordCounts = new HashMap<>();
    private final Map<String, Integer> totalWords = new HashMap<>();
    private final Set<String> vocabulary = new HashSet<>();
    private int docs;

    public void learn(String label, List<String> tokens) {
        docs++;
        docsPerLabel.merge(label, 1, Integer::sum);
        Map<String, Integer> counts = wordCounts.computeIfAbsent(label, k -> new HashMap<>());
        for (String t : tokens) {
            counts.merge(t, 1, Integer::sum);
            totalWords.merge(label, 1, Integer::sum);
            vocabulary.add(t);
        }
    }

    /** Most likely label, with its probability among all labels. */
    public Prediction predict(List<String> tokens) {
        if (docs == 0) throw new IllegalStateException("No training data");
        Map<String, Double> logP = new LinkedHashMap<>();
        int v = vocabulary.size();
        for (String label : docsPerLabel.keySet()) {
            double lp = Math.log((double) docsPerLabel.get(label) / docs);
            Map<String, Integer> counts = wordCounts.get(label);
            int total = totalWords.getOrDefault(label, 0);
            for (String t : tokens) {
                if (!vocabulary.contains(t)) continue;
                lp += Math.log((counts.getOrDefault(t, 0) + 1.0) / (total + v));
            }
            logP.put(label, lp);
        }
        double max = logP.values().stream().mapToDouble(d -> d).max().orElse(0);
        double sum = 0;
        String best = null;
        double bestP = -1;
        for (var e : logP.entrySet()) {
            double p = Math.exp(e.getValue() - max);
            sum += p;
            if (p > bestP) { bestP = p; best = e.getKey(); }
        }
        boolean knownWord = tokens.stream().anyMatch(vocabulary::contains);
        return new Prediction(best, knownWord ? bestP / sum : 0);
    }

    public Set<String> labels() {
        return docsPerLabel.keySet();
    }
}
