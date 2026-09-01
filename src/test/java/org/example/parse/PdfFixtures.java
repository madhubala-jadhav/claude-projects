package org.example.parse;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds statement-shaped PDFs for the C4a tests.
 *
 * <p>The fixtures are <em>generated</em>, not checked in, and deliberately so. The layout they
 * reproduce was reverse-engineered from a real HDFC Regalia credit-card statement - a document
 * carrying a name, a postal address and a card number - and §13 keeps that kind of file out of
 * the repository. Generating the same geometry instead gives the tests the properties that
 * actually matter, each of which broke a naive parser during Slice 3:</p>
 *
 * <ul>
 *   <li>the table sits at a different x-offset on page 1 than on page 2;</li>
 *   <li>amounts are right-aligned, so a long one starts left of its own column header;</li>
 *   <li>money-in is marked with a leading "+" and money-out is unmarked;</li>
 *   <li>a wrapped description occupies the lines both above and below its date;</li>
 *   <li>a cardholder-name sub-header sits under the column header, belonging to no row.</li>
 * </ul>
 */
public final class PdfFixtures {

    private static final float FONT_SIZE = 7f;
    private static final float AMOUNT_RIGHT_EDGE = 558f;

    private record Cell(float x, String text, boolean rightAligned) {
    }

    private PdfFixtures() {
    }

    /** The two-page card statement: 4 debits, 1 credit, one of them with a wrapped description. */
    public static Path creditCardStatement(Path target) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            // Page 1: the table is pushed right, as it is on the real statement where card art
            // occupies the left margin.
            PDPage page1 = new PDPage(PDRectangle.A4);
            doc.addPage(page1);
            List<Line> p1 = new ArrayList<>();
            p1.add(header(100, 170, 275));
            p1.add(new Line(114, List.of(new Cell(275, "ASHA KUMARI", false))));
            p1.add(row(129, 170, 275, "11/07/2026| 14:02", "IND*ADOBE", null, "689.00"));
            p1.add(row(143, 170, 275, "13/07/2026| 20:56", "BigbasketCHENNAI", "+ 4", "259.00"));
            p1.add(new Line(153.7f, List.of(new Cell(275, "CREDIT CARD PAYMENTNet Banking (Ref#", false))));
            p1.add(row(158, 170, 275, "16/07/2026| 07:18", null, null, "+  47,890.65"));
            p1.add(new Line(162.3f, List.of(new Cell(275, "00000000000123456789012)", false))));
            p1.add(row(172, 170, 275, "18/07/2026| 08:00", "QUICKSERVE SOLUTIONSJAIPUR", "+ 24", "1,315.00"));
            p1.add(new Line(800, List.of(new Cell(26, "Page 1 of 2", false))));
            draw(doc, page1, font, p1);

            // Page 2: same table, different x-offset - the case that fixed column geometry in
            // config would silently mangle.
            PDPage page2 = new PDPage(PDRectangle.A4);
            doc.addPage(page2);
            List<Line> p2 = new ArrayList<>();
            p2.add(header(100, 25, 130));
            p2.add(row(114, 25, 130, "05/08/2026| 10:49", "QUICK SERVICESJAIPUR", "+ 8", "598.00"));
            p2.add(new Line(800, List.of(new Cell(26, "Page 2 of 2", false))));
            draw(doc, page2, font, p2);

            doc.save(target.toFile());
        }
        return target;
    }

    /** A PDF with text on it but no transaction table - nothing any profile can claim. */
    public static Path unrecognisableStatement(Path target) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            List<Line> lines = new ArrayList<>();
            lines.add(new Line(100, List.of(new Cell(50, "Annual Insurance Premium Notice", false))));
            lines.add(new Line(120, List.of(new Cell(50, "Policy holder: A. Person", false))));
            lines.add(new Line(140, List.of(new Cell(50, "This document contains no transaction table.", false))));
            draw(doc, page, font, lines);
            doc.save(target.toFile());
        }
        return target;
    }

    private static Line header(float y, float dateX, float descX) {
        return new Line(y, List.of(
                new Cell(dateX, "DATE & TIME", false),
                new Cell(descX, "TRANSACTION DESCRIPTION", false),
                new Cell(463, "REWARDS", false),
                new Cell(AMOUNT_RIGHT_EDGE, "AMOUNT", true),
                new Cell(568, "PI", false)));
    }

    private static Line row(float y, float dateX, float descX, String date, String description,
                            String rewards, String amount) {
        List<Cell> cells = new ArrayList<>();
        cells.add(new Cell(dateX, date, false));
        if (description != null) {
            cells.add(new Cell(descX, description, false));
        }
        if (rewards != null) {
            cells.add(new Cell(472, rewards, false));
        }
        cells.add(new Cell(AMOUNT_RIGHT_EDGE, amount, true));
        return new Line(y, cells);
    }

    private record Line(float yFromTop, List<Cell> cells) {
    }

    private static void draw(PDDocument doc, PDPage page, PDFont font, List<Line> lines) throws IOException {
        float pageHeight = page.getMediaBox().getHeight();
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.setFont(font, FONT_SIZE);
            for (Line line : lines) {
                for (Cell cell : line.cells()) {
                    float width = font.getStringWidth(cell.text()) / 1000 * FONT_SIZE;
                    float x = cell.rightAligned() ? cell.x() - width : cell.x();
                    cs.beginText();
                    // The content stream's origin is the bottom-left corner; the parser reports
                    // y from the top, so the fixture is written in top-down coordinates and
                    // flipped exactly here.
                    cs.newLineAtOffset(x, pageHeight - line.yFromTop());
                    cs.showText(cell.text());
                    cs.endText();
                }
            }
        }
    }
}
