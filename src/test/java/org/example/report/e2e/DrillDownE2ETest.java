package org.example.report.e2e;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FR19: "clicking a category drills into the transactions behind it."
 *
 * <p>The panel is always in the DOM and slid off-screen with {@code transform}, so visibility is
 * the wrong question to ask it - every assertion here goes through {@code aria-hidden} and
 * whether the panel is actually in the viewport, which is what a user experiences.</p>
 */
class DrillDownE2ETest {

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

    private Locator drill() {
        return page.locator("#drill");
    }

    /**
     * A row of S01's section 5. Scoped, because the largest-transactions table carries
     * {@code data-category} too and five of these names appear in both.
     */
    private Locator categoryRow(String name) {
        return page.locator("section.card:has(h2:text-is('Spend by category')) tr[data-category='" + name + "']");
    }

    // ---------------------------------------------------- the three ways in

    @Test
    void aLegendEntryOpensTheTransactionsBehindThatCategory_FR19() {
        assertThat(drill()).hasAttribute("aria-hidden", "true");

        page.locator("li[data-category='Food & Dining']").click();

        assertThat(drill()).hasAttribute("aria-hidden", "false");
        assertThat(drill()).isInViewport();
        assertThat(page.locator("#drill-title")).hasText("Food & Dining");
        assertEquals(2, page.locator("#drill-list li").count());
        assertThat(page.locator("#drill-meta")).containsText("2 transactions");
        assertThat(page.locator("#drill-meta")).containsText("4,200.00");
    }

    @Test
    void aCategoryTableRowOpensTheSamePanel_FR19() {
        categoryRow("Groceries").click();

        assertThat(page.locator("#drill-title")).hasText("Groceries");
        assertThat(page.locator("#drill-list li")).hasCount(1);
        assertThat(page.locator("#drill-list")).containsText("FRESHCART");
    }

    @Test
    void theTopCategoryCalloutButtonOpensItsOwnTransactions_FR19() {
        // S01's callout ends in "See the N transactions"; the number it promises and the number
        // the panel then shows are produced by different code, so they are worth comparing.
        Locator button = page.locator(".callout button[data-category]");
        assertTrue(button.innerText().contains("1 transaction"), "callout button said: " + button.innerText());

        button.click();

        assertThat(page.locator("#drill-title")).hasText("Rent/Housing");
        assertThat(page.locator("#drill-list li")).hasCount(1);
    }

    @Test
    void aLegendEntryIsReachableByKeyboard_FR19() {
        // Every drill-down trigger carries tabindex="0"; a chart you can only reach with a mouse
        // fails the same users ADR-0007's pattern rules exist for.
        page.locator("li[data-category='Healthcare']").press("Enter");

        assertThat(drill()).hasAttribute("aria-hidden", "false");
        assertThat(page.locator("#drill-title")).hasText("Healthcare");
    }

    // ------------------------------------------------------------- contents

    @Test
    void theDrillIsSortedByAmountDescending_FR19() {
        categoryRow("Uncategorized").click();

        List<Double> amounts = new ArrayList<>();
        for (String row : page.locator("#drill-list .row-top span.num").allInnerTexts()) {
            amounts.add(Double.parseDouble(row.replaceAll("[^0-9.]", "")));
        }
        assertEquals(List.of(1250.0, 799.0), amounts);
    }

    @Test
    void theDrillShowsTheRawDescriptionAndTheReviewFlag_FR19() {
        categoryRow("Uncategorized").click();

        // The raw text is what the user needs in order to recognise the transaction at all -
        // the cleaned description is a guess, the raw line is what the bank actually printed.
        assertThat(page.locator("#drill-list")).containsText("POS QUICKMART 42 CHENNAI");
        assertThat(page.locator("#drill-list .badge.review")).hasText("needs review");
    }

    @Test
    void otherStaysDrillableAndCarriesEveryCategoryFoldedIntoIt_ADR0007() {
        // ADR-0007 rule 4: anything folded into "Other" "stays fully itemised in the table and
        // drillable" - the fold is a palette decision, never a loss of detail.
        page.locator("li[data-category='Other']").click();

        assertThat(page.locator("#drill-list li")).hasCount(2);
        assertThat(page.locator("#drill-list")).containsText("STREAMFLIX");
        assertThat(page.locator("#drill-list")).containsText("INDEX FUND SIP");
        assertThat(page.locator("#drill-meta")).containsText("2,499.00");
    }

    @Test
    void aCreditIsSignedAndKeptOutOfTheDrillTotal_FR19() {
        categoryRow("Income").click();

        assertThat(page.locator("#drill-list")).containsText("+");
        assertThat(page.locator("#drill-list")).containsText("65,000.00");
        // Income is money in; a panel that added it to a "spend" total would be lying quietly.
        assertThat(page.locator("#drill-meta")).containsText("0.00");
    }

    // --------------------------------------------------------------- escape

    @Test
    void escapeClosesThePanel_FR19() {
        page.locator("li[data-category='Transport']").click();
        assertThat(drill()).hasAttribute("aria-hidden", "false");

        page.keyboard().press("Escape");

        assertThat(drill()).hasAttribute("aria-hidden", "true");
        assertThat(drill()).not().isInViewport();
    }

    @Test
    void theCloseButtonClosesThePanelAndTakesFocusWhenItOpens_FR19() {
        page.locator("li[data-category='Utilities']").click();
        // Opening moves focus into the panel, so a keyboard user is not left behind on the page
        // while a panel they cannot see has their content in it.
        assertThat(page.locator("#drill-close")).isFocused();

        page.locator("#drill-close").click();

        assertThat(drill()).hasAttribute("aria-hidden", "true");
    }

    @Test
    void openingASecondCategoryReplacesTheFirst_FR19() {
        page.locator("li[data-category='Shopping']").click();
        assertThat(page.locator("#drill-title")).hasText("Shopping");

        page.locator("li[data-category='EMI/Loan Payments']").click();

        assertThat(page.locator("#drill-title")).hasText("EMI/Loan Payments");
        assertThat(page.locator("#drill-list li")).hasCount(1);
    }
}
