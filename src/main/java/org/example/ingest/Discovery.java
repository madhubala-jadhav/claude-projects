package org.example.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 01-components.md C3 {@code discover}/{@code detect_kind}. FR5 (Slice 2): unreadable and
 * zero-byte files are no longer silently dropped here - they're still listed (with
 * {@code sizeBytes() == 0} or {@code -1} respectively) so the caller (Cli) can route them
 * through {@code RunReport.excludeFile} with a proper reason (PARSE-205 / PARSE-209) instead
 * of them vanishing invisibly. Dotfiles and Office lock files (~$...) are still filtered
 * here without a report entry - they were never candidate statements in the first place.
 */
public final class Discovery {

    private Discovery() {
    }

    public static List<StatementFile> discover(Path inputDir) throws IOException {
        List<StatementFile> files = new ArrayList<>();
        if (Files.notExists(inputDir)) {
            return files;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(inputDir)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    continue;
                }
                String name = path.getFileName().toString();
                if (name.startsWith(".") || name.startsWith("~$")) {
                    continue;
                }
                long size;
                try {
                    size = Files.size(path);
                } catch (IOException e) {
                    // Unreadable/permission-denied: sizeBytes = -1 is the sentinel Cli
                    // maps to PARSE-209, rather than the file disappearing silently.
                    files.add(new StatementFile(path, FileKind.UNSUPPORTED, -1, filenameHint(name)));
                    continue;
                }
                FileKind kind = size == 0 ? FileKind.UNSUPPORTED : detectKind(path);
                files.add(new StatementFile(path, kind, size, filenameHint(name)));
            }
        }
        // DirectoryStream order is filesystem-dependent. Sorting by name makes a multi-file
        // run reproducible, which matters once deduplicate() starts keeping the first of two
        // identical rows: without a fixed order, "which file won" could change between runs
        // on the same input (the StatementParser protocol's determinism obligation, 03 §3).
        files.sort(java.util.Comparator.comparing(f -> f.path().getFileName().toString()));
        return files;
    }

    /**
     * Magic-bytes-first (FR1): "a .csv that is really a PDF must not reach the CSV parser."
     */
    public static FileKind detectKind(Path path) {
        byte[] header = new byte[8];
        int read;
        try (InputStream in = Files.newInputStream(path)) {
            read = in.read(header);
        } catch (IOException e) {
            return FileKind.UNSUPPORTED;
        }
        if (read >= 5 && header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F'
                && header[4] == '-') {
            return FileKind.PDF;
        }
        if (read >= 4 && (header[0] & 0xFF) == 0x50 && (header[1] & 0xFF) == 0x4B
                && (header[2] & 0xFF) == 0x03 && (header[3] & 0xFF) == 0x04) {
            // A zip-container signature alone isn't proof of a spreadsheet - .docx/.pptx/
            // .zip are zip containers too. Only classify as XLSX/XLS when the extension
            // actually says so; otherwise it's a real container, just not a statement format.
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".xlsx")) {
                return FileKind.XLSX;
            }
            if (name.endsWith(".xls")) {
                return FileKind.XLS;
            }
            return FileKind.UNSUPPORTED;
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".json")) {
            return FileKind.CORRECTION_PATCH;
        }
        if (name.endsWith(".csv") || name.endsWith(".txt")) {
            return FileKind.CSV;
        }
        return FileKind.UNSUPPORTED;
    }

    private static String filenameHint(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String bank : new String[]{"hdfc", "icici", "sbi", "axis", "kotak"}) {
            if (lower.contains(bank)) {
                return bank;
            }
        }
        return null;
    }
}
