package org.example.cli;

import org.junit.jupiter.api.Assumptions;
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
 * Slice 2 exit criteria (07-implementation-roadmap.md), proven directly rather than claimed:
 * a folder containing one valid CSV plus a corrupt PDF, a zero-byte file and a .docx still
 * produces a report from the valid file with exit 0, and all three bad files are named with
 * reasons; a folder of only bad files still produces a real "no transactions found" report,
 * exit 0; no log line contains a full account number or an absolute path.
 */
class FaultInjectionTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/hdfc_savings_sample.csv");

    @Test
    void validCsvPlusThreeBadFilesStillProducesAReportFromTheValidFile(@TempDir Path workspace) throws IOException {
        Path inputDir = seedBadFiles(workspace);
        Files.copy(FIXTURE, inputDir.resolve("hdfc_aug2026.csv"), StandardCopyOption.REPLACE_EXISTING);

        Cli.Options options = new Cli.Options(workspace.resolve("config"), inputDir, workspace.resolve("output"),
                false);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Cli.RunResult result = Cli.run(options, new PrintStream(buffer, true, StandardCharsets.UTF_8));

        assertEquals(0, result.exitCode());
        String html = Files.readString(result.reportPath());

        // The valid file's transactions are present (hand-computed total from Slice 1).
        assertTrue(html.contains("39,700.00") || html.contains("39700.00"));

        // All three bad files are named, with a reason, in the skipped-files panel.
        assertTrue(html.contains("corrupt.pdf"), "corrupt.pdf should be named");
        assertTrue(html.contains("empty_statement.csv"), "the zero-byte file should be named");
        assertTrue(html.contains("resume.docx"), "resume.docx should be named");
        assertTrue(html.contains("file is empty"));
        assertTrue(html.contains("unsupported file type"));
    }

    @Test
    void folderOfOnlyBadFilesStillProducesAReportExitZero(@TempDir Path workspace) throws IOException {
        Path inputDir = seedBadFiles(workspace);

        Cli.Options options = new Cli.Options(workspace.resolve("config"), inputDir, workspace.resolve("output"),
                false);
        Cli.RunResult result = Cli.run(options, new PrintStream(new ByteArrayOutputStream(), true,
                StandardCharsets.UTF_8));

        assertEquals(0, result.exitCode());
        String html = Files.readString(result.reportPath());
        assertTrue(html.contains("No transactions found"));
        assertTrue(html.contains("corrupt.pdf"));
        assertTrue(html.contains("resume.docx"));
    }

    @Test
    void missingInputFolderExitsThree(@TempDir Path workspace) {
        Path missingInput = workspace.resolve("does-not-exist");
        Cli.Options options = new Cli.Options(workspace.resolve("config"), missingInput,
                workspace.resolve("output"), false);

        Cli.RunResult result = Cli.run(options, new PrintStream(new ByteArrayOutputStream(), true,
                StandardCharsets.UTF_8));

        assertEquals(3, result.exitCode());
    }

    @Test
    void outputPathBlockedByAFileExitsThree(@TempDir Path workspace) throws IOException {
        Path inputDir = workspace.resolve("input");
        Files.createDirectories(inputDir);
        Files.copy(FIXTURE, inputDir.resolve("hdfc_aug2026.csv"), StandardCopyOption.REPLACE_EXISTING);

        // A plain file sitting where the output directory needs to be created makes
        // Files.createDirectories fail portably, without relying on OS permission bits.
        Path outputAsFile = workspace.resolve("output");
        Files.writeString(outputAsFile, "not a directory");

        Cli.Options options = new Cli.Options(workspace.resolve("config"), inputDir, outputAsFile, false);
        Cli.RunResult result = Cli.run(options, new PrintStream(new ByteArrayOutputStream(), true,
                StandardCharsets.UTF_8));

        assertEquals(3, result.exitCode());
    }

    @Test
    void unreadableFileIsExcludedNotFatal_bestEffort(@TempDir Path workspace) throws IOException {
        Path inputDir = workspace.resolve("input");
        Files.createDirectories(inputDir);
        Files.copy(FIXTURE, inputDir.resolve("hdfc_aug2026.csv"), StandardCopyOption.REPLACE_EXISTING);

        Path locked = inputDir.resolve("locked.csv");
        Files.writeString(locked, "Date,Narration,Withdrawal Amt.,Deposit Amt.\n01/08/26,X,1.00,\n");
        boolean revoked = locked.toFile().setReadable(false, false);
        // On some Windows configurations (e.g. running as the file owner/admin) the JVM
        // cannot actually revoke owner read access, so Files.size() below would still
        // succeed. That's an environment limitation, not a code defect - skip rather than
        // false-fail when the OS didn't cooperate.
        Assumptions.assumeTrue(revoked, "OS did not allow revoking read access in this environment");
        try {
            Files.size(locked);
        } catch (IOException confirmedUnreadable) {
            // good - proceed to prove the run still completes
            Cli.Options options = new Cli.Options(workspace.resolve("config"), inputDir,
                    workspace.resolve("output"), false);
            Cli.RunResult result = Cli.run(options, new PrintStream(new ByteArrayOutputStream(), true,
                    StandardCharsets.UTF_8));
            assertEquals(0, result.exitCode());
            String html = Files.readString(result.reportPath());
            assertTrue(html.contains("locked.csv"));
            assertTrue(html.contains("permission denied"));
            return;
        } finally {
            locked.toFile().setReadable(true, false);
        }
        Assumptions.assumeTrue(false, "revoking read access did not actually block Files.size() here");
    }

    @Test
    void aMalformedCsvIsSkippedAsCorruptNotAsCrash(@TempDir Path workspace) throws IOException {
        Path inputDir = workspace.resolve("input");
        Files.createDirectories(inputDir);
        // The header matches the hdfc profile exactly, so the file gets as far as being read;
        // the data row then puts bare text immediately after a closing quote, which Commons
        // CSV rejects outright. A real, deterministic corruption rather than a hand-thrown
        // exception.
        Files.writeString(inputDir.resolve("malformed.csv"),
                "Date,Narration,Withdrawal Amt.,Deposit Amt.,Closing Balance,Chq./Ref.No.\n"
                        + "01/08/26,\"QUOTED\"TRAILING,100.00,,900.00,REF1\n");
        Files.copy(FIXTURE, inputDir.resolve("hdfc_aug2026.csv"), StandardCopyOption.REPLACE_EXISTING);

        Cli.Options options = new Cli.Options(workspace.resolve("config"), inputDir, workspace.resolve("output"),
                false);
        Cli.RunResult result = Cli.run(options, new PrintStream(new ByteArrayOutputStream(), true,
                StandardCharsets.UTF_8));

        assertEquals(0, result.exitCode());
        String html = Files.readString(result.reportPath());
        assertTrue(html.contains("malformed.csv"), "the corrupt file should be named");
        assertTrue(html.contains("file is corrupt or truncated"));
        // And the exception's own message never reached the report. Several JDK and Commons CSV
        // exceptions embed the absolute file path, which §13 keeps out of diagnostics.
        //
        // Scoped to the skipped-files panel deliberately, not the whole page: the report DOES
        // legitimately print absolute paths elsewhere. ADR-0006 requires the corrections tray to
        // state the literal input folder ("so there is nothing to look up") and S01's footer
        // states where the report was saved. A local report showing the user their own paths is
        // not a leak; a diagnostic string carrying one into a bug report is.
        String fromPanel = html.substring(html.indexOf("panel-danger"));
        String skippedPanel = fromPanel.substring(0, fromPanel.indexOf("</section>"));
        assertFalse(skippedPanel.contains(inputDir.toAbsolutePath().toString()),
                "the parse failure's own message leaked a path into the skipped-files panel");
    }

    @Test
    void aTruncatedRowCostsOnlyThatRowNotTheWholeFile(@TempDir Path workspace) throws IOException {
        Path inputDir = workspace.resolve("input");
        Files.createDirectories(inputDir);
        // One well-formed row and one that simply stops early. The taxonomy degrades to the
        // smallest scope that contains the problem (03 §6.3), so the short row is a ROW error
        // and its file still contributes everything else it has.
        Files.writeString(inputDir.resolve("short_row.csv"),
                "Date,Narration,Withdrawal Amt.,Deposit Amt.,Closing Balance,Chq./Ref.No.\n"
                        + "01/08/26,GOOD ROW,250.00,,900.00,REF1\n"
                        + "02/08/26,SHORT ROW\n");

        Cli.Options options = new Cli.Options(workspace.resolve("config"), inputDir, workspace.resolve("output"),
                false);
        Cli.RunResult result = Cli.run(options, new PrintStream(new ByteArrayOutputStream(), true,
                StandardCharsets.UTF_8));

        assertEquals(0, result.exitCode());
        String html = Files.readString(result.reportPath());
        assertFalse(html.contains("file is corrupt or truncated"), "the file itself is readable");
        assertTrue(html.contains("250.00"), "the good row must still be counted");

        String log = Files.readString(result.reportPath().getParent().resolve("run-log.txt"));
        assertTrue(log.contains("ROW-303"), log);
    }

    @Test
    void runLogContainsNoAbsolutePathAndNoUnmaskedLongDigitRun(@TempDir Path workspace) throws IOException {
        Path inputDir = workspace.resolve("input");
        Files.createDirectories(inputDir);
        Files.copy(FIXTURE, inputDir.resolve("hdfc_aug2026.csv"), StandardCopyOption.REPLACE_EXISTING);

        Cli.Options options = new Cli.Options(workspace.resolve("config"), inputDir, workspace.resolve("output"),
                false);
        Cli.RunResult result = Cli.run(options, new PrintStream(new ByteArrayOutputStream(), true,
                StandardCharsets.UTF_8));
        assertEquals(0, result.exitCode());

        Path logPath = result.reportPath().getParent().resolve("run-log.txt");
        assertTrue(Files.exists(logPath), "run-log.txt should exist");
        List<String> lines = Files.readAllLines(logPath, StandardCharsets.UTF_8);
        String workspaceAbsolute = workspace.toAbsolutePath().toString();

        for (String line : lines) {
            assertFalse(line.contains(workspaceAbsolute), "log line leaks an absolute path: " + line);
            assertFalse(line.matches(".*\\d{8,}.*"), "log line leaks an unmasked long digit run: " + line);
        }
    }

    /** Seeds a fresh input/ with exactly the three bad files (no valid statement). */
    private static Path seedBadFiles(Path workspace) throws IOException {
        Path inputDir = workspace.resolve("input");
        Files.createDirectories(inputDir);

        byte[] corruptPdf = ("%PDF-1.4\n" + "garbage garbage garbage not a real pdf table").getBytes(StandardCharsets.UTF_8);
        Files.write(inputDir.resolve("corrupt.pdf"), corruptPdf);

        Files.write(inputDir.resolve("empty_statement.csv"), new byte[0]);

        byte[] docxZipHeader = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00, 0x00, 0x00};
        Files.write(inputDir.resolve("resume.docx"), docxZipHeader);

        return inputDir;
    }
}
