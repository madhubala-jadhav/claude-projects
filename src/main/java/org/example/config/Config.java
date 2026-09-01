package org.example.config;

import java.util.List;

/**
 * 01-components.md C2. Carries the fields the pipeline actually consumes: {@code ocr} and
 * {@code accounts} still aren't bound into Java types (nothing reads them until Slice 7) -
 * the shipped default config.yaml documents their full shape regardless. {@code dedupe}
 * joins here in Slice 3 with {@code deduplicate} as its first consumer.
 */
public record Config(
        Workspace workspace,
        String currencyDefault,
        String locale,
        CategorySet categories,
        List<BankProfile> bankProfiles,
        DedupeSettings dedupe,
        boolean openBrowser,
        int topNSlices,
        double minSlicePercent
) {
}
