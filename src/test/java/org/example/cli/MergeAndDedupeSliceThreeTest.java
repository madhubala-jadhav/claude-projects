package org.example.cli;

import org.example.parse.ColumnMapper;
import org.example.parse.PdfFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice 3's exit criteria end to end (AC1, AC9, FR6/EC1).
 *
 * <p>"One real PDF and one real CSV from different banks, same month, produce one merged report
 * with correct totals verified by hand against the statements. Adding the same file twice
 * changes no total. {@code transactions.csv} has the 15 frozen columns."</p>
 *
 * <p>The PDF here is generated rather than checked in - see {@code PdfFixtures} - but its layout
 * is the one validated against a real HDFC credit-card export, whose two printed control totals
 * the parser reproduces to the paisa.</p>
 */
class MergeAndDedupeSliceThreeTest {

    /** Sum of the fixture CSV's 14 August debits. */
    private static final String CSV_AUGUST_SPEND = "39700.00";

    /** The one August debit in the generated card statement (05/08, QUICK SERVICES). */
    private static final String PDF_AUGUST_SPEND = "598.00";

    private static final String MERGED_AUGUST_SPEND = "40298.00";

    @Test
    void aPdfAndACsvForTheSameMonthMergeIntoOneReport(@TempDir Path workspace) throws IOException {
        Path inputDir = seedInput(workspace);
        PdfFixtures.creditCardStatement(inputDir.resolve("hdfc_card_aug2026.pdf"));

        Cli.RunResult result = run(workspace, inputDir);

        assertEquals(0, result.exitCode());
        String html = Files.readString(result.reportPath(), StandardCharsets.UTF_8);
        assertTrue(html.contains("40,298.00") || html.contains(MERGED_AUGUST_SPEND),
                "merged spend should be the CSV's " + CSV_AUGUST_SPEND + " plus the PDF's " + PDF_AUGUST_SPEND);
    }

    @Test
    void addingTheSameFileTwiceChangesNoTotal(@TempDir Path workspace) throws IOException {
        Path inputDir = seedInput(workspace);
        Files.copy(fixtureCsv(), inputDir.resolve("hdfc_aug2026_again.csv"), StandardCopyOption.REPLACE_EXISTING);

        Cli.RunResult result = run(workspace, inputDir);

        assertEquals(0, result.exitCode());
        String html = Files.readString(result.reportPath(), StandardCharsets.UTF_8);
        // EC1: the duplicate file contributes nothing. Without dedupe this would read 79,400.00.
        assertTrue(html.contains("39,700.00") || html.contains(CSV_AUGUST_SPEND), html.length() + " chars");
        assertFalse(html.contains("79,400.00"), "the re-added file was counted twice");
    }

    @Test
    void writesATransactionsCsvBesideTheReport(@TempDir Path workspace) throws IOException {
        Path inputDir = seedInput(workspace);
        PdfFixtures.creditCardStatement(inputDir.resolve("hdfc_card_aug2026.pdf"));

        Cli.RunResult result = run(workspace, inputDir);

        Path csv = result.reportPath().getParent().resolve("transactions.csv");
        assertTrue(Files.exists(csv), "AC9: the categorized list must also be available as CSV");

        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        assertEquals(15, lines.get(0).substring(1).split(",", -1).length);
        // 15 CSV rows + the single August row from the card statement.
        assertEquals(16, lines.size() - 1);
        assertTrue(lines.stream().anyMatch(l -> l.contains("hdfc_card_aug2026.pdf")), "the PDF's row is missing");
        assertTrue(lines.stream().anyMatch(l -> l.contains("hdfc_aug2026.csv")), "the CSV's rows are missing");
    }

    @Test
    void julyRowsFromTheCardsBillingCycleAreExcludedButAccountedFor(@TempDir Path workspace) throws IOException {
        Path inputDir = seedInput(workspace);
        PdfFixtures.creditCardStatement(inputDir.resolve("hdfc_card_aug2026.pdf"));

        Cli.RunResult result = run(workspace, inputDir);

        // A card billing cycle straddles two calendar months. ADR-0012/A2 groups by transaction
        // date, so the four July rows are not in August's report - but nothing may vanish
        // silently, so each is named in the run log.
        String log = Files.readString(result.reportPath().getParent().resolve("run-log.txt"),
                StandardCharsets.UTF_8);
        assertTrue(log.contains("WARN-505"), log);
        assertTrue(log.contains("ROW-306"), log);
    }

    private static Path seedInput(Path workspace) throws IOException {
        Path inputDir = workspace.resolve("input");
        Files.createDirectories(inputDir);
        Files.copy(fixtureCsv(), inputDir.resolve("hdfc_aug2026.csv"), StandardCopyOption.REPLACE_EXISTING);
        return inputDir;
    }

    private static Path fixtureCsv() {
        return Path.of("src/test/resources/fixtures/hdfc_savings_sample.csv");
    }

    private static Cli.RunResult run(Path workspace, Path inputDir) {
        Cli.Options options = new Cli.Options(
                workspace.resolve("config"), inputDir, workspace.resolve("output"), false);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        return Cli.run(options, new PrintStream(buffer, true, StandardCharsets.UTF_8), nonInteractive(), null);
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
