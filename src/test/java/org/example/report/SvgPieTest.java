package org.example.report;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The degraded chart path (ADR-0007 alternative B, {@code WARN-507}). It only runs when the
 * vendored Chart.js asset is missing, which is exactly why it needs its own tests: nothing in a
 * normal run exercises it, so a break here would surface for the first time on the install that
 * could least afford it.
 */
class SvgPieTest {

    private static final List<ChartModel.Slice> SLICES = List.of(
            slice("Rent/Housing", "15000.00", 62.6, 1, ChartModel.KIND_SLOT),
            slice("Food & Dining", "8148.00", 34.0, 2, ChartModel.KIND_SLOT),
            slice("Uncategorized", "799.00", 3.4, null, ChartModel.KIND_UNCATEGORIZED));

    @Test
    void producesWellFormedSvg() {
        String svg = SvgPie.render(SLICES, "Rent/Housing");

        assertDoesNotThrow(() -> DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8))),
                "the fallback is inlined into the page verbatim, so it must be parseable markup");
    }

    @Test
    void drawsOneWedgePerSlice() {
        String svg = SvgPie.render(SLICES, "Rent/Housing");

        assertEquals(3, countOccurrences(svg, "<path "));
    }

    @Test
    void keepsTheTopSliceCueAndTheSeparatorStroke() {
        String svg = SvgPie.render(SLICES, "Rent/Housing");

        // FR17's emphasis is geometry, not colour, so the fallback has to carry it too.
        assertTrue(svg.contains("stroke=\"var(--chart-slice-gap)\""), "the 2px separator survives");
        assertTrue(svg.contains("font-weight=\"600\""), "the top slice keeps its bolder direct label");
    }

    @Test
    void referencesPaletteTokensRatherThanBakingInHexValues() {
        String svg = SvgPie.render(SLICES, "Rent/Housing");

        // Emitting var() is what lets the static fallback still follow the theme toggle.
        assertTrue(svg.contains("var(--chart-series-slot1)"));
        assertFalse(svg.contains("#2a78d6"), "a frozen hex would stop responding to the theme");
    }

    @Test
    void hatchesTheUncategorizedSlice_NFR6() {
        String svg = SvgPie.render(SLICES, "Rent/Housing");

        assertTrue(svg.contains("url(#uncat-hatch)"));
        assertTrue(svg.contains("var(--chart-uncategorized-hatch)"));
    }

    @Test
    void labelsOnlySlicesAtOrAboveThreePercent() {
        String svg = SvgPie.render(List.of(
                slice("Rent/Housing", "9800.00", 98.0, 1, ChartModel.KIND_SLOT),
                slice("Groceries", "200.00", 2.0, 3, ChartModel.KIND_SLOT)), "Rent/Housing");

        assertTrue(svg.contains("Rent/Housing"));
        assertFalse(svg.contains("Groceries"), "a 2% slice gets no direct label");
    }

    @Test
    void aSingleCategoryIsDrawnAsAFullCircle() {
        String svg = SvgPie.render(List.of(slice("Rent/Housing", "100.00", 100.0, 1, ChartModel.KIND_SLOT)),
                "Rent/Housing");

        // A 360-degree arc cannot be expressed as one SVG arc segment; drawn naively it collapses
        // to nothing and the only category on the chart disappears.
        assertTrue(svg.contains(" a "), "a full circle is drawn with arc commands, not a wedge");
        assertDoesNotThrow(() -> DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void anEmptyChartRendersNothingRatherThanDividingByZero() {
        assertEquals("", SvgPie.render(List.of(), null));
    }

    private static ChartModel.Slice slice(String name, String amount, double percent, Integer slot, String kind) {
        return new ChartModel.Slice(name, new BigDecimal(amount), percent, 1, slot, kind, List.of(name));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            count++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return count;
    }
}
