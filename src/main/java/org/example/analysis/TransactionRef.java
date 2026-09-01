package org.example.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 02-data-model.md §1.2 {@code top_transactions} (FR15). A deliberately narrow projection of
 * {@link org.example.normalize.Transaction}: the top-5 panel needs an identity to link to the
 * drill-down and four fields to show, and nothing else about the transaction belongs in the
 * summary.
 */
public record TransactionRef(
        String id,
        LocalDate date,
        String description,
        BigDecimal amount,
        String category
) {
}
