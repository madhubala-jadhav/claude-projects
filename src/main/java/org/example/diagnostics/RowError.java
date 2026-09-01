package org.example.diagnostics;

/**
 * 01-components.md C9 {@code RunReport.row_errors}: NFR3 "this row is dropped, the file
 * continues". {@code sourceFile} is a basename only. {@code message} is the taxonomy reason
 * string, optionally with row-specific detail appended (e.g. the offending date text) - the
 * same user-facing-prose rule as {@link ExcludedFile} (03 §6.2 rule #4).
 */
public record RowError(String sourceFile, int rowIndex, String code, String message) {
}
