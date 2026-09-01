package org.example.config;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The 12 FR10 categories (plus any the user adds), preserving categories.yaml's declaration
 * order - the ladder's determinism depends on that order being stable.
 *
 * <p>{@code overrides} is the ladder's L3 layer: an exact merchant_key to category mapping that
 * beats every keyword rule. Keys are matched case-insensitively because 02-data-model.md §3.2
 * writes them lowercase ({@code "amazon pay": "Shopping"}) while a merchant_key is uppercase.</p>
 */
public record CategorySet(List<Category> categories, Map<String, String> overrides) {

    public CategorySet(List<Category> categories) {
        this(categories, Map.of());
    }

    /** L3: the category this merchant_key is pinned to by categories.yaml, or null. */
    public String overrideFor(String merchantKey) {
        if (merchantKey == null || merchantKey.isBlank() || overrides.isEmpty()) {
            return null;
        }
        return overrides.get(merchantKey.trim().toLowerCase(Locale.ROOT));
    }

    public List<Category> inOrder() {
        return categories;
    }

    public Category byName(String name) {
        for (Category c : categories) {
            if (c.name().equals(name)) {
                return c;
            }
        }
        return null;
    }
}
