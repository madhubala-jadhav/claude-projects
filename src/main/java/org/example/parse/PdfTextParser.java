package org.example.parse;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.example.config.BankProfile;
import org.example.config.Config;
import org.example.diagnostics.Diagnostic;
import org.example.diagnostics.ErrorCode;
import org.example.ingest.FileKind;
import org.example.ingest.StatementFile;
import org.example.normalize.RowParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 01-components.md C4a, implemented per ADR-0016 (PDFBox rather than pdfplumber; every other
 * part of ADR-0003's design carries over unchanged).
 *
 * <p>The pipeline for one document is: decrypt if needed, classify each page by text layer,
 * resolve a PDF bank profile from the header row, then for each page derive column boundaries
 * from that page's own header and cluster glyphs into cells. Rows are anchored on a parseable
 * date; description-only lines near an anchor are folded back into it (EC2).</p>
 *
 * <p>OCR is deliberately absent: {@link org.example.diagnostics.ErrorCode#PARSE_202} is the
 * documented FR3 degradation until C4b lands in Slice 7, so an image-only PDF is excluded with
 * an install hint and the run continues (NFR3).</p>
 */
public final class PdfTextParser implements StatementParser {

    /** ADR-0016 step 2's threshold for "this page has a usable text layer". */
    private static final int MIN_CHARS_PER_PAGE = 40;

    /**
     * Fraction of the median row pitch within which a description-only line is treated as a
     * continuation of the nearest transaction (EC2) rather than as unrelated page furniture.
     * In the validated fixture a wrapped line sits ~4.3 pt from its anchor while the row pitch
     * is ~14.3 pt, and the cardholder-name sub-header sits a full pitch away - so anything up
     * to about half a pitch is a wrap and anything beyond it is not.
     */
    private static final double WRAP_GAP_PITCH_FRACTION = 0.45;

    private static final double DEFAULT_WRAP_GAP = 6.0;

    @Override
    public String name() {
        return "pdf_text";
    }

    @Override
    public boolean supports(StatementFile file) {
        return file.kind() == FileKind.PDF;
    }

    @Override
    public ParseOutcome parse(StatementFile file, Config config, PasswordPrompt askPassword) {
        String fileName = file.path().getFileName().toString();
        PDDocument doc = null;
        try {
            try {
                doc = Loader.loadPDF(file.path().toFile());
            } catch (InvalidPasswordException first) {
                // spec §11.1: prompt once. §13: the value is used here and nowhere else -
                // it is never logged, never stored, and never put into a diagnostic message.
                String password = askPassword == null ? null : askPassword.ask(fileName);
                if (password == null || password.isEmpty()) {
                    return ParseOutcome.failed(fileName, ErrorCode.PARSE_201, "no password supplied");
                }
                try {
                    doc = Loader.loadPDF(file.path().toFile(), password);
                } catch (InvalidPasswordException second) {
                    return ParseOutcome.failed(fileName, ErrorCode.PARSE_201, "password was not accepted");
                }
            }

            List<List<PdfWords.Glyph>> pageGlyphs = new ArrayList<>();
            int pagesWithText = 0;
            for (int page = 1; page <= doc.getNumberOfPages(); page++) {
                List<PdfWords.Glyph> glyphs = PdfWords.glyphs(doc, page);
                if (glyphs.size() >= MIN_CHARS_PER_PAGE) {
                    pagesWithText++;
                }
                pageGlyphs.add(glyphs);
            }
            if (pagesWithText == 0) {
                return ParseOutcome.failed(fileName, ErrorCode.PARSE_202,
                        "install Tesseract (and its native libtesseract/leptonica libraries) to read scanned PDFs");
            }

            BankProfile profile = resolveProfile(lineUp(pageGlyphs, BankProfile.PdfTable.defaults().yTolerance()),
                    config.bankProfiles(), file.profileHint());
            if (profile == null) {
                return ParseOutcome.failed(fileName, ErrorCode.PARSE_204, "no PDF profile matched this layout");
            }

            // Re-line the pages at the resolved profile's own y-tolerance; the pass above used
            // the default only to find which profile applies.
            BankProfile.PdfTable table = profile.pdfTableOrDefault();
            List<List<PdfWords.Line>> pages = lineUp(pageGlyphs, table.yTolerance());

            List<Diagnostic> warnings = new ArrayList<>();
            String accountHint = extractAccountHint(pages, profile, file.profileHint(), warnings);

            List<RawRow> rows = new ArrayList<>();
            int rowIndex = 0;
            for (int page = 1; page <= doc.getNumberOfPages(); page++) {
                List<PdfWords.Line> lines = crop(pages.get(page - 1),
                        doc.getPage(page - 1).getMediaBox().getHeight(), table);
                rowIndex = extractPage(lines, profile, table, fileName, page, accountHint, rowIndex, rows);
            }

            if (rows.isEmpty()) {
                return ParseOutcome.failed(fileName, ErrorCode.PARSE_203, "profile " + profile.name());
            }
            return ParseOutcome.ok(rows, profile.name(), warnings);
        } catch (IOException | RuntimeException e) {
            // NFR3: a malformed or truncated PDF degrades to one excluded file. The exception
            // message is never surfaced - PDFBox embeds the absolute file path in several of
            // its own messages, which §13 forbids writing anywhere.
            return ParseOutcome.failed(fileName, ErrorCode.PARSE_208, e.getClass().getSimpleName());
        } finally {
            if (doc != null) {
                try {
                    doc.close();
                } catch (IOException ignored) {
                    // Closing a document we have already read from cannot lose data.
                }
            }
        }
    }

    /**
     * FR4 for PDFs. Scores each PDF-applicable profile by how much of its
     * {@code match.headers_any} appears on a <em>single</em> line of the document, which is
     * what a real header row looks like. Scoring against the whole document instead would let
     * a savings-account profile claim a card statement on the strength of the word "Date"
     * appearing in "Statement Date".
     */
    static BankProfile resolveProfile(List<List<PdfWords.Line>> pages, List<BankProfile> profiles, String hint) {
        BankProfile best = null;
        double bestScore = 0.0;
        for (BankProfile profile : profiles) {
            if (!profile.appliesTo("pdf")) {
                continue;
            }
            List<String> wanted = profile.match().headersAny();
            if (wanted.isEmpty()) {
                continue;
            }
            double score = 0.0;
            for (List<PdfWords.Line> page : pages) {
                for (PdfWords.Line line : page) {
                    String text = line.text().toLowerCase(Locale.ROOT);
                    long hits = wanted.stream()
                            .filter(w -> text.contains(w.toLowerCase(Locale.ROOT)))
                            .count();
                    score = Math.max(score, (double) hits / wanted.size());
                }
            }
            if (hint != null && matchesFilenameHint(profile, hint)) {
                score = Math.min(1.0, score + 0.1);
            }
            if (score > bestScore) {
                bestScore = score;
                best = profile;
            }
        }
        return bestScore >= 0.8 ? best : null;
    }

    private static boolean matchesFilenameHint(BankProfile profile, String hint) {
        return profile.match().filenameContains().stream()
                .anyMatch(f -> f.equalsIgnoreCase(hint));
    }

    /** Header/footer suppression: the crop percentages from the profile's {@code pdf_table}. */
    private static List<PdfWords.Line> crop(List<PdfWords.Line> lines, double pageHeight, BankProfile.PdfTable table) {
        double top = pageHeight * table.cropTopPct();
        double bottom = pageHeight * (1.0 - table.cropBottomPct());
        List<PdfWords.Line> out = new ArrayList<>();
        for (PdfWords.Line line : lines) {
            if (line.y() >= top && line.y() <= bottom) {
                out.add(line);
            }
        }
        return out;
    }

    /**
     * Extracts every table on one page. A page may repeat the header (the statement's own
     * continuation header); each occurrence starts a new block that runs to the next header or
     * the bottom of the cropped page, which is what {@code drop_header_rows_after_first_page}
     * asks for - the repeated header line itself is a delimiter, never a data row.
     */
    private static int extractPage(List<PdfWords.Line> lines, BankProfile profile, BankProfile.PdfTable table,
                                   String fileName, int page, String accountHint, int rowIndex, List<RawRow> out) {
        List<Integer> headerIndexes = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (isHeader(lines.get(i), table.headerRowContains())) {
                headerIndexes.add(i);
            }
        }
        for (int h = 0; h < headerIndexes.size(); h++) {
            int start = headerIndexes.get(h) + 1;
            int end = h + 1 < headerIndexes.size() ? headerIndexes.get(h + 1) : lines.size();
            PdfColumns.Layout layout = PdfColumns.resolve(
                    PdfColumns.segment(lines.get(headerIndexes.get(h)), table.xTolerance()),
                    profile.columns(), table.xTolerance());
            if (!layout.maps("date")) {
                continue; // this header does not carry the profile's date column; not our table
            }
            rowIndex = extractBlock(lines.subList(start, end), layout, profile, table, fileName, page,
                    accountHint, rowIndex, out);
        }
        return rowIndex;
    }

    private static List<List<PdfWords.Line>> lineUp(List<List<PdfWords.Glyph>> pageGlyphs, double yTolerance) {
        List<List<PdfWords.Line>> pages = new ArrayList<>();
        for (List<PdfWords.Glyph> glyphs : pageGlyphs) {
            pages.add(PdfWords.toLines(glyphs, yTolerance));
        }
        return pages;
    }

    private static boolean isHeader(PdfWords.Line line, List<String> mustContain) {
        String text = line.text().toLowerCase(Locale.ROOT);
        if (mustContain.isEmpty()) {
            return false;
        }
        return mustContain.stream().allMatch(t -> text.contains(t.toLowerCase(Locale.ROOT)));
    }

    private static int extractBlock(List<PdfWords.Line> lines, PdfColumns.Layout layout, BankProfile profile,
                                    BankProfile.PdfTable table, String fileName, int page, String accountHint,
                                    int rowIndex, List<RawRow> out) {
        List<Pattern> skip = compile(profile.skipRowsMatching());

        record Candidate(PdfWords.Line line, String[] cells, boolean anchor) {
        }
        List<Candidate> candidates = new ArrayList<>();
        for (PdfWords.Line line : lines) {
            if (matchesAny(line.text(), skip)) {
                continue;
            }
            String[] cells = PdfColumns.cells(line, layout);
            boolean anchor = RowParser.parseStatementDate(PdfColumns.cell(cells, layout, "date"),
                    profile.dateFormats()) != null;
            candidates.add(new Candidate(line, cells, anchor));
        }

        List<Candidate> anchors = candidates.stream().filter(Candidate::anchor).toList();
        if (anchors.isEmpty()) {
            return rowIndex;
        }
        double wrapGap = wrapGap(anchors.stream().map(c -> c.line().y()).toList(), table);

        // Continuation lines can sit above their anchor as well as below it: the statement
        // centres a multi-line description cell against a single-line date cell, so the first
        // wrapped line is printed higher than the date it belongs to (EC2).
        Map<Integer, List<PdfWords.Line>> extraByAnchor = new LinkedHashMap<>();
        for (Candidate c : candidates) {
            if (c.anchor()) {
                continue;
            }
            String description = PdfColumns.cell(c.cells(), layout, "description");
            if (description == null || description.isBlank() || hasAmount(c.cells(), layout)) {
                continue;
            }
            int nearest = -1;
            double nearestGap = Double.MAX_VALUE;
            for (int i = 0; i < anchors.size(); i++) {
                double gap = Math.abs(anchors.get(i).line().y() - c.line().y());
                if (gap < nearestGap) {
                    nearestGap = gap;
                    nearest = i;
                }
            }
            if (nearest >= 0 && nearestGap <= wrapGap) {
                extraByAnchor.computeIfAbsent(nearest, k -> new ArrayList<>()).add(c.line());
            }
        }

        for (int i = 0; i < anchors.size(); i++) {
            Candidate anchor = anchors.get(i);
            List<PdfWords.Line> parts = new ArrayList<>(extraByAnchor.getOrDefault(i, List.of()));
            parts.add(anchor.line());
            parts.sort((a, b) -> Double.compare(a.y(), b.y()));

            StringBuilder description = new StringBuilder();
            for (PdfWords.Line part : parts) {
                String text = PdfColumns.cell(PdfColumns.cells(part, layout), layout, "description");
                if (text != null && !text.isBlank()) {
                    if (description.length() > 0) {
                        description.append(' ');
                    }
                    description.append(text);
                }
            }

            out.add(new RawRow(
                    fileName,
                    page,
                    rowIndex++,
                    PdfColumns.cell(anchor.cells(), layout, "date"),
                    description.toString(),
                    PdfColumns.cell(anchor.cells(), layout, "debit"),
                    PdfColumns.cell(anchor.cells(), layout, "credit"),
                    PdfColumns.cell(anchor.cells(), layout, "amount"),
                    PdfColumns.cell(anchor.cells(), layout, "balance"),
                    accountHint,
                    1.0,
                    "pdf_text"
            ));
        }
        return rowIndex;
    }

    private static boolean hasAmount(String[] cells, PdfColumns.Layout layout) {
        for (String name : new String[]{"amount", "debit", "credit"}) {
            String value = PdfColumns.cell(cells, layout, name);
            if (value != null && !value.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static double wrapGap(List<Double> anchorYs, BankProfile.PdfTable table) {
        if (table.wrapMaxGap() > 0) {
            return table.wrapMaxGap();
        }
        if (anchorYs.size() < 2) {
            return DEFAULT_WRAP_GAP;
        }
        List<Double> pitches = new ArrayList<>();
        for (int i = 1; i < anchorYs.size(); i++) {
            pitches.add(Math.abs(anchorYs.get(i) - anchorYs.get(i - 1)));
        }
        pitches.sort(Double::compare);
        double medianPitch = pitches.get(pitches.size() / 2);
        return medianPitch > 0 ? medianPitch * WRAP_GAP_PITCH_FRACTION : DEFAULT_WRAP_GAP;
    }

    /**
     * §13: {@code source_account} is "masked to last 4 digits at parse time". The raw account
     * number found here is never returned, logged or stored - only the masked form leaves this
     * method.
     */
    private static String extractAccountHint(List<List<PdfWords.Line>> pages, BankProfile profile, String bankHint,
                                             List<Diagnostic> warnings) {
        if (profile.accountNumberRegex() == null || profile.accountNumberRegex().isBlank()) {
            return null;
        }
        Pattern pattern;
        try {
            pattern = Pattern.compile(profile.accountNumberRegex());
        } catch (RuntimeException e) {
            warnings.add(new Diagnostic("parse", ErrorCode.CFG_003.code(),
                    "bank profile " + profile.name() + " has an account_number_regex that does not compile"));
            return null;
        }
        for (List<PdfWords.Line> page : pages) {
            for (PdfWords.Line line : page) {
                Matcher m = pattern.matcher(line.text());
                if (m.find() && m.groupCount() >= 1 && m.group(1) != null) {
                    return mask(m.group(1), bankHint, profile.name());
                }
            }
        }
        warnings.add(new Diagnostic("parse", ErrorCode.FIELD_404.code(), ErrorCode.FIELD_404.reason()));
        return null;
    }

    /** Produces the 02-data-model.md §1.1 form, e.g. {@code HDFC-XXXX1234}. */
    private static String mask(String accountNumber, String bankHint, String profileName) {
        String digits = accountNumber.replaceAll("\\D", "");
        String last4 = digits.length() >= 4 ? digits.substring(digits.length() - 4) : digits;
        String bank = bankHint != null && !bankHint.isBlank()
                ? bankHint
                : profileName.split("_")[0];
        return bank.toUpperCase(Locale.ROOT) + "-XXXX" + last4;
    }

    private static List<Pattern> compile(List<String> regexes) {
        List<Pattern> out = new ArrayList<>();
        for (String r : regexes) {
            try {
                out.add(Pattern.compile(r));
            } catch (RuntimeException ignored) {
                // A bad skip pattern must not take the whole file down (NFR3); the row it
                // would have skipped simply stays in, where the row-level parser judges it.
            }
        }
        return out;
    }

    private static boolean matchesAny(String text, List<Pattern> patterns) {
        if (text == null || text.isBlank()) {
            return true;
        }
        for (Pattern p : patterns) {
            if (p.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }
}
