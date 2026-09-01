package org.example.cli;

import org.example.analysis.Analysis;
import org.example.analysis.CategorySpend;
import org.example.analysis.MonthlySummary;
import org.example.categorize.Categorizer;
import org.example.categorize.Correction;
import org.example.categorize.CorrectionsStore;
import org.example.config.BankProfile;
import org.example.config.Config;
import org.example.config.ConfigException;
import org.example.config.ConfigLoader;
import org.example.config.Workspace;
import org.example.diagnostics.ErrorCode;
import org.example.diagnostics.RunLogger;
import org.example.diagnostics.RunReport;
import org.example.ingest.CorrectionHarvester;
import org.example.ingest.Discovery;
import org.example.ingest.FileKind;
import org.example.ingest.StatementFile;
import org.example.normalize.RowParser;
import org.example.normalize.Transaction;
import org.example.normalize.Deduplicator;
import org.example.parse.ColumnMapper;
import org.example.parse.ParseOutcome;
import org.example.parse.Parsers;
import org.example.parse.PasswordPrompt;
import org.example.parse.RawRow;
import org.example.parse.StatementParser;
import org.example.parse.TabularParser;
import org.example.report.ReportRenderer;
import org.example.report.TransactionsCsvWriter;

import java.io.Console;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 01-components.md C1. {@link #run} is the testable core (mirrors the contract's
 * {@code run()} vs {@code main()} split); it always returns an exit code rather than calling
 * {@code System.exit} itself.
 *
 * <p>Slice 2 (NFR3, FR5, AC7): every file- and row-level failure now degrades through
 * {@link RunReport} instead of vanishing or aborting the run - "a transaction that vanishes
 * without an entry in RunReport is a bug, not a design choice" (01-components.md, Cross-
 * component invariant 3). {@link RunLogger} writes the parallel human-readable
 * {@code run-log.txt} once the reporting month (and so the output directory) is known - plain
 * progress lines are buffered here until then, then flushed together with RunReport's
 * excluded-file/row-error/warning records. Exit codes follow
 * 03-interfaces-and-contracts.md §5: 0 = completed (including a degraded run), 2 = config
 * error, 3 = workspace error, 4 = internal error.</p>
 */
public final class Cli {

    private Cli() {
    }

    /** FR15: "Exactly min(5, n) largest non-transfer debits." */
    private static final int TOP_TRANSACTIONS = 5;

    public record Options(Path configDir, Path inputDir, Path outputDir, boolean openBrowser) {
    }

    public record RunResult(int exitCode, Path reportPath) {
    }

    public static RunResult run(Options options, PrintStream out) {
        return run(options, out, ColumnMapper.consolePrompt(), consolePassword());
    }

    /**
     * The seams the two interactive flows hang from - spec §11.2's one-time column mapper and
     * §11.1's single password prompt. Both are injected rather than reached for directly so a
     * test can drive them; with no console attached both go quiet and the run degrades to
     * {@code PARSE-204} / {@code PARSE-201} instead of blocking on input nobody will type.
     */
    public static RunResult run(Options options, PrintStream out, ColumnMapper.Prompt prompt,
                                PasswordPrompt passwordPrompt) {
        RunReport runReport = new RunReport();
        List<String[]> progress = new ArrayList<>(); // buffered (stage, message) INFO lines

        try {
            Workspace workspace = resolveWorkspace(options);

            // WS-101: a missing input folder is fatal - there's nowhere to look for
            // statements, and silently treating it as "zero files" would hide a broken
            // workspace setup from the user. No report/log can exist yet.
            if (Files.notExists(workspace.inputDir())) {
                out.println("Workspace error [" + ErrorCode.WS_101.code() + "]: " + ErrorCode.WS_101.reason()
                        + ": " + workspace.inputDir());
                return new RunResult(3, null);
            }

            ensureBootstrapped(workspace.configDir(), out);

            Config config;
            try {
                // C2 reports non-fatal config problems (unknown keys per F8, a file older than
                // this build, a profile shadowing a built-in) rather than failing: none of them
                // should stop a run, but all of them explain a surprising result later.
                config = ConfigLoader.loadConfig(workspace.configDir(),
                        d -> runReport.warn(d.stage(), d.code() == null
                                ? d.message() : "[" + d.code() + "] " + d.message()));
            } catch (ConfigException e) {
                out.println("Configuration error: " + e.getMessage());
                return new RunResult(2, null);
            }

            // WS-102: output not writable is the other fatal workspace class - there is
            // nowhere to put the answer.
            try {
                Files.createDirectories(workspace.outputDir());
            } catch (IOException e) {
                out.println("Workspace error [" + ErrorCode.WS_102.code() + "]: " + ErrorCode.WS_102.reason()
                        + ": " + workspace.outputDir());
                return new RunResult(3, null);
            }

            // ADR-0006: harvest BEFORE discovery and parsing, "so corrections apply to the run
            // that harvests them, not the one after". A loop whose result only shows up next
            // month is one nobody would trust enough to use.
            CorrectionsStore corrections = harvestCorrections(workspace, config, runReport, progress);

            List<StatementFile> files = Discovery.discover(workspace.inputDir());
            progress.add(new String[]{"ingest", files.size() + " candidate files"});

            List<Transaction> transactions = new ArrayList<>();
            for (StatementFile file : files) {
                String fileName = file.path().getFileName().toString();
                if (file.sizeBytes() < 0) {
                    runReport.excludeFile(fileName, ErrorCode.PARSE_209);
                    continue;
                }
                if (file.sizeBytes() == 0) {
                    runReport.excludeFile(fileName, ErrorCode.PARSE_205);
                    continue;
                }
                StatementParser parser = Parsers.get(file);
                if (parser == null) {
                    runReport.excludeFile(fileName, ErrorCode.PARSE_206, "detected as " + file.kind());
                    continue;
                }
                int before = transactions.size();
                transactions.addAll(parseFile(parser, file, config, runReport, prompt, passwordPrompt,
                        workspace.configDir()));
                progress.add(new String[]{"parse", fileName + ": " + (transactions.size() - before) + " rows"});
            }

            progress.add(new String[]{"config", "loaded " + config.categories().inOrder().size()
                    + " categories, " + config.bankProfiles().size() + " bank profiles"});

            // FR6/EC1: merge first, then de-duplicate across the merged set - the same
            // transaction appearing in two files is exactly what this removes.
            Deduplicator.Result deduped = Deduplicator.deduplicate(transactions, config.dedupe(), runReport);
            transactions = deduped.kept();
            if (deduped.removed() > 0) {
                progress.add(new String[]{"normalize", deduped.removed() + " duplicate rows removed"});
            }

            transactions = Categorizer.categorizeAll(transactions, config.categories(), corrections, runReport);
            long corrected = transactions.stream()
                    .filter(t -> t.categorySource() == org.example.normalize.CategorySource.USER_OVERRIDE)
                    .count();
            if (corrected > 0) {
                progress.add(new String[]{"categorize", corrected + " transactions categorised by your own rules"});
            }

            String month = inferMonth(transactions);
            if (month == null) {
                // EC5: zero transactions - still produce a real report, dated to today
                // rather than an "unknown" folder, so the run is still discoverable.
                month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            }
            List<Transaction> monthTxns = filterToMonth(transactions, month);
            if (!transactions.isEmpty() && monthTxns.size() < transactions.size()) {
                runReport.warn(ErrorCode.WARN_505.stage(), ErrorCode.WARN_505,
                        (transactions.size() - monthTxns.size()) + " rows outside " + month);
                // Nothing may vanish without a RunReport entry (C1 cross-component invariant 3);
                // a card statement's billing cycle routinely straddles two calendar months, so
                // these rows are dropped often and must each be accounted for.
                for (Transaction txn : transactions) {
                    if (!monthTxns.contains(txn)) {
                        runReport.addRowError(txn.sourceFile(), txn.rowIndex(), ErrorCode.ROW_306,
                                txn.date() + " is outside " + month);
                    }
                }
            }

            Analysis.Totals totals = Analysis.computeTotals(monthTxns);
            List<CategorySpend> byCategory = Analysis.spendByCategory(monthTxns, config.categories(),
                    totals.totalSpend());

            CategorySpend uncategorized = byCategory.stream()
                    .filter(c -> "Uncategorized".equals(c.name()))
                    .findFirst()
                    .orElse(new CategorySpend("Uncategorized", zero(), 0.0, 0));

            progress.add(new String[]{"categorize", monthTxns.size() + " transactions, "
                    + uncategorized.txnCount() + " uncategorized"});

            MonthlySummary summary = new MonthlySummary(
                    1,
                    month,
                    config.currencyDefault(),
                    LocalDateTime.now(),
                    totals.totalSpend(),
                    totals.totalIncome(),
                    totals.net(),
                    Analysis.topCategory(byCategory, totals.totalSpend()),
                    byCategory,
                    Analysis.topTransactions(monthTxns, TOP_TRANSACTIONS),
                    uncategorized.txnCount(),
                    uncategorized.amount(),
                    Analysis.needsReviewCount(monthTxns),
                    Analysis.transfersTotal(monthTxns),
                    monthTxns.size(),
                    Analysis.accounts(monthTxns),
                    // FR14 is Slice 6; until a HistoryStore exists there is no prior month to
                    // name, which S01 renders as its documented "no prior month" state.
                    null
            );

            progress.add(new String[]{"analysis", "total_spend=" + summary.totalSpend() + " "
                    + summary.currency() + ", month=" + summary.month()});

            Path reportPath = ReportRenderer.renderReport(summary, monthTxns, runReport,
                    config.topNSlices(), config.minSlicePercent(), workspace.outputDir(),
                    config.categories(), workspace.inputDir());
            Path csvPath = TransactionsCsvWriter.write(monthTxns, reportPath.getParent());
            progress.add(new String[]{"report", "wrote " + csvPath.getFileName() + " ("
                    + monthTxns.size() + " rows)"});

            boolean openedInBrowser = options.openBrowser() && ReportRenderer.openInBrowser(reportPath);
            if (!openedInBrowser && options.openBrowser()) {
                runReport.warn(ErrorCode.WARN_506.stage(), ErrorCode.WARN_506, "");
            }
            progress.add(new String[]{"run", "completed"});

            writeRunLog(reportPath.getParent(), progress, runReport);

            out.println(reportPath.toAbsolutePath().toString());

            return new RunResult(0, reportPath);
        } catch (Exception e) {
            // INT-901: an unhandled exception escaped the pipeline. A bug, not a data
            // problem - reported plainly rather than left to print a raw stack trace.
            runReport.markFatal("run");
            out.println("Internal error [" + ErrorCode.INT_901.code() + "]: " + e);
            return new RunResult(4, null);
        }
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static Workspace resolveWorkspace(Options options) {
        Path explicitBase = options.configDir() != null ? options.configDir().getParent() : null;
        Workspace resolved = ConfigLoader.resolveWorkspace(explicitBase);
        Path configDir = options.configDir() != null ? options.configDir() : resolved.configDir();
        Path inputDir = options.inputDir() != null ? options.inputDir() : resolved.inputDir();
        Path outputDir = options.outputDir() != null ? options.outputDir() : resolved.outputDir();
        return new Workspace(configDir, inputDir, outputDir, resolved.archiveDir());
    }

    private static void ensureBootstrapped(Path configDir, PrintStream out) throws IOException {
        boolean anyMissing = false;
        for (String name : new String[]{"config.yaml", "categories.yaml", "bank_profiles.yaml", "accounts.yaml"}) {
            if (Files.notExists(configDir.resolve(name))) {
                anyMissing = true;
                break;
            }
        }
        if (anyMissing) {
            List<Path> written = ConfigLoader.writeDefaultConfig(configDir, false);
            for (Path p : written) {
                out.println("wrote default config: " + p.getFileName());
            }
        }
    }

    /**
     * Runs one file through its parser and turns the resulting rows into transactions. Every
     * failure lands in {@link RunReport} and the run continues (FR5/NFR3/AC7); a tabular file
     * that matches no profile gets one chance at the interactive column mapper (§11.2) before
     * it is excluded.
     */
    private static List<Transaction> parseFile(StatementParser parser, StatementFile file, Config config,
                                               RunReport runReport, ColumnMapper.Prompt prompt,
                                               PasswordPrompt passwordPrompt, Path configDir) {
        String fileName = file.path().getFileName().toString();
        ParseOutcome outcome = parser.parse(file, config, passwordPrompt);

        if (outcome.isFailed() && ErrorCode.PARSE_204.code().equals(outcome.failed().code())
                && file.kind() != FileKind.PDF) {
            outcome = mapInteractively(file, fileName, prompt, configDir, outcome);
        }

        if (outcome.isFailed()) {
            runReport.excludeFile(fileName, outcome.failed());
            return List.of();
        }
        for (var warning : outcome.warnings()) {
            runReport.warn(warning.stage(), warning.message());
        }

        BankProfile profile = profileNamed(config, outcome.profileUsed());
        List<Transaction> result = new ArrayList<>();
        for (RawRow row : outcome.rows()) {
            try {
                result.add(RowParser.parseRow(row, profile, config.currencyDefault()));
            } catch (RowParser.RowParseException e) {
                runReport.addRowError(fileName, row.rowIndex(), e.code(), e.getMessage());
            }
        }
        return result;
    }

    /** spec §11.2: map the columns once, save the profile, then read the file with it. */
    private static ParseOutcome mapInteractively(StatementFile file, String fileName, ColumnMapper.Prompt prompt,
                                                 Path configDir, ParseOutcome original) {
        try {
            TabularParser.SniffedLayout layout = TabularParser.sniffLayout(file.path(), file.kind());
            BankProfile mapped = ColumnMapper.promptAndSaveProfile(layout.headers(), fileName,
                    configDir.resolve("bank_profiles.yaml"), prompt);
            if (mapped == null) {
                return original;
            }
            return ParseOutcome.ok(TabularParser.mapColumns(layout, mapped, fileName), mapped.name(), List.of());
        } catch (IOException | RuntimeException e) {
            return original;
        }
    }

    /**
     * The profile a parser reported using. A parser only ever reports one it resolved from the
     * loaded config, except the profile the column mapper has just written - which is on disk
     * but not in this run's already-loaded {@link Config}, so it is re-read here.
     */
    private static BankProfile profileNamed(Config config, String name) {
        for (BankProfile profile : config.bankProfiles()) {
            if (profile.name().equals(name)) {
                return profile;
            }
        }
        for (BankProfile profile : ConfigLoader.loadBankProfiles(
                config.workspace().configDir().resolve("bank_profiles.yaml"))) {
            if (profile.name().equals(name)) {
                return profile;
            }
        }
        throw new IllegalStateException("parser reported an unknown profile: " + name);
    }

    /**
     * §11.1's single password prompt. {@link Console#readPassword} keeps the value off the
     * screen and out of the shell history, and it is handed straight to PDFBox and never
     * stored - §13 forbids writing it anywhere.
     */
    private static PasswordPrompt consolePassword() {
        Console console = System.console();
        return fileName -> {
            if (console == null) {
                return null;
            }
            char[] entered = console.readPassword("%s is password-protected. Password (not saved): ", fileName);
            return entered == null ? null : new String(entered);
        };
    }

    /**
     * FR20 → FR9 → AC4: applies any correction patches the user dropped in {@code input/}, then
     * hands back the store the ladder's L1/L2 layers read from.
     *
     * <p>Failures here are warnings, never fatal. A corrections file that cannot be read is a
     * bad day for the learning loop, but it is not a reason to refuse to tell the user what they
     * spent this month (NFR3).</p>
     */
    private static CorrectionsStore harvestCorrections(Workspace workspace, Config config,
                                                       RunReport runReport, List<String[]> progress) {
        Path correctionsFile = workspace.configDir().resolve(CorrectionsStore.FILE_NAME);
        try {
            CorrectionHarvester.HarvestResult harvest = CorrectionHarvester.harvest(
                    workspace.inputDir(), correctionsFile, workspace.archiveDir(), config.categories());
            if (harvest.patchesApplied() > 0) {
                progress.add(new String[]{"ingest", harvest.correctionsApplied() + " corrections applied from "
                        + harvest.patchesApplied() + " patch" + (harvest.patchesApplied() == 1 ? "" : "es")});
            }
            for (CorrectionHarvester.Rejection rejection : harvest.rejected()) {
                // WARN-504: the whole patch was refused and left in place for the user to fix.
                runReport.warn(ErrorCode.WARN_504.stage(), ErrorCode.WARN_504,
                        rejection.file() + " - " + rejection.reason());
            }

            CorrectionsStore store = CorrectionsStore.load(correctionsFile);
            if (!store.isEmpty()) {
                progress.add(new String[]{"config", store.corrections().size() + " saved corrections loaded"});
            }
            for (Correction stale : store.staleAgainst(config.categories())) {
                runReport.warn(ErrorCode.WARN_501.stage(), ErrorCode.WARN_501,
                        "'" + stale.merchantKey() + "' -> '" + stale.category() + "'");
            }
            return store;
        } catch (IOException | RuntimeException e) {
            runReport.warn("ingest", "saved corrections could not be read ("
                    + e.getClass().getSimpleName() + "); this run used the category rules only");
            return CorrectionsStore.empty();
        }
    }

    /** C1: group by YYYY-MM, select the month with the most transactions. */
    private static String inferMonth(List<Transaction> transactions) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        for (Transaction txn : transactions) {
            counts.merge(txn.date().format(fmt), 1, Integer::sum);
        }
        String best = null;
        int bestCount = -1;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > bestCount) {
                bestCount = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    private static List<Transaction> filterToMonth(List<Transaction> transactions, String month) {
        if (month == null) {
            return transactions;
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        List<Transaction> result = new ArrayList<>();
        for (Transaction txn : transactions) {
            if (txn.date().format(fmt).equals(month)) {
                result.add(txn);
            }
        }
        return result;
    }

    /** C9: output/&lt;month&gt;/run-log.txt, mirroring 02-data-model.md §3.10's shape. */
    private static void writeRunLog(Path monthDir, List<String[]> progress, RunReport runReport) {
        RunLogger logger = new RunLogger(monthDir);
        for (String[] line : progress) {
            logger.info(line[0], line[1]);
        }
        for (var ex : runReport.excludedFiles()) {
            String detail = ex.detail() == null || ex.detail().isBlank() ? "" : " (" + ex.detail() + ")";
            logger.error("parse", ex.file() + " excluded [" + ex.code() + "]: " + ex.reason() + detail);
        }
        for (var re : runReport.rowErrors()) {
            logger.warn("normalize", re.sourceFile() + " row " + re.rowIndex() + " [" + re.code() + "]: "
                    + re.message());
        }
        for (var w : runReport.warnings()) {
            // The code is what a user searches the docs for (taxonomy rule 4), so it is
            // printed for warnings exactly as it already is for row errors and exclusions.
            logger.warn(w.stage(), w.code() == null ? w.message() : "[" + w.code() + "] " + w.message());
        }
    }
}
