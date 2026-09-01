package org.example.analysis;

import org.example.config.Category;
import org.example.config.CategorySet;
import org.example.normalize.CategorySource;
import org.example.normalize.Direction;
import org.example.normalize.Transaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnalysisTest {

    private static Transaction debit(String category, String amount) {
        return new Transaction("id", LocalDate.of(2026, 8, 1), "desc", "desc",
                new BigDecimal(amount), "INR", Direction.DEBIT, null, "f.csv",
                category, CategorySource.RULE, false, false, "KEY", 1.0, null, null, 0);
    }

    private static Transaction credit(String amount) {
        return new Transaction("id", LocalDate.of(2026, 8, 1), "desc", "desc",
                new BigDecimal(amount), "INR", Direction.CREDIT, null, "f.csv",
                "Uncategorized", CategorySource.UNCATEGORIZED, false, false, "KEY", 1.0, null, null, 0);
    }

    @Test
    void computeTotalsSumsDebitsAndCreditsExactly() {
        List<Transaction> txns = List.of(
                debit("Food & Dining", "100.50"),
                debit("Food & Dining", "200.25"),
                credit("500.00")
        );

        Analysis.Totals totals = Analysis.computeTotals(txns);

        assertEquals(0, new BigDecimal("300.75").compareTo(totals.totalSpend()));
        assertEquals(0, new BigDecimal("500.00").compareTo(totals.totalIncome()));
        assertEquals(0, new BigDecimal("199.25").compareTo(totals.net()));
    }

    @Test
    void spendByCategoryPercentagesSumToExactlyOneHundred() {
        // A fixture whose raw shares don't round cleanly, per ADR-0018's own verification
        // example: naive per-row rounding would sum to 100.1 or 99.9 here.
        CategorySet categories = new CategorySet(List.of(
                new Category("A", List.of(), List.of(), false, false, 1, 0),
                new Category("B", List.of(), List.of(), false, false, 2, 1),
                new Category("C", List.of(), List.of(), false, false, 3, 2)
        ));
        List<Transaction> txns = List.of(
                debit("A", "100.00"),
                debit("B", "100.00"),
                debit("C", "100.01")
        );
        Analysis.Totals totals = Analysis.computeTotals(txns);

        List<CategorySpend> byCategory = Analysis.spendByCategory(txns, categories, totals.totalSpend());

        double sum = 0.0;
        for (CategorySpend c : byCategory) {
            sum += c.percent();
        }
        assertEquals(100.0, sum, 0.0001);
    }

    @Test
    void hdfcFixtureCategoryAmountsSumToTotalSpend() {
        // Mirrors the shipped end-to-end fixture's hand-computed totals
        // (see EndToEndSliceOneTest for the full pipeline version of this check).
        CategorySet categories = new CategorySet(List.of(
                new Category("Rent/Housing", List.of(), List.of(), false, false, 1, 0),
                new Category("EMI/Loan Payments", List.of(), List.of(), false, false, 7, 1),
                new Category("Uncategorized", List.of(), List.of(), false, false, null, 2)
        ));
        List<Transaction> txns = List.of(
                debit("Rent/Housing", "15000.00"),
                debit("EMI/Loan Payments", "8000.00"),
                debit("Uncategorized", "799.00")
        );
        Analysis.Totals totals = Analysis.computeTotals(txns);
        List<CategorySpend> byCategory = Analysis.spendByCategory(txns, categories, totals.totalSpend());

        BigDecimal sum = BigDecimal.ZERO;
        for (CategorySpend c : byCategory) {
            sum = sum.add(c.amount());
        }
        assertEquals(0, totals.totalSpend().compareTo(sum));
    }
}
