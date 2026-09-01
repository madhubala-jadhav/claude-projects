package org.example.diagnostics;

/**
 * 03-interfaces-and-contracts.md §6.1. Every taxonomy code carries exactly one of these.
 */
public enum Severity {
    /** Run aborts, non-zero exit. */
    FATAL,
    /** This file is skipped, run continues (FR5). */
    FILE,
    /** This row is dropped, file continues (NFR3). */
    ROW,
    /** Row kept, field degraded, needs_review=true (NFR6). */
    FIELD,
    /** Nothing dropped; user should know. */
    WARN
}
