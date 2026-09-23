package org.example.report.e2e;

import org.example.analysis.Analysis;
import org.example.analysis.CategorySpend;
import org.example.analysis.MonthlySummary;
import org.example.config.Category;
import org.example.config.CategorySet;
import org.example.diagnostics.ErrorCode;
import org.example.diagnostics.RunReport;
import org.example.normalize.CategorySource;
import org.example.normalize.Direction;
import org.example.normalize.Transaction;
import org.example.report.ReportRenderer;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The report these tests drive, built the way {@code Cli} builds a real one.
 *
 * <p>The numbers come out of {@link Analysis} rather than being written down here on purpose.
 * The parity test in {@link SliceParityE2ETest} compares what {@code report.js} recomputes from
 * the transactions against what {@code ChartModel} rendered from the summary; a hand-written
 * summary whose category totals did not actually add up from its own transactions would make
 * that comparison fail for a reason that has nothing to do with the code under test.</p>
 *
 * <p>Every merchant, account and amount below is invented (§13). The shape is realistic - a
 * rent payment that dominates, an unslotted category that has to fold into "Other", a
 * zero-spend category that must leave the chart but stay in the table (EC6), two uncategorized
 * debits with <em>different</em> merchant keys, and a credit - but none of it is anyone's
 * actual spending.</p>
 */
final class ReportFixture {

    static final String MONTH = "2026-08";
    static final String CURRENCY = "INR";
    static final String ACCOUNT = "TESTBANK-XXXX0001";
    static final String SOURCE_FILE = "testbank_aug2026.csv";

    /** The two uncategorized rows the corrections tests act on. */
    static final String UNCATEGORIZED_ID = "t-11";
    static final String UNCATEGORIZED_MERCHANT_KEY = "RANDOMSHOP";
    static final String SECOND_UNCATEGORIZED_ID = "t-12";
    static final String SECOND_UNCATEGORIZED_MERCHANT_KEY = "QUICKMART";

    /** A file that could not be read, so AC7's "listed by name with a reason" has something to show. */
    static final String SKIPPED_FILE = "truncated_statement.pdf";

    private ReportFixture() {
    }

    /**
     * The 12 shipped categories (default-config/categories.yaml), plus an Income category so
     * the credit below has somewhere to sit that is not the uncategorized review list. Slots
     * 1-8 are the palette; a null slot folds into "Other".
     */
    static CategorySet categories() {
        List<Category> categories = new ArrayList<>();
        categories.add(category("Rent/Housing", 1, 0));
        categories.add(category("Food & Dining", 2, 1));
        categories.add(category("Groceries", 3, 2));
        categories.add(category("Transport", 4, 3));
        categories.add(category("Shopping", 5, 4));
        categories.add(category("Utilities", 6, 5));
        categories.add(category("EMI/Loan Payments", 7, 6));
        categories.add(category("Healthcare", 8, 7));
        categories.add(category("Entertainment", null, 8));
        categories.add(category("Investments/Savings", null, 9));
        categories.add(new Category("Transfers/Self", List.of(), List.of(), true, false, null, 10));
        categories.add(new Category("Income", List.of(), List.of(), false, true, null, 11));
        categories.add(category("Uncategorized", null, 12));
        return new CategorySet(categories);
    }

    private static Category category(String name, Integer slot, int order) {
        return new Category(name, List.of(), List.of(), false, false, slot, order);
    }

    static List<Transaction> transactions() {
        List<Transaction> txns = new ArrayList<>();
        // Slots 1-8: exactly eight coloured slices, so the fixture sits on ADR-0007 rule 4's cap.
        txns.add(debit("t-01", 1, "RENT AUGUST", "NEFT DR-TESTBANK-RENT AUGUST", "15000.00", "Rent/Housing", "RENT AUGUST"));
        txns.add(debit("t-02", 3, "NOODLE HOUSE", "POS NOODLE HOUSE 41", "2400.00", "Food & Dining", "NOODLE HOUSE"));
        txns.add(debit("t-03", 9, "NOODLE HOUSE", "POS NOODLE HOUSE 41", "1800.00", "Food & Dining", "NOODLE HOUSE"));
        txns.add(debit("t-04", 4, "FRESHCART", "UPI-FRESHCART-ORDER", "3200.00", "Groceries", "FRESHCART"));
        txns.add(debit("t-05", 6, "CITY CABS", "UPI-CITY CABS-RIDE", "950.00", "Transport", "CITY CABS"));
        txns.add(debit("t-06", 11, "THREADBARE", "POS THREADBARE ONLINE", "2750.00", "Shopping", "THREADBARE"));
        txns.add(debit("t-07", 14, "POWERGRID BILL", "BILLPAY POWERGRID BILL", "1450.00", "Utilities", "POWERGRID BILL"));
        txns.add(debit("t-08", 5, "CAR LOAN EMI", "ACH DR-CAR LOAN EMI", "6500.00", "EMI/Loan Payments", "CAR LOAN EMI"));
        txns.add(debit("t-09", 18, "WELLNESS PHARMACY", "POS WELLNESS PHARMACY", "1100.00", "Healthcare", "WELLNESS PHARMACY"));
        // Unslotted but with spend: rule 4 folds these two into "Other", which must stay
        // drillable (FR19) and fully itemised in the table while it does.
        txns.add(debit("t-10", 2, "STREAMFLIX", "UPI-STREAMFLIX-SUB", "499.00", "Entertainment", "STREAMFLIX"));
        txns.add(debit("t-14", 7, "INDEX FUND SIP", "ACH DR-INDEX FUND SIP", "2000.00",
                "Investments/Savings", "INDEX FUND SIP"));
        // NFR6's accuracy gap. Two distinct merchant keys, so a correction on one must not move the other.
        txns.add(debit(UNCATEGORIZED_ID, 12, "RANDOMSHOP XYZ", "POS RANDOMSHOP XYZ12345", "799.00",
                "Uncategorized", UNCATEGORIZED_MERCHANT_KEY));
        txns.add(needsReview(debit(SECOND_UNCATEGORIZED_ID, 20, "QUICKMART 42", "POS QUICKMART 42 CHENNAI",
                "1250.00", "Uncategorized", SECOND_UNCATEGORIZED_MERCHANT_KEY)));
        // A credit: it must stay out of spend, out of the chart, and render with a leading "+"
        // in the drill-down. Investments/Savings and Transfers/Self stay at zero spend (EC6).
        txns.add(new Transaction("t-13", LocalDate.parse("2026-08-28"), "SALARY AUGUST", "NEFT CR-SALARY AUGUST",
                new BigDecimal("65000.00"), CURRENCY, Direction.CREDIT, ACCOUNT, SOURCE_FILE,
                "Income", CategorySource.RULE, false, false, "SALARY AUGUST", 1.0, null, null, 13));
        return List.copyOf(txns);
    }

    private static Transaction debit(String id, int day, String description, String raw, String amount,
                                     String category, String merchantKey) {
        return new Transaction(id, LocalDate.of(2026, 8, day), description, raw, new BigDecimal(amount),
                CURRENCY, Direction.DEBIT, ACCOUNT, SOURCE_FILE, category,
                "Uncategorized".equals(category) ? CategorySource.UNCATEGORIZED : CategorySource.RULE,
                false, false, merchantKey, 1.0, null, null, Integer.parseInt(id.substring(2)));
    }

    private static Transaction needsReview(Transaction txn) {
        return new Transaction(txn.id(), txn.date(), txn.description(), txn.rawDescription(), txn.amount(),
                txn.currency(), txn.direction(), txn.sourceAccount(), txn.sourceFile(), txn.category(),
                txn.categorySource(), txn.isTransfer(), true, txn.merchantKey(), txn.confidence(),
                txn.originalAmount(), txn.originalCurrency(), txn.rowIndex());
    }

    /** Assembled exactly as {@code Cli} assembles the real one, so the fixture cannot drift from it. */
    static MonthlySummary summary(List<Transaction> transactions) {
        Analysis.Totals totals = Analysis.computeTotals(transactions);
        List<CategorySpend> byCategory = Analysis.spendByCategory(transactions, categories(), totals.totalSpend());
        CategorySpend uncategorized = byCategory.stream()
                .filter(c -> "Uncategorized".equals(c.name()))
                .findFirst()
                .orElse(new CategorySpend("Uncategorized", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), 0.0, 0));

        return new MonthlySummary(
                1, MONTH, CURRENCY,
                // Fixed, so the header line is stable and nothing here depends on the wall clock.
                LocalDateTime.of(2026, 9, 1, 20, 14),
                totals.totalSpend(), totals.totalIncome(), totals.net(),
                Analysis.topCategory(byCategory, totals.totalSpend()),
                byCategory,
                Analysis.topTransactions(transactions, 5),
                uncategorized.txnCount(), uncategorized.amount(),
                Analysis.needsReviewCount(transactions),
                Analysis.transfersTotal(transactions),
                transactions.size(),
                Analysis.accounts(transactions),
                null); // FR14 is Slice 6: S01's documented "no prior month" state.
    }

    /** A run report carrying one unreadable file, so AC7 has something to assert against. */
    static RunReport runReport() {
        RunReport runReport = new RunReport();
        runReport.excludeFile(SKIPPED_FILE, ErrorCode.PARSE_208);
        return runReport;
    }

    /** Renders the standard report into {@code dir} and returns the {@code file:} URL to open. */
    static String render(Path dir) throws IOException {
        List<Transaction> transactions = transactions();
        Path report = ReportRenderer.renderReport(summary(transactions), transactions, runReport(),
                8, 1.0, dir.resolve("output"), categories(), dir.resolve("input"));
        // The footer and the CSV tile link to it; a 404 in the same folder would be a different bug.
        Files.writeString(report.resolveSibling("transactions.csv"), "id,date\n");
        return report.toUri().toString();
    }

    /** EC5 / S07: a run that parsed nothing still owes the user a real report. */
    static String renderEmpty(Path dir) throws IOException {
        RunReport runReport = new RunReport();
        runReport.excludeFile(SKIPPED_FILE, ErrorCode.PARSE_208);
        MonthlySummary empty = new MonthlySummary(
                1, MONTH, CURRENCY, LocalDateTime.of(2026, 9, 1, 20, 14),
                zero(), zero(), zero(), null, List.of(), List.of(), 0, zero(), 0, zero(), 0, List.of(), null);
        Path report = ReportRenderer.renderReport(empty, List.of(), runReport, 8, 1.0,
                dir.resolve("output"), categories(), dir.resolve("input"));
        return report.toUri().toString();
    }

    /** The absolute path ADR-0006 requires the saved-tray to state literally. */
    static String inputPath(Path dir) {
        return dir.resolve("input").toAbsolutePath().toString();
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
