package org.example.parse;

import org.example.config.BankProfile;
import org.example.config.Config;
import org.example.config.ConfigLoader;
import org.example.ingest.Discovery;
import org.example.ingest.FileKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TabularParserTest {

    private static final Path FIXTURE = Path.of("src/test/resources/fixtures/hdfc_savings_sample.csv");

    @Test
    void resolvesTheHdfcProfileFromTheFixtureHeaders(@TempDir Path tmp) throws IOException {
        Config config = bootstrapConfig(tmp);
        TabularParser.SniffedLayout layout = TabularParser.sniffLayout(FIXTURE);

        BankProfile profile = TabularParser.resolveProfile(layout.headers(), config.bankProfiles(), "hdfc");

        assertNotNull(profile);
        assertEquals("hdfc_savings_csv", profile.name());
    }

    @Test
    void mapsAllFifteenFixtureRows(@TempDir Path tmp) throws IOException {
        Config config = bootstrapConfig(tmp);
        TabularParser.SniffedLayout layout = TabularParser.sniffLayout(FIXTURE);
        BankProfile profile = TabularParser.resolveProfile(layout.headers(), config.bankProfiles(), "hdfc");

        List<RawRow> rows = TabularParser.mapColumns(layout, profile, "hdfc_aug2026.csv");

        assertEquals(15, rows.size());
        RawRow first = rows.get(0);
        assertEquals("01/08/26", first.dateText());
        assertEquals("NEFT DR-RENT TRANSFER-AUG", first.descriptionText());
        assertEquals("15000.00", first.debitText());
    }

    @Test
    void detectKindRejectsAPdfMisnamedAsCsv(@TempDir Path tmp) throws IOException {
        Path fakeCsv = tmp.resolve("statement.csv");
        Files.write(fakeCsv, "%PDF-1.4\n...".getBytes());

        FileKind kind = Discovery.detectKind(fakeCsv);

        assertEquals(FileKind.PDF, kind);
    }

    @Test
    void resolveProfileReturnsNullBelowTheEightyPercentThreshold(@TempDir Path tmp) throws IOException {
        Config config = bootstrapConfig(tmp);
        BankProfile match = TabularParser.resolveProfile(List.of("Totally", "Unrelated", "Headers"),
                config.bankProfiles(), null);
        assertNull(match);
    }

    private static Config bootstrapConfig(Path tmp) throws IOException {
        Path configDir = tmp.resolve("config");
        ConfigLoader.writeDefaultConfig(configDir, false);
        return ConfigLoader.loadConfig(configDir);
    }
}
