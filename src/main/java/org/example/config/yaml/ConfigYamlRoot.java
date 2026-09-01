package org.example.config.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Binds config.yaml (02-data-model.md §3.1). {@code transfers}/{@code ocr}/{@code logging}/
 * {@code paths} remain present in the shipped file but unbound - nothing reads them until
 * Slice 7 - and the mapper has FAIL_ON_UNKNOWN_PROPERTIES disabled. {@code dedupe} is bound
 * from Slice 3 on, when {@code deduplicate} (FR6/EC1) starts consuming it.
 */
public record ConfigYamlRoot(
        // Boxed: a primitive cannot tell "schema_version: 0" from a file that omits
        // the key entirely, and those are different problems to report.
        @JsonProperty("schema_version") Integer schemaVersion,
        CurrencySection currency,
        DedupeSection dedupe,
        ReportSection report
) {
    public record CurrencySection(@JsonProperty("default") String defaultCurrency, String locale) {
    }

    public record DedupeSection(
            List<String> key,
            @JsonProperty("cross_file_only") Boolean crossFileOnly,
            @JsonProperty("window_days") Integer windowDays
    ) {
    }

    public record ReportSection(
            @JsonProperty("open_browser") boolean openBrowser,
            String theme,
            @JsonProperty("top_n_slices") int topNSlices,
            @JsonProperty("min_slice_percent") double minSlicePercent,
            @JsonProperty("show_transfers_panel") boolean showTransfersPanel,
            @JsonProperty("keep_reruns") boolean keepReruns
    ) {
    }
}
