package org.example.parse;

import org.example.config.BankProfile;
import org.example.config.ConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** C4c {@code prompt_and_save_profile} - spec §11.2's one-time column mapping. */
class ColumnMapperTest {

    /** Replays scripted answers, so the flow can be exercised without a terminal. */
    private static final class ScriptedPrompt implements ColumnMapper.Prompt {
        private final Deque<String> answers;
        final List<String> said = new ArrayList<>();

        ScriptedPrompt(String... answers) {
            this.answers = new ArrayDeque<>(List.of(answers));
        }

        @Override
        public boolean isInteractive() {
            return true;
        }

        @Override
        public String ask(String question) {
            return answers.isEmpty() ? "" : answers.poll();
        }

        @Override
        public void say(String message) {
            said.add(message);
        }
    }

    private static final List<String> HEADERS =
            List.of("Txn Date", "Particulars", "Debit", "Credit", "Running Balance");

    @Test
    void mapsColumnsOnceAndSavesAProfileThatLoadsBackFromDisk(@TempDir Path tmp) throws IOException {
        Path profiles = defaultProfiles(tmp);
        // date, description, separate columns? y, debit, credit, balance, date format, name
        ScriptedPrompt io = new ScriptedPrompt("1", "2", "y", "3", "4", "5", "1", "mybank_savings");

        BankProfile profile = ColumnMapper.promptAndSaveProfile(HEADERS, "mybank_aug.csv", profiles, io);

        assertNotNull(profile);
        assertEquals("mybank_savings", profile.name());
        assertEquals("Txn Date", profile.columns().get("date"));
        assertEquals("Particulars", profile.columns().get("description"));
        assertEquals("Debit", profile.columns().get("debit"));
        assertEquals("Credit", profile.columns().get("credit"));
        assertEquals("Running Balance", profile.columns().get("balance"));
        assertEquals(BankProfile.SIGN_SEPARATE_COLUMNS, profile.amountSign());

        // The point of the flow: the answer survives to the next run.
        List<BankProfile> reloaded = ConfigLoader.loadBankProfiles(profiles);
        assertTrue(reloaded.stream().anyMatch(p -> p.name().equals("mybank_savings")),
                () -> "saved profile missing from " + reloaded.stream().map(BankProfile::name).toList());
    }

    @Test
    void theSavedProfileMatchesTheFileItWasMappedFrom(@TempDir Path tmp) throws IOException {
        Path profiles = defaultProfiles(tmp);
        ScriptedPrompt io = new ScriptedPrompt("1", "2", "y", "3", "4", "5", "1", "mybank_savings");
        ColumnMapper.promptAndSaveProfile(HEADERS, "mybank_aug.csv", profiles, io);

        List<BankProfile> reloaded = ConfigLoader.loadBankProfiles(profiles);

        // "the saved profile works unattended on the next run" - the slice-3 exit criterion.
        BankProfile resolved = TabularParser.resolveProfile(HEADERS, reloaded, null);
        assertNotNull(resolved);
        assertEquals("mybank_savings", resolved.name());
    }

    @Test
    void aSingleAmountColumnWithACreditMarkerIsRecordedAsSuch(@TempDir Path tmp) throws IOException {
        Path profiles = defaultProfiles(tmp);
        List<String> headers = List.of("Date", "Narration", "Amount");
        // date, description, separate columns? n, amount, negatives? n, marker, balance, format, name
        ScriptedPrompt io = new ScriptedPrompt("1", "2", "n", "3", "n", "Cr", "", "1", "single_col_bank");

        BankProfile profile = ColumnMapper.promptAndSaveProfile(headers, "bank.csv", profiles, io);

        assertNotNull(profile);
        assertEquals(BankProfile.SIGN_DR_CR_SUFFIX, profile.amountSign());
        assertEquals(List.of("Cr"), profile.creditMarkers());
        assertNull(profile.columns().get("balance"));
        assertTrue(Files.readString(profiles, StandardCharsets.UTF_8).contains("credit_markers"));
    }

    @Test
    void withNoConsoleNothingIsAskedAndNothingIsWritten(@TempDir Path tmp) throws IOException {
        Path profiles = defaultProfiles(tmp);
        String before = Files.readString(profiles, StandardCharsets.UTF_8);
        ColumnMapper.Prompt silent = new ColumnMapper.Prompt() {
            @Override
            public boolean isInteractive() {
                return false;
            }

            @Override
            public String ask(String question) {
                throw new AssertionError("must not prompt without a console");
            }

            @Override
            public void say(String message) {
                throw new AssertionError("must not print without a console");
            }
        };

        // C4c: no TTY means PARSE-204, never a run that blocks waiting for input nobody types.
        assertNull(ColumnMapper.promptAndSaveProfile(HEADERS, "mybank_aug.csv", profiles, silent));
        assertEquals(before, Files.readString(profiles, StandardCharsets.UTF_8));
    }

    @Test
    void givingUpOnARequiredAnswerLeavesTheConfigUntouched(@TempDir Path tmp) throws IOException {
        Path profiles = defaultProfiles(tmp);
        String before = Files.readString(profiles, StandardCharsets.UTF_8);
        ScriptedPrompt io = new ScriptedPrompt("", "", "");

        assertNull(ColumnMapper.promptAndSaveProfile(HEADERS, "mybank_aug.csv", profiles, io));
        assertEquals(before, Files.readString(profiles, StandardCharsets.UTF_8));
    }

    @Test
    void appendingPreservesTheCommentsInTheUsersConfigFile(@TempDir Path tmp) throws IOException {
        Path profiles = defaultProfiles(tmp);
        ScriptedPrompt io = new ScriptedPrompt("1", "2", "y", "3", "4", "5", "1", "mybank_savings");

        ColumnMapper.promptAndSaveProfile(HEADERS, "mybank_aug.csv", profiles, io);

        // F8 makes bank_profiles.yaml the user's file to hand-edit; a Jackson round-trip would
        // strip every comment in it, including the guide for adding a bank.
        String after = Files.readString(profiles, StandardCharsets.UTF_8);
        assertTrue(after.contains("This file is an OVERLAY"), "shipped comments were lost");
        assertTrue(after.contains("disable_shipped_profiles"), "the how-to-edit guide was lost");
        assertTrue(after.contains("mybank_savings"), "the appended profile is missing");
    }

    private static Path defaultProfiles(Path tmp) throws IOException {
        Path configDir = tmp.resolve("config");
        ConfigLoader.writeDefaultConfig(configDir, false);
        return configDir.resolve("bank_profiles.yaml");
    }
}
