package org.example.normalize;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * F1 (03-interfaces-and-contracts.md): the Transaction shape is frozen from v1 onward - the
 * 12 spec fields plus the 7 architect additions (02-data-model.md §1.1). {@code category}/
 * {@code categorySource}/{@code isTransfer}/{@code needsReview} start at C5's defaults
 * ("Uncategorized"/uncategorized/false/false) and are finalized by C6 via
 * {@link #withCategory}, mirroring the S5/S6 producer-consumer contract for an otherwise
 * immutable record.
 */
public record Transaction(
        String id,
        LocalDate date,
        String description,
        String rawDescription,
        BigDecimal amount,
        String currency,
        Direction direction,
        String sourceAccount,
        String sourceFile,
        String category,
        CategorySource categorySource,
        boolean isTransfer,
        boolean needsReview,
        String merchantKey,
        double confidence,
        BigDecimal originalAmount,
        String originalCurrency,
        int rowIndex
) {
    public Transaction withCategory(String category, CategorySource categorySource, boolean needsReview) {
        return new Transaction(id, date, description, rawDescription, amount, currency, direction,
                sourceAccount, sourceFile, category, categorySource, isTransfer, needsReview,
                merchantKey, confidence, originalAmount, originalCurrency, rowIndex);
    }
}
