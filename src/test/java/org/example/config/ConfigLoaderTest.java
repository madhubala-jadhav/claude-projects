package org.example.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    @Test
    void writeDefaultConfigWritesAllFourFiles(@TempDir Path tmp) throws IOException {
        Path configDir = tmp.resolve("config");
        List<Path> written = ConfigLoader.writeDefaultConfig(configDir, false);

        assertEquals(4, written.size());
        assertTrue(Files.exists(configDir.resolve("config.yaml")));
        assertTrue(Files.exists(configDir.resolve("categories.yaml")));
        assertTrue(Files.exists(configDir.resolve("bank_profiles.yaml")));
        assertTrue(Files.exists(configDir.resolve("accounts.yaml")));
    }

    @Test
    void writeDefaultConfigDoesNotOverwriteExistingFilesByDefault(@TempDir Path tmp) throws IOException {
        Path configDir = tmp.resolve("config");
        ConfigLoader.writeDefaultConfig(configDir, false);
        Files.writeString(configDir.resolve("config.yaml"), "schema_version: 1\ncurrency:\n  default: USD\n  locale: en-US\n");

        List<Path> secondWrite = ConfigLoader.writeDefaultConfig(configDir, false);

        assertTrue(secondWrite.isEmpty());
        assertTrue(Files.readString(configDir.resolve("config.yaml")).contains("USD"));
    }

    @Test
    void loadConfigParsesTwelveCategoriesInDeclaredOrderAndTheEnabledBankProfiles(@TempDir Path tmp)
            throws IOException {
        Path configDir = tmp.resolve("config");
        ConfigLoader.writeDefaultConfig(configDir, false);

        Config config = ConfigLoader.loadConfig(configDir);

        assertEquals("INR", config.currencyDefault());
        assertEquals("en-IN", config.locale());
        assertEquals(12, config.categories().inOrder().size());
        assertEquals("Rent/Housing", config.categories().inOrder().get(0).name());
        assertEquals("Uncategorized", config.categories().inOrder().get(11).name());
        // Three profiles ship enabled; the unvalidated per-bank seeds stay commented out
        // until a real export of that bank confirms them (see bank_profiles.yaml).
        assertEquals(List.of("hdfc_credit_card_pdf", "hdfc_savings_csv", "generic_pdf_indian_savings"),
                config.bankProfiles().stream().map(BankProfile::name).toList());
    }
}
