package org.example.diagnostics;

/**
 * 01-components.md C9 {@code RunReport.duplicates_removed} (FR6). Modeled now so
 * {@code RunReport}'s shape matches the contract in full; {@code deduplicate} itself is
 * Slice 3 scope (EC1), so this list is always empty this slice.
 */
public record Duplicate(String sourceFile, int rowIndex, String reason) {
}
