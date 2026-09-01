package org.example.analysis;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 02-data-model.md §1.2 - the derived answer, and the shape F2 freezes.
 *
 * <p>C7's rule is that this record holds <em>every</em> number the report shows and nothing it
 * does not: S7 says C8 "formats; it never computes a number that is not already in the
 * summary". A figure that appears on the page but not here is a bug in this record, not a
 * convenience in the template.</p>
 *
 * <p>Two fields are structurally present but not yet computed, each waiting on the slice that
 * owns it: {@code previousMonth} is null and every {@link CategorySpend} carries
 * {@code momStatus == "unavailable"} until FR14 lands in Slice 6, and {@code transfersTotal}
 * stays zero until FR11's transfer detection lands in Slice 7. Both are states §1.2 already
 * defines, so the report renders them honestly rather than hiding the sections.</p>
 */
public record MonthlySummary(
        int schemaVersion,
        String month,
        String currency,
        LocalDateTime generatedAt,
        BigDecimal totalSpend,
        BigDecimal totalIncome,
        BigDecimal net,
        TopCategory topCategory,
        List<CategorySpend> byCategory,
        List<TransactionRef> topTransactions,
        int uncategorizedCount,
        BigDecimal uncategorizedAmount,
        int needsReviewCount,
        BigDecimal transfersTotal,
        int transactionCount,
        List<AccountSummary> accounts,
        String previousMonth
) {
}
