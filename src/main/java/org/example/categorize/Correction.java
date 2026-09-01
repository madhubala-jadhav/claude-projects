package org.example.categorize;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * 02-data-model.md §1.4 — one thing the user taught the tool.
 *
 * <p>{@code exampleDescription} is not decoration. F6 freezes {@code merchant_key}'s
 * derivation precisely because a change to it stops saved corrections matching; this field is
 * what a future migration would use to re-derive keys, and it is also what makes
 * {@code corrections.json} readable enough to hand-edit (ADR-0006's "manual escape hatch").</p>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record Correction(
        @JsonProperty("merchant_key") String merchantKey,
        String category,
        String scope,
        @JsonProperty("transaction_id") String transactionId,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("source_month") String sourceMonth,
        @JsonProperty("example_description") String exampleDescription
) {
    /** Applies to every future transaction with this merchant key — what AC4 requires. */
    public static final String SCOPE_MERCHANT = "merchant";

    /** Pins exactly one {@code Transaction.id}; the escape hatch for a genuinely one-off charge. */
    public static final String SCOPE_TRANSACTION = "transaction";

    @JsonIgnore
    public boolean isTransactionScoped() {
        return SCOPE_TRANSACTION.equals(scope);
    }

    /** The identity two corrections collide on for last-write-wins. */
    @JsonIgnore
    public String identity() {
        return isTransactionScoped() ? SCOPE_TRANSACTION + ":" + transactionId : SCOPE_MERCHANT + ":" + merchantKey;
    }
}
