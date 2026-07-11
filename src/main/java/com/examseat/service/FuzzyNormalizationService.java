package com.examseat.service;

import org.springframework.stereotype.Service;
import org.apache.commons.text.similarity.LevenshteinDistance;

import java.util.*;

@Service
public class FuzzyNormalizationService {

    private static final double DEFAULT_THRESHOLD = 0.80;
    private static final LevenshteinDistance levenshtein = new LevenshteinDistance();

    private static final Map<String, List<String>> HEADER_MAPPINGS = new HashMap<>();
    private static final Map<String, List<String>> VALUE_MAPPINGS = new HashMap<>();

    static {
        HEADER_MAPPINGS.put("department", Arrays.asList("dept", "department", "branch", "stream", "discipline", "course", "program"));
        HEADER_MAPPINGS.put("courseCode", Arrays.asList("course code", "sub code", "subject code", "coursecode", "paper id", "code", "paper code"));
        HEADER_MAPPINGS.put("subject", Arrays.asList("subject", "subject name", "paper", "course name", "title", "subject title"));
        HEADER_MAPPINGS.put("date", Arrays.asList("date", "exam date", "day", "scheduled date", "examination date", "exam day"));
        HEADER_MAPPINGS.put("session", Arrays.asList("session", "fn/an", "time", "slot", "forenoon", "afternoon", "fn", "an", "morning"));
        HEADER_MAPPINGS.put("year", Arrays.asList("year", "yr", "semester", "sem", "academic year", "class"));

        VALUE_MAPPINGS.put("session", Arrays.asList("fn", "forenoon", "morning", "an", "afternoon", "1", "2"));
    }

    public String mapHeader(String inputHeader) {
        if (inputHeader == null || inputHeader.trim().isEmpty()) {
            return "UNKNOWN";
        }

        String normalized = inputHeader.toLowerCase().trim();
        
        for (Map.Entry<String, List<String>> entry : HEADER_MAPPINGS.entrySet()) {
            for (String variant : entry.getValue()) {
                double similarity = calculateSimilarity(normalized, variant);
                if (similarity >= DEFAULT_THRESHOLD) {
                    return entry.getKey();
                }
                if (normalized.contains(variant) || variant.contains(normalized)) {
                    return entry.getKey();
                }
            }
        }
        
        return findBestMatch(normalized, HEADER_MAPPINGS.keySet());
    }

    public String mapValue(String fieldName, String value) {
        if (value == null || value.trim().isEmpty()) {
            return value;
        }

        List<String> variants = VALUE_MAPPINGS.get(fieldName);
        if (variants == null) {
            return value.trim();
        }

        String normalized = value.toLowerCase().trim();
        
        for (String variant : variants) {
            if (normalized.equals(variant) || normalized.contains(variant)) {
                if (fieldName.equals("session")) {
                    if (variant.equals("fn") || variant.equals("forenoon") || variant.equals("morning") || variant.equals("1")) {
                        return "FN";
                    }
                    return "AN";
                }
            }
        }
        
        if (fieldName.equals("session")) {
            if (normalized.contains("fn") || normalized.contains("morning") || normalized.contains("a") || normalized.contains("forenoon")) {
                return "FN";
            }
            return "AN";
        }

        return value.trim();
    }

    public double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }
        if (s1.isEmpty() && s2.isEmpty()) {
            return 1.0;
        }
        
        int maxLength = Math.max(s1.length(), s2.length());
        if (maxLength == 0) {
            return 1.0;
        }
        
        int distance = levenshtein.apply(s1, s2);
        return 1.0 - ((double) distance / maxLength);
    }

    public double getFieldConfidence(String fieldName, String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0.0;
        }

        String normalized = value.toLowerCase().trim();
        
        List<String> variants = HEADER_MAPPINGS.getOrDefault(fieldName, Collections.emptyList());
        if (variants.isEmpty()) {
            return "UNKNOWN".equals(fieldName) ? 0.0 : 0.9;
        }

        for (String variant : variants) {
            double similarity = calculateSimilarity(normalized, variant);
            if (similarity >= DEFAULT_THRESHOLD) {
                return similarity;
            }
        }

        return calculateBestMatchScore(normalized, variants);
    }

    private double calculateBestMatchScore(String input, List<String> candidates) {
        double bestScore = 0.0;
        for (String candidate : candidates) {
            double score = calculateSimilarity(input, candidate);
            bestScore = Math.max(bestScore, score);
        }
        return bestScore;
    }

    private String findBestMatch(String input, Set<String> candidates) {
        double bestScore = 0.0;
        String bestMatch = "UNKNOWN";
        
        for (String candidate : candidates) {
            double score = calculateSimilarity(input, candidate);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = candidate;
            }
        }
        
        return bestScore >= 0.5 ? bestMatch : "UNKNOWN";
    }

    public Map<String, Double> calculateAllFieldConfidences(Map<String, String> extractedFields) {
        Map<String, Double> confidences = new HashMap<>();
        
        for (Map.Entry<String, String> entry : extractedFields.entrySet()) {
            confidences.put(entry.getKey(), getFieldConfidence(entry.getKey(), entry.getValue()));
        }
        
        return confidences;
    }
}