package com.tenahub.bot.util;

import com.tenahub.bot.dto.MedicineSuggestionResult;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class MedicineSuggestionEngine {

    private static final int MAX_TYPO_SUGGESTIONS = 5;
    private static final int MAX_ALTERNATIVE_SUGGESTIONS = 5;

    private static final Map<String, List<String>> RELATED_MEDICINES = Map.ofEntries(
            Map.entry("paracetamol", List.of("ibuprofen", "aspirin")),
            Map.entry("ibuprofen", List.of("paracetamol", "diclofenac")),
            Map.entry("cetirizine", List.of("loratadine", "chlorpheniramine")),
            Map.entry("loratadine", List.of("cetirizine", "chlorpheniramine")),
            Map.entry("omeprazole", List.of("famotidine", "ranitidine")),
            Map.entry("metformin", List.of("glibenclamide", "glimepiride")),
            Map.entry("salbutamol", List.of("chlorpheniramine", "loratadine"))
    );

    private MedicineSuggestionEngine() {
    }

    public static MedicineSuggestionResult build(String input, Collection<String> catalogMedicines) {
        String queryKey = MedicineSearchNormalizer.normalizeSearchKey(input);
        String canonicalInput = MedicineSearchNormalizer.normalizeToEnglishCanonical(input);

        if (queryKey.isBlank()) {
            return new MedicineSuggestionResult(canonicalInput, List.of(), List.of());
        }

        Set<String> catalog = catalogMedicines == null
                ? Set.of()
                : catalogMedicines.stream()
                        .map(MedicineSearchNormalizer::normalizeToEnglishCanonical)
                        .filter(medicine -> !medicine.isBlank())
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        if (catalog.contains(canonicalInput)) {
            return new MedicineSuggestionResult(canonicalInput, List.of(), buildAlternatives(canonicalInput, List.of(), catalog));
        }

        List<ScoredCandidate> scoredCandidates = catalog.stream()
                .map(candidate -> scoreCandidate(candidate, queryKey, canonicalInput))
                .filter(candidate -> candidate.score() > 0)
                .sorted(Comparator
                        .comparingInt(ScoredCandidate::score).reversed()
                        .thenComparingInt(ScoredCandidate::distance)
                        .thenComparing(ScoredCandidate::canonical))
                .limit(MAX_TYPO_SUGGESTIONS)
                .toList();

        List<String> typoSuggestions = scoredCandidates.stream()
                .map(ScoredCandidate::canonical)
                .distinct()
                .limit(MAX_TYPO_SUGGESTIONS)
                .toList();

        List<String> alternativeSuggestions = buildAlternatives(canonicalInput, typoSuggestions, catalog);

        return new MedicineSuggestionResult(canonicalInput, typoSuggestions, alternativeSuggestions);
    }

    private static ScoredCandidate scoreCandidate(String candidate, String queryKey, String canonicalInput) {
        int bestScore = 0;
        int bestDistance = Integer.MAX_VALUE;

        for (String form : MedicineSearchNormalizer.searchFormsForCanonical(candidate)) {
            String normalizedForm = MedicineSearchNormalizer.normalizeSearchKey(form);
            MatchScore rawScore = match(queryKey, normalizedForm);
            if (rawScore.score() > bestScore || (rawScore.score() == bestScore && rawScore.distance() < bestDistance)) {
                bestScore = rawScore.score();
                bestDistance = rawScore.distance();
            }

            if (!canonicalInput.equals(queryKey)) {
                MatchScore canonicalScore = match(canonicalInput, normalizedForm);
                if (canonicalScore.score() > bestScore || (canonicalScore.score() == bestScore && canonicalScore.distance() < bestDistance)) {
                    bestScore = canonicalScore.score();
                    bestDistance = canonicalScore.distance();
                }
            }
        }

        return new ScoredCandidate(candidate, bestScore, bestDistance == Integer.MAX_VALUE ? 99 : bestDistance);
    }

    private static MatchScore match(String queryKey, String candidateKey) {
        if (queryKey.isBlank() || candidateKey.isBlank()) {
            return new MatchScore(0, 99);
        }

        if (candidateKey.equals(queryKey)) {
            return new MatchScore(100, 0);
        }

        if (candidateKey.startsWith(queryKey) || queryKey.startsWith(candidateKey)) {
            return new MatchScore(85, Math.abs(candidateKey.length() - queryKey.length()));
        }

        if (candidateKey.contains(queryKey) || queryKey.contains(candidateKey)) {
            return new MatchScore(72, Math.abs(candidateKey.length() - queryKey.length()));
        }

        int distance = levenshteinDistance(candidateKey, queryKey);
        int maxLength = Math.max(candidateKey.length(), queryKey.length());
        if (distance <= 1) {
            return new MatchScore(68, distance);
        }
        if (distance == 2) {
            return new MatchScore(60, distance);
        }
        if (distance == 3 && maxLength >= 8) {
            return new MatchScore(50, distance);
        }

        return new MatchScore(0, 99);
    }

    private static List<String> buildAlternatives(String canonicalInput, List<String> typoSuggestions, Set<String> catalog) {
        LinkedHashSet<String> seeds = new LinkedHashSet<>();
        if (!canonicalInput.isBlank()) {
            seeds.add(canonicalInput);
        }
        if (!typoSuggestions.isEmpty()) {
            seeds.add(typoSuggestions.get(0));
        }

        LinkedHashSet<String> alternatives = new LinkedHashSet<>();
        for (String seed : seeds) {
            for (String medicine : RELATED_MEDICINES.getOrDefault(seed, List.of())) {
                String normalized = MedicineSearchNormalizer.normalizeToEnglishCanonical(medicine);
                if (catalog.contains(normalized) && !normalized.equals(canonicalInput) && !typoSuggestions.contains(normalized)) {
                    alternatives.add(normalized);
                }
            }
        }

        return alternatives.stream()
                .limit(MAX_ALTERNATIVE_SUGGESTIONS)
                .toList();
    }

    private static int levenshteinDistance(String left, String right) {
        int[][] dp = new int[left.length() + 1][right.length() + 1];

        for (int i = 0; i <= left.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= right.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= left.length(); i++) {
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[left.length()][right.length()];
    }

    private record MatchScore(int score, int distance) {
    }

    private record ScoredCandidate(String canonical, int score, int distance) {
    }
}