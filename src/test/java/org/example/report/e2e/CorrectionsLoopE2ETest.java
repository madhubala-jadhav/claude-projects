package org.example.report.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.example.categorize.CorrectionPatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FR20 and the browser half of AC4 ("reassigning a transaction's category in the report
 * persists"): the S03 corrections loop, from the select on an uncategorized row to the patch
 * file that leaves the page.
 *
 * <p>The Java half - {@code CorrectionHarvester} picking that patch back up and
 * {@code CorrectionsStore} applying it - is already covered by
 * {@code CorrectionsLoopSliceFiveTest}. What was untested until now is the half in between:
 * whether the page produces a patch those classes can actually read.</p>
 */
class CorrectionsLoopE2ETest {

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

    private Locator select(String txnId) {
        return page.locator(".category-select[data-txn='" + txnId + "']");
    }

    private Locator row(String txnId) {
        return page.locator("tr[data-txn='" + txnId + "']");
    }

    /** A staged row is marked with {@code is-pending}; an untouched one carries no class at all. */
    private boolean isPending(String txnId) {
        return (Boolean) row(txnId).evaluate("el => el.classList.contains('is-pending')");
    }

    /** Section 5's row for a category, read as a number whatever formatter produced it. */
    private double tableAmount(String category) {
        String cell = page.locator("section.card:has(h2:text-is('Spend by category')) tr[data-category='"
                + category + "'] td:nth-child(3)").innerText();
        return Double.parseDouble(cell.replaceAll("[^0-9.]", ""));
    }

    private int tableTxnCount(String category) {
        String cell = page.locator("section.card:has(h2:text-is('Spend by category')) tr[data-category='"
                + category + "'] td:nth-child(2)").innerText();
        return Integer.parseInt(cell.trim());
    }

    // ----------------------------------------------------------- staging

    @Test
    void thereIsNoTrayUntilThereIsSomethingToSave_FR20() {
        // S01 is explicit that the save button is never rendered as an enabled control that
        // does nothing, so the tray's resting state is absent rather than disabled.
        assertThat(page.locator("#tray")).isHidden();
        assertThat(page.locator("#tray-saved")).isHidden();
    }

    @Test
    void everyUncategorizedRowOffersACategoryChoice_FR20() {
        assertThat(select(ReportFixture.UNCATEGORIZED_ID)).isVisible();
        assertThat(select(ReportFixture.SECOND_UNCATEGORIZED_ID)).isVisible();

        // FR20: the select lists the user's own categories, never a hardcoded twelve.
        List<String> options = select(ReportFixture.UNCATEGORIZED_ID).locator("option").allInnerTexts();
        assertEquals(ReportFixture.categories().inOrder().stream().map(c -> c.name()).toList(), options);
        assertEquals("Uncategorized", select(ReportFixture.UNCATEGORIZED_ID).inputValue());
    }

    @Test
    void theRowStatesWhatTheCorrectionWillBeRememberedAgainst_F6() {
        // F6 freezes merchant_key derivation because changing it orphans every saved
        // correction; S03's answer is to show the user the key itself, so the choice between
        // "this merchant" and "this transaction" is an informed one.
        assertThat(row(ReportFixture.UNCATEGORIZED_ID).locator(".merchant-key"))
                .containsText(ReportFixture.UNCATEGORIZED_MERCHANT_KEY);
        assertThat(row(ReportFixture.UNCATEGORIZED_ID).locator(".scope-choice"))
                .containsText(ReportFixture.UNCATEGORIZED_MERCHANT_KEY);
    }

    @Test
    void choosingACategoryStagesItAndRaisesTheTray_FR20() {
        select(ReportFixture.UNCATEGORIZED_ID).selectOption("Groceries");

        assertThat(page.locator("#tray")).isVisible();
        assertThat(page.locator("#tray-count")).hasText("1 unsaved correction");
        assertThat(page.locator("#tray-summary"))
                .hasText(ReportFixture.UNCATEGORIZED_MERCHANT_KEY + " → Groceries");
        assertThat(row(ReportFixture.UNCATEGORIZED_ID)).hasClass(java.util.regex.Pattern.compile("is-pending"));
    }

    @Test
    void aCorrectionOnOneMerchantLeavesTheOtherAlone_ADR0005() {
        // The two rows have different merchant keys on purpose: ADR-0005's L2 correction is
        // scoped to one key, and a page that moved both would be inventing a rule the user
        // never asked for.
        select(ReportFixture.UNCATEGORIZED_ID).selectOption("Groceries");

        assertEquals("Uncategorized", select(ReportFixture.SECOND_UNCATEGORIZED_ID).inputValue());
        assertThat(page.locator("#tray-count")).hasText("1 unsaved correction");
        assertFalse(isPending(ReportFixture.SECOND_UNCATEGORIZED_ID));
    }

    @Test
    void twoStagedCorrectionsAreBothListed_FR20() {
        select(ReportFixture.UNCATEGORIZED_ID).selectOption("Groceries");
        select(ReportFixture.SECOND_UNCATEGORIZED_ID).selectOption("Shopping");

        assertThat(page.locator("#tray-count")).hasText("2 unsaved corrections");
        assertThat(page.locator("#tray-summary")).containsText(ReportFixture.UNCATEGORIZED_MERCHANT_KEY + " → Groceries");
        assertThat(page.locator("#tray-summary")).containsText(ReportFixture.SECOND_UNCATEGORIZED_MERCHANT_KEY + " → Shopping");
    }

    @Test
    void puttingTheCategoryBackUnstagesTheChange_FR20() {
        select(ReportFixture.UNCATEGORIZED_ID).selectOption("Groceries");
        assertThat(page.locator("#tray")).isVisible();

        select(ReportFixture.UNCATEGORIZED_ID).selectOption("Uncategorized");

        // Back at the original value there is nothing to save, so the tray must leave again
        // rather than offer to write a correction that says nothing.
        assertThat(page.locator("#tray")).isHidden();
        assertFalse(isPending(ReportFixture.UNCATEGORIZED_ID));
    }

    // ------------------------------------------------- optimistic preview

    @Test
    void stagingRecalculatesTheTableBeforeAnythingIsSaved_S03() {
        // S03: "the user is making a decision about their money", so the corrected picture is
        // shown before they commit to it, not after the next run.
        assertEquals(3200.0, tableAmount("Groceries"));
        assertEquals(2049.0, tableAmount("Uncategorized"));

        select(ReportFixture.UNCATEGORIZED_ID).selectOption("Groceries");

        assertEquals(3999.0, tableAmount("Groceries"), "the 799.00 row should have moved");
        assertEquals(2, tableTxnCount("Groceries"));
        assertEquals(1250.0, tableAmount("Uncategorized"));
        assertEquals(1, tableTxnCount("Uncategorized"));
    }

    @Test
    void recategorizingNeverChangesTotalSpend_S03() {
        String before = page.locator(".tiles .tile .value").first().innerText();

        select(ReportFixture.UNCATEGORIZED_ID).selectOption("Groceries");

        // The tile is re-rendered client-side after a change; moving a transaction between two
        // categories must leave the total alone, and the client must write it the same way the
        // server did or the number will appear to flicker for no reason.
        assertEquals(before, page.locator(".tiles .tile .value").first().innerText());
    }

    @Test
    void stagingRedrawsTheChart_S03() {
        page.waitForFunction("() => window.Chart && Chart.getChart('spend-chart')");
        Object before = page.evaluate("() => Chart.getChart('spend-chart').data.datasets[0].data.slice()");

        select(ReportFixture.UNCATEGORIZED_ID).selectOption("Groceries");

        Object after = page.evaluate("() => Chart.getChart('spend-chart').data.datasets[0].data.slice()");
        assertFalse(before.equals(after), "the pie should reflect the pending correction, not wait for a re-run");
    }

    @Test
    void discardPutsEverythingBack_FR20() {
        select(ReportFixture.UNCATEGORIZED_ID).selectOption("Groceries");
        select(ReportFixture.SECOND_UNCATEGORIZED_ID).selectOption("Shopping");

        page.locator("#tray-discard").click();

        assertThat(page.locator("#tray")).isHidden();
        assertEquals("Uncategorized", select(ReportFixture.UNCATEGORIZED_ID).inputValue());
        assertEquals("Uncategorized", select(ReportFixture.SECOND_UNCATEGORIZED_ID).inputValue());
        assertEquals(3200.0, tableAmount("Groceries"));
        assertEquals(2049.0, tableAmount("Uncategorized"));
    }

    // --------------------------------------------------------- the export

    @Test
    void savingDownloadsAPatchTheHarvesterCanRead_AC4() throws IOException {
        select(ReportFixture.UNCATEGORIZED_ID).selectOption("Groceries");

        Download download = page.waitForDownload(() -> page.locator("#tray-save").click());

        // ADR-0006 routes the loop back through the input folder, so the file name is what the
        // user has to recognise in their Downloads folder - and what CorrectionHarvester globs.
        assertEquals("corrections-" + ReportFixture.MONTH + ".json", download.suggestedFilename());

        JsonNode patch = new ObjectMapper().readTree(Files.readString(download.path()));
        assertEquals(CorrectionPatch.KIND, patch.get("kind").asText());
        assertEquals(1, patch.get("schema_version").asInt());
        assertEquals(ReportFixture.MONTH, patch.get("month").asText());
        assertTrue(patch.hasNonNull("exported_at"), "§3.6 requires an export timestamp");

        JsonNode corrections = patch.get("corrections");
        assertEquals(1, corrections.size());
        JsonNode correction = corrections.get(0);
        assertEquals(ReportFixture.UNCATEGORIZED_MERCHANT_KEY, correction.get("merchant_key").asText());
        assertEquals("Groceries", correction.get("category").asText());
        assertEquals("merchant", correction.get("scope").asText());
        assertTrue(correction.get("transaction_id").isNull(), "a merchant-scoped correction pins no id");
        assertEquals("POS RANDOMSHOP XYZ12345", correction.get("example_description").asText());
    }

    @Test
    void aTransactionScopedCorrectionCarriesTheTransactionId_ADR0005() throws IOException {
        // ADR-0005's L1: "only this one" must not become a rule for every future purchase from
        // the same merchant.
        row(ReportFixture.UNCATEGORIZED_ID).locator("input[value='transaction']").check();
        select(ReportFixture.UNCATEGORIZED_ID).selectOption("Healthcare");

        Download download = page.waitForDownload(() -> page.locator("#tray-save").click());
        JsonNode correction = new ObjectMapper().readTree(Files.readString(download.path()))
                .get("corrections").get(0);

        assertEquals("transaction", correction.get("scope").asText());
        assertEquals(ReportFixture.UNCATEGORIZED_ID, correction.get("transaction_id").asText());
    }

    @Test
    void changingTheScopeAfterStagingIsPickedUp_ADR0005() throws IOException {
        select(ReportFixture.UNCATEGORIZED_ID).selectOption("Healthcare");
        row(ReportFixture.UNCATEGORIZED_ID).locator("input[value='transaction']").check();

        Download download = page.waitForDownload(() -> page.locator("#tray-save").click());
        JsonNode correction = new ObjectMapper().readTree(Files.readString(download.path()))
                .get("corrections").get(0);

        assertEquals("transaction", correction.get("scope").asText(),
                "the scope radio must still count after the category was chosen");
    }

    @Test
    void afterSavingTheUserIsToldTheLiteralFolderToDropItIn_ADR0006() {
        select(ReportFixture.UNCATEGORIZED_ID).selectOption("Groceries");
        page.waitForDownload(() -> page.locator("#tray-save").click());

        assertThat(page.locator("#tray")).isHidden();
        assertThat(page.locator("#tray-saved")).isVisible();
        assertThat(page.locator("#saved-headline")).containsText("corrections-" + ReportFixture.MONTH + ".json");
        // ADR-0006: "The report states the literal destination path [...] so there is nothing
        // to look up." A relative "input/" would be useless from a Downloads folder.
        assertThat(page.locator("#saved-path")).hasText(ReportFixture.inputPath(workspace));
        assertThat(page.locator("#saved-detail")).containsText(ReportFixture.UNCATEGORIZED_MERCHANT_KEY + " → Groceries");

        page.locator("#saved-dismiss").click();
        assertThat(page.locator("#tray-saved")).isHidden();
    }

    @Test
    void savingClearsThePendingChanges_FR20() {
        select(ReportFixture.UNCATEGORIZED_ID).selectOption("Groceries");
        page.waitForDownload(() -> page.locator("#tray-save").click());

        assertThat(page.locator("#tray")).isHidden();
        assertFalse(leavingIsBlocked(), "once saved there is nothing left to lose");
    }

    // ----------------------------------------------------- the exit guard

    @Test
    void unsavedCorrectionsBlockLeavingThePage_ADR0006() {
        // "The one place an easy mistake would silently discard the user's work." Dispatching
        // the event directly is deterministic where driving a real navigation dialog is not,
        // and it asks the browser exactly what the browser asks: was this event cancelled?
        assertFalse(leavingIsBlocked(), "an untouched report should never nag on the way out");

        select(ReportFixture.UNCATEGORIZED_ID).selectOption("Groceries");

        assertTrue(leavingIsBlocked(), "pending corrections must survive a mistaken close");
    }

    private boolean leavingIsBlocked() {
        return (Boolean) page.evaluate(
                "() => { const e = new Event('beforeunload', { cancelable: true });"
                        + " window.dispatchEvent(e); return e.defaultPrevented; }");
    }
}
