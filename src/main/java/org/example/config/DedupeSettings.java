package org.example.config;

/**
 * 02-data-model.md §3.1's {@code dedupe} block, consumed by
 * {@link org.example.normalize.Deduplicator} (FR6, EC1).
 *
 * <p>{@code crossFileOnly} is the rule that matters: C5's contract says "same key from the
 * SAME source_file is kept (a real repeated charge); same key from a DIFFERENT source_file
 * is dropped as a re-added file". Two identical ₹120 coffees on one day are a real pair of
 * charges, not a data error - only re-adding a file is.</p>
 */
public record DedupeSettings(boolean crossFileOnly, int windowDays) {
    public static DedupeSettings defaults() {
        return new DedupeSettings(true, 0);
    }
}
