package com.example.productsearch.model;

import java.util.List;
import java.util.Map;

public record SearchExplanation(
        String query,
        Map<String, Double> queryTokens,
        List<ScoredResult> results
) {
    public record ScoredResult(
            Product product,
            double score,
            double maxScore
    ) {}
}
