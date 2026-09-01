package org.example.categorize;

import org.example.config.Category;
import org.example.config.CategorySet;
import org.example.normalize.CategorySource;
import org.example.normalize.Direction;
import org.example.normalize.Transaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategorizerTest {

    private static final CategorySet CATEGORIES = new CategorySet(List.of(
            new Category("Food & Dining", List.of("swiggy", "zomato"), List.of(), false, false, 2, 0),
            new Category("Transport", List.of("uber"), List.of("FASTAG\\s*RECHARGE"), false, false, 4, 1),
            new Category("Groceries", List.of("mart"), List.of(), false, false, 3, 2),
            new Category("Uncategorized", List.of(), List.of(), false, false, null, 3)
    ));

    private static Transaction txn(String description) {
        return new Transaction("id1", LocalDate.of(2026, 8, 1), description, description,
                new BigDecimal("100.00"), "INR", Direction.DEBIT, null, "fixture.csv",
                "Uncategorized", CategorySource.UNCATEGORIZED, false, false, "KEY", 1.0, null, null, 0);
    }

    @Test
    void keywordMatchAssignsRuleCategory() {
        Transaction result = Categorizer.categorizeOne(txn("POS SWIGGY*ORDER 4471"), CATEGORIES);
        assertEquals("Food & Dining", result.category());
        assertEquals(CategorySource.RULE, result.categorySource());
        assertFalse(result.needsReview());
    }

    @Test
    void longestKeywordWinsAcrossCategories() {
        // "mart" (Groceries) is a substring of nothing here directly, but "dmart" style
        // longer keywords should still win over a shorter overlapping keyword when both
        // categories match the same text - use a synthetic overlap to prove length wins.
        CategorySet overlapping = new CategorySet(List.of(
                new Category("Short", List.of("mart"), List.of(), false, false, null, 0),
                new Category("Long", List.of("bigmart"), List.of(), false, false, null, 1)
        ));
        Transaction result = Categorizer.categorizeOne(txn("PAYMENT TO BIGMART STORE"), overlapping);
        assertEquals("Long", result.category());
    }

    @Test
    void patternMatchAppliesOnlyWhenNoKeywordMatched() {
        Transaction result = Categorizer.categorizeOne(txn("FASTAG RECHARGE HDFC"), CATEGORIES);
        assertEquals("Transport", result.category());
        assertEquals(CategorySource.RULE, result.categorySource());
    }

    @Test
    void noMatchFallsBackToUncategorizedAndNeedsReview() {
        Transaction result = Categorizer.categorizeOne(txn("RANDOM MERCHANT XYZ"), CATEGORIES);
        assertEquals("Uncategorized", result.category());
        assertEquals(CategorySource.UNCATEGORIZED, result.categorySource());
        assertTrue(result.needsReview());
    }
}
