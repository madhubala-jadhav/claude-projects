package org.example.normalize;

import org.example.config.DedupeSettings;
import org.example.diagnostics.RunReport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** C5 {@code deduplicate} - FR6, EC1. */
class DeduplicatorTest {

    @Test
    void theSameTransactionReadFromTwoFilesIsKeptOnce() {
        List<Transaction> input = List.of(
                txn("2026-08-02", "SWIGGY ORDER 4471", "487.00", Direction.DEBIT, "hdfc_aug2026.pdf", 0),
                txn("2026-08-02", "SWIGGY ORDER 4471", "487.00", Direction.DEBIT, "hdfc_aug2026_copy.pdf", 0));
        RunReport report = new RunReport();

        Deduplicator.Result result = Deduplicator.deduplicate(input, DedupeSettings.defaults(), report);

        assertEquals(1, result.kept().size());
        assertEquals(1, result.removed());
        assertEquals("hdfc_aug2026.pdf", result.kept().get(0).sourceFile());
        // Nothing may vanish without a RunReport entry.
        assertEquals(1, report.duplicatesRemoved().size());
        assertEquals("hdfc_aug2026_copy.pdf", report.duplicatesRemoved().get(0).sourceFile());
    }

    @Test
    void twoIdenticalChargesWithinOneFileAreBothRealAndBothKept() {
        List<Transaction> input = List.of(
                txn("2026-08-02", "CAFE COFFEE DAY", "120.00", Direction.DEBIT, "hdfc_aug2026.csv", 3),
                txn("2026-08-02", "CAFE COFFEE DAY", "120.00", Direction.DEBIT, "hdfc_aug2026.csv", 4));
        RunReport report = new RunReport();

        Deduplicator.Result result = Deduplicator.deduplicate(input, DedupeSettings.defaults(), report);

        // Two coffees on one day are two transactions; only a re-added *file* is a duplicate.
        assertEquals(2, result.kept().size());
        assertEquals(0, result.removed());
    }

    @Test
    void punctuationAndCaseDifferencesBetweenAPdfAndACsvStillMatch() {
        List<Transaction> input = List.of(
                txn("2026-08-02", "POS SWIGGY*ORDER 4471 BANGALORE IN", "487.00", Direction.DEBIT, "a.pdf", 0),
                txn("2026-08-02", "pos swiggy order 4471, bangalore in", "487.00", Direction.DEBIT, "b.csv", 0));

        Deduplicator.Result result = Deduplicator.deduplicate(input, DedupeSettings.defaults(), new RunReport());

        assertEquals(1, result.kept().size());
    }

    @Test
    void aDifferentAmountOnTheSameDayIsADifferentTransaction() {
        List<Transaction> input = List.of(
                txn("2026-08-02", "SWIGGY", "487.00", Direction.DEBIT, "a.pdf", 0),
                txn("2026-08-02", "SWIGGY", "488.00", Direction.DEBIT, "b.csv", 0));

        assertEquals(2, Deduplicator.deduplicate(input, DedupeSettings.defaults(), new RunReport()).kept().size());
    }

    @Test
    void aDebitAndACreditOfTheSameAmountAreNeverConfused() {
        List<Transaction> input = List.of(
                txn("2026-08-02", "ACME", "500.00", Direction.DEBIT, "a.pdf", 0),
                txn("2026-08-02", "ACME", "500.00", Direction.CREDIT, "b.csv", 0));

        // EC3: a refund is never netted against its charge, so it must never be deduped
        // against it either.
        assertEquals(2, Deduplicator.deduplicate(input, DedupeSettings.defaults(), new RunReport()).kept().size());
    }

    @Test
    void aWindowOfDaysMatchesTheSameTransactionPostedOnDifferentDates() {
        List<Transaction> input = List.of(
                txn("2026-08-02", "SWIGGY", "487.00", Direction.DEBIT, "a.pdf", 0),
                txn("2026-08-04", "SWIGGY", "487.00", Direction.DEBIT, "b.csv", 0));

        assertEquals(2, Deduplicator.deduplicate(input, DedupeSettings.defaults(), new RunReport()).kept().size());
        assertEquals(1, Deduplicator.deduplicate(input, new DedupeSettings(true, 3), new RunReport()).kept().size());
    }

    @Test
    void crossFileOnlyDisabledCollapsesRepeatsWithinOneFileToo() {
        List<Transaction> input = List.of(
                txn("2026-08-02", "CAFE COFFEE DAY", "120.00", Direction.DEBIT, "one.csv", 3),
                txn("2026-08-02", "CAFE COFFEE DAY", "120.00", Direction.DEBIT, "one.csv", 4));

        Deduplicator.Result result = Deduplicator.deduplicate(input, new DedupeSettings(false, 0), new RunReport());

        assertEquals(1, result.kept().size());
        assertTrue(result.removed() > 0);
    }

    private static Transaction txn(String date, String description, String amount, Direction direction,
                                   String sourceFile, int rowIndex) {
        return new Transaction(
                "id" + rowIndex + sourceFile, LocalDate.parse(date), description, description,
                new BigDecimal(amount), "INR", direction, null, sourceFile,
                "Uncategorized", CategorySource.UNCATEGORIZED, false, false,
                MerchantKey.of(description), 1.0, null, null, rowIndex);
    }
}
