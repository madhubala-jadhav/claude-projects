package org.example.diagnostics;

/**
 * The full error taxonomy, 03-interfaces-and-contracts.md §6.2. Every failure in the
 * pipeline carries one of these codes; the code is what appears in {@code run-log.txt}, in
 * {@code ExcludedFile.reason}/{@code RowError.reason}, and in the report's skipped-files
 * panel. Reason strings are copied verbatim from §6.2 - "user-facing prose, not exception
 * class names" (taxonomy rule #4).
 *
 * <p>{@link #stage()} is this implementation's mapping of the taxonomy's "Raised by" column
 * (C1-C9) onto the lowercase stage names used in the {@code run-log.txt} example in
 * 02-data-model.md §3.10 (config/ingest/parse/normalize/categorize/analysis/report/run). It
 * is the default stage used when a call site does not name one explicitly.</p>
 */
public enum ErrorCode {
    CFG_001("CFG-001", Severity.FATAL, "config", "config file is missing or unreadable"),
    CFG_002("CFG-002", Severity.FATAL, "config", "config file has invalid YAML"),
    CFG_003("CFG-003", Severity.FATAL, "config", "config value has the wrong type or an unknown enum"),
    CFG_004("CFG-004", Severity.FATAL, "config", "two categories claim the same chart slot"),
    CFG_005("CFG-005", Severity.WARN, "config", "unknown configuration key ignored"),

    WS_101("WS-101", Severity.FATAL, "run", "input folder not found"),
    WS_102("WS-102", Severity.FATAL, "run", "output folder is not writable"),

    PARSE_201("PARSE-201", Severity.FILE, "parse", "unreadable / password-protected"),
    PARSE_202("PARSE-202", Severity.FILE, "parse", "scanned PDF and OCR is not installed"),
    PARSE_203("PARSE-203", Severity.FILE, "parse", "no transaction table found in this PDF"),
    PARSE_204("PARSE-204", Severity.FILE, "parse", "no matching bank profile; run interactively once to map columns"),
    PARSE_205("PARSE-205", Severity.FILE, "ingest", "file is empty"),
    PARSE_206("PARSE-206", Severity.FILE, "ingest", "unsupported file type"),
    PARSE_207("PARSE-207", Severity.FILE, "parse", "text encoding could not be determined"),
    PARSE_208("PARSE-208", Severity.FILE, "parse", "file is corrupt or truncated"),
    PARSE_209("PARSE-209", Severity.FILE, "ingest", "permission denied"),

    ROW_301("ROW-301", Severity.ROW, "normalize", "date could not be parsed"),
    ROW_302("ROW-302", Severity.ROW, "normalize", "amount could not be parsed"),
    ROW_303("ROW-303", Severity.ROW, "normalize", "row has neither a debit nor a credit amount"),
    ROW_304("ROW-304", Severity.ROW, "normalize", "description was empty after cleaning"),
    ROW_305("ROW-305", Severity.ROW, "normalize", "duplicate of a row from another file — removed"),
    ROW_306("ROW-306", Severity.ROW, "run", "transaction is outside the reporting month"),

    FIELD_401("FIELD-401", Severity.FIELD, "parse", "low OCR confidence — please verify"),
    FIELD_402("FIELD-402", Severity.FIELD, "normalize", "foreign currency with no converted amount"),
    FIELD_403("FIELD-403", Severity.FIELD, "categorize", "no category rule matched"),
    FIELD_404("FIELD-404", Severity.FIELD, "parse", "account number not found in the statement header"),

    WARN_501("WARN-501", Severity.WARN, "categorize", "a saved correction points at a category that no longer exists"),
    WARN_502("WARN-502", Severity.WARN, "categorize", "a category regex failed to compile and was skipped"),
    WARN_503("WARN-503", Severity.WARN, "analysis", "no prior month found — month-over-month unavailable"),
    WARN_504("WARN-504", Severity.WARN, "ingest", "correction patch rejected"),
    WARN_505("WARN-505", Severity.WARN, "run", "statements span more than one month; other months were excluded"),
    WARN_506("WARN-506", Severity.WARN, "report", "browser could not be opened — report path printed instead"),
    WARN_507("WARN-507", Severity.WARN, "report", "Chart.js asset missing — report rendered without the pie chart"),

    INT_901("INT-901", Severity.FATAL, "run", "internal error");

    private final String code;
    private final Severity severity;
    private final String stage;
    private final String reason;

    ErrorCode(String code, Severity severity, String stage, String reason) {
        this.code = code;
        this.severity = severity;
        this.stage = stage;
        this.reason = reason;
    }

    public String code() {
        return code;
    }

    /** Alias for {@link #code()} - the taxonomy's own column header is "Code". */
    public String id() {
        return code;
    }

    public Severity severity() {
        return severity;
    }

    public String stage() {
        return stage;
    }

    /** User-facing prose (taxonomy rule #4) - never an exception class name. */
    public String reason() {
        return reason;
    }
}
