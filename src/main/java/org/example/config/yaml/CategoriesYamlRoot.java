package org.example.config.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Binds categories.yaml (02-data-model.md §3.2). {@code categories} is a
 * {@code Map&lt;String, CategoryYaml&gt;} because Jackson deserializes a YAML mapping into a
 * LinkedHashMap by default, preserving the file's declaration order - which the
 * categorization ladder's tie-break rule depends on. {@code overrides} is the ladder's L3
 * layer: an exact merchant_key to category mapping that beats every keyword rule.
 */
public record CategoriesYamlRoot(
        // Boxed: a primitive cannot tell "schema_version: 0" from a file that omits
        // the key entirely, and those are different problems to report.
        @JsonProperty("schema_version") Integer schemaVersion,
        Map<String, CategoryYaml> categories,
        Map<String, String> overrides
) {
    public record CategoryYaml(
            Integer slot,
            List<String> keywords,
            List<String> patterns,
            @JsonProperty("is_transfer") boolean isTransfer,
            @JsonProperty("is_income") boolean isIncome
    ) {
    }
}
