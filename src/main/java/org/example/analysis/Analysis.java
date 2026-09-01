package org.example.analysis;

import org.example.config.Category;
import org.example.config.CategorySet;
import org.example.normalize.Direction;
import org.example.normalize.Transaction;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 01-components.md C7 - "Compute every number the report shows, and nothing the report does
 * not." Slice 4 adds {@code top_category} (FR13/AC3), {@code top_transactions} (FR15), the
 * chart slots and the accuracy-gap counts. {@code month_over_month} (FR14) is Slice 6 and
 * transfer exclusion (FR11) is Slice 7.
 */
public final class Analysis {

    private Analysis() {
    }

    public record Totals(BigDecimal totalSpend, BigDecimal totalIncome, BigDecimal net) {
    }

    public static Totals computeTotals(List<Transaction> transactions) {
        BigDecimal spend = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal income = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (Transaction txn : transactions) {
            if (txn.isTransfer()) {
                continue;
            }
            if (txn.direction() == Direction.DEBIT) {
                spend = spend.add(txn.amount());
            } else {
                income = income.add(txn.amount());
            }
        }
        return new Totals(spend, income, income.subtract(spend));
    }

    public static List<CategorySpend> spendByCategory(List<Transaction> transactions, CategorySet categories,
                                                        BigDecimal totalSpend) {
        Map<String, BigDecimal> amountByCategory = new LinkedHashMap<>();
        Map<String, Integer> countByCategory = new LinkedHashMap<>();
        for (Category category : categories.inOrder()) {
            amountByCategory.put(category.name(), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            countByCategory.put(category.name(), 0);
        }
        for (Transaction txn : transactions) {
            if (txn.isTransfer() || txn.direction() != Direction.DEBIT) {
                continue;
            }
            amountByCategory.merge(txn.category(), txn.amount(), BigDecimal::add);
            countByCategory.merge(txn.category(), 1, Integer::sum);
        }

        List<CategorySpend> spends = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : amountByCategory.entrySet()) {
            spends.add(new CategorySpend(e.getKey(), e.getValue(), 0.0, countByCategory.get(e.getKey()))
                    .withSlot(slotOf(categories, e.getKey())));
        }
        spends.sort((a, b) -> b.amount().compareTo(a.amount()));

        applyLargestRemainderPercentages(spends, totalSpend);
        return spends;
    }

    private static Integer slotOf(CategorySet categories, String name) {
        for (Category category : categories.inOrder()) {
            if (category.name().equals(name)) {
                return category.slot();
            }
        }
        return null;
    }

    /**
     * FR13/AC3. "Highest spend. Ties are broken by the larger transaction count, then
     * alphabetically, so the callout is stable across reruns." Returns null when nothing was
     * spent (EC5) - the callout then has nothing to claim, rather than claiming zero.
     */
    public static TopCategory topCategory(List<CategorySpend> byCategory, BigDecimal totalSpend) {
        if (totalSpend == null || totalSpend.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        CategorySpend best = null;
        for (CategorySpend candidate : byCategory) {
            if (candidate.isZeroSpend()) {
                continue;
            }
            if (best == null || beats(candidate, best)) {
                best = candidate;
            }
        }
        return best == null ? null : new TopCategory(best.name(), best.amount(), best.percent());
    }

    private static boolean beats(CategorySpend candidate, CategorySpend incumbent) {
        int byAmount = candidate.amount().compareTo(incumbent.amount());
        if (byAmount != 0) {
            return byAmount > 0;
        }
        if (candidate.txnCount() != incumbent.txnCount()) {
            return candidate.txnCount() > incumbent.txnCount();
        }
        return candidate.name().compareTo(incumbent.name()) < 0;
    }

    /**
     * FR15. "Largest debits by absolute amount regardless of category, transfers excluded,
     * ties broken by date then description."
     */
    public static List<TransactionRef> topTransactions(List<Transaction> transactions, int n) {
        List<Transaction> debits = new ArrayList<>();
        for (Transaction txn : transactions) {
            if (!txn.isTransfer() && txn.direction() == Direction.DEBIT) {
                debits.add(txn);
            }
        }
        debits.sort(Comparator.comparing(Transaction::amount).reversed()
                .thenComparing(Transaction::date)
                .thenComparing(Transaction::description));

        List<TransactionRef> refs = new ArrayList<>();
        for (Transaction txn : debits.subList(0, Math.min(n, debits.size()))) {
            refs.add(new TransactionRef(txn.id(), txn.date(), txn.description(), txn.amount(), txn.category()));
        }
        return refs;
    }

    /**
     * NFR6: rows the tool is not confident about - low OCR confidence (FR3) or a foreign
     * currency with no converted amount (EC4). Deliberately counted separately from
     * "uncategorized": a row can be confidently read and still have no rule, or be correctly
     * categorised and badly scanned, and conflating the two hides both.
     */
    public static int needsReviewCount(List<Transaction> transactions) {
        int count = 0;
        for (Transaction txn : transactions) {
            if (txn.needsReview()) {
                count++;
            }
        }
        return count;
    }

    /** FR11's excluded total. Zero until transfer detection lands in Slice 7. */
    public static BigDecimal transfersTotal(List<Transaction> transactions) {
        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (Transaction txn : transactions) {
            if (txn.isTransfer()) {
                total = total.add(txn.amount());
            }
        }
        return total;
    }

    /**
     * US6/AC1's merge evidence: one entry per (account, file) pair actually seen. Grouped by
     * both because a statement need not disclose its account number - {@code source_account}
     * is null then, and the file name is the only thing that distinguishes two such sources.
     */
    public static List<AccountSummary> accounts(List<Transaction> transactions) {
        record Source(String account, String file) {
        }
        Map<Source, int[]> counts = new LinkedHashMap<>();
        Map<Source, BigDecimal> spend = new LinkedHashMap<>();
        for (Transaction txn : transactions) {
            Source key = new Source(txn.sourceAccount(), txn.sourceFile());
            counts.computeIfAbsent(key, k -> new int[1])[0]++;
            spend.putIfAbsent(key, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            if (!txn.isTransfer() && txn.direction() == Direction.DEBIT) {
                spend.put(key, spend.get(key).add(txn.amount()));
            }
        }
        List<AccountSummary> out = new ArrayList<>();
        for (Map.Entry<Source, int[]> e : counts.entrySet()) {
            out.add(new AccountSummary(e.getKey().account(), e.getKey().file(),
                    e.getValue()[0], spend.get(e.getKey())));
        }
        return out;
    }

    /**
     * FR12: the displayed 1-decimal-place percent column must sum to exactly 100.0. Exact
     * BigDecimal per-mille shares are floored to integers; the leftover per-mille units (at
     * most one per category) go to the categories with the largest fractional remainder -
     * the standard largest-remainder / Hare-Niemeyer apportionment method ADR-0018 requires.
     */
    private static void applyLargestRemainderPercentages(List<CategorySpend> spends, BigDecimal totalSpend) {
        if (totalSpend.compareTo(BigDecimal.ZERO) == 0) {
            for (int i = 0; i < spends.size(); i++) {
                spends.set(i, spends.get(i).withPercent(0.0));
            }
            return;
        }
        int n = spends.size();
        long[] perMilleFloor = new long[n];
        BigDecimal[] remainder = new BigDecimal[n];
        long assigned = 0;
        for (int i = 0; i < n; i++) {
            BigDecimal exactPerMille = spends.get(i).amount()
                    .multiply(BigDecimal.valueOf(1000))
                    .divide(totalSpend, MathContext.DECIMAL128);
            long floor = exactPerMille.setScale(0, RoundingMode.DOWN).longValueExact();
            perMilleFloor[i] = floor;
            remainder[i] = exactPerMille.subtract(BigDecimal.valueOf(floor));
            assigned += floor;
        }
        long leftover = 1000 - assigned;

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            order.add(i);
        }
        order.sort((a, b) -> remainder[b].compareTo(remainder[a]));
        for (int k = 0; k < leftover && k < order.size(); k++) {
            perMilleFloor[order.get(k)] += 1;
        }

        for (int i = 0; i < n; i++) {
            spends.set(i, spends.get(i).withPercent(perMilleFloor[i] / 10.0));
        }
    }
}
