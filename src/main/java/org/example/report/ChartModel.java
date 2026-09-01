package org.example.report;

import org.example.analysis.CategorySpend;
import org.example.analysis.MonthlySummary;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Decides what the pie is made of, applying ADR-0007's binding rules once, in one place.
 *
 * <p>This is presentation, not analysis - C7 owns every number, C8 owns which of them become
 * slices - but it deliberately does not live in the template or the page script either: both
 * the Chart.js path and the static-SVG fallback consume this list, and a fallback that composed
 * its own slices would be free to disagree with the real chart about what "Other" contains.</p>
 *
 * <p>The rules, from ADR-0007 §"The pie chart, specifically":</p>
 * <ol>
 *   <li>colour follows the category's fixed slot, never its rank;</li>
 *   <li>slices are ordered by slot, not by value, so two months compare at a glance;</li>
 *   <li>at most 8 coloured slices - beyond {@code top_n_slices}, below
 *       {@code min_slice_percent}, or {@code slot: null} folds into a neutral "Other", which
 *       stays fully itemised in the table and drillable;</li>
 *   <li>zero-spend categories leave the chart but stay in {@code by_category} (EC6);</li>
 *   <li>Uncategorized is never a slot colour - it gets the hatched fill (NFR6).</li>
 * </ol>
 */
public final class ChartModel {

    public static final String KIND_SLOT = "slot";
    public static final String KIND_OTHER = "other";
    public static final String KIND_UNCATEGORIZED = "uncategorized";

    /** ADR-0007 rule 5's threshold: at or above this share, a slice carries a direct label. */
    public static final double DIRECT_LABEL_MIN_PERCENT = 3.0;

    private static final String UNCATEGORIZED = "Uncategorized";

    /**
     * One drawn slice. {@code members} is what a click on it drills into - for an ordinary
     * slice that is just itself, for "Other" it is everything folded in.
     */
    public record Slice(
            String name,
            BigDecimal amount,
            double percent,
            int txnCount,
            Integer slot,
            String kind,
            List<String> members
    ) {
        /**
         * The CSS custom property carrying this slice's fill. Returning a variable name rather
         * than a hex value is what lets the SVG fallback re-theme itself with the rest of the
         * page instead of baking in whichever palette was current at render time.
         */
        public String fillVar() {
            return switch (kind) {
                case KIND_UNCATEGORIZED -> "--chart-uncategorized-fill";
                case KIND_OTHER -> "--chart-other";
                default -> "--chart-series-slot" + slot;
            };
        }

        public boolean labelled() {
            return percent >= DIRECT_LABEL_MIN_PERCENT;
        }
    }

    private ChartModel() {
    }

    public static List<Slice> slices(MonthlySummary summary, int topNSlices, double minSlicePercent) {
        List<CategorySpend> spending = new ArrayList<>();
        for (CategorySpend c : summary.byCategory()) {
            if (!c.isZeroSpend()) {
                spending.add(c); // EC6
            }
        }
        // by_category arrives sorted by amount descending, so the largest categories get first
        // claim on the palette. The cap counts *coloured slices admitted*, not position in that
        // list: rule 4 limits how many hues the chart uses, and an unslotted category consumes
        // no hue, so letting one occupy a rank would silently push a slotted category out of the
        // palette while leaving one of the eight slots unused.
        List<CategorySpend> slotted = new ArrayList<>();
        List<CategorySpend> folded = new ArrayList<>();
        CategorySpend uncategorized = null;
        int coloured = 0;

        for (CategorySpend c : spending) {
            if (UNCATEGORIZED.equals(c.name())) {
                uncategorized = c;
                continue;
            }
            boolean foldable = c.slot() == null || coloured >= topNSlices || c.percent() < minSlicePercent;
            if (foldable) {
                folded.add(c);
            } else {
                slotted.add(c);
                coloured++;
            }
        }

        slotted.sort(Comparator.comparing(CategorySpend::slot));

        List<Slice> out = new ArrayList<>();
        for (CategorySpend c : slotted) {
            out.add(new Slice(c.name(), c.amount(), c.percent(), c.txnCount(), c.slot(), KIND_SLOT,
                    List.of(c.name())));
        }
        if (!folded.isEmpty()) {
            BigDecimal amount = BigDecimal.ZERO;
            double percent = 0;
            int count = 0;
            List<String> members = new ArrayList<>();
            for (CategorySpend c : folded) {
                amount = amount.add(c.amount());
                percent += c.percent();
                count += c.txnCount();
                members.add(c.name());
            }
            out.add(new Slice("Other", amount, Math.round(percent * 10) / 10.0, count, null, KIND_OTHER,
                    List.copyOf(members)));
        }
        if (uncategorized != null) {
            out.add(new Slice(uncategorized.name(), uncategorized.amount(), uncategorized.percent(),
                    uncategorized.txnCount(), null, KIND_UNCATEGORIZED, List.of(UNCATEGORIZED)));
        }
        return List.copyOf(out);
    }
}
