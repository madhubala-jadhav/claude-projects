package org.example.config;

import java.util.List;
import java.util.Map;

/**
 * 02-data-model.md §1.5.
 *
 * <p>Slice 3 adds the two fields §1.5 leaves as an open object or defaults:</p>
 * <ul>
 *   <li>{@code pdfTable} - the §1.5 {@code pdf_table.*} block, which ADR-0016 keeps as pure
 *       configuration ("Templates stay configuration ... it describes clustering tolerances
 *       and crop regions, not a library API").</li>
 *   <li>{@code creditMarkers} - an <em>additive optional</em> field (permitted at the same
 *       schema version by 03-interfaces-and-contracts.md §1's FROZEN change policy) that
 *       parameterises the existing {@code amount_sign: dr_cr_suffix} mode. §1.5 froze
 *       {@code amount_sign} to three values and named the marker mode after the Dr/Cr
 *       convention; real statements use other glyphs for the same idea - the validated HDFC
 *       credit-card export marks its one credit row with a leading {@code +} - so the
 *       <em>marker set</em> is configuration while the enum stays frozen. Empty ⇒ the
 *       documented default {@code ["Cr", "CR"]}.</li>
 * </ul>
 */
public record BankProfile(
        String name,
        List<String> appliesTo,
        Match match,
        Map<String, String> columns,
        String dateOrder,
        List<String> dateFormats,
        String decimalSeparator,
        String thousandsSeparator,
        String amountSign,
        List<String> creditMarkers,
        String accountNumberRegex,
        List<String> skipRowsMatching,
        PdfTable pdfTable
) {
    public record Match(List<String> headersAny, List<String> filenameContains) {
    }

    /**
     * 02-data-model.md §3.3's {@code pdf_table} block. {@code strategy} is carried through
     * verbatim but unused by the PDFBox word-clustering path (it named a pdfplumber table
     * strategy in the pre-ADR-0016 design); it is kept so a profile file written against the
     * documented shape still loads unchanged.
     */
    public record PdfTable(
            String strategy,
            double xTolerance,
            double yTolerance,
            List<String> headerRowContains,
            boolean dropHeaderRowsAfterFirstPage,
            double cropTopPct,
            double cropBottomPct,
            double wrapMaxGap
    ) {
        /** The §3.3 defaults, used for any key a profile leaves out. */
        public static PdfTable defaults() {
            return new PdfTable("lines", 2.0, 1.5, List.of("Date", "Balance"), true, 0.0, 0.06, 0.0);
        }
    }

    /** Direction encodings 02-data-model.md §1.5 freezes for {@code amount_sign}. */
    public static final String SIGN_SEPARATE_COLUMNS = "separate_columns";
    public static final String SIGN_DEBIT_NEGATIVE = "debit_negative";
    public static final String SIGN_DR_CR_SUFFIX = "dr_cr_suffix";

    public List<String> creditMarkersOrDefault() {
        return creditMarkers == null || creditMarkers.isEmpty() ? List.of("Cr", "CR") : creditMarkers;
    }

    public PdfTable pdfTableOrDefault() {
        return pdfTable == null ? PdfTable.defaults() : pdfTable;
    }

    public boolean appliesTo(String kind) {
        return appliesTo != null && appliesTo.stream().anyMatch(k -> k.equalsIgnoreCase(kind));
    }
}
