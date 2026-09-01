package org.example.categorize;

import org.example.config.CategorySet;

import java.util.ArrayList;
import java.util.List;

/**
 * 02-data-model.md §3.6's harvest validation: "{@code kind} must match exactly; every
 * {@code category} must exist; every {@code merchant_key} must be non-empty. Any violation
 * rejects the <strong>whole</strong> patch with a reason (a half-applied patch is worse than
 * none)."
 *
 * <p>Whole-patch rejection is the point. A patch is one editing session — the user changed five
 * rows and saved. Applying three of those and dropping two leaves them believing all five stuck,
 * with no signal about which two did not, and no way to tell without re-reading a JSON file.</p>
 */
public final class PatchValidator {

    /** Why a patch was refused, phrased for a user reading {@code run-log.txt}. */
    public record Result(boolean valid, String reason) {
        static Result ok() {
            return new Result(true, "");
        }

        static Result rejected(String reason) {
            return new Result(false, reason);
        }
    }

    private PatchValidator() {
    }

    public static Result validate(CorrectionPatch patch, CategorySet categories) {
        if (patch == null) {
            return Result.rejected("the file is empty or is not JSON");
        }
        if (!CorrectionPatch.KIND.equals(patch.kind())) {
            return Result.rejected("'kind' is " + describe(patch.kind())
                    + " rather than '" + CorrectionPatch.KIND + "', so this is not a corrections patch");
        }
        if (patch.schemaVersion() != null && patch.schemaVersion() > 1) {
            return Result.rejected("it declares schema_version " + patch.schemaVersion()
                    + ", which this build does not understand");
        }
        if (patch.corrections() == null || patch.corrections().isEmpty()) {
            return Result.rejected("it contains no corrections");
        }

        List<String> problems = new ArrayList<>();
        for (int i = 0; i < patch.corrections().size(); i++) {
            CorrectionPatch.PatchEntry entry = patch.corrections().get(i);
            String at = "correction " + (i + 1);
            if (entry.merchantKey() == null || entry.merchantKey().isBlank()) {
                problems.add(at + " has no merchant_key");
            }
            if (entry.category() == null || entry.category().isBlank()) {
                problems.add(at + " has no category");
            } else if (categories.byName(entry.category()) == null) {
                // Not the same thing as WARN-501: that is a stored correction going stale later,
                // this is a patch arriving with a category that never existed.
                problems.add(at + " names category '" + entry.category()
                        + "', which is not in categories.yaml");
            }
            if (Correction.SCOPE_TRANSACTION.equals(entry.scope())
                    && (entry.transactionId() == null || entry.transactionId().isBlank())) {
                problems.add(at + " is transaction-scoped but names no transaction_id");
            }
            if (entry.scope() != null
                    && !Correction.SCOPE_MERCHANT.equals(entry.scope())
                    && !Correction.SCOPE_TRANSACTION.equals(entry.scope())) {
                problems.add(at + " has an unknown scope '" + entry.scope() + "'");
            }
        }
        return problems.isEmpty() ? Result.ok() : Result.rejected(String.join("; ", problems));
    }

    private static String describe(String kind) {
        return kind == null ? "absent" : "'" + kind + "'";
    }
}
