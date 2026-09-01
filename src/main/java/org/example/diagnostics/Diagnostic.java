package org.example.diagnostics;

/**
 * 01-components.md C9 {@code RunReport.warnings}: WARN-level, never fatal, nothing dropped.
 * {@code code} is a taxonomy code (e.g. {@code "WARN-505"}) when the warning has one, or
 * {@code null} for a freeform note.
 */
public record Diagnostic(String stage, String code, String message) {
}
