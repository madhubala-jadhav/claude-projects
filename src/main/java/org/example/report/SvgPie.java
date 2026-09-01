package org.example.report;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Locale;

/**
 * The degraded chart path: a server-rendered SVG pie, used when the vendored Chart.js asset is
 * absent from the package.
 *
 * <p>ADR-0007 alternative B keeps this as an explicit fallback so "FR17's chart still appears
 * even in a degraded install" rather than the page showing an empty box or a spinner that hangs
 * forever. It reproduces the three cues that carry meaning - the pulled top slice, the 2 px
 * separator, the direct labels - and gives up only the hover tooltip, because the table rows
 * remain the drill-down path either way (they are FR19's keyboard route in the normal case
 * too).</p>
 *
 * <p>Fills are emitted as {@code var(--token)} references, so the fallback re-themes with the
 * rest of the page instead of freezing whichever palette was current at render time.</p>
 */
final class SvgPie {

    private static final double DIAMETER = 340;
    private static final double EXPLODE = 12;
    private static final double LABEL_GAP = 16;
    private static final double PADDING = 150;

    private SvgPie() {
    }

    static String render(List<ChartModel.Slice> slices, String topCategoryName) {
        double size = DIAMETER + 2 * PADDING;
        double cx = size / 2;
        double cy = size / 2;
        double r = DIAMETER / 2;

        BigDecimal total = BigDecimal.ZERO;
        for (ChartModel.Slice s : slices) {
            total = total.add(s.amount());
        }
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return "";
        }

        StringBuilder svg = new StringBuilder();
        svg.append("<svg viewBox=\"0 0 ").append(num(size)).append(' ').append(num(size))
                .append("\" width=\"100%\" style=\"max-width:").append(num(size)).append("px\" role=\"img\">");
        svg.append("<defs><pattern id=\"uncat-hatch\" width=\"8\" height=\"8\" patternUnits=\"userSpaceOnUse\" ")
                .append("patternTransform=\"rotate(45)\">")
                .append("<rect width=\"8\" height=\"8\" fill=\"var(--chart-uncategorized-fill)\"/>")
                .append("<line x1=\"0\" y1=\"0\" x2=\"0\" y2=\"8\" stroke=\"var(--chart-uncategorized-hatch)\" ")
                .append("stroke-width=\"2\"/></pattern></defs>");

        double angle = -Math.PI / 2; // 12 o'clock, matching Chart.js's start
        for (ChartModel.Slice slice : slices) {
            double share = slice.amount().divide(total, MathContext.DECIMAL64).doubleValue();
            double sweep = share * 2 * Math.PI;
            double mid = angle + sweep / 2;
            boolean isTop = slice.name().equals(topCategoryName);
            double offset = isTop ? EXPLODE : 0;
            double ox = Math.cos(mid) * offset;
            double oy = Math.sin(mid) * offset;

            String fill = ChartModel.KIND_UNCATEGORIZED.equals(slice.kind())
                    ? "url(#uncat-hatch)"
                    : "var(" + slice.fillVar() + ")";

            svg.append("<path d=\"").append(arcPath(cx + ox, cy + oy, r, angle, angle + sweep))
                    .append("\" fill=\"").append(fill)
                    .append("\" stroke=\"var(--chart-slice-gap)\" stroke-width=\"2\"/>");

            if (slice.labelled()) {
                double x0 = cx + ox + Math.cos(mid) * r;
                double y0 = cy + oy + Math.sin(mid) * r;
                double x1 = cx + ox + Math.cos(mid) * (r + LABEL_GAP);
                double y1 = cy + oy + Math.sin(mid) * (r + LABEL_GAP);
                boolean right = Math.cos(mid) >= 0;
                double x2 = x1 + (right ? 12 : -12);
                svg.append("<polyline points=\"").append(num(x0)).append(',').append(num(y0)).append(' ')
                        .append(num(x1)).append(',').append(num(y1)).append(' ')
                        .append(num(x2)).append(',').append(num(y1))
                        .append("\" fill=\"none\" stroke=\"var(--chart-axis)\" stroke-width=\"1\"/>");
                svg.append("<text x=\"").append(num(x2 + (right ? 4 : -4))).append("\" y=\"").append(num(y1))
                        .append("\" text-anchor=\"").append(right ? "start" : "end")
                        .append("\" dominant-baseline=\"middle\" font-size=\"12\" ")
                        .append("font-family=\"var(--font-sans)\" font-weight=\"").append(isTop ? "600" : "400")
                        .append("\" fill=\"var(--text-primary)\">")
                        .append(Html.escape(slice.name())).append("  ")
                        .append(String.format(Locale.ROOT, "%.1f", slice.percent())).append("%</text>");
            }
            angle += sweep;
        }
        svg.append("</svg>");
        return svg.toString();
    }

    /** A full circle cannot be expressed as a single arc, so a lone slice is drawn as one. */
    private static String arcPath(double cx, double cy, double r, double from, double to) {
        if (to - from >= 2 * Math.PI - 1e-9) {
            return "M " + num(cx - r) + " " + num(cy)
                    + " a " + num(r) + " " + num(r) + " 0 1 0 " + num(2 * r) + " 0"
                    + " a " + num(r) + " " + num(r) + " 0 1 0 " + num(-2 * r) + " 0 Z";
        }
        double x0 = cx + Math.cos(from) * r;
        double y0 = cy + Math.sin(from) * r;
        double x1 = cx + Math.cos(to) * r;
        double y1 = cy + Math.sin(to) * r;
        int largeArc = (to - from) > Math.PI ? 1 : 0;
        return "M " + num(cx) + " " + num(cy)
                + " L " + num(x0) + " " + num(y0)
                + " A " + num(r) + " " + num(r) + " 0 " + largeArc + " 1 " + num(x1) + " " + num(y1)
                + " Z";
    }

    private static String num(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
