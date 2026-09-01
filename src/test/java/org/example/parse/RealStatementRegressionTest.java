package org.example.parse;

import org.example.config.BankProfile;
import org.example.config.Config;
import org.example.config.ConfigLoader;
import org.example.ingest.Discovery;
import org.example.ingest.StatementFile;
import org.example.normalize.Direction;
import org.example.normalize.RowParser;
import org.example.normalize.Transaction;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Slice 3 hard gate, checked against the real statement rather than a fixture.
 *
 * <p>The roadmap's exit criterion is totals "verified by hand against the statements". A credit
 * card statement conveniently prints its own control totals - PURCHASES/DEBIT and
 * PAYMENTS/CREDITS RECEIVED - so the hand-verification is encoded here as an assertion instead
 * of living only in a commit message.</p>
 *
 * <p>The statement is real financial data and so are the figures it prints, so <em>neither</em>
 * is in the repository - not the file, not its name, not the totals. They live together in the
 * gitignored {@code input/} folder (§13), the totals in {@link #EXPECTATIONS}. Everywhere that
 * file is absent - CI, a public checkout, another machine - this test skips, and
 * {@link PdfTextParserTest} covers the same layout with a generated fixture whose figures are
 * invented. Nothing here prints statement content; only two aggregates are compared.</p>
 *
 * <p>The properties file is three lines, and the totals are read off the statement by hand:</p>
 *
 * <pre>
 * file=&lt;the statement's filename, inside input/&gt;
 * purchases=&lt;the PURCHASES/DEBIT total it prints&gt;
 * payments=&lt;the PAYMENTS/CREDITS RECEIVED total it prints&gt;
 * </pre>
 */
class RealStatementRegressionTest {

    private static final Path INPUT = Path.of("input");
    private static final Path EXPECTATIONS = INPUT.resolve("regression-expected.properties");

    /** The statement and the two control totals it prints on itself; null when not on this machine. */
    private record Expected(Path statement, BigDecimal purchases, BigDecimal payments) {
    }

    private static Expected load() throws IOException {
        if (!Files.exists(EXPECTATIONS)) {
            return null;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(EXPECTATIONS)) {
            props.load(in);
        }
        String name = props.getProperty("file", "").trim();
        String purchases = props.getProperty("purchases", "").trim();
        String payments = props.getProperty("payments", "").trim();
        if (name.isEmpty() || purchases.isEmpty() || payments.isEmpty()) {
            return null;
        }
        Path statement = INPUT.resolve(name);
        if (!Files.exists(statement)) {
            return null;
        }
        return new Expected(statement, new BigDecimal(purchases), new BigDecimal(payments));
    }

    private static Expected required() throws IOException {
        Expected expected = load();
        Assumptions.assumeTrue(expected != null, "no real statement and " + EXPECTATIONS
                + " on this machine; this check only runs where both are present");
        return expected;
    }

    @Test
    void theRealCardStatementTotalsMatchTheFiguresItPrintsOnItself(@TempDir Path tmp) throws IOException {
        Expected expected = required();

        Path configDir = tmp.resolve("config");
        ConfigLoader.writeDefaultConfig(configDir, false);
        Config config = ConfigLoader.loadConfig(configDir);

        Path statement = expected.statement();
        StatementFile file = new StatementFile(statement, Discovery.detectKind(statement),
                Files.size(statement), null);
        ParseOutcome outcome = Parsers.get(file).parse(file, config, null);

        assertFalse(outcome.isFailed(), "the real statement should parse");
        assertEquals("hdfc_credit_card_pdf", outcome.profileUsed());
        assertEquals(7, outcome.rows().size());

        BankProfile profile = config.bankProfiles().stream()
                .filter(p -> p.name().equals(outcome.profileUsed())).findFirst().orElseThrow();
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (RawRow row : outcome.rows()) {
            Transaction txn = RowParser.parseRow(row, profile, config.currencyDefault());
            if (txn.direction() == Direction.DEBIT) {
                debits = debits.add(txn.amount());
            } else {
                credits = credits.add(txn.amount());
            }
        }

        assertEquals(0, expected.purchases().compareTo(debits), "spend disagrees with the statement's own total");
        assertEquals(0, expected.payments().compareTo(credits), "credits disagree with the statement's own total");
    }

    @Test
    void theAccountIsReportedOnlyAsItsLastFourDigits(@TempDir Path tmp) throws IOException {
        Expected expected = required();

        Path configDir = tmp.resolve("config");
        ConfigLoader.writeDefaultConfig(configDir, false);
        Config config = ConfigLoader.loadConfig(configDir);
        Path statement = expected.statement();
        StatementFile file = new StatementFile(statement, Discovery.detectKind(statement),
                Files.size(statement), null);

        ParseOutcome outcome = Parsers.get(file).parse(file, config, null);

        String account = outcome.rows().get(0).accountHint();
        // §13: the card number is masked at parse time, so no more than four digits of it can
        // reach the report, the CSV or the log.
        assertTrue(account != null && account.matches("[A-Z]+-XXXX\\d{4}"), "unexpected account form");
    }
}
