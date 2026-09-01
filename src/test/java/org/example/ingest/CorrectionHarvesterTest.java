package org.example.ingest;

import org.example.categorize.CorrectionsStore;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** C3 {@code harvest_correction_patches} — FR20 → FR9 → AC4, and §3.6's whole-patch rule. */
class CorrectionHarvesterTest {

    @Test
    void aValidPatchIsAppliedAndArchived(@TempDir Path tmp) throws IOException {
        Workspace w = workspace(tmp);
        writePatch(w.input.resolve("corrections-2026-08.json"), """
                {"schema_version":1,"kind":"expense-nutshell-corrections-patch","month":"2026-08",
                 "corrections":[{"merchant_key":"AMAZON","category":"Groceries","scope":"merchant",
                                 "transaction_id":null,"example_description":"UPI/AMAZON/1/PAYMENT"}]}""");

        CorrectionHarvester.HarvestResult result = CorrectionHarvester.harvest(
                w.input, w.corrections, w.archive, categories());

        assertEquals(1, result.patchesApplied());
        assertEquals(1, result.correctionsApplied());
        assertEquals("Groceries",
                CorrectionsStore.load(w.corrections).lookup(txn("x", "AMAZON", "Uncategorized")).category());
        // Moved, not deleted: that is what makes the harvest idempotent and leaves an audit trail.
        assertFalse(Files.exists(w.input.resolve("corrections-2026-08.json")));
        assertEquals(1, result.archived().size());
        assertTrue(result.archived().get(0).getFileName().toString().contains(".applied-"));
    }

    @Test
    void reRunningWithAnAlreadyArchivedPatchChangesNothing(@TempDir Path tmp) throws IOException {
        Workspace w = workspace(tmp);
        writePatch(w.input.resolve("corrections-2026-08.json"), """
                {"schema_version":1,"kind":"expense-nutshell-corrections-patch","month":"2026-08",
                 "corrections":[{"merchant_key":"AMAZON","category":"Groceries","scope":"merchant"}]}""");
        CorrectionHarvester.harvest(w.input, w.corrections, w.archive, categories());
        String afterFirst = Files.readString(w.corrections, StandardCharsets.UTF_8);

        CorrectionHarvester.HarvestResult second = CorrectionHarvester.harvest(
                w.input, w.corrections, w.archive, categories());

        // The slice-5 exit criterion, verbatim: "Re-running with an already-archived patch
        // changes nothing."
        assertEquals(0, second.patchesApplied());
        assertEquals(afterFirst, Files.readString(w.corrections, StandardCharsets.UTF_8));
    }

    @Test
    void aPatchNamingAnUnknownCategoryIsRejectedWholeAndLeftInPlace(@TempDir Path tmp) throws IOException {
        Workspace w = workspace(tmp);
        Path patch = w.input.resolve("corrections-2026-08.json");
        writePatch(patch, """
                {"schema_version":1,"kind":"expense-nutshell-corrections-patch","month":"2026-08",
                 "corrections":[{"merchant_key":"AMAZON","category":"Groceries","scope":"merchant"},
                                {"merchant_key":"SWIGGY","category":"Nonexistent","scope":"merchant"}]}""");

        CorrectionHarvester.HarvestResult result = CorrectionHarvester.harvest(
                w.input, w.corrections, w.archive, categories());

        // §3.6: "Any violation rejects the WHOLE patch". The valid first entry must not sneak in
        // - a half-applied patch leaves the user believing all of it stuck.
        assertEquals(0, result.correctionsApplied());
        assertEquals(1, result.rejected().size());
        assertTrue(result.rejected().get(0).reason().contains("Nonexistent"));
        assertTrue(Files.exists(patch), "a rejected patch stays put so the user can fix it");
        assertFalse(Files.exists(w.corrections), "nothing was written");
    }

    @Test
    void aFileThatIsNotACorrectionsPatchIsRejectedByItsKind(@TempDir Path tmp) throws IOException {
        Workspace w = workspace(tmp);
        writePatch(w.input.resolve("corrections-something.json"), "{\"kind\":\"something-else\"}");

        CorrectionHarvester.HarvestResult result = CorrectionHarvester.harvest(
                w.input, w.corrections, w.archive, categories());

        assertEquals(1, result.rejected().size());
        assertTrue(result.rejected().get(0).reason().contains("not a corrections patch"));
    }

    @Test
    void unreadableJsonIsRejectedWithoutLeakingAPath(@TempDir Path tmp) throws IOException {
        Workspace w = workspace(tmp);
        writePatch(w.input.resolve("corrections-2026-08.json"), "{ this is not json");

        CorrectionHarvester.HarvestResult result = CorrectionHarvester.harvest(
                w.input, w.corrections, w.archive, categories());

        assertEquals(1, result.rejected().size());
        // §13: Jackson's own message embeds the absolute path, so it is never surfaced.
        assertFalse(result.rejected().get(0).reason().contains(tmp.toString()));
    }

    @Test
    void anEmptyMerchantKeyRejectsThePatch(@TempDir Path tmp) throws IOException {
        Workspace w = workspace(tmp);
        writePatch(w.input.resolve("corrections-2026-08.json"), """
                {"schema_version":1,"kind":"expense-nutshell-corrections-patch","month":"2026-08",
                 "corrections":[{"merchant_key":"","category":"Groceries","scope":"merchant"}]}""");

        CorrectionHarvester.HarvestResult result = CorrectionHarvester.harvest(
                w.input, w.corrections, w.archive, categories());

        assertEquals(1, result.rejected().size());
        assertTrue(result.rejected().get(0).reason().contains("no merchant_key"));
    }

    @Test
    void noPatchesIsNotAnError(@TempDir Path tmp) throws IOException {
        Workspace w = workspace(tmp);

        CorrectionHarvester.HarvestResult result = CorrectionHarvester.harvest(
                w.input, w.corrections, w.archive, categories());

        assertNotNull(result);
        assertEquals(0, result.patchesApplied());
        assertEquals(List.of(), result.rejected());
    }

    private record Workspace(Path input, Path corrections, Path archive) {
    }

    private static Workspace workspace(Path tmp) throws IOException {
        Path input = tmp.resolve("input");
        Files.createDirectories(input);
        return new Workspace(input, tmp.resolve("config").resolve("corrections.json"), tmp.resolve("archive"));
    }

    private static void writePatch(Path path, String json) throws IOException {
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    private static CategorySet categories() {
        return new CategorySet(List.of(
                new Category("Food & Dining", List.of("swiggy"), List.of(), false, false, 2, 0),
                new Category("Groceries", List.of("bigbasket"), List.of(), false, false, 3, 1),
                new Category("Uncategorized", List.of(), List.of(), false, false, null, 2)));
    }

    private static Transaction txn(String id, String merchantKey, String category) {
        return new Transaction(id, LocalDate.of(2026, 8, 2), merchantKey,
                "UPI/" + merchantKey + "/9928311/PAYMENT", new BigDecimal("487.00"), "INR",
                Direction.DEBIT, null, "f.csv", category, CategorySource.UNCATEGORIZED,
                false, false, merchantKey, 1.0, null, null, 0);
    }
}
