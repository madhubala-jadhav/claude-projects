package org.example.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import org.example.analysis.AccountSummary;
import org.example.analysis.CategorySpend;
import org.example.analysis.MonthlySummary;
import org.example.analysis.TransactionRef;
import org.example.config.Category;
import org.example.config.CategorySet;
import org.example.diagnostics.ErrorCode;
import org.example.diagnostics.ExcludedFile;
import org.example.diagnostics.RunReport;
import org.example.normalize.Transaction;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 01-components.md C8 {@code render_report}/{@code open_in_browser}.
 *
 * <p>S7 is the rule this class is built around: "C8 formats; it never computes a number that is
 * not already in the summary." Everything numeric here arrives from {@link MonthlySummary};
 * what is decided locally is presentation only - which categories become slices
 * ({@link ChartModel}), how money is written, and what the page does when clicked.</p>
 *
 * <p>The output is one self-contained file: stylesheet, script and the whole of Chart.js are
 * inlined, and {@link #assertNoExternalReferences} refuses to write anything that reaches out
 * to the network. ADR-0007: a CDN reference "would not merely be a broken chart offline - it
 * would be an outbound request carrying the report's existence to a third party, breaking AC8's
 * promise on the very artifact that is supposed to embody it."</p>
 */
public final class ReportRenderer {

    private static final PebbleEngine ENGINE = new PebbleEngine.Builder().build();
    private static final String[] BANNED_REFERENCES = {"src=\"http", "href=\"http", "@import url(http", "fetch("};

    private static final String CHART_ASSET = "/assets/chart.umd.js";
    private static final String VERSION = "1.0";

    /**
     * The report loads no web font (NFR1). tokens.json names Inter and Roboto Mono as its
     * <em>Figma</em> proxies and says so in its own description; these are the real stacks the
     * shipped report uses, which is why they are declared here rather than emitted from tokens.
     */
    private static final String FONT_STACKS =
            ":root{--font-sans:system-ui,-apple-system,\"Segoe UI\",Roboto,Helvetica,Arial,sans-serif;"
                    + "--font-mono:ui-monospace,\"Cascadia Mono\",\"Segoe UI Mono\",\"Roboto Mono\",Menlo,Consolas,monospace;}";

    private ReportRenderer() {
    }

    public static Path renderReport(MonthlySummary summary, List<Transaction> transactions, RunReport runReport,
                                    int topNSlices, double minSlicePercent, Path outputDir,
                                    CategorySet categories, Path inputDir) throws IOException {
        Path monthDir = outputDir.resolve(summary.month());
        Files.createDirectories(monthDir);
        Path reportPath = monthDir.resolve("report.html");

        String html = render(summary, transactions, runReport, topNSlices, minSlicePercent, categories, inputDir);
        assertNoExternalReferences(html); // AC8/NFR1: mechanical proof, before it ever hits disk.
        Files.writeString(reportPath, html, StandardCharsets.UTF_8);
        return reportPath;
    }

    /** Convenience overload for callers and tests that only have a summary. */
    public static Path renderReport(MonthlySummary summary, Path outputDir) throws IOException {
        return renderReport(summary, List.of(), new RunReport(), 8, 1.0, outputDir,
                new CategorySet(List.of()), outputDir.resolveSibling("input"));
    }

    public static String render(MonthlySummary summary, List<Transaction> transactions, RunReport runReport,
                                int topNSlices, double minSlicePercent) throws IOException {
        return render(summary, transactions, runReport, topNSlices, minSlicePercent,
                new CategorySet(List.of()), Path.of("input"));
    }

    public static String render(MonthlySummary summary, List<Transaction> transactions, RunReport runReport,
                                int topNSlices, double minSlicePercent, CategorySet categories, Path inputDir)
            throws IOException {
        try {
            return renderOrThrow(summary, transactions, runReport, topNSlices, minSlicePercent,
                    categories, inputDir);
        } catch (RuntimeException e) {
            // C8's failure mode: "Template error -> falls back to a minimal, guaranteed-
            // renderable HTML that shows the summary numbers and the diagnostics, so the user
            // always gets something (NFR3)."
            runReport.warn("report", "the full report template failed to render; a minimal report was written instead");
            return MinimalReport.render(summary, runReport);
        }
    }

    private static String renderOrThrow(MonthlySummary summary, List<Transaction> transactions, RunReport runReport,
                                        int topNSlices, double minSlicePercent, CategorySet categories,
                                        Path inputDir) throws IOException {
        PebbleTemplate template = ENGINE.getTemplate("templates/report.peb");
        Money money = new Money(summary.currency());
        List<ChartModel.Slice> slices = ChartModel.slices(summary, topNSlices, minSlicePercent);

        String chartJs = readAsset(CHART_ASSET);
        boolean chartAvailable = chartJs != null;
        if (!chartAvailable) {
            runReport.warn(ErrorCode.WARN_507.stage(), ErrorCode.WARN_507, "");
        }

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("month", summary.month());
        ctx.put("monthLabel", monthLabel(summary.month()));
        ctx.put("currency", summary.currency());
        ctx.put("generatedAt", summary.generatedAt().format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")));
        ctx.put("version", VERSION);

        ctx.put("totalSpend", money.format(summary.totalSpend()));
        ctx.put("totalIncome", money.format(summary.totalIncome()));
        ctx.put("net", money.format(summary.net()));
        ctx.put("netPositive", summary.net().compareTo(BigDecimal.ZERO) >= 0);
        ctx.put("transactionCount", summary.transactionCount());
        ctx.put("uncategorizedCount", summary.uncategorizedCount());
        ctx.put("uncategorizedAmount", money.format(summary.uncategorizedAmount()));
        ctx.put("needsReviewCount", summary.needsReviewCount());
        ctx.put("transfersTotal", money.format(summary.transfersTotal()));
        ctx.put("hasTransfers", summary.transfersTotal().compareTo(BigDecimal.ZERO) > 0);
        ctx.put("accountCount", summary.accounts().size());

        // Pebble evaluates only Boolean/String/Number in a condition, so the presence of the
        // callout is passed as its own flag rather than by testing the map itself.
        ctx.put("topCategory", topCategoryView(summary, money));
        ctx.put("hasTopCategory", summary.topCategory() != null);
        ctx.put("topCategoryName", summary.topCategory() == null ? "none" : summary.topCategory().name());
        ctx.put("calloutSupport", calloutSupport(summary, money));
        ctx.put("momTitle", momTitle(summary));

        ctx.put("byCategory", categoryRows(summary, money));
        ctx.put("slices", sliceViews(slices, money));
        ctx.put("topTransactions", topTransactionRows(summary, money));
        ctx.put("uncategorizedRows", uncategorizedRows(transactions, money));
        ctx.put("accounts", accountRows(summary, money));
        ctx.put("excludedFiles", excludedRows(runReport));

        // FR20 / S03: the select lists the user's own categories, never a hardcoded twelve.
        List<Map<String, Object>> categoryOptions = new ArrayList<>();
        for (Category category : categories.inOrder()) {
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("name", category.name());
            option.put("slotVar", category.slot() == null ? null : "--chart-series-slot" + category.slot());
            categoryOptions.add(option);
        }
        ctx.put("categoryOptions", categoryOptions);
        ctx.put("canCorrect", !categoryOptions.isEmpty());
        ctx.put("patchFileName", "corrections-" + summary.month() + ".json");
        // ADR-0006: "The report states the literal destination path, injected at render time,
        // so there is nothing to look up."
        ctx.put("inputPath", inputDir.toAbsolutePath().toString());
        ctx.put("staleCorrections", staleCorrectionNotes(runReport));

        ctx.put("chartAvailable", chartAvailable);
        ctx.put("chartWarningCode", ErrorCode.WARN_507.code());
        ctx.put("svgFallback", chartAvailable ? "" : SvgPie.render(slices, topName(summary)));
        ctx.put("chartJs", chartAvailable ? chartJs : "");

        ctx.put("css", FONT_STACKS + "\n" + DesignTokens.load().css() + "\n" + readRequired("/templates/report.css"));
        ctx.put("js", readRequired("/templates/report.js"));
        ctx.put("dataIsland", dataIsland(summary, transactions, runReport, slices, topNSlices, minSlicePercent));

        Writer writer = new StringWriter();
        template.evaluate(writer, ctx);
        return writer.toString();
    }

    // ------------------------------------------------------------------ views

    private static String topName(MonthlySummary summary) {
        return summary.topCategory() == null ? null : summary.topCategory().name();
    }

    private static Map<String, Object> topCategoryView(MonthlySummary summary, Money money) {
        if (summary.topCategory() == null) {
            return null;
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("name", summary.topCategory().name());
        view.put("amount", money.format(summary.topCategory().amount()));
        view.put("percent", oneDecimal(summary.topCategory().percentOfSpend()));
        view.put("txnCount", txnCountOf(summary, summary.topCategory().name()));
        return view;
    }

    private static int txnCountOf(MonthlySummary summary, String category) {
        for (CategorySpend c : summary.byCategory()) {
            if (c.name().equals(category)) {
                return c.txnCount();
            }
        }
        return 0;
    }

    /**
     * S01: "The supporting line is generated, not decorative." Its full rule compares
     * month-over-month movement to argue which category the user should actually look at; with
     * no prior month yet (FR14 is Slice 6) the honest version names the runner-up and says
     * plainly why the comparison is missing, rather than asserting a trend it cannot see.
     */
    private static String calloutSupport(MonthlySummary summary, Money money) {
        CategorySpend second = null;
        boolean pastTop = false;
        for (CategorySpend c : summary.byCategory()) {
            if (c.isZeroSpend()) {
                continue;
            }
            if (!pastTop) {
                pastTop = true;
                continue;
            }
            second = c;
            break;
        }
        String base = second == null
                ? "It is the only category with spend this month."
                : "Your second largest is " + second.name() + " (" + money.format(second.amount())
                        + ", " + oneDecimal(second.percent()) + "%).";
        return base + " " + momTitle(summary);
    }

    private static String momTitle(MonthlySummary summary) {
        return summary.previousMonth() == null
                ? "No earlier month is stored yet, so there is nothing to compare against — next month's report will show the change."
                : "Compared with " + monthLabel(summary.previousMonth()) + ".";
    }

    private static List<Map<String, Object>> categoryRows(MonthlySummary summary, Money money) {
        String top = topName(summary);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CategorySpend c : summary.byCategory()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", c.name());
            row.put("amount", money.format(c.amount()));
            row.put("percent", oneDecimal(c.percent()));
            row.put("txnCount", c.txnCount());
            row.put("isTop", c.name().equals(top));
            row.put("isUncategorized", "Uncategorized".equals(c.name()));
            row.put("isZero", c.isZeroSpend());
            row.put("slotVar", c.slot() == null ? null : "--chart-series-slot" + c.slot());
            rows.add(row);
        }
        return rows;
    }

    private static List<Map<String, Object>> sliceViews(List<ChartModel.Slice> slices, Money money) {
        List<Map<String, Object>> views = new ArrayList<>();
        for (ChartModel.Slice s : slices) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("name", s.name());
            view.put("amount", money.format(s.amount()));
            view.put("percent", oneDecimal(s.percent()));
            view.put("txnCount", s.txnCount());
            view.put("kind", s.kind());
            view.put("fillVar", s.fillVar());
            views.add(view);
        }
        return views;
    }

    private static List<Map<String, Object>> topTransactionRows(MonthlySummary summary, Money money) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TransactionRef ref : summary.topTransactions()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", ref.date().toString());
            row.put("description", ref.description());
            row.put("category", ref.category());
            row.put("amount", money.format(ref.amount()));
            rows.add(row);
        }
        return rows;
    }

    private static List<Map<String, Object>> uncategorizedRows(List<Transaction> transactions, Money money) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Transaction txn : transactions) {
            if ("Uncategorized".equals(txn.category())) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", txn.id());
                row.put("date", txn.date().toString());
                row.put("description", txn.description());
                row.put("rawDescription", txn.rawDescription());
                row.put("merchantKey", txn.merchantKey());
                row.put("sourceFile", txn.sourceFile());
                row.put("amount", money.format(txn.amount()));
                rows.add(row);
            }
        }
        return rows;
    }

    private static List<Map<String, Object>> accountRows(MonthlySummary summary, Money money) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AccountSummary account : summary.accounts()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("account", account.sourceAccount() == null ? "not stated" : account.sourceAccount());
            row.put("file", account.sourceFile());
            row.put("txnCount", account.txnCount());
            row.put("spend", money.format(account.spend()));
            rows.add(row);
        }
        return rows;
    }

    /**
     * WARN-501, surfaced on the page rather than only in the log: "1 saved correction refers to
     * 'Groceries (old)', which is no longer in your categories file. It was not applied." Kept,
     * not deleted, so the user can fix the name.
     */
    private static List<String> staleCorrectionNotes(RunReport runReport) {
        List<String> notes = new ArrayList<>();
        for (var warning : runReport.warnings()) {
            if (ErrorCode.WARN_501.code().equals(warning.code())) {
                notes.add(warning.message());
            }
        }
        return notes;
    }

    private static List<Map<String, Object>> excludedRows(RunReport runReport) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ExcludedFile ex : runReport.excludedFiles()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("file", ex.file());
            row.put("reason", ex.reason());
            row.put("detail", ex.detail() == null ? "" : ex.detail());
            rows.add(row);
        }
        return rows;
    }

    // ------------------------------------------------------------- data island

    /**
     * S9: the {@code #expense-data} island is the only thing the page's script reads. Amounts
     * are strings throughout, per F1 - "487.00 as a JSON number round-trips through a float in
     * most parsers and can come back as 486.99999999999994."
     */
    private static String dataIsland(MonthlySummary summary, List<Transaction> transactions, RunReport runReport,
                                     List<ChartModel.Slice> slices, int topNSlices, double minSlicePercent)
            throws IOException {
        Map<String, Object> summaryJson = new LinkedHashMap<>();
        summaryJson.put("schema_version", summary.schemaVersion());
        summaryJson.put("month", summary.month());
        summaryJson.put("currency", summary.currency());
        summaryJson.put("total_spend", summary.totalSpend().toPlainString());
        summaryJson.put("total_income", summary.totalIncome().toPlainString());
        summaryJson.put("net", summary.net().toPlainString());
        if (summary.topCategory() != null) {
            summaryJson.put("top_category", Map.of(
                    "name", summary.topCategory().name(),
                    "amount", summary.topCategory().amount().toPlainString(),
                    "percent_of_spend", summary.topCategory().percentOfSpend()));
        } else {
            summaryJson.put("top_category", null);
        }
        List<Map<String, Object>> categories = new ArrayList<>();
        for (CategorySpend c : summary.byCategory()) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("name", c.name());
            node.put("amount", c.amount().toPlainString());
            node.put("percent", c.percent());
            node.put("txn_count", c.txnCount());
            node.put("slot", c.slot());
            node.put("mom_status", c.momStatus());
            categories.add(node);
        }
        summaryJson.put("by_category", categories);
        summaryJson.put("uncategorized_count", summary.uncategorizedCount());
        summaryJson.put("uncategorized_amount", summary.uncategorizedAmount().toPlainString());
        summaryJson.put("needs_review_count", summary.needsReviewCount());
        summaryJson.put("transaction_count", summary.transactionCount());
        summaryJson.put("previous_month", summary.previousMonth());

        List<Map<String, Object>> txnJson = new ArrayList<>();
        for (Transaction txn : transactions) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", txn.id());
            node.put("date", txn.date().toString());
            node.put("description", txn.description());
            node.put("raw_description", txn.rawDescription());
            node.put("amount", txn.amount().toPlainString());
            node.put("direction", txn.direction().name().toLowerCase(Locale.ROOT));
            node.put("category", txn.category());
            node.put("category_source", txn.categorySource().wireName());
            // S03: the row shows what a correction would be remembered against, so "Amazon" vs
            // "Amazon Pay" vs this one transaction is a visible choice rather than a hidden one.
            node.put("merchant_key", txn.merchantKey());
            node.put("needs_review", txn.needsReview());
            node.put("source_file", txn.sourceFile());
            txnJson.add(node);
        }

        List<Map<String, Object>> sliceJson = new ArrayList<>();
        for (ChartModel.Slice s : slices) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("name", s.name());
            node.put("amount", s.amount().toPlainString());
            node.put("percent", s.percent());
            node.put("txn_count", s.txnCount());
            node.put("kind", s.kind());
            node.put("fill_var", s.fillVar());
            node.put("members", s.members());
            sliceJson.add(node);
        }

        Map<String, Object> island = new LinkedHashMap<>();
        island.put("summary", summaryJson);
        island.put("transactions", txnJson);
        island.put("run_report", runReport.toMap());
        island.put("chart", Map.of("slices", sliceJson,
                "top_n_slices", topNSlices, "min_slice_percent", minSlicePercent));
        island.put("locale", "en-IN");
        island.put("corrections", Map.of(
                "patch_file", "corrections-" + summary.month() + ".json",
                "month", summary.month(),
                "schema_version", 1,
                "kind", org.example.categorize.CorrectionPatch.KIND));

        return Html.escapeJsonIsland(new ObjectMapper().writeValueAsString(island));
    }

    // ---------------------------------------------------------------- helpers

    /** Formats money the way the page states it: grouped, two decimals, with the currency sign. */
    private static final class Money {
        private final NumberFormat format;

        Money(String currencyCode) {
            NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"));
            try {
                nf.setCurrency(Currency.getInstance(currencyCode));
            } catch (IllegalArgumentException e) {
                // An unknown ISO code is a config problem, not a reason to fail the report;
                // the locale default still renders a readable, grouped number.
            }
            nf.setMinimumFractionDigits(2);
            nf.setMaximumFractionDigits(2);
            this.format = nf;
        }

        String format(BigDecimal value) {
            return format.format(value);
        }
    }

    private static String oneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String monthLabel(String yyyyMm) {
        try {
            java.time.YearMonth ym = java.time.YearMonth.parse(yyyyMm);
            return ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
        } catch (RuntimeException e) {
            return yyyyMm;
        }
    }

    private static String readRequired(String resource) throws IOException {
        String content = readAsset(resource);
        if (content == null) {
            throw new IOException("report asset missing from the package: " + resource);
        }
        return content;
    }

    private static String readAsset(String resource) throws IOException {
        try (InputStream in = ReportRenderer.class.getResourceAsStream(resource)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void assertNoExternalReferences(String html) {
        String lower = html.toLowerCase(Locale.ROOT);
        for (String needle : BANNED_REFERENCES) {
            if (lower.contains(needle)) {
                throw new IllegalStateException(
                        "Rendered report contains an external reference (" + needle + ") - violates AC8/NFR1");
            }
        }
    }

    /** FR16/NFR3: headless/WSL/SSH/no-default-browser degrades to "print the path", never a crash. */
    public static boolean openInBrowser(Path reportPath) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(reportPath.toUri());
                return true;
            }
        } catch (Exception e) {
            // fall through - caller prints the absolute path instead
        }
        return false;
    }
}
