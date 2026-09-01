package org.example.parse;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.DuplicateHeaderMode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.config.BankProfile;
import org.example.config.Config;
import org.example.diagnostics.ErrorCode;
import org.example.ingest.FileKind;
import org.example.ingest.StatementFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 01-components.md C4c - CSV and Excel, per ADR-0015 (Apache Commons CSV + Apache POI).
 *
 * <p>Slice 3 completes the component: encoding, delimiter and header-row sniffing (spec
 * §11.2), profile scoring against every plausible header row, and {@code .xlsx} support.
 * Legacy BIFF {@code .xls} stays out by design - 05-traceability.md §8 records it as FR1's
 * documented partial, degrading to {@code PARSE-206} with a "re-save as .xlsx or .csv" hint
 * rather than dragging in a fragile reader.</p>
 */
public final class TabularParser implements StatementParser {

    /** Delimiters tried when sniffing a CSV, most likely first (spec §11.2). */
    private static final char[] DELIMITERS = {',', ';', '\t', '|'};

    /**
     * How far into a file to look for the header row. Bank exports put an account-details
     * preamble above the table; none of the real ones seen run to anything like this depth,
     * and a larger window only increases the chance of matching a summary block below the data.
     */
    private static final int MAX_HEADER_SCAN_ROWS = 25;

    /** ADR-0004/C4c: the header-overlap score a profile must reach to claim a file (FR4). */
    static final double PROFILE_MATCH_THRESHOLD = 0.8;

    /**
     * A parsed grid plus what had to be guessed to read it. {@code dataRows} holds only the
     * rows below the header, so callers never re-derive where the table starts.
     */
    public record SniffedLayout(
            List<String> headers,
            Charset charset,
            char delimiter,
            int headerRowIndex,
            List<List<String>> dataRows
    ) {
    }

    @Override
    public String name() {
        return "tabular";
    }

    @Override
    public boolean supports(StatementFile file) {
        return file.kind() == FileKind.CSV || file.kind() == FileKind.XLSX || file.kind() == FileKind.XLS;
    }

    @Override
    public ParseOutcome parse(StatementFile file, Config config, PasswordPrompt askPassword) {
        String fileName = file.path().getFileName().toString();
        if (file.kind() == FileKind.XLS) {
            return ParseOutcome.failed(fileName, ErrorCode.PARSE_206,
                    "legacy .xls is not supported - re-save the statement as .xlsx or .csv");
        }
        try {
            SniffedLayout layout = sniffLayout(file.path(), file.kind());
            BankProfile profile = resolveProfile(layout.headers(), config.bankProfiles(), file.profileHint());
            if (profile == null) {
                return ParseOutcome.failed(fileName, ErrorCode.PARSE_204, "headers: " + layout.headers());
            }
            return ParseOutcome.ok(mapColumns(layout, profile, fileName), profile.name(), List.of());
        } catch (CharacterCodingException e) {
            return ParseOutcome.failed(fileName, ErrorCode.PARSE_207, "");
        } catch (IOException | RuntimeException e) {
            // A malformed quote, a truncated file, a zip container that is not a workbook:
            // one excluded file, never a run-wide crash (NFR3). The message is withheld -
            // several JDK and POI exceptions embed the absolute file path (§13).
            return ParseOutcome.failed(fileName, ErrorCode.PARSE_208, e.getClass().getSimpleName());
        }
    }

    public static SniffedLayout sniffLayout(Path path) throws IOException {
        return sniffLayout(path, FileKind.CSV);
    }

    public static SniffedLayout sniffLayout(Path path, FileKind kind) throws IOException {
        return kind == FileKind.XLSX ? sniffWorkbook(path) : sniffCsv(path);
    }

    // ---------------------------------------------------------------- CSV

    private static SniffedLayout sniffCsv(Path path) throws IOException {
        Charset charset = detectCharset(path);
        String text = stripBom(new String(Files.readAllBytes(path), charset));

        List<List<String>> best = null;
        char bestDelimiter = DELIMITERS[0];
        double bestScore = -1;
        for (char delimiter : DELIMITERS) {
            List<List<String>> grid = readCsvGrid(text, delimiter);
            double score = gridScore(grid);
            if (score > bestScore) {
                bestScore = score;
                best = grid;
                bestDelimiter = delimiter;
            }
        }
        return withHeaderRow(best, charset, bestDelimiter);
    }

    private static List<List<String>> readCsvGrid(String text, char delimiter) throws IOException {
        // Duplicate header names are tolerated at this level because nothing is keyed by name
        // yet - the grid is positional. Ambiguity is resolved later, when a profile names the
        // columns it wants.
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setDuplicateHeaderMode(DuplicateHeaderMode.ALLOW_ALL)
                .build();
        List<List<String>> grid = new ArrayList<>();
        try (Reader reader = new StringReader(text); CSVParser parser = format.parse(reader)) {
            for (CSVRecord record : parser) {
                List<String> row = new ArrayList<>(record.size());
                for (String value : record) {
                    row.add(value == null ? "" : value.trim());
                }
                grid.add(row);
            }
        }
        return grid;
    }

    /**
     * Rewards the delimiter that produces the widest <em>consistent</em> table. A wrong
     * delimiter yields one fat single-column row per line, which scores 1; the right one
     * yields many rows agreeing on a column count well above 1.
     */
    private static double gridScore(List<List<String>> grid) {
        if (grid.isEmpty()) {
            return -1;
        }
        int modalWidth = 0;
        int modalCount = 0;
        for (int width = 1; width <= 64; width++) {
            int count = 0;
            for (List<String> row : grid) {
                if (row.size() == width) {
                    count++;
                }
            }
            if (count > modalCount || (count == modalCount && width > modalWidth)) {
                modalCount = count;
                modalWidth = width;
            }
        }
        return modalWidth <= 1 ? 0 : modalWidth + (double) modalCount / grid.size();
    }

    // ---------------------------------------------------------------- Excel

    private static SniffedLayout sniffWorkbook(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path); Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = widestSheet(workbook);
            DataFormatter formatter = new DataFormatter(Locale.ENGLISH);
            List<List<String>> grid = new ArrayList<>();
            if (sheet != null) {
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        Cell cell = row.getCell(c);
                        cells.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
                    }
                    grid.add(cells);
                }
            }
            // The workbook is already decoded text and has no delimiter; UTF-8 and ',' are
            // recorded so the SniffedLayout shape stays uniform across both media.
            return withHeaderRow(grid, StandardCharsets.UTF_8, ',');
        }
    }

    /** §11.2 "sheet selection": a statement workbook's table is the sheet with the most rows. */
    private static Sheet widestSheet(Workbook workbook) {
        Sheet best = null;
        int bestRows = -1;
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (sheet.getPhysicalNumberOfRows() > bestRows) {
                bestRows = sheet.getPhysicalNumberOfRows();
                best = sheet;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- header row

    /**
     * §11.2 "auto-detect header row". The header is the row with the most non-blank,
     * non-numeric cells within the scan window - a numeric cell is data, and the preamble
     * lines above a bank table are one or two cells wide where the header is five or six.
     */
    private static SniffedLayout withHeaderRow(List<List<String>> grid, Charset charset, char delimiter) {
        int bestIndex = 0;
        int bestScore = -1;
        int limit = Math.min(grid.size(), MAX_HEADER_SCAN_ROWS);
        for (int i = 0; i < limit; i++) {
            int score = headerScore(grid.get(i));
            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        if (grid.isEmpty()) {
            return new SniffedLayout(List.of(), charset, delimiter, 0, List.of());
        }
        List<String> headers = List.copyOf(grid.get(bestIndex));
        List<List<String>> dataRows = new ArrayList<>();
        for (int i = bestIndex + 1; i < grid.size(); i++) {
            dataRows.add(List.copyOf(grid.get(i)));
        }
        return new SniffedLayout(headers, charset, delimiter, bestIndex, List.copyOf(dataRows));
    }

    private static int headerScore(List<String> row) {
        int score = 0;
        for (String cell : row) {
            if (cell == null || cell.isBlank()) {
                continue;
            }
            score += looksNumeric(cell) ? -1 : 1;
        }
        return score;
    }

    private static boolean looksNumeric(String cell) {
        return cell.matches("[\\d.,/:\\-+()\\s]+");
    }

    // ---------------------------------------------------------------- profile

    /**
     * FR4: score each profile by normalized header-set overlap; accept the best match at or
     * above {@value #PROFILE_MATCH_THRESHOLD}, else {@code null}, which sends the file to the
     * interactive column mapper (§11.2) or to {@code PARSE-204} when nobody can be asked.
     */
    public static BankProfile resolveProfile(List<String> headers, List<BankProfile> profiles, String hint) {
        Set<String> normalizedHeaders = normalize(headers);
        BankProfile best = null;
        double bestScore = 0.0;
        for (BankProfile profile : profiles) {
            if (!profile.appliesTo("csv") && !profile.appliesTo("xlsx")) {
                continue;
            }
            Set<String> want = normalize(profile.match().headersAny());
            if (want.isEmpty()) {
                continue;
            }
            long overlap = want.stream().filter(normalizedHeaders::contains).count();
            double score = (double) overlap / want.size();
            if (hint != null && profile.match().filenameContains().stream().anyMatch(hint::equalsIgnoreCase)) {
                // A filename hint breaks ties between two banks whose exports share most of
                // their column names; it can never carry a profile over the threshold alone.
                score = Math.min(1.0, score + 0.1);
            }
            if (score > bestScore) {
                bestScore = score;
                best = profile;
            }
        }
        return bestScore >= PROFILE_MATCH_THRESHOLD ? best : null;
    }

    // ---------------------------------------------------------------- mapping

    public static List<RawRow> mapColumns(SniffedLayout layout, BankProfile profile, String sourceFileName) {
        List<Pattern> skipPatterns = new ArrayList<>();
        for (String p : profile.skipRowsMatching()) {
            try {
                skipPatterns.add(Pattern.compile(p));
            } catch (RuntimeException ignored) {
                // A bad skip pattern must not take the file down (NFR3).
            }
        }

        int dateCol = indexOf(layout.headers(), profile.columns().get("date"));
        int descCol = indexOf(layout.headers(), profile.columns().get("description"));
        int debitCol = indexOf(layout.headers(), profile.columns().get("debit"));
        int creditCol = indexOf(layout.headers(), profile.columns().get("credit"));
        int amountCol = indexOf(layout.headers(), profile.columns().get("amount"));
        int balanceCol = indexOf(layout.headers(), profile.columns().get("balance"));

        List<RawRow> rows = new ArrayList<>();
        int rowIndex = 0;
        for (List<String> record : layout.dataRows()) {
            if (isBlank(record) || matchesSkipPattern(record, skipPatterns)) {
                continue;
            }
            String dateText = at(record, dateCol);
            String descText = at(record, descCol);
            if (isBlankValue(dateText) && isBlankValue(descText)) {
                continue;
            }
            rows.add(new RawRow(
                    sourceFileName,
                    null,
                    rowIndex++,
                    dateText,
                    descText,
                    at(record, debitCol),
                    at(record, creditCol),
                    at(record, amountCol),
                    at(record, balanceCol),
                    null,
                    1.0,
                    "tabular"
            ));
        }
        return rows;
    }

    private static int indexOf(List<String> headers, String columnName) {
        if (columnName == null) {
            return -1;
        }
        String want = columnName.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).trim().toLowerCase(Locale.ROOT).equals(want)) {
                return i;
            }
        }
        return -1;
    }

    private static String at(List<String> record, int index) {
        if (index < 0 || index >= record.size()) {
            return null;
        }
        String value = record.get(index);
        return value == null ? null : value.trim();
    }

    private static boolean matchesSkipPattern(List<String> record, List<Pattern> patterns) {
        if (patterns.isEmpty()) {
            return false;
        }
        // Blank cells are excluded here (a fully-blank row is caught separately by
        // isBlank(record) above): a per-bank pattern like "^\s*$" is meant to catch a
        // wholly-blank row, not trivially match every ordinary row's empty debit-or-credit
        // column.
        for (String value : record) {
            if (value == null || value.isBlank()) {
                continue;
            }
            for (Pattern p : patterns) {
                if (p.matcher(value).find()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isBlank(List<String> record) {
        for (String value : record) {
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBlankValue(String value) {
        return value == null || value.isBlank();
    }

    private static Set<String> normalize(List<String> values) {
        Set<String> out = new LinkedHashSet<>();
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                out.add(v.trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    /**
     * C4c's declared encoding fallback: UTF-8 (BOM stripped), then windows-1252, then
     * ISO-8859-1 - the doc's "utf-8-sig, cp1252, latin-1". ISO-8859-1 decodes any byte
     * sequence, so the chain always terminates; {@code PARSE-207} is reserved for a file that
     * cannot be read at all.
     */
    private static Charset detectCharset(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        for (Charset charset : List.of(StandardCharsets.UTF_8, Charset.forName("windows-1252"))) {
            try {
                charset.newDecoder().decode(ByteBuffer.wrap(bytes));
                return charset;
            } catch (CharacterCodingException ignored) {
                // try the next declared encoding
            }
        }
        return StandardCharsets.ISO_8859_1;
    }

    private static String stripBom(String text) {
        final char bom = '﻿'; // escaped, so the source file holds no invisible codepoint
        return !text.isEmpty() && text.charAt(0) == bom ? text.substring(1) : text;
    }
}
