package org.example.parse;

import org.example.diagnostics.Diagnostic;
import org.example.diagnostics.ErrorCode;
import org.example.diagnostics.ExcludedFile;

import java.util.List;

/**
 * The S4 contract (03-interfaces-and-contracts.md §3): "{@code failed is not None} ⇒
 * {@code rows == []}". The static factories are the only way to build one, so that invariant
 * cannot be violated by a caller.
 *
 * <p>Every {@link StatementParser} returns one of these rather than throwing: obligation 1 of
 * the parser protocol is "never raise for data problems" (NFR3, FR5).</p>
 */
public record ParseOutcome(
        List<RawRow> rows,
        ExcludedFile failed,
        List<Diagnostic> warnings,
        String profileUsed
) {
    public static ParseOutcome ok(List<RawRow> rows, String profileUsed, List<Diagnostic> warnings) {
        return new ParseOutcome(List.copyOf(rows), null, warnings == null ? List.of() : List.copyOf(warnings),
                profileUsed);
    }

    public static ParseOutcome failed(String fileName, ErrorCode code, String detail) {
        return new ParseOutcome(List.of(),
                new ExcludedFile(fileName, code.code(), code.reason(), detail == null ? "" : detail),
                List.of(), null);
    }

    public boolean isFailed() {
        return failed != null;
    }
}
