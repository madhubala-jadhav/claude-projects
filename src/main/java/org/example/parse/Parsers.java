package org.example.parse;

import org.example.ingest.StatementFile;

import java.util.List;

/**
 * 01-components.md C4 {@code get_parser}: "Registry lookup. Order: TabularParser (CSV/XLSX/XLS),
 * PdfTextParser, PdfOcrParser. Returns None for UNSUPPORTED (FR1)."
 *
 * <p>{@code PdfOcrParser} is Slice 7; until then {@link PdfTextParser} reports the documented
 * {@code PARSE-202} degradation for a PDF with no text layer, so the cascade still terminates
 * with a reason rather than a gap.</p>
 */
public final class Parsers {

    private static final List<StatementParser> REGISTRY = List.of(new TabularParser(), new PdfTextParser());

    private Parsers() {
    }

    public static StatementParser get(StatementFile file) {
        for (StatementParser parser : REGISTRY) {
            if (parser.supports(file)) {
                return parser;
            }
        }
        return null;
    }
}
