package org.example.analysis;

import org.example.normalize.CategorySource;
import org.example.normalize.Direction;
import org.example.normalize.Transaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** C7's FR13 and FR15 functions. */
class TopCategoryAndTransactionsTest {

    @Test
    void topCategoryIsTheLargestSpend() {
        TopCategory top = Analysis.topCategory(List.of(
                cat("Rent/Housing", "15000.00", 62.6, 1),
                cat("Food & Dining", "8148.00", 34.0, 12)), new BigDecimal("23148.00"));

        assertEquals("Rent/Housing", top.name());
        assertEquals(0, new BigDecimal("15000.00").compareTo(top.amount()));
        assertEquals(62.6, top.percentOfSpend());
    }

    @Test
    void tiedAmountsAreBrokenByTransactionCountThenAlphabetically() {
        // The callout has to say the same thing on every rerun of the same input, so the
        // tie-break is specified rather than left to list order.
        TopCategory byCount = Analysis.topCategory(List.of(
                cat("Groceries", "5000.00", 50.0, 3),
                cat("Shopping", "5000.00", 50.0, 9)), new BigDecimal("10000.00"));
        assertEquals("Shopping", byCount.name());

        TopCategory byName = Analysis.topCategory(List.of(
                cat("Shopping", "5000.00", 50.0, 4),
                cat("Groceries", "5000.00", 50.0, 4)), new BigDecimal("10000.00"));
        assertEquals("Groceries", byName.name());
    }

    @Test
    void thereIsNoTopCategoryWhenNothingWasSpent_EC5() {
        assertNull(Analysis.topCategory(List.of(cat("Rent/Housing", "0.00", 0.0, 0)), BigDecimal.ZERO));
    }

    @Test
    void topTransactionsAreTheLargestDebits() {
        List<TransactionRef> top = Analysis.topTransactions(List.of(
                debit("2026-08-01", "RENT", "15000.00"),
                debit("2026-08-02", "SWIGGY", "487.00"),
                debit("2026-08-03", "CROMA", "2100.00")), 5);

        assertEquals(List.of("RENT", "CROMA", "SWIGGY"),
                top.stream().map(TransactionRef::description).toList());
    }

    @Test
    void topTransactionsExcludesCreditsAndTransfers() {
        Transaction credit = new Transaction("c", LocalDate.of(2026, 8, 4), "SALARY", "SALARY",
                new BigDecimal("65000.00"), "INR", Direction.CREDIT, null, "f.csv", "Income",
                CategorySource.RULE, false, false, "SALARY", 1.0, null, null, 0);
        Transaction transfer = new Transaction("t", LocalDate.of(2026, 8, 5), "SELF", "SELF",
                new BigDecimal("20000.00"), "INR", Direction.DEBIT, null, "f.csv", "Transfers/Self",
                CategorySource.RULE, true, false, "SELF", 1.0, null, null, 1);

        List<TransactionRef> top = Analysis.topTransactions(
                List.of(credit, transfer, debit("2026-08-01", "RENT", "15000.00")), 5);

        // FR15 is about spend: income is not a large expense, and a transfer between your own
        // accounts is not an expense at all (FR11).
        assertEquals(List.of("RENT"), top.stream().map(TransactionRef::description).toList());
    }

    @Test
    void topTransactionsIsCappedAtTheRequestedCount() {
        List<Transaction> many = List.of(
                debit("2026-08-01", "A", "100.00"), debit("2026-08-02", "B", "200.00"),
                debit("2026-08-03", "C", "300.00"), debit("2026-08-04", "D", "400.00"),
                debit("2026-08-05", "E", "500.00"), debit("2026-08-06", "F", "600.00"));

        assertEquals(5, Analysis.topTransactions(many, 5).size());
        assertEquals(3, Analysis.topTransactions(many.subList(0, 3), 5).size(), "min(5, n)");
    }

    @Test
    void accountsSummarisesEachSourceSeparately() {
        List<AccountSummary> accounts = Analysis.accounts(List.of(
                debitFrom("HDFC-XXXX1234", "hdfc.csv", "1000.00"),
                debitFrom("HDFC-XXXX1234", "hdfc.csv", "500.00"),
                debitFrom("ICICI-XXXX9087", "icici.csv", "250.00")));

        assertEquals(2, accounts.size());
        assertEquals(2, accounts.get(0).txnCount());
        assertEquals(0, new BigDecimal("1500.00").compareTo(accounts.get(0).spend()));
        assertEquals(0, new BigDecimal("250.00").compareTo(accounts.get(1).spend()));
    }

    private static CategorySpend cat(String name, String amount, double percent, int txnCount) {
        return new CategorySpend(name, new BigDecimal(amount), percent, txnCount);
    }

    private static Transaction debit(String date, String description, String amount) {
        return new Transaction(description, LocalDate.parse(date), description, description,
                new BigDecimal(amount), "INR", Direction.DEBIT, null, "f.csv", "Shopping",
                CategorySource.RULE, false, false, description, 1.0, null, null, 0);
    }

    private static Transaction debitFrom(String account, String file, String amount) {
        return new Transaction(account + amount, LocalDate.of(2026, 8, 1), "X", "X",
                new BigDecimal(amount), "INR", Direction.DEBIT, account, file, "Shopping",
                CategorySource.RULE, false, false, "X", 1.0, null, null, 0);
    }
}
