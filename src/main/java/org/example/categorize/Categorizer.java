package org.example.categorize;

import org.example.config.Category;
import org.example.config.CategorySet;
import org.example.diagnostics.ErrorCode;
import org.example.diagnostics.RunReport;
import org.example.normalize.CategorySource;
import org.example.normalize.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 01-components.md C6 — the full deterministic precedence ladder (ADR-0005).
 *
 * <pre>
 *   L1  corrections.json, scope="transaction", exact Transaction.id  -> user_override
 *   L2  corrections.json, scope="merchant",    exact merchant_key    -> user_override
 *   L3  categories.yaml  overrides:            exact merchant_key    -> rule
 *   L4  categories.yaml  keywords, then regex patterns               -> rule
 *   L5  no match -> "Uncategorized", needs_review = true             (FR8)
 * </pre>
 *
 * <p>ADR-0005 fixes the order and the reason for it: "A correction the user made last month
 * (L1/L2) must survive an edit to categories.yaml, or FR9 and AC4 are false. Within user
 * corrections, a transaction-scoped pin (L1) beats a merchant-wide rule (L2), because it is the
 * more specific statement."</p>
 *
 * <p>01-components.md's own summary of the ladder describes L1 as "exact merchant_key" and L2 as
 * "pattern", which predates ADR-0005's more specific version; the ADR is what this implements.</p>
 *
 * <p>Determinism is the feature, not an implementation detail: "the same input must produce the
 * same categories on every run, or the user cannot trust a correction to stick."</p>
 */
public final class Categorizer {

    private Categorizer() {
    }

    public static List<Transaction> categorizeAll(List<Transaction> transactions, CategorySet categories) {
        return categorizeAll(transactions, categories, null);
    }

    public static List<Transaction> categorizeAll(List<Transaction> transactions, CategorySet categories,
                                                    RunReport runReport) {
        return categorizeAll(transactions, categories, CorrectionsStore.empty(), runReport);
    }

    public static List<Transaction> categorizeAll(List<Transaction> transactions, CategorySet categories,
                                                    CorrectionsStore corrections, RunReport runReport) {
        List<Transaction> result = new ArrayList<>(transactions.size());
        for (Transaction txn : transactions) {
            result.add(categorizeOne(txn, categories, corrections, runReport));
        }
        return result;
    }

    public static Transaction categorizeOne(Transaction txn, CategorySet categories) {
        return categorizeOne(txn, categories, CorrectionsStore.empty(), null);
    }

    public static Transaction categorizeOne(Transaction txn, CategorySet categories, RunReport runReport) {
        return categorizeOne(txn, categories, CorrectionsStore.empty(), runReport);
    }

    public static Transaction categorizeOne(Transaction txn, CategorySet categories,
                                            CorrectionsStore corrections, RunReport runReport) {
        // L1 and L2 - what the user taught the tool. Checked before any rule in categories.yaml,
        // so editing that file never silently discards a correction (ADR-0005).
        Correction correction = corrections == null ? null : corrections.lookup(txn);
        if (correction != null) {
            if (categories.byName(correction.category()) != null) {
                return txn.withCategory(correction.category(), CategorySource.USER_OVERRIDE, false);
            }
            // WARN-501: the correction names a category that no longer exists. It is kept in the
            // file and reported, never deleted - the user renamed a category, they did not ask to
            // forget what they taught. The row falls through to L3/L4 for this run.
            if (runReport != null) {
                runReport.warn(ErrorCode.WARN_501.stage(), ErrorCode.WARN_501,
                        "'" + correction.merchantKey() + "' -> '" + correction.category() + "'");
            }
        }

        // L3 - categories.yaml `overrides:`, an exact merchant_key mapping that beats keywords.
        String overridden = categories.overrideFor(txn.merchantKey());
        if (overridden != null && categories.byName(overridden) != null) {
            return txn.withCategory(overridden, CategorySource.RULE, false);
        }

        String haystack = txn.description().toUpperCase(Locale.ROOT);

        // L4a - keywords: longest match wins across ALL categories; ties break by
        // categories.yaml declaration order.
        Category bestCategory = null;
        int bestLength = -1;
        for (Category category : categories.inOrder()) {
            for (String keyword : category.keywords()) {
                if (keyword == null || keyword.isBlank()) {
                    continue;
                }
                if (haystack.contains(keyword.toUpperCase(Locale.ROOT))) {
                    int length = keyword.length();
                    if (length > bestLength) {
                        bestLength = length;
                        bestCategory = category;
                    }
                }
            }
        }
        if (bestCategory != null) {
            return txn.withCategory(bestCategory.name(), CategorySource.RULE, false);
        }

        // L4b - regex patterns: tried only when no keyword matched anywhere, first
        // category (declared order) whose pattern matches wins.
        for (Category category : categories.inOrder()) {
            for (String pattern : category.patterns()) {
                if (pattern == null || pattern.isBlank()) {
                    continue;
                }
                try {
                    if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(haystack).find()) {
                        return txn.withCategory(category.name(), CategorySource.RULE, false);
                    }
                } catch (PatternSyntaxException e) {
                    // WARN-502: skip the bad rule rather than fail the run.
                    String message = "category '" + category.name() + "' pattern failed to compile: " + pattern;
                    if (runReport != null) {
                        runReport.warn("categorize", message);
                    } else {
                        System.err.println("WARN [" + ErrorCode.WARN_502.code() + "]: " + message);
                    }
                }
            }
        }

        // L5 - no match.
        return txn.withCategory("Uncategorized", CategorySource.UNCATEGORIZED, true);
    }
}
