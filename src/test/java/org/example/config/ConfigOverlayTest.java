package org.example.config;

import org.example.diagnostics.Diagnostic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bank profiles are built into the build and overlaid by the config file.
 *
 * <p>The reason is a real incident: Slice 3 shipped a profile that could read the owner's HDFC
 * PDF, and the run excluded that PDF anyway with {@code PARSE-204}, because
 * {@code ensureBootstrapped} writes a config file only when it is <em>missing</em> and the
 * workspace already had a Slice-1 one. The first test here is that scenario.</p>
 */
class ConfigOverlayTest {

    @Test
    void aWorkspaceWhoseConfigPredatesAProfileStillGetsThatProfile(@TempDir Path tmp) throws IOException {
        // Exactly the Slice-1 file: one CSV profile, none of the PDF ones added later.
        Path configDir = staleWorkspace(tmp, """
                schema_version: 1
                profiles:
                  - name: hdfc_savings_csv
                    applies_to: [csv]
                    match:
                      headers_any: ["Narration"]
                    date_order: DMY
                    date_formats: ["dd/MM/yy"]
                    amount_sign: separate_columns
                """);

        List<String> names = names(ConfigLoader.loadBankProfiles(configDir.resolve("bank_profiles.yaml")));

        assertTrue(names.contains("hdfc_credit_card_pdf"),
                () -> "a profile shipped after this config was written never reached it: " + names);
        assertTrue(names.contains("generic_pdf_indian_savings"), () -> names.toString());
    }

    @Test
    void aUserProfileOverridesTheBuiltInOfTheSameNameAndSaysSo(@TempDir Path tmp) throws IOException {
        Path configDir = staleWorkspace(tmp, """
                schema_version: 1
                profiles:
                  - name: hdfc_credit_card_pdf
                    applies_to: [pdf]
                    match:
                      headers_any: ["MY OWN HEADER"]
                    date_order: DMY
                    date_formats: ["dd/MM/yyyy"]
                    amount_sign: dr_cr_suffix
                """);
        List<Diagnostic> warnings = new ArrayList<>();

        List<BankProfile> profiles = ConfigLoader.loadBankProfiles(
                configDir.resolve("bank_profiles.yaml"), warnings::add);

        BankProfile overridden = profiles.stream()
                .filter(p -> p.name().equals("hdfc_credit_card_pdf")).findFirst().orElseThrow();
        assertEquals(List.of("MY OWN HEADER"), overridden.match().headersAny());
        assertEquals(1, profiles.stream().filter(p -> p.name().equals("hdfc_credit_card_pdf")).count(),
                "the built-in should be replaced, not duplicated");
        // Silence here is how a forgotten copy holds someone on an old profile forever.
        assertTrue(warnings.stream().anyMatch(w -> w.message().contains("overrides the built-in")),
                () -> "no shadow warning in " + warnings);
    }

    @Test
    void aProfileTheUserAddedIsTriedBeforeTheBuiltIns(@TempDir Path tmp) throws IOException {
        Path configDir = staleWorkspace(tmp, """
                schema_version: 1
                profiles:
                  - name: my_own_bank
                    applies_to: [csv]
                    match:
                      headers_any: ["Whatever"]
                    date_order: DMY
                    date_formats: ["dd/MM/yyyy"]
                    amount_sign: separate_columns
                """);

        List<String> names = names(ConfigLoader.loadBankProfiles(configDir.resolve("bank_profiles.yaml")));

        // Profile scoring keeps the first of two equal matches, so the user's own mapping has to
        // come first to win that tie against a shipped profile.
        assertEquals("my_own_bank", names.get(0), () -> names.toString());
    }

    @Test
    void aBuiltInCanBeSwitchedOffByName(@TempDir Path tmp) throws IOException {
        Path configDir = staleWorkspace(tmp, """
                schema_version: 1
                disable_shipped_profiles:
                  - generic_pdf_indian_savings
                """);

        List<String> names = names(ConfigLoader.loadBankProfiles(configDir.resolve("bank_profiles.yaml")));

        // Deleting a built-in from the file cannot work - it was never in the file - so there
        // has to be a way to say no.
        assertFalse(names.contains("generic_pdf_indian_savings"), () -> names.toString());
        assertTrue(names.contains("hdfc_credit_card_pdf"), "only the named profile is disabled");
    }

    @Test
    void anUnknownTopLevelKeyIsWarnedAboutAndIgnored(@TempDir Path tmp) throws IOException {
        Path configDir = tmp.resolve("config");
        ConfigLoader.writeDefaultConfig(configDir, false);
        Files.writeString(configDir.resolve("config.yaml"),
                Files.readString(configDir.resolve("config.yaml"), StandardCharsets.UTF_8)
                        + "\nmispelled_section:\n  foo: 1\n", StandardCharsets.UTF_8);
        List<Diagnostic> warnings = new ArrayList<>();

        Config config = ConfigLoader.loadConfig(configDir, warnings::add);

        // F8: "unknown top-level keys are warned about, never fatal."
        assertEquals("INR", config.currencyDefault(), "the rest of the file still loads");
        assertTrue(warnings.stream().anyMatch(w -> "CFG-005".equals(w.code())
                && w.message().contains("mispelled_section")), () -> warnings.toString());
    }

    @Test
    void aConfigOlderThanTheBuildIsNamedRatherThanMigrated(@TempDir Path tmp) throws IOException {
        Path configDir = tmp.resolve("config");
        ConfigLoader.writeDefaultConfig(configDir, false);
        Path profiles = configDir.resolve("bank_profiles.yaml");
        Files.writeString(profiles, "schema_version: 0\n", StandardCharsets.UTF_8);
        List<Diagnostic> warnings = new ArrayList<>();

        ConfigLoader.loadBankProfiles(profiles, warnings::add);

        // Warned, never rewritten: F8 makes this the user's file, and a Jackson round-trip to
        // migrate it would destroy the comments documenting how to edit it.
        assertTrue(warnings.stream().anyMatch(w -> w.message().contains("schema_version 0")),
                () -> warnings.toString());
        assertEquals("schema_version: 0\n", Files.readString(profiles, StandardCharsets.UTF_8));
    }

    @Test
    void theShippedDefaultsThemselvesProduceNoWarnings(@TempDir Path tmp) throws IOException {
        Path configDir = tmp.resolve("config");
        ConfigLoader.writeDefaultConfig(configDir, false);
        List<Diagnostic> warnings = new ArrayList<>();

        ConfigLoader.loadConfig(configDir, warnings::add);

        // A false warning on a pristine install would teach the user to ignore the channel. This
        // caught a real one: accounts.yaml's documented `income_rules` key was missing from the
        // known-keys list, so every clean run reported it as unknown.
        assertEquals(List.of(), warnings.stream().map(Diagnostic::message).toList());
    }

    @Test
    void aFreshWorkspaceGetsAnOverlayRatherThanACopyOfTheBuiltIns(@TempDir Path tmp) throws IOException {
        Path configDir = tmp.resolve("config");
        ConfigLoader.writeDefaultConfig(configDir, false);

        String written = Files.readString(configDir.resolve("bank_profiles.yaml"), StandardCharsets.UTF_8);

        // A full copy on disk would shadow the built-ins permanently, so improving a shipped
        // profile later would never reach anyone who had already run the tool once - which is
        // the staleness this whole mechanism exists to remove.
        assertFalse(written.contains("hdfc_credit_card_pdf"), "the default file must not copy the built-ins");
        assertTrue(written.contains("OVERLAY"), "it should explain what it is");
        // And the built-ins are still all there when loaded.
        assertEquals(3, ConfigLoader.loadBankProfiles(configDir.resolve("bank_profiles.yaml")).size());
    }

    private static Path staleWorkspace(Path tmp, String profilesYaml) throws IOException {
        Path configDir = tmp.resolve("config");
        ConfigLoader.writeDefaultConfig(configDir, false);
        Files.writeString(configDir.resolve("bank_profiles.yaml"), profilesYaml, StandardCharsets.UTF_8);
        return configDir;
    }

    private static List<String> names(List<BankProfile> profiles) {
        return profiles.stream().map(BankProfile::name).toList();
    }
}
