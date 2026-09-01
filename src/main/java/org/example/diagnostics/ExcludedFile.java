package org.example.diagnostics;

/**
 * 01-components.md C9. {@code file} is a basename only - never the full path, which may
 * contain a username (§13). {@code reason} is a taxonomy reason string (03 §6.2);
 * {@code code} is the taxonomy code itself (e.g. {@code "PARSE-206"}), shown alongside the
 * reason because it is the string a user searches the docs for.
 */
public record ExcludedFile(String file, String code, String reason, String detail) {
    public ExcludedFile {
        detail = detail == null ? "" : detail;
    }
}
