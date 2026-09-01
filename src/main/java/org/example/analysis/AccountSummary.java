package org.example.analysis;

import java.math.BigDecimal;

/**
 * 02-data-model.md §1.2 {@code accounts} - "Serves US6/FR6/AC1: shows the merge actually
 * happened."
 *
 * <p>{@code sourceAccount} is already masked to its last four digits by the time it reaches
 * here (§13); this record never sees a full account number.</p>
 */
public record AccountSummary(
        String sourceAccount,
        String sourceFile,
        int txnCount,
        BigDecimal spend
) {
}
