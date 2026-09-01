package org.example.normalize;

import org.example.config.BankProfile;
import org.example.parse.RawRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RowParserTest {

    private static final BankProfile PROFILE = new BankProfile(
            "hdfc_savings_csv", List.of("csv"),
            new BankProfile.Match(List.of(), List.of()),
            java.util.Map.of(),
            "DMY", List.of("dd/MM/yy", "dd/MM/yyyy"),
            ".", ",", BankProfile.SIGN_SEPARATE_COLUMNS, List.of(), null, List.of(), null
    );

    /** The validated HDFC credit-card layout: one amount column, money-in flagged with "+". */
    private static final BankProfile CARD_PROFILE = new BankProfile(
            "hdfc_credit_card_pdf", List.of("pdf"),
            new BankProfile.Match(List.of(), List.of()),
            java.util.Map.of(),
            "DMY", List.of("dd/MM/yyyy"),
            ".", ",", BankProfile.SIGN_DR_CR_SUFFIX, List.of("+"), null, List.of(), null
    );

    @Test
    void parsesCommaThousandsAmountExactlyAsBigDecimal() {
        RawRow row = new RawRow("hdfc_aug2026.csv", null, 0, "01/08/26", "SOME MERCHANT",
                "12,345.67", null, null, null, null, 1.0, "tabular");

        Transaction txn = RowParser.parseRow(row, PROFILE, "INR");

        assertEquals(0, new BigDecimal("12345.67").compareTo(txn.amount()));
        assertEquals(Direction.DEBIT, txn.direction());
    }

    @Test
    void parsesDdMmYyDate() {
        RawRow row = new RawRow("hdfc_aug2026.csv", null, 0, "05/08/26", "SOME MERCHANT",
                "100.00", null, null, null, null, 1.0, "tabular");

        Transaction txn = RowParser.parseRow(row, PROFILE, "INR");

        assertEquals(LocalDate.of(2026, 8, 5), txn.date());
    }

    @Test
    void creditColumnProducesCreditDirection() {
        RawRow row = new RawRow("hdfc_aug2026.csv", null, 0, "16/08/26", "SALARY",
                null, "65000.00", null, null, null, 1.0, "tabular");

        Transaction txn = RowParser.parseRow(row, PROFILE, "INR");

        assertEquals(Direction.CREDIT, txn.direction());
        assertEquals(0, new BigDecimal("65000.00").compareTo(txn.amount()));
    }

    @Test
    void unparseableDateThrowsRowParseException() {
        RawRow row = new RawRow("hdfc_aug2026.csv", null, 0, "not-a-date", "SOME MERCHANT",
                "100.00", null, null, null, null, 1.0, "tabular");

        org.junit.jupiter.api.Assertions.assertThrows(RowParser.RowParseException.class,
                () -> RowParser.parseRow(row, PROFILE, "INR"));
    }

    @Test
    void singleAmountColumnDefaultsToDebitAndStripsTheCurrencyGlyph() {
        RawRow row = new RawRow("hdfc_aug2026.pdf", 1, 0, "11/07/2026| 14:02", "IND*ADOBE",
                null, null, "\u20B9 1,315.00", null, null, 1.0, "pdf_text");

        Transaction txn = RowParser.parseRow(row, CARD_PROFILE, "INR");

        assertEquals(Direction.DEBIT, txn.direction());
        assertEquals(0, new BigDecimal("1315.00").compareTo(txn.amount()));
        // The time half of the cell must not defeat the date: dropping this row would lose a
        // real transaction over punctuation.
        assertEquals(LocalDate.of(2026, 7, 11), txn.date());
    }

    @Test
    void configuredCreditMarkerFlipsASingleColumnAmountToCredit() {
        RawRow row = new RawRow("hdfc_aug2026.pdf", 1, 2, "16/07/2026| 07:18", "CREDIT CARD PAYMENT",
                null, null, "+ \u20B9 47,890.65", null, null, 1.0, "pdf_text");

        Transaction txn = RowParser.parseRow(row, CARD_PROFILE, "INR");

        // Getting this wrong counts a card bill payment as spend and inflates the month by
        // an order of magnitude, so the fixture asserts the exact figure.
        assertEquals(Direction.CREDIT, txn.direction());
        assertEquals(0, new BigDecimal("47890.65").compareTo(txn.amount()));
    }

    @Test
    void trailingCrSuffixIsTheDefaultCreditMarker() {
        BankProfile suffixProfile = new BankProfile(
                "generic", List.of("csv"), new BankProfile.Match(List.of(), List.of()),
                java.util.Map.of(), "DMY", List.of("dd/MM/yyyy"),
                ".", ",", BankProfile.SIGN_DR_CR_SUFFIX, List.of(), null, List.of(), null);
        RawRow row = new RawRow("bank.csv", null, 0, "01/08/2026", "REFUND",
                null, null, "1,200.00 Cr", null, null, 1.0, "tabular");

        assertEquals(Direction.CREDIT, RowParser.parseRow(row, suffixProfile, "INR").direction());
    }

    @Test
    void parenthesisedAmountIsNegativeUnderDebitNegative() {
        BankProfile signedProfile = new BankProfile(
                "generic", List.of("csv"), new BankProfile.Match(List.of(), List.of()),
                java.util.Map.of(), "DMY", List.of("dd/MM/yyyy"),
                ".", ",", BankProfile.SIGN_DEBIT_NEGATIVE, List.of(), null, List.of(), null);
        RawRow row = new RawRow("bank.csv", null, 0, "01/08/2026", "ATM WITHDRAWAL",
                null, null, "(2,000.00)", null, null, 1.0, "tabular");

        Transaction txn = RowParser.parseRow(row, signedProfile, "INR");

        assertEquals(Direction.DEBIT, txn.direction());
        // amount is always a positive magnitude; direction carries the sign (02 §1.1)
        assertEquals(0, new BigDecimal("2000.00").compareTo(txn.amount()));
    }

    @Test
    void rowWithNeitherDebitNorCreditIsNull() {
        RawRow row = new RawRow("hdfc_aug2026.csv", null, 0, "01/08/26", "EMPTY ROW",
                null, null, null, null, null, 1.0, "tabular");

        assertNull(RowParser.parseAmount(row, PROFILE));
    }
}
