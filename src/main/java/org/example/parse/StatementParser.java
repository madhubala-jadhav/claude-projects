package org.example.parse;

import org.example.config.Config;
import org.example.ingest.StatementFile;

/**
 * The extension point from 03-interfaces-and-contracts.md §3: "Adding support for a new
 * statement medium means adding one class; nothing else changes."
 *
 * <p>Obligations on any implementation, verbatim from that section: never raise for data
 * problems (return a failed {@link ParseOutcome}); never perform network I/O (F10); set
 * {@code confidence} honestly; emit basenames in {@code source_file}, never absolute paths
 * (§13); be deterministic.</p>
 */
public interface StatementParser {

    String name();

    boolean supports(StatementFile file);

    ParseOutcome parse(StatementFile file, Config config, PasswordPrompt askPassword);
}
