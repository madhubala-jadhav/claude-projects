package org.example.report.e2e;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The check {@code report.js} was written to be given.
 *
 * <p>{@code recomputeSlices} exists because the optimistic preview has to rebuild the pie
 * client-side before anything is saved, which means ADR-0007's slice rules are implemented
 * twice - once in {@code ChartModel}, once in JavaScript. The script says so itself, and
 * exposes {@code window.__recomputeSlices} "so the headless check can compare it against what
 * the server rendered". This is that comparison: with nothing pending, the two must agree
 * exactly, so drift fails the build instead of being discovered by eye months later.</p>
 */
class SliceParityE2ETest {

    @TempDir
    Path workspace;

    private BrowserContext context;
    private Page page;

    @BeforeEach
    void open() throws IOException {
        context = Browsers.context();
        page = context.newPage();
        page.navigate(ReportFixture.render(workspace));
    }

    @AfterEach
    void close() {
        // Null when the browser was unavailable and @BeforeEach aborted: without this guard the
        // skip is reported as an error, which is exactly what the skip exists to avoid.
        if (context != null) {
            context.close();
        }
    }

    /**
     * Compared field by field rather than by object identity: {@code slot} is on the server's
     * slices and not the client's, and that difference is deliberate - the client derives its
     * fill from {@code fill_var}, which is the field that has to match.
     */
    private static final String NORMALIZE =
            "s => s.map(x => [x.name, x.amount, x.percent, x.txn_count, x.kind, x.fill_var,"
                    + " (x.members || []).join('|')])";

    @Test
    @SuppressWarnings("unchecked")
    void theClientRebuildsExactlyTheSlicesTheServerRendered_ADR0007() {
        Map<String, Object> both = (Map<String, Object>) page.evaluate(
                "() => { const norm = " + NORMALIZE + ";"
                        + " const island = JSON.parse(document.getElementById('expense-data').textContent);"
                        + " return { server: norm(island.chart.slices), client: norm(window.__recomputeSlices()) }; }");

        List<Object> server = (List<Object>) both.get("server");
        List<Object> client = (List<Object>) both.get("client");

        assertTrue(server.size() >= 3, "the fixture should produce slots, an Other fold and the gap");
        assertEquals(server, client,
                "ChartModel and report.js have drifted apart: the optimistic preview would "
                        + "redraw the chart differently from the way it was first rendered");
    }

    @Test
    void aRepaintWithNothingPendingRewritesTheTableToTheSameNumbers_S1_2() {
        // Not the same assertion as the one above: the slice list can match while the
        // apportionment behind it differs on a category that folded into "Other". The table is
        // where the client's own per-category percentages become observable, so the round trip
        // is staged and then unstaged - which forces two repaints and must land back exactly
        // where the server left it, largest-remainder rounding included.
        List<Object> before = categoryTable();

        page.locator(".category-select[data-txn='" + ReportFixture.UNCATEGORIZED_ID + "']")
                .selectOption("Groceries");
        page.locator(".category-select[data-txn='" + ReportFixture.UNCATEGORIZED_ID + "']")
                .selectOption("Uncategorized");

        assertEquals(before, categoryTable(),
                "a no-op round trip through report.js must reproduce the server's table exactly");
    }

    /**
     * Every category row as [name, txn count, amount, percent], read as literal text. Comparing
     * the strings rather than parsed numbers is deliberate: the server writes these cells with
     * {@code NumberFormat} and the client rewrites them with {@code Intl.NumberFormat}, and two
     * formatters that disagree would make the table appear to flicker on every correction.
     */
    @SuppressWarnings("unchecked")
    private List<Object> categoryTable() {
        return (List<Object>) page.evaluate(
                "() => [...document.querySelectorAll("
                        + "\"section.card:has(h2) table tbody tr[data-category]\")]"
                        + ".map(tr => [...tr.querySelectorAll('td')].slice(0, 4)"
                        + ".map(td => td.textContent.trim()))");
    }

    @Test
    void aPendingCorrectionStillLeavesTheChartAddingUpToOneHundred_S03() {
        page.locator(".category-select[data-txn='" + ReportFixture.UNCATEGORIZED_ID + "']")
                .selectOption("Groceries");

        Object total = page.evaluate(
                "() => window.__recomputeSlices().reduce((t, s) => t + s.percent, 0)");
        assertEquals(100.0, ((Number) total).doubleValue(), 0.15,
                "the recomputed pie must still describe the whole of spend");
    }

    @Test
    void aPendingCorrectionMovesSpendWithoutInventingOrLosingAny_S03() {
        double before = recomputedSpend();

        page.locator(".category-select[data-txn='" + ReportFixture.UNCATEGORIZED_ID + "']")
                .selectOption("Groceries");

        assertEquals(before, recomputedSpend(), 0.005,
                "recategorizing moves money between slices; it must never create or destroy it");
    }

    @Test
    void theGapSliceDisappearsOnceEveryGapIsClaimed_NFR6() {
        page.locator(".category-select[data-txn='" + ReportFixture.UNCATEGORIZED_ID + "']")
                .selectOption("Groceries");
        page.locator(".category-select[data-txn='" + ReportFixture.SECOND_UNCATEGORIZED_ID + "']")
                .selectOption("Shopping");

        Object kinds = page.evaluate("() => window.__recomputeSlices().map(s => s.kind).join(',')");
        assertTrue(!kinds.toString().contains("uncategorized"),
                "with nothing left uncategorized the hatched slice should be gone, was: " + kinds);
    }

    private double recomputedSpend() {
        Object spend = page.evaluate(
                "() => window.__recomputeSlices().reduce((t, s) => t + Number(s.amount), 0)");
        return ((Number) spend).doubleValue();
    }
}
