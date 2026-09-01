package org.example.config.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Binds bank_profiles.yaml (02-data-model.md §3.3), including §3.3's {@code pdf_table} block.
 *
 * <p>{@code disable_shipped_profiles} is the escape hatch for the built-in overlay: profiles
 * named here are dropped from the shipped set rather than merged in. Without it, deleting a
 * built-in from the config file would be futile - the overlay would simply put it back.</p>
 */
public record BankProfilesYamlRoot(
        // Boxed: a primitive cannot tell "schema_version: 0" from a file that omits
        // the key entirely, and those are different problems to report.
        @JsonProperty("schema_version") Integer schemaVersion,
        @JsonProperty("disable_shipped_profiles") List<String> disableShippedProfiles,
        List<BankProfileYaml> profiles
) {
    public record BankProfileYaml(
            String name,
            @JsonProperty("applies_to") List<String> appliesTo,
            MatchYaml match,
            Map<String, String> columns,
            @JsonProperty("date_order") String dateOrder,
            @JsonProperty("date_formats") List<String> dateFormats,
            @JsonProperty("decimal_separator") String decimalSeparator,
            @JsonProperty("thousands_separator") String thousandsSeparator,
            @JsonProperty("amount_sign") String amountSign,
            @JsonProperty("credit_markers") List<String> creditMarkers,
            @JsonProperty("account_number_regex") String accountNumberRegex,
            @JsonProperty("skip_rows_matching") List<String> skipRowsMatching,
            @JsonProperty("pdf_table") PdfTableYaml pdfTable
    ) {
    }

    public record MatchYaml(
            @JsonProperty("headers_any") List<String> headersAny,
            @JsonProperty("filename_contains") List<String> filenameContains
    ) {
    }

    /**
     * Boxed types throughout: a null means "the profile did not say", which
     * {@code ConfigLoader} then fills from {@link org.example.config.BankProfile.PdfTable}'s
     * documented defaults. Primitives would silently turn an omitted {@code crop_bottom_pct}
     * into 0.0 and stop suppressing the page footer.
     */
    public record PdfTableYaml(
            String strategy,
            @JsonProperty("x_tolerance") Double xTolerance,
            @JsonProperty("y_tolerance") Double yTolerance,
            @JsonProperty("header_row_contains") List<String> headerRowContains,
            @JsonProperty("drop_header_rows_after_first_page") Boolean dropHeaderRowsAfterFirstPage,
            @JsonProperty("crop_top_pct") Double cropTopPct,
            @JsonProperty("crop_bottom_pct") Double cropBottomPct,
            @JsonProperty("wrap_max_gap") Double wrapMaxGap
    ) {
    }
}
