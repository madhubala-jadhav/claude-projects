package org.example.cli;

import org.example.parse.ColumnMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice 5's exit criterion, end to end — and therefore AC4.
 *
 * <p>"Change a category in the report → save → drop the patch in {@code input/} → re-run on a
 * <strong>different</strong> statement containing the same merchant with a different reference
 * number → the transaction lands in the corrected category with
 * {@code category_source == "user_override"}."</p>
 *
 * <p>The "different statement, different reference number" part is the whole point: a
 * correction that only matched the exact transaction it was made on would satisfy nothing. It
 * is what makes {@code merchant_key} (F6) load-bearing.</p>
 */
class CorrectionsLoopSliceFiveTest {

    private static final String AUGUST = """
            Date,Narration,Withdrawal Amt.,Deposit Amt.,Closing Balance,Chq./Ref.No.
            03/08/26,UPI/BIGBASKET/9928311/PAYMENT,1980.00,,32533.00,REF1003
            """;

    /** September: same merchant, different reference number, different amount and id. */
    private static final String SEPTEMBER = """
            Date,Narration,Withdrawal Amt.,Deposit Amt.,Closing Balance,Chq./Ref.No.
            04/09/26,UPI/BIGBASKET/4471002/PAYMENT,2240.00,,30293.00,REF2001
            """;

    private static final String PATCH = """
            {"schema_version":1,
             "kind":"expense-nutshell-corrections-patch",
             "month":"2026-08",
             "exported_at":"2026-09-01T20:12:40",
             "corrections":[{"merchant_key":"BIGBASKET","category":"Entertainment","scope":"merchant",
                             "transaction_id":null,
                             "example_description":"UPI/BIGBASKET/9928311/PAYMENT"}]}
            """;

    @Test
    void aCorrectionMadeInAugustAppliesToSeptembersDifferentTransaction(@TempDir Path ws) throws IOException {
        Path input = ws.resolve("input");
        Files.createDirectories(input);

        // 1. August: BIGBASKET is categorised by the shipped keyword rule.
        Files.writeString(input.resolve("hdfc_aug2026.csv"), AUGUST, StandardCharsets.UTF_8);
        Cli.RunResult august = run(ws, input);
        assertEquals(0, august.exitCode());
        assertTrue(csvOf(august).stream().anyMatch(l -> l.contains("Groceries") && l.contains("rule")),
                "the keyword rule should claim it before any correction exists");

        // 2. The user disagrees, and the report exports a patch. Drop it in input/.
        Files.writeString(input.resolve("corrections-2026-08.json"), PATCH, StandardCharsets.UTF_8);

        // 3. A different month, a different statement, a different reference number.
        Files.delete(input.resolve("hdfc_aug2026.csv"));
        Files.writeString(input.resolve("hdfc_sep2026.csv"), SEPTEMBER, StandardCharsets.UTF_8);
        Cli.RunResult september = run(ws, input);

        assertEquals(0, september.exitCode());
        List<String> rows = csvOf(september);
        assertTrue(rows.stream().anyMatch(l -> l.contains("2240.00")
                        && l.contains("Entertainment") && l.contains("user_override")),
                () -> "the correction did not carry over: " + rows);
    }

    @Test
    void theCorrectionAppliesOnTheVeryRunThatHarvestsIt(@TempDir Path ws) throws IOException {
        Path input = ws.resolve("input");
        Files.createDirectories(input);
        Files.writeString(input.resolve("hdfc_aug2026.csv"), AUGUST, StandardCharsets.UTF_8);
        Files.writeString(input.resolve("corrections-2026-08.json"), PATCH, StandardCharsets.UTF_8);

        Cli.RunResult result = run(ws, input);

        // ADR-0006: "Harvest happens first ... so corrections apply to the run that harvests
        // them, not the one after." A loop whose result only shows up next month is one nobody
        // would trust enough to use.
        assertTrue(csvOf(result).stream().anyMatch(l -> l.contains("Entertainment")
                        && l.contains("user_override")),
                "the patch dropped for this run should already be applied");
    }

    @Test
    void anAppliedPatchIsArchivedAndNeverAppliedTwice(@TempDir Path ws) throws IOException {
        Path input = ws.resolve("input");
        Files.createDirectories(input);
        Files.writeString(input.resolve("hdfc_aug2026.csv"), AUGUST, StandardCharsets.UTF_8);
        Files.writeString(input.resolve("corrections-2026-08.json"), PATCH, StandardCharsets.UTF_8);

        run(ws, input);

        assertFalse(Files.exists(input.resolve("corrections-2026-08.json")), "the patch was moved out of input/");
        try (var archived = Files.list(ws.resolve("archive").resolve("corrections"))) {
            assertEquals(1, archived.count());
        }
        String afterFirst = Files.readString(ws.resolve("config").resolve("corrections.json"),
                StandardCharsets.UTF_8);

        run(ws, input);

        assertEquals(afterFirst, Files.readString(ws.resolve("config").resolve("corrections.json"),
                StandardCharsets.UTF_8), "a second run must change nothing");
    }

    @Test
    void aRejectedPatchIsNamedInTheReportAndLeftForTheUserToFix(@TempDir Path ws) throws IOException {
        Path input = ws.resolve("input");
        Files.createDirectories(input);
        Files.writeString(input.resolve("hdfc_aug2026.csv"), AUGUST, StandardCharsets.UTF_8);
        Files.writeString(input.resolve("corrections-2026-08.json"), PATCH.replace("Entertainment", "Nope"),
                StandardCharsets.UTF_8);

        Cli.RunResult result = run(ws, input);

        assertEquals(0, result.exitCode(), "a bad patch never fails the run");
        String log = Files.readString(result.reportPath().getParent().resolve("run-log.txt"), StandardCharsets.UTF_8);
        assertTrue(log.contains("WARN-504"), log);
        assertTrue(Files.exists(input.resolve("corrections-2026-08.json")), "left in place to be fixed");
        assertFalse(Files.exists(ws.resolve("config").resolve("corrections.json")), "nothing was stored");
    }

    @Test
    void aCorrectionForARenamedCategoryIsReportedAndTheRowFallsThrough(@TempDir Path ws) throws IOException {
        Path input = ws.resolve("input");
        Files.createDirectories(input);
        Files.writeString(input.resolve("hdfc_aug2026.csv"), AUGUST, StandardCharsets.UTF_8);
        Files.writeString(input.resolve("corrections-2026-08.json"), PATCH, StandardCharsets.UTF_8);
        run(ws, input);

        // The user renames the category the correction points at.
        Path categories = ws.resolve("config").resolve("categories.yaml");
        Files.writeString(categories,
                Files.readString(categories, StandardCharsets.UTF_8).replace("Entertainment:", "Fun Stuff:"),
                StandardCharsets.UTF_8);
        Cli.RunResult after = run(ws, input);

        String log = Files.readString(after.reportPath().getParent().resolve("run-log.txt"), StandardCharsets.UTF_8);
        assertTrue(log.contains("WARN-501"), log);
        // Kept, not deleted - the user renamed a category, they did not ask to forget what they
        // taught. The row falls back to the keyword rule for this run.
        assertTrue(Files.readString(ws.resolve("config").resolve("corrections.json"), StandardCharsets.UTF_8)
                .contains("Entertainment"));
        assertTrue(csvOf(after).stream().anyMatch(l -> l.contains("Groceries")));
    }

    private static List<String> csvOf(Cli.RunResult result) throws IOException {
        return Files.readAllLines(result.reportPath().getParent().resolve("transactions.csv"),
                StandardCharsets.UTF_8);
    }

    private static Cli.RunResult run(Path ws, Path input) {
        Cli.Options options = new Cli.Options(ws.resolve("config"), input, ws.resolve("output"), false);
        return Cli.run(options, new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                nonInteractive(), null);
    }

    private static ColumnMapper.Prompt nonInteractive() {
        return new ColumnMapper.Prompt() {
            @Override
            public boolean isInteractive() {
                return false;
            }

            @Override
            public String ask(String question) {
                throw new AssertionError("no prompting in an unattended run");
            }

            @Override
            public void say(String message) {
                throw new AssertionError("no prompting in an unattended run");
            }
        };
    }
}
