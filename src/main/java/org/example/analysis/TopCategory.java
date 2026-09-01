package org.example.analysis;

import java.math.BigDecimal;

/**
 * 02-data-model.md §1.2 {@code top_category} - the FR13/AC3 answer.
 *
 * <p>AC3 requires the report to <em>state</em> the top category, its amount and its share "not
 * just visually implied by chart size", so these three values exist as data and are rendered as
 * literal text in the callout. {@code null} for the whole record means {@code total_spend == 0}
 * (EC5), which is why the callout has an empty state rather than a zeroed one.</p>
 */
public record TopCategory(String name, BigDecimal amount, double percentOfSpend) {
}
