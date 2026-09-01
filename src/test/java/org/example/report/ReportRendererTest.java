package org.example.report;

import org.example.analysis.AccountSummary;
import org.example.analysis.CategorySpend;
import org.example.analysis.MonthlySummary;
import org.example.analysis.TopCategory;
import org.example.analysis.TransactionRef;
import org.example.diagnostics.RunReport;
import org.example.normalize.CategorySource;
import org.example.normalize.Direction;
import org.example.normalize.Transaction;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** C8 — the rendered artifact (FR13, FR16, FR17, FR18, NFR1/AC8, AC2, AC3, AC5). */
class ReportRendererTest {

    static MonthlySummary fixtureSummary() {
        List<CategorySpend> byCategory = List.of(
                new CategorySpend("Rent/Housing", new BigDecimal("15000.00"), 62.6, 1, 1, null, "unavailable"),
                new CategorySpend("Food & Dining", new BigDecimal("8148.00"), 34.0, 12, 2, null, "unavailable"),
                new CategorySpend("Uncategorized", new BigDecimal("799.00"), 3.4, 1, null, null, "unavailable"),
                new CategorySpend("Entertainment", new BigDecimal("0.00"), 0.0, 0, null, null, "unavailable"));
        return new MonthlySummary(1, "2026-08", "INR", LocalDateTime.of(2026, 9, 1, 20, 14),
                new BigDecimal("23947.00"), new BigDecimal("65000.00"), new BigDecimal("41053.00"),
                new TopCategory("Rent/Housing", new BigDecimal("15000.00"), 62.6),
                byCategory,
                List.of(new TransactionRef("abc123", LocalDate.of(2026, 8, 1), "RENT TRANSFER",
                        new BigDecimal("15000.00"), "Rent/Housing")),
                1, new BigDecimal("799.00"), 0, new BigDecimal("0.00"), 14,
                List.of(new AccountSummary("HDFC-XXXX1234", "hdfc_aug2026.csv", 14, new BigDecimal("23947.00"))),
                null);
    }

    static List<Transaction> fixtureTransactions() {
        return List.of(new Transaction("abc123", LocalDate.of(2026, 8, 1), "RANDOMSHOP XYZ", "POS RANDOMSHOP XYZ12345",
                new BigDecimal("799.00"), "INR", Direction.DEBIT, "HDFC-XXXX1234", "hdfc_aug2026.csv",
                "Uncategorized", CategorySource.UNCATEGORIZED, false, false, "RANDOMSHOP", 1.0, null, null, 12));
    }

    private static String render() throws IOException {
        return ReportRenderer.render(fixtureSummary(), fixtureTransactions(), new RunReport(), 8, 1.0);
    }

    @Test
    void statesTheTopCategoryAsLiteralText_AC3() throws IOException {
        String html = render();

        // AC3 is explicit that the top category must be stated, "not just visually implied by
        // chart size" - so the name, the formatted amount and the percentage all appear as text.
        assertTrue(html.contains("Rent/Housing:"), "callout should name the category");
        assertTrue(html.contains("15,000.00"), "callout should state the amount");
        assertTrue(html.contains("62.6% of your spend"), "callout should state the share");
    }

    @Test
    void flagsUncategorizedInBothTheTileAndTheTable_AC5() throws IOException {
        String html = render();

        // AC5: "flagged in both the summary table and a count/callout" - two places, not one.
        assertTrue(html.contains("tile warning"), "the uncategorized stat tile should be present");
        assertTrue(html.contains("1 · ₹799.00"), "the tile should carry both count and amount");
        assertTrue(html.contains("is-uncategorized"), "the table row should be badged");
    }

    @Test
    void embedsAPieChartAndItsData_AC2() throws IOException {
        String html = render();

        assertTrue(html.contains("id=\"spend-chart\""), "the chart canvas should be present");
        assertTrue(html.contains("Chart.js v4"), "Chart.js should be inlined, not linked");
        assertTrue(html.contains("\"kind\":\"slot\""), "the data island should carry the slices");
    }

    @Test
    void excludesZeroSpendCategoriesFromTheChartButKeepsThemInTheTable_EC6() throws IOException {
        String html = render();

        assertTrue(html.contains("Entertainment"), "a zero-spend category stays in the table for FR14 history");
        assertFalse(html.contains("\"name\":\"Entertainment\",\"amount\":\"0.00\",\"percent\":0.0,\"txn_count\":0,\"kind\""),
                "a zero-spend category must not be drawn as a slice");
    }

    @Test
    void containsNoExternalReferences_AC8() throws IOException {
        String html = render().toLowerCase(Locale.ROOT);

        assertFalse(html.contains("src=\"http"));
        assertFalse(html.contains("href=\"http"));
        assertFalse(html.contains("@import url(http"));
        // The report must not be able to call home even if something later adds code to it.
        assertFalse(html.contains("fetch("));
    }

    @Test
    void colourComesFromTheDesignTokensRatherThanHardCodedHex() throws IOException {
        String html = render();

        // ADR-0007's anti-drift rule: the palette reaches the page as custom properties emitted
        // from tokens.json, so the report and the Figma file cannot disagree.
        assertTrue(html.contains("--chart-series-slot1:#2a78d6"), "slot 1 should come from tokens.json");
        assertTrue(html.contains("var(--chart-series-slot1)"), "the page should reference the token, not a literal");
    }

    @Test
    void offersEveryRowAsAKeyboardReachableDrillDownTarget_FR19() throws IOException {
        String html = render();

        // S01: "the canvas is not focusable, the rows are. This is the keyboard-accessible
        // equivalent of FR19."
        assertTrue(html.contains("data-category=\"Rent/Housing\""));
        assertTrue(html.contains("tabindex=\"0\""));
        assertTrue(html.contains("id=\"drill\""));
    }

    @Test
    void rendersTheEmptyStateWhenNothingParsed_EC5() throws IOException {
        MonthlySummary empty = new MonthlySummary(1, "2026-08", "INR", LocalDateTime.of(2026, 9, 1, 20, 14),
                new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00"), null,
                List.of(), List.of(), 0, new BigDecimal("0.00"), 0, new BigDecimal("0.00"), 0, List.of(), null);

        String html = ReportRenderer.render(empty, List.of(), new RunReport(), 8, 1.0);

        assertTrue(html.contains("No transactions found"));
        assertFalse(html.contains("Your largest category"), "there is no top category to claim");
    }
}
