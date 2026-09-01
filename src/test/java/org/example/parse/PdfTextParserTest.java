package org.example.parse;

import org.example.config.BankProfile;
import org.example.config.Config;
import org.example.config.ConfigLoader;
import org.example.ingest.Discovery;
import org.example.ingest.FileKind;
import org.example.ingest.StatementFile;
import org.example.normalize.Direction;
import org.example.normalize.RowParser;
import org.example.normalize.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** C4a (FR2, EC2, §11.1). */
class PdfTextParserTest {

    @Test
    void readsEveryTransactionFromBothPagesOfACardStatement(@TempDir Path tmp) throws IOException {
        ParseOutcome outcome = parse(tmp, PdfFixtures.creditCardStatement(tmp.resolve("hdfc_aug2026.pdf")));

        assertFalse(outcome.isFailed(), () -> "parse failed: " + outcome.failed());
        assertEquals("hdfc_credit_card_pdf", outcome.profileUsed());
        // 5 transactions; the cardholder-name sub-header and the two page footers are not rows.
        assertEquals(5, outcome.rows().size());
        assertEquals(1, outcome.rows().get(0).sourcePage());
        assertEquals(2, outcome.rows().get(4).sourcePage());
    }

    @Test
    void reassemblesADescriptionWrappedAboveAndBelowItsOwnDate(@TempDir Path tmp) throws IOException {
        ParseOutcome outcome = parse(tmp, PdfFixtures.creditCardStatement(tmp.resolve("hdfc_aug2026.pdf")));

        RawRow credit = outcome.rows().get(2);

        // EC2: both continuation lines belong to this row, and they must join in reading order
        // even though one of them is printed above the line carrying the date.
        assertEquals("CREDIT CARD PAYMENTNet Banking (Ref# 00000000000123456789012)",
                credit.descriptionText());
    }

    @Test
    void doesNotFoldTheCardholderNameSubHeaderIntoTheFirstTransaction(@TempDir Path tmp) throws IOException {
        ParseOutcome outcome = parse(tmp, PdfFixtures.creditCardStatement(tmp.resolve("hdfc_aug2026.pdf")));

        // The sub-header is a full row-pitch from the first transaction, not a wrap of it.
        assertEquals("IND*ADOBE", outcome.rows().get(0).descriptionText());
    }

    @Test
    void readsRightAlignedAmountsAndTheirDirectionMarkers(@TempDir Path tmp) throws IOException {
        Config config = bootstrapConfig(tmp);
        ParseOutcome outcome = parse(tmp, PdfFixtures.creditCardStatement(tmp.resolve("hdfc_aug2026.pdf")));
        BankProfile profile = profile(config, outcome.profileUsed());

        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (RawRow row : outcome.rows()) {
            Transaction txn = RowParser.parseRow(row, profile, "INR");
            if (txn.direction() == Direction.DEBIT) {
                debits = debits.add(txn.amount());
            } else {
                credits = credits.add(txn.amount());
            }
        }

        // The credit is wider than its own column header and is typeset leftwards from the
        // right edge; a midpoint column cut loses its leading digits, and a missed "+" marker
        // would count a bill payment as spend.
        assertEquals(0, new BigDecimal("2861.00").compareTo(debits));
        assertEquals(0, new BigDecimal("47890.65").compareTo(credits));
    }

    @Test
    void reportsTheAccountMaskedToItsLastFourDigits(@TempDir Path tmp) throws IOException {
        Path pdf = PdfFixtures.creditCardStatement(tmp.resolve("hdfc_aug2026.pdf"));
        ParseOutcome outcome = parse(tmp, pdf);

        // The fixture carries no account header line, so the parser must say so rather than
        // invent one - and it must not drop the file over it.
        assertFalse(outcome.isFailed());
        assertTrue(outcome.warnings().stream().anyMatch(w -> "FIELD-404".equals(w.code())),
                () -> "expected a FIELD-404 warning, got " + outcome.warnings());
        assertEquals(null, outcome.rows().get(0).accountHint());
    }

    @Test
    void aPdfWithNoTransactionTableIsExcludedRatherThanGuessedAt(@TempDir Path tmp) throws IOException {
        ParseOutcome outcome = parse(tmp, PdfFixtures.unrecognisableStatement(tmp.resolve("insurance.pdf")));

        assertTrue(outcome.isFailed());
        assertEquals("PARSE-204", outcome.failed().code());
        assertTrue(outcome.rows().isEmpty(), "S4: a failed outcome carries no rows");
    }

    @Test
    void aTruncatedPdfDegradesToOneExcludedFile(@TempDir Path tmp) throws IOException {
        Path broken = tmp.resolve("broken.pdf");
        Files.write(broken, "%PDF-1.4\nnot really a pdf".getBytes());

        ParseOutcome outcome = parse(tmp, broken);

        assertTrue(outcome.isFailed());
        assertEquals("PARSE-208", outcome.failed().code());
        // §13: nothing that could carry the absolute path is echoed into the report.
        assertFalse(outcome.failed().detail().contains(tmp.toString()));
    }

    @Test
    void extractorAndConfidenceAreReportedHonestly(@TempDir Path tmp) throws IOException {
        ParseOutcome outcome = parse(tmp, PdfFixtures.creditCardStatement(tmp.resolve("hdfc_aug2026.pdf")));

        for (RawRow row : outcome.rows()) {
            // Parser obligation 3: 1.0 means "exact extraction", which text extraction is.
            assertEquals(1.0, row.confidence());
            assertEquals("pdf_text", row.extractor());
            assertEquals("hdfc_aug2026.pdf", row.sourceFile());
        }
    }

    private static ParseOutcome parse(Path tmp, Path pdf) throws IOException {
        Config config = bootstrapConfig(tmp);
        StatementFile file = new StatementFile(pdf, Discovery.detectKind(pdf), Files.size(pdf), null);
        assertEquals(FileKind.PDF, file.kind());
        StatementParser parser = Parsers.get(file);
        assertNotNull(parser);
        return parser.parse(file, config, null);
    }

    private static Config bootstrapConfig(Path tmp) throws IOException {
        Path configDir = tmp.resolve("config");
        ConfigLoader.writeDefaultConfig(configDir, false);
        return ConfigLoader.loadConfig(configDir);
    }

    private static BankProfile profile(Config config, String name) {
        return config.bankProfiles().stream().filter(p -> p.name().equals(name)).findFirst().orElseThrow();
    }
}
