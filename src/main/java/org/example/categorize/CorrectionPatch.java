package org.example.categorize;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 02-data-model.md §3.6 — what the browser exports (FR20), "deliberately a subset of §3.5, so
 * the browser never has to reproduce the full store".
 *
 * <p>F5 freezes this shape because "a report from March must still be usable in September —
 * old reports are archived artifacts, not upgradable clients". The {@code kind} discriminator
 * is what lets the harvester tell a correction patch from any other JSON a user might drop in
 * {@code input/}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CorrectionPatch(
        @JsonProperty("schema_version") Integer schemaVersion,
        String kind,
        String month,
        @JsonProperty("exported_at") String exportedAt,
        List<PatchEntry> corrections
) {
    /** The exact discriminator §3.6 requires; anything else rejects the whole file. */
    public static final String KIND = "expense-nutshell-corrections-patch";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PatchEntry(
            @JsonProperty("merchant_key") String merchantKey,
            String category,
            String scope,
            @JsonProperty("transaction_id") String transactionId,
            @JsonProperty("example_description") String exampleDescription
    ) {
    }
}
