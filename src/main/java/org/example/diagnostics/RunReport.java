package org.example.diagnostics;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 01-components.md C9. "The single place where 'something went wrong but the run continues'
 * is recorded, and guarantee nothing sensitive is ever written to a log." One instance per
 * run; the only permitted shared mutable state in the pipeline (S8, 03 §3).
 *
 * <p>{@code file}/{@code sourceFile} arguments throughout this class are basenames, never a
 * full {@link java.nio.file.Path} - callers convert with {@code path.getFileName()} before
 * calling in, so nothing here can accidentally persist a path that contains a username
 * (§13). Writing {@code run-log.txt} itself is {@link RunLogger}'s job, applying
 * {@link Redaction#redact} to every line as the second line of defence the same section
 * names; this class only accumulates the structured records {@link RunLogger} reads from.</p>
 */
public final class RunReport {

    private final LocalDateTime startedAt = LocalDateTime.now();
    private final List<ExcludedFile> excludedFiles = new ArrayList<>();
    private final List<RowError> rowErrors = new ArrayList<>();
    private final List<Duplicate> duplicatesRemoved = new ArrayList<>();
    private final List<Diagnostic> warnings = new ArrayList<>();
    private final Map<String, Double> stageTimings = new LinkedHashMap<>();
    private String fatalStage;

    /** A {@link #timed} scope; deliberately declares no checked exception. */
    public interface TimerHandle extends AutoCloseable {
        @Override
        void close();
    }

    public LocalDateTime startedAt() {
        return startedAt;
    }

    public List<ExcludedFile> excludedFiles() {
        return Collections.unmodifiableList(excludedFiles);
    }

    public List<RowError> rowErrors() {
        return Collections.unmodifiableList(rowErrors);
    }

    public List<Duplicate> duplicatesRemoved() {
        return Collections.unmodifiableList(duplicatesRemoved);
    }

    public List<Diagnostic> warnings() {
        return Collections.unmodifiableList(warnings);
    }

    public Map<String, Double> stageTimings() {
        return Collections.unmodifiableMap(stageTimings);
    }

    public String fatalStage() {
        return fatalStage;
    }

    /** FR5/AC7: this file is skipped, the run continues. */
    public void excludeFile(String fileName, ErrorCode code) {
        excludeFile(fileName, code, "");
    }

    /**
     * Records an exclusion a {@code StatementParser} already decided (S4). The basename is
     * re-supplied by the caller so a parser cannot smuggle a full path into the report (§13).
     */
    public void excludeFile(String fileName, ExcludedFile excluded) {
        excludedFiles.add(new ExcludedFile(fileName, excluded.code(), excluded.reason(), excluded.detail()));
    }

    public void excludeFile(String fileName, ErrorCode code, String detail) {
        excludedFiles.add(new ExcludedFile(fileName, code.code(), code.reason(), detail));
    }

    /** NFR3/NFR6: this row is dropped (ROW) or kept-and-flagged (FIELD); the file continues. */
    public void addRowError(String fileName, int rowIndex, ErrorCode code, String message) {
        rowErrors.add(new RowError(fileName, rowIndex, code.code(), message == null ? code.reason() : message));
    }

    public void duplicate(String sourceFile, int rowIndex, String reason) {
        duplicatesRemoved.add(new Duplicate(sourceFile, rowIndex, reason));
    }

    /** Nothing dropped; user should know. */
    public void warn(String stage, String message) {
        warnings.add(new Diagnostic(stage, null, message));
    }

    public void warn(String stage, ErrorCode code, String detail) {
        String message = code.reason() + (detail == null || detail.isBlank() ? "" : " (" + detail + ")");
        warnings.add(new Diagnostic(stage, code.code(), message));
    }

    public TimerHandle timed(String stage) {
        long startNanos = System.nanoTime();
        return () -> stageTimings.put(stage, (System.nanoTime() - startNanos) / 1_000_000_000.0);
    }

    /**
     * C1's failure mode: "Any stage raising an unexpected exception -> caught, recorded as
     * RunReport.fatal_stage, and C8 is still invoked so the user gets a report explaining the
     * failure (NFR3)."
     */
    public void markFatal(String stage) {
        this.fatalStage = stage;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("startedAt", startedAt.toString());
        map.put("excludedFiles", excludedFiles());
        map.put("rowErrors", rowErrors());
        map.put("duplicatesRemoved", duplicatesRemoved());
        map.put("warnings", warnings());
        map.put("stageTimings", stageTimings());
        map.put("fatalStage", fatalStage);
        return map;
    }
}
