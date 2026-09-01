package org.example.report;

import org.example.analysis.CategorySpend;
import org.example.analysis.MonthlySummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ADR-0007's pie-chart binding rules, which are part of the decision rather than left open. */
class ChartModelTest {

    @Test
    void ordersSlicesBySlotNotByValue() {
        List<ChartModel.Slice> slices = ChartModel.slices(summary(
                cat("Food & Dining", "8000.00", 40.0, 2),
                cat("Rent/Housing", "12000.00", 60.0, 1)), 8, 1.0);

        // Rule 2: "Fixed ordering keeps adjacency stable and makes two months' charts directly
        // comparable at a glance." Rent is larger but Food owns the earlier slot.
        assertEquals(List.of("Rent/Housing", "Food & Dining"),
                slices.stream().map(ChartModel.Slice::name).toList());
    }

    @Test
    void colourFollowsTheCategorySlotNotItsRank() {
        List<ChartModel.Slice> slices = ChartModel.slices(summary(
                cat("Healthcare", "9000.00", 90.0, 8),
                cat("Groceries", "1000.00", 10.0, 3)), 8, 1.0);

        // Rule 1: Healthcare is the biggest category here, but it keeps slot 8's hue - otherwise
        // the whole chart repaints whenever spending shifts.
        assertEquals("--chart-series-slot3", slices.get(0).fillVar());
        assertEquals("--chart-series-slot8", slices.get(1).fillVar());
    }

    @Test
    void theSliceCapCountsColouredSlicesNotCategoryRank() {
        // Two unslotted categories rank above several slotted ones. If the cap counted position
        // in the ranked list, they would push real slots out of the palette while leaving those
        // slots unused - the chart would show "Other" instead of a hue it had available.
        List<CategorySpend> categories = new ArrayList<>();
        categories.add(cat("Investments/Savings", "9000.00", 30.0, null));
        categories.add(cat("Entertainment", "8000.00", 26.0, null));
        for (int slot = 1; slot <= 8; slot++) {
            categories.add(cat("Cat" + slot, String.valueOf(1000 - slot) + ".00", 5.0 - slot * 0.1, slot));
        }

        List<ChartModel.Slice> slices = ChartModel.slices(summary(categories.toArray(new CategorySpend[0])), 8, 1.0);

        long coloured = slices.stream().filter(s -> ChartModel.KIND_SLOT.equals(s.kind())).count();
        assertEquals(8, coloured, "all eight palette slots should be used");
        ChartModel.Slice other = slices.get(slices.size() - 1);
        assertEquals("Other", other.name());
        assertEquals(List.of("Investments/Savings", "Entertainment"), other.members());
    }

    @Test
    void foldsSlicesBelowTheMinimumPercentIntoOther() {
        List<ChartModel.Slice> slices = ChartModel.slices(summary(
                cat("Rent/Housing", "9950.00", 99.5, 1),
                cat("Groceries", "50.00", 0.5, 3)), 8, 1.0);

        assertEquals(2, slices.size());
        assertEquals("Other", slices.get(1).name());
        assertEquals(List.of("Groceries"), slices.get(1).members());
    }

    @Test
    void zeroSpendCategoriesNeverBecomeSlices_EC6() {
        List<ChartModel.Slice> slices = ChartModel.slices(summary(
                cat("Rent/Housing", "1000.00", 100.0, 1),
                cat("Entertainment", "0.00", 0.0, 4)), 8, 1.0);

        assertEquals(1, slices.size());
        assertFalse(slices.stream().anyMatch(s -> s.name().equals("Entertainment")),
                "EC6: no empty slices - the category stays in by_category for FR14 history");
    }

    @Test
    void uncategorizedIsAlwaysItsOwnHatchedSliceAndNeverASlotColour() {
        List<ChartModel.Slice> slices = ChartModel.slices(summary(
                cat("Rent/Housing", "1000.00", 50.0, 1),
                cat("Uncategorized", "1000.00", 50.0, null)), 8, 1.0);

        ChartModel.Slice last = slices.get(slices.size() - 1);
        assertEquals("Uncategorized", last.name());
        assertEquals(ChartModel.KIND_UNCATEGORIZED, last.kind());
        assertEquals("--chart-uncategorized-fill", last.fillVar());
    }

    @Test
    void otherRemainsDrillableIntoEveryCategoryItSwallowed() {
        List<ChartModel.Slice> slices = ChartModel.slices(summary(
                cat("Rent/Housing", "9000.00", 90.0, 1),
                cat("Investments/Savings", "600.00", 6.0, null),
                cat("Entertainment", "400.00", 4.0, null)), 8, 1.0);

        ChartModel.Slice other = slices.get(1);
        // Rule 4: "Other" stays "fully itemised in the table (FR18) and drillable (FR19)".
        assertEquals(List.of("Investments/Savings", "Entertainment"), other.members());
        assertEquals(0, new BigDecimal("1000.00").compareTo(other.amount()));
    }

    @Test
    void onlySlicesAtOrAboveThreePercentCarryADirectLabel() {
        List<ChartModel.Slice> slices = ChartModel.slices(summary(
                cat("Rent/Housing", "9700.00", 97.0, 1),
                cat("Groceries", "300.00", 3.0, 3)), 8, 1.0);

        assertTrue(slices.get(0).labelled());
        assertTrue(slices.get(1).labelled(), "exactly 3% is on the labelled side of the rule");
    }

    private static CategorySpend cat(String name, String amount, double percent, Integer slot) {
        return new CategorySpend(name, new BigDecimal(amount), percent, 1, slot, null, "unavailable");
    }

    private static MonthlySummary summary(CategorySpend... categories) {
        return new MonthlySummary(1, "2026-08", "INR", LocalDateTime.now(),
                new BigDecimal("10000.00"), BigDecimal.ZERO, BigDecimal.ZERO, null,
                List.of(categories), List.of(), 0, BigDecimal.ZERO, 0, BigDecimal.ZERO,
                1, List.of(), null);
    }
}
