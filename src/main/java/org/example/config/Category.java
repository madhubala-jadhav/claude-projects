package org.example.config;

import java.util.List;

/**
 * 02-data-model.md §1.3. {@code order} is the category's declaration position in
 * categories.yaml, used as the categorization ladder's deterministic tie-breaker (FR7).
 */
public record Category(
        String name,
        List<String> keywords,
        List<String> patterns,
        boolean isTransfer,
        boolean isIncome,
        Integer slot,
        int order
) {
}
