package org.example.analysis;

import java.math.BigDecimal;

/**
 * 02-data-model.md §1.2 {@code CategorySpend}.
 *
 * <p>{@code percent} is the <em>display</em> value: 1 decimal place, apportioned by largest
 * remainder so the rendered column sums to exactly 100.0. Chart geometry must never use it -
 * §1.2 is explicit that "Chart geometry uses the exact Decimal, never the rounded value" - so
 * the renderer passes {@code amount} to the chart and {@code percent} to the table.</p>
 *
 * <p>{@code slot} is the fixed chart colour slot (1-8) this category owns, or {@code null} for
 * a category that renders neutral. ADR-0007 rule 1: "Colour follows the category, never its
 * rank", so Food is the same colour every month and two months' charts stay comparable.</p>
 *
 * <p>{@code momChangePercent}/{@code momStatus} are FR14's, delivered in Slice 6. Until then
 * every row carries {@link #MOM_UNAVAILABLE}, which is a real state §1.2 names - not a
 * placeholder - and which S01 renders as an em dash.</p>
 */
public record CategorySpend(
        String name,
        BigDecimal amount,
        double percent,
        int txnCount,
        Integer slot,
        Double momChangePercent,
        String momStatus
) {
    /** One of {@code "up" | "down" | "flat" | "new" | "gone" | "unavailable"} (§1.2). */
    public static final String MOM_UNAVAILABLE = "unavailable";

    public CategorySpend(String name, BigDecimal amount, double percent, int txnCount) {
        this(name, amount, percent, txnCount, null, null, MOM_UNAVAILABLE);
    }

    public CategorySpend withPercent(double percent) {
        return new CategorySpend(name, amount, percent, txnCount, slot, momChangePercent, momStatus);
    }

    public CategorySpend withSlot(Integer slot) {
        return new CategorySpend(name, amount, percent, txnCount, slot, momChangePercent, momStatus);
    }

    /** EC6: a zero-spend category stays in {@code by_category} for FR14 but leaves the chart. */
    public boolean isZeroSpend() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }
}
