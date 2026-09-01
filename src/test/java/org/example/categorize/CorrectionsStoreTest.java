package org.example.categorize;

import org.example.config.Category;
import org.example.config.CategorySet;
import org.example.normalize.CategorySource;
import org.example.normalize.Direction;
import org.example.normalize.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** C6 {@code CorrectionsStore} — FR9, and the durability rules ADR-0006 puts around it. */
class CorrectionsStoreTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 20, 14);

    @Test
    void aMerchantScopedCorrectionMatchesEveryTransactionWithThatKey() {
        CorrectionsStore store = CorrectionsStore.empty();
        store.apply(patch(entry("SWIGGY", "Groceries", "merchant", null)), NOW);

        // Different id, different date, different reference - same merchant. This is AC4.
        assertNotNull(store.lookup(txn("a1", "SWIGGY", "Uncategorized")));
        assertNotNull(store.lookup(txn("b2", "SWIGGY", "Food & Dining")));
        assertNull(store.lookup(txn("c3", "ZOMATO", "Uncategorized")));
    }

    @Test
    void aTransactionScopedPinBeatsAMerchantWideRule() {
        CorrectionsStore store = CorrectionsStore.empty();
        store.apply(patch(entry("SWIGGY", "Food & Dining", "merchant", null)), NOW);
        store.apply(patch(entry("SWIGGY", "Entertainment", "transaction", "a1")), NOW);

        // ADR-0005: "a transaction-scoped pin (L1) beats a merchant-wide rule (L2), because it
        // is the more specific statement."
        assertEquals("Entertainment", store.lookup(txn("a1", "SWIGGY", "Uncategorized")).category());
        assertEquals("Food & Dining", store.lookup(txn("b2", "SWIGGY", "Uncategorized")).category());
    }

    @Test
    void aLaterCorrectionWinsAndThePriorValueIsKept() {
        CorrectionsStore store = CorrectionsStore.empty();
        store.apply(patch(entry("AMAZON", "Shopping", "merchant", null)), NOW);
        store.apply(patch(entry("AMAZON", "Groceries", "merchant", null)), NOW.plusDays(30));

        assertEquals("Groceries", store.lookup(txn("x", "AMAZON", "Uncategorized")).category());
        assertEquals(1, store.corrections().size(), "last-write-wins replaces, it does not accumulate");
        // "Prior values move to history[] rather than being overwritten, so a mistaken
        // correction is recoverable."
        assertEquals(1, store.history().size());
        assertEquals("Shopping", store.history().get(0).category());
    }

    @Test
    void savingAndReloadingPreservesEverything(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("corrections.json");
        CorrectionsStore store = CorrectionsStore.empty();
        store.apply(patch(entry("AMAZON", "Shopping", "merchant", null)), NOW);
        store.apply(patch(entry("AMAZON", "Groceries", "merchant", null)), NOW.plusDays(1));
        store.save(file, NOW.plusDays(1));

        CorrectionsStore reloaded = CorrectionsStore.load(file);

        assertEquals(1, reloaded.corrections().size());
        assertEquals(1, reloaded.history().size());
        assertEquals("Groceries", reloaded.lookup(txn("x", "AMAZON", "Uncategorized")).category());
        // §3.5 stores ISO-8601, not a Jackson timestamp array.
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("2026-09-02T20:14"));
    }

    @Test
    void savingLeavesNoTemporaryFileBehind(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("corrections.json");
        CorrectionsStore store = CorrectionsStore.empty();
        store.apply(patch(entry("AMAZON", "Shopping", "merchant", null)), NOW);

        store.save(file, NOW);

        try (var entries = Files.list(tmp)) {
            assertEquals(List.of("corrections.json"),
                    entries.map(p -> p.getFileName().toString()).sorted().toList());
        }
    }

    @Test
    void aStoreWrittenByANewerBuildIsRefusedRatherThanOverwritten(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("corrections.json");
        Files.writeString(file, "{\"schema_version\": 99, \"corrections\": []}", StandardCharsets.UTF_8);

        // F4: this file is "the one thing the tool cannot recreate". Reading it as empty and
        // then saving would erase corrections a later build understands.
        IOException e = assertThrows(IOException.class, () -> CorrectionsStore.load(file));
        assertTrue(e.getMessage().contains("newer version"));
    }

    @Test
    void aCorrectionNamingAMissingCategoryIsReportedNotDeleted() {
        CorrectionsStore store = CorrectionsStore.empty();
        store.apply(patch(entry("AMAZON", "Groceries (old)", "merchant", null)), NOW);

        List<Correction> stale = store.staleAgainst(categories());

        // WARN-501: kept in the file so the user can fix the category name.
        assertEquals(1, stale.size());
        assertEquals("Groceries (old)", stale.get(0).category());
        assertFalse(store.isEmpty(), "the correction stays in the store");
    }

    static CorrectionPatch patch(CorrectionPatch.PatchEntry... entries) {
        return new CorrectionPatch(1, CorrectionPatch.KIND, "2026-08", "2026-09-01T20:12:40", List.of(entries));
    }

    static CorrectionPatch.PatchEntry entry(String key, String category, String scope, String txnId) {
        return new CorrectionPatch.PatchEntry(key, category, scope, txnId, "UPI/" + key + "/1234/PAYMENT");
    }

    static Transaction txn(String id, String merchantKey, String category) {
        return new Transaction(id, LocalDate.of(2026, 8, 2), merchantKey, "UPI/" + merchantKey + "/9928311/PAYMENT",
                new BigDecimal("487.00"), "INR", Direction.DEBIT, null, "f.csv", category,
                CategorySource.UNCATEGORIZED, false, false, merchantKey, 1.0, null, null, 0);
    }

    static CategorySet categories() {
        return new CategorySet(List.of(
                new Category("Food & Dining", List.of("swiggy"), List.of(), false, false, 2, 0),
                new Category("Groceries", List.of("bigbasket"), List.of(), false, false, 3, 1),
                new Category("Shopping", List.of(), List.of(), false, false, 5, 2),
                new Category("Entertainment", List.of(), List.of(), false, false, null, 3),
                new Category("Uncategorized", List.of(), List.of(), false, false, null, 4)));
    }
}
