package org.example.parse;

/** 01-components.md C4. */
public record RawRow(
        String sourceFile,
        Integer sourcePage,
        int rowIndex,
        String dateText,
        String descriptionText,
        String debitText,
        String creditText,
        String amountText,
        String balanceText,
        String accountHint,
        double confidence,
        String extractor
) {
}
