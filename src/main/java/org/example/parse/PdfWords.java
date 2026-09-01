package org.example.parse;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * ADR-0016 step 3: "extract words with bounding boxes by subclassing {@code PDFTextStripper}
 * and overriding {@code writeString(String, List&lt;TextPosition&gt;)} - this is PDFBox's
 * equivalent of pdfplumber's raw-words-with-bounding-boxes model."
 *
 * <p>This class deliberately stops at <em>positioned characters</em> rather than words.
 * PDFBox's own word grouping merges glyphs across a column boundary whenever the gap happens
 * to be narrow, which silently moves a rewards-points value into the amount cell; clustering
 * into columns first, from raw character x-positions, cannot make that mistake.</p>
 */
final class PdfWords {

    /** One glyph with its position on the page. {@code y} increases downward. */
    record Glyph(double x, double width, double y, String text) {
        double xEnd() {
            return x + width;
        }
    }

    /** Glyphs sharing a baseline, left to right. */
    record Line(double y, List<Glyph> glyphs) {
        String text() {
            StringBuilder sb = new StringBuilder();
            for (Glyph g : glyphs) {
                sb.append(g.text());
            }
            return sb.toString().trim();
        }
    }

    private PdfWords() {
    }

    static List<Glyph> glyphs(PDDocument doc, int pageNumber) throws IOException {
        List<Glyph> out = new ArrayList<>();
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> positions) {
                for (TextPosition t : positions) {
                    String unicode = normalizeGlyph(t);
                    if (unicode.isEmpty()) {
                        continue;
                    }
                    out.add(new Glyph(t.getXDirAdj(), t.getWidthDirAdj(), t.getYDirAdj(), unicode));
                }
            }
        };
        stripper.setSortByPosition(true);
        stripper.setStartPage(pageNumber);
        stripper.setEndPage(pageNumber);
        stripper.getText(doc);
        out.sort(Comparator.comparingDouble(Glyph::y).thenComparingDouble(Glyph::x));
        return out;
    }

    /**
     * Indian bank statements print the rupee sign from a bundled symbol font
     * (HDFC's is {@code ITFRupee}) whose glyph is encoded at the codepoint for an ordinary
     * letter - the real statement validated in Slice 3 extracts every amount as
     * {@code "C 689.00"}. Mapping any single glyph drawn in a rupee font back to U+20B9 is
     * the difference between an amount cell that parses and a description polluted with a
     * stray capital letter, and it is a property of the font, not a per-bank guess.
     */
    private static String normalizeGlyph(TextPosition t) {
        String unicode = t.getUnicode();
        if (unicode == null) {
            return "";
        }
        String fontName = t.getFont() == null ? null : t.getFont().getName();
        if (unicode.length() == 1 && fontName != null
                && fontName.toLowerCase(Locale.ROOT).contains("rupee")) {
            return "\u20B9";
        }
        return unicode;
    }

    /**
     * Groups glyphs into lines. Two glyphs share a line when their baselines differ by less
     * than {@code yTolerance}; the statement's own row pitch (~14 pt in the validated
     * fixture) is an order of magnitude larger, so this never merges adjacent table rows.
     */
    static List<Line> toLines(List<Glyph> glyphs, double yTolerance) {
        List<Line> lines = new ArrayList<>();
        List<Glyph> current = new ArrayList<>();
        double currentY = Double.NaN;
        for (Glyph g : glyphs) {
            if (current.isEmpty() || Math.abs(g.y() - currentY) <= yTolerance) {
                if (current.isEmpty()) {
                    currentY = g.y();
                }
                current.add(g);
            } else {
                lines.add(finishLine(currentY, current));
                current = new ArrayList<>();
                current.add(g);
                currentY = g.y();
            }
        }
        if (!current.isEmpty()) {
            lines.add(finishLine(currentY, current));
        }
        return lines;
    }

    private static Line finishLine(double y, List<Glyph> glyphs) {
        List<Glyph> sorted = new ArrayList<>(glyphs);
        sorted.sort(Comparator.comparingDouble(Glyph::x));
        return new Line(y, List.copyOf(sorted));
    }
}
