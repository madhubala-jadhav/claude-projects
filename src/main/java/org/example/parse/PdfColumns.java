package org.example.parse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a statement's header line into column boundaries, so that a data line's glyphs can be
 * assigned to cells.
 *
 * <p>The boundaries are derived <em>per page</em> from that page's own header row rather than
 * being configured. The validated HDFC export prints the same table at x=169 on page 1 (beside
 * a card-art block) and at x=25 on page 2; any fixed x geometry in {@code bank_profiles.yaml}
 * would parse one page and silently mangle the other, which is exactly the "confidently-wrong
 * table extraction" ADR-0016 alternative A refuses to accept.</p>
 */
final class PdfColumns {

    /**
     * A header label and the x-range it occupies. {@code name} is null for a column the
     * profile does not map.
     */
    record Column(String name, String label, double x0, double x1) {
    }

    /**
     * Columns left to right, plus the {@code n-1} x-values separating them. A glyph belongs to
     * column {@code i} when its left edge falls in {@code [cut[i-1], cut[i])}.
     */
    record Layout(List<Column> columns, double[] cuts) {

        int columnAt(double x) {
            for (int i = 0; i < cuts.length; i++) {
                if (x < cuts[i]) {
                    return i;
                }
            }
            return columns.size() - 1;
        }

        boolean maps(String name) {
            return columns.stream().anyMatch(c -> name.equals(c.name()));
        }
    }

    /**
     * Columns holding right-aligned numbers. Their left cut is placed just past the previous
     * header label instead of at the midpoint: a long amount (the validated fixture's
     * 47,890.65 credit) is typeset rightwards from further left than its own header starts, so
     * a midpoint cut would slice the leading characters into the neighbouring cell.
     */
    private static final List<String> RIGHT_ALIGNED = List.of("amount", "debit", "credit", "balance");

    private PdfColumns() {
    }

    /**
     * Splits a header line into labels wherever the glyph-to-glyph gap is unusually wide.
     * The threshold adapts to the line: within a label ("DATE and TIME") gaps are a space
     * wide, between labels they are tens of points, so the median gap times four separates
     * them without needing a per-bank tuning knob. {@code xTolerance} sets the floor for a
     * header whose glyphs all touch.
     */
    static List<Column> segment(PdfWords.Line header, double xTolerance) {
        List<PdfWords.Glyph> glyphs = header.glyphs();
        List<Double> gaps = new ArrayList<>();
        for (int i = 1; i < glyphs.size(); i++) {
            gaps.add(Math.max(0.0, glyphs.get(i).x() - glyphs.get(i - 1).xEnd()));
        }
        double threshold = Math.max(median(gaps) * 4.0, xTolerance * 2.0);

        List<Column> out = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        double start = 0;
        double end = 0;
        for (PdfWords.Glyph g : glyphs) {
            if (text.length() == 0) {
                start = g.x();
            } else if (g.x() - end > threshold) {
                addSegment(out, text.toString(), start, end);
                text.setLength(0);
                start = g.x();
            }
            text.append(g.text());
            end = g.xEnd();
        }
        addSegment(out, text.toString(), start, end);
        return out;
    }

    private static void addSegment(List<Column> out, String text, double x0, double x1) {
        if (!text.isBlank()) {
            out.add(new Column(null, text.trim(), x0, x1));
        }
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compare);
        return sorted.get(sorted.size() / 2);
    }

    /**
     * Names each header segment after the profile column that claims it. Segments no column
     * claims (the card statement's REWARDS and PI) stay unnamed but are <em>kept</em>: they
     * still bound their neighbours, so a rewards value cannot drift into the amount cell.
     */
    static Layout resolve(List<Column> segments, Map<String, String> columnLabels, double xTolerance) {
        Map<Integer, String> assigned = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : columnLabels.entrySet()) {
            String label = entry.getValue();
            if (label == null || label.isBlank()) {
                continue;
            }
            int best = -1;
            int bestRank = Integer.MAX_VALUE;
            for (int i = 0; i < segments.size(); i++) {
                if (assigned.containsKey(i)) {
                    continue;
                }
                int rank = matchRank(segments.get(i).label(), label);
                if (rank < bestRank) {
                    bestRank = rank;
                    best = i;
                }
            }
            if (best >= 0 && bestRank < Integer.MAX_VALUE) {
                assigned.put(best, entry.getKey());
            }
        }

        List<Column> named = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            Column c = segments.get(i);
            named.add(new Column(assigned.get(i), c.label(), c.x0(), c.x1()));
        }

        double[] cuts = new double[Math.max(0, named.size() - 1)];
        for (int i = 0; i < cuts.length; i++) {
            Column left = named.get(i);
            Column right = named.get(i + 1);
            cuts[i] = RIGHT_ALIGNED.contains(String.valueOf(right.name()))
                    ? left.x1() + xTolerance
                    : (left.x1() + right.x0()) / 2.0;
        }
        return new Layout(List.copyOf(named), cuts);
    }

    /** Lower is better; {@link Integer#MAX_VALUE} means "not this column". */
    private static int matchRank(String segmentLabel, String configuredLabel) {
        String a = normalize(segmentLabel);
        String b = normalize(configuredLabel);
        if (a.isEmpty() || b.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        if (a.equals(b)) {
            return 0;
        }
        if (a.startsWith(b) || b.startsWith(a)) {
            return 1;
        }
        if (a.contains(b) || b.contains(a)) {
            return 2;
        }
        return Integer.MAX_VALUE;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** Splits one data line into cells, indexed the same way as {@code layout.columns()}. */
    static String[] cells(PdfWords.Line line, Layout layout) {
        StringBuilder[] buffers = new StringBuilder[layout.columns().size()];
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = new StringBuilder();
        }
        for (PdfWords.Glyph g : line.glyphs()) {
            buffers[layout.columnAt(g.x())].append(g.text());
        }
        String[] cells = new String[buffers.length];
        for (int i = 0; i < buffers.length; i++) {
            cells[i] = buffers[i].toString().trim().replaceAll("\\s+", " ");
        }
        return cells;
    }

    static String cell(String[] cells, Layout layout, String name) {
        for (int i = 0; i < layout.columns().size(); i++) {
            if (name.equals(layout.columns().get(i).name())) {
                return cells[i];
            }
        }
        return null;
    }
}
