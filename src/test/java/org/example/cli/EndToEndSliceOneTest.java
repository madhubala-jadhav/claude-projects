package org.example.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves Slice 1's exit criteria end to end, independent of the manual run.bat execution:
 * one CSV in input/ produces output/&lt;YYYY-MM&gt;/report.html showing total spend and a
 * category table, using the hand-computed totals for the shipped fixture (see
 * src/test/resources/fixtures/hdfc_savings_sample.csv).
 */
class EndToEndSliceOneTest {

    @Test
    void oneCsvProducesAReportWithTheHandComputedTotals(@TempDir Path workspace) throws IOException {
        Path inputDir = workspace.resolve("input");
        Files.createDirectories(inputDir);
        Files.copy(Path.of("src/test/resources/fixtures/hdfc_savings_sample.csv"),
                inputDir.resolve("hdfc_aug2026.csv"), StandardCopyOption.REPLACE_EXISTING);

        Cli.Options options = new Cli.Options(
                workspace.resolve("config"), inputDir, workspace.resolve("output"), false);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Cli.RunResult result = Cli.run(options, new PrintStream(buffer, true, StandardCharsets.UTF_8));

        assertEquals(0, result.exitCode());

        Path reportPath = workspace.resolve("output").resolve("2026-08").resolve("report.html");
        assertTrue(Files.exists(reportPath), "report.html should exist at " + reportPath);
        assertEquals(reportPath.toAbsolutePath(), result.reportPath().toAbsolutePath());

        String html = Files.readString(reportPath);

        // Hand-computed from the fixture: total spend = sum of 14 debit rows = 39700.00,
        // total income = the one salary credit = 65000.00.
        assertTrue(html.contains("39,700.00") || html.contains("39700.00"));
        assertTrue(html.contains("65,000.00") || html.contains("65000.00"));

        // Every category the fixture exercises should have its own row.
        for (String category : new String[]{
                "Rent/Housing", "Groceries", "Transport", "Shopping",
                "Utilities", "Healthcare", "Entertainment", "Uncategorized"
        }) {
            assertTrue(html.contains(category), "missing category row: " + category);
        }
        assertTrue(html.contains("EMI/Loan Payments") || html.contains("EMI/Loan"));
        assertTrue(html.contains("Food") && html.contains("Dining"));

        // AC8: no external references anywhere in the rendered artifact.
        String lower = html.toLowerCase(java.util.Locale.ROOT);
        assertTrue(!lower.contains("src=\"http"));
        assertTrue(!lower.contains("href=\"http"));
        assertTrue(!lower.contains("fetch("));
    }
}
