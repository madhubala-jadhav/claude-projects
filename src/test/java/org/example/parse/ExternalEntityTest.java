package org.example.parse;

import org.example.config.Config;
import org.example.config.ConfigLoader;
import org.example.ingest.Discovery;
import org.example.ingest.FileKind;
import org.example.ingest.StatementFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC8 / NFR1 / F10, extended to cover Slice 3's new dependencies.
 *
 * <p>{@link org.example.PrivacyStaticCheckTest} scans this project's own source for networking
 * APIs, which is the right check as long as the code does its own I/O. Slice 3 adds two XML
 * parsers (Apache POI for {@code .xlsx}, ADR-0015) whose <em>input</em> can ask them to fetch
 * something: an XML external entity in a workbook is a URL the parser would resolve, and a
 * statement file is by definition untrusted input. A source scan cannot see that, so it is
 * checked here by feeding the parser a workbook that tries.</p>
 *
 * <p>The entity here points at a local file rather than a URL - the assertion is that the
 * declared entity is never resolved at all, and a local target keeps the test itself offline.</p>
 */
class ExternalEntityTest {

    @Test
    void aWorkbookDeclaringAnExternalEntityIsRejectedAndNeverResolvesIt(@TempDir Path tmp) throws IOException {
        Path secret = tmp.resolve("secret.txt");
        Files.writeString(secret, "TOP_SECRET_ENTITY_MARKER", StandardCharsets.UTF_8);

        Path workbook = tmp.resolve("statement.xlsx");
        String contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE Types [ <!ENTITY xxe SYSTEM \"" + secret.toUri() + "\"> ]>\n"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"xml\" ContentType=\"&xxe;\"/></Types>";
        try (OutputStream out = Files.newOutputStream(workbook);
             ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write(contentTypes.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        StatementFile file = new StatementFile(workbook, Discovery.detectKind(workbook),
                Files.size(workbook), null);
        assertEquals(FileKind.XLSX, file.kind());
        Config config = config(tmp);

        ParseOutcome outcome = Parsers.get(file).parse(file, config, null);

        // NFR3: a hostile file is one excluded file, not a crashed run.
        assertTrue(outcome.isFailed());
        assertEquals("PARSE-208", outcome.failed().code());
        // AC8: the entity's contents must not have been read, let alone surfaced.
        assertFalse(outcome.toString().contains("TOP_SECRET_ENTITY_MARKER"));
    }

    private static Config config(Path tmp) throws IOException {
        Path configDir = tmp.resolve("config");
        ConfigLoader.writeDefaultConfig(configDir, false);
        return ConfigLoader.loadConfig(configDir);
    }
}
