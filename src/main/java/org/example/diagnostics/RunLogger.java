package org.example.diagnostics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 01-components.md C9's "logging handler": writes {@code output/<month>/run-log.txt},
 * mirroring 02-data-model.md §3.10's line shape. "Log file not writable -&gt; logging
 * degrades to stderr, run continues. The diagnostics component itself must never be able to
 * fail the run" - so every write here is best-effort; once the log file itself proves
 * unwritable this instance permanently switches to stderr rather than throwing.
 *
 * <p>{@link Redaction#redact} is applied to every line here - not by callers - matching
 * {@code redact()}'s contract note that "the redaction filter is not removable by a flag".</p>
 */
public final class RunLogger {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path logPath;
    private boolean degraded = false;

    public RunLogger(Path monthDir) {
        this.logPath = monthDir.resolve("run-log.txt");
    }

    public void info(String stage, String message) {
        write("INFO", stage, message);
    }

    public void warn(String stage, String message) {
        write("WARN", stage, message);
    }

    public void error(String stage, String message) {
        write("ERROR", stage, message);
    }

    private void write(String level, String stage, String message) {
        String line = String.format("%s %-5s %-11s %s", LocalDateTime.now().format(TS), level, stage,
                Redaction.redact(message));
        if (degraded) {
            System.err.println(line);
            return;
        }
        try {
            Files.createDirectories(logPath.getParent());
            Files.writeString(logPath, line + System.lineSeparator(), StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            degraded = true;
            System.err.println(line);
        }
    }
}
