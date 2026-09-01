package org.example.parse;

import org.example.config.BankProfile;
import org.example.config.ConfigException;
import org.example.config.ConfigLoader;

import java.io.Console;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 01-components.md C4c {@code prompt_and_save_profile} - spec §11.2's one-time column mapping:
 * "If a file's columns don't match any known profile, prompt the user to map columns once and
 * save that mapping as a new profile for future runs."
 *
 * <p>The saved profile is <em>appended</em> to {@code bank_profiles.yaml} as text rather than
 * re-serialized from the parsed model. The file is the user's to hand-edit (F8), and a Jackson
 * round-trip would silently strip every comment in it - including the ones that explain how to
 * add a bank. The appended block is then re-read before the write is allowed to stand, so a
 * malformed profile can never leave the config file unloadable.</p>
 */
public final class ColumnMapper {

    /** The interaction surface, so the flow can be driven by a test as well as by a terminal. */
    public interface Prompt {
        boolean isInteractive();

        /** @return the user's answer, trimmed; empty string for "skip". */
        String ask(String question);

        void say(String message);
    }

    /** An uncommented top-level {@code profiles:} key, which an appended profile needs. */
    private static final Pattern PROFILES_KEY = Pattern.compile("^profiles:", Pattern.MULTILINE);

    /** Date formats offered by number, so the user never has to know the pattern syntax. */
    private static final List<String> DATE_FORMATS = List.of(
            "dd/MM/yyyy", "dd/MM/yy", "dd-MM-yyyy", "yyyy-MM-dd", "dd MMM yyyy", "MM/dd/yyyy");

    private ColumnMapper() {
    }

    /**
     * The real terminal. {@link System#console()} is null when stdin is redirected or the tool
     * runs from a scheduler, which is exactly the "no TTY" case C4c says must fall through to
     * {@code PARSE-204} instead of blocking a run nobody is watching.
     */
    public static Prompt consolePrompt() {
        Console console = System.console();
        return new Prompt() {
            @Override
            public boolean isInteractive() {
                return console != null;
            }

            @Override
            public String ask(String question) {
                if (console == null) {
                    return "";
                }
                String answer = console.readLine("%s ", question);
                return answer == null ? "" : answer.trim();
            }

            @Override
            public void say(String message) {
                if (console != null) {
                    console.printf("%s%n", message);
                }
            }
        };
    }

    /**
     * Walks the user through mapping one file's columns and persists the answer.
     *
     * @return the new profile, or {@code null} if there is nobody to ask or the user gave up.
     */
    public static BankProfile promptAndSaveProfile(List<String> headers, String fileName, Path profilesFile,
                                                   Prompt io) throws IOException {
        if (!io.isInteractive() || headers.isEmpty()) {
            return null;
        }

        io.say("");
        io.say("I don't recognise the columns in " + fileName + ". Let's map them once - "
                + "I'll remember this layout for future runs.");
        for (int i = 0; i < headers.size(); i++) {
            io.say("  [" + (i + 1) + "] " + headers.get(i));
        }

        Integer date = askColumn(io, headers, "Which column holds the transaction date?", true);
        if (date == null) {
            return null;
        }
        Integer description = askColumn(io, headers, "Which column holds the description or narration?", true);
        if (description == null) {
            return null;
        }

        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("date", headers.get(date));
        columns.put("description", headers.get(description));

        String amountSign;
        List<String> creditMarkers = List.of();
        if (askYesNo(io, "Does this file use separate withdrawal and deposit columns?")) {
            Integer debit = askColumn(io, headers, "Which column holds withdrawals (money out)?", true);
            Integer credit = askColumn(io, headers, "Which column holds deposits (money in)?", true);
            if (debit == null || credit == null) {
                return null;
            }
            columns.put("debit", headers.get(debit));
            columns.put("credit", headers.get(credit));
            amountSign = BankProfile.SIGN_SEPARATE_COLUMNS;
        } else {
            Integer amount = askColumn(io, headers, "Which column holds the amount?", true);
            if (amount == null) {
                return null;
            }
            columns.put("amount", headers.get(amount));
            if (askYesNo(io, "Are money-out amounts shown as negative numbers in that column?")) {
                amountSign = BankProfile.SIGN_DEBIT_NEGATIVE;
            } else {
                amountSign = BankProfile.SIGN_DR_CR_SUFFIX;
                String marker = io.ask("What marks a money-in row - e.g. Cr or + ? (blank for Cr)");
                creditMarkers = marker.isEmpty() ? List.of("Cr", "CR") : List.of(marker);
            }
        }

        Integer balance = askColumn(io, headers, "Which column holds the running balance? (blank to skip)", false);
        if (balance != null) {
            columns.put("balance", headers.get(balance));
        }

        String dateFormat = askDateFormat(io);
        String name = askProfileName(io, fileName);

        BankProfile profile = new BankProfile(
                name,
                List.of("csv", "xlsx"),
                new BankProfile.Match(new ArrayList<>(columns.values()), List.of()),
                columns,
                dateOrderOf(dateFormat),
                List.of(dateFormat),
                ".",
                ",",
                amountSign,
                creditMarkers,
                null,
                List.of(),
                null);

        append(profilesFile, profile);
        io.say("Saved profile '" + name + "' to " + profilesFile.getFileName()
                + ". Future statements with these columns will be read automatically.");
        return profile;
    }

    private static Integer askColumn(Prompt io, List<String> headers, String question, boolean required) {
        for (int attempt = 0; attempt < 3; attempt++) {
            String answer = io.ask(question);
            if (answer.isEmpty()) {
                if (!required) {
                    return null;
                }
                io.say("That one is required - please pick a number from the list.");
                continue;
            }
            try {
                int index = Integer.parseInt(answer) - 1;
                if (index >= 0 && index < headers.size()) {
                    return index;
                }
            } catch (NumberFormatException ignored) {
                // fall through to the same retry message
            }
            io.say("Please enter a number between 1 and " + headers.size() + ".");
        }
        return null;
    }

    private static boolean askYesNo(Prompt io, String question) {
        String answer = io.ask(question + " (y/n)").toLowerCase(Locale.ROOT);
        return answer.startsWith("y");
    }

    private static String askDateFormat(Prompt io) {
        io.say("How are dates written in this file?");
        for (int i = 0; i < DATE_FORMATS.size(); i++) {
            io.say("  [" + (i + 1) + "] " + DATE_FORMATS.get(i) + "  e.g. " + example(DATE_FORMATS.get(i)));
        }
        String answer = io.ask("Pick a number (blank for " + DATE_FORMATS.get(0) + ")");
        try {
            int index = Integer.parseInt(answer) - 1;
            if (index >= 0 && index < DATE_FORMATS.size()) {
                return DATE_FORMATS.get(index);
            }
        } catch (NumberFormatException ignored) {
            // blank or nonsense: the first entry is the overwhelmingly common Indian form
        }
        return DATE_FORMATS.get(0);
    }

    private static String example(String pattern) {
        return java.time.LocalDate.of(2026, 8, 14)
                .format(java.time.format.DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
    }

    private static String askProfileName(Prompt io, String fileName) {
        String suggested = sanitize(fileName);
        String answer = io.ask("Name this layout (blank for '" + suggested + "')");
        return answer.isEmpty() ? suggested : sanitize(answer);
    }

    private static String sanitize(String raw) {
        String base = raw.replaceAll("\\.[A-Za-z0-9]+$", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return base.isEmpty() ? "custom_profile" : base;
    }

    /** {@code date_order} is never inferred from data (§1.5) - it follows from the chosen pattern. */
    private static String dateOrderOf(String pattern) {
        String p = pattern.toLowerCase(Locale.ROOT);
        if (p.startsWith("y")) {
            return "YMD";
        }
        return p.startsWith("m") ? "MDY" : "DMY";
    }

    /**
     * Appends one profile to the end of {@code bank_profiles.yaml}, then reloads the file and
     * rolls the whole append back if it no longer parses. The append assumes {@code profiles:}
     * is the file's last top-level key, which is how the shipped default is written and what
     * the header comment there tells a hand-editor to preserve.
     */
    private static void append(Path profilesFile, BankProfile profile) throws IOException {
        String original = Files.readString(profilesFile, StandardCharsets.UTF_8);
        Path backup = profilesFile.resolveSibling(profilesFile.getFileName() + ".bak");
        Files.copy(profilesFile, backup, StandardCopyOption.REPLACE_EXISTING);

        StringBuilder yaml = new StringBuilder();
        if (!original.endsWith("\n")) {
            yaml.append('\n');
        }
        // A fresh workspace gets an overlay file whose `profiles:` key appears only as a
        // commented example, because the shipped profiles are built into the build. The
        // first mapped profile therefore has to create the key it is appending to.
        if (!PROFILES_KEY.matcher(original).find()) {
            yaml.append("\nprofiles:\n");
        }
        yaml.append("\n  # Added by the one-time column mapper (spec §11.2).\n");
        yaml.append("  - name: ").append(profile.name()).append('\n');
        yaml.append("    applies_to: [csv, xlsx]\n");
        yaml.append("    match:\n");
        yaml.append("      headers_any: ").append(quotedList(profile.match().headersAny())).append('\n');
        yaml.append("    columns:\n");
        for (Map.Entry<String, String> entry : profile.columns().entrySet()) {
            yaml.append("      ").append(entry.getKey()).append(": ").append(quote(entry.getValue())).append('\n');
        }
        yaml.append("    date_order: ").append(profile.dateOrder()).append('\n');
        yaml.append("    date_formats: ").append(quotedList(profile.dateFormats())).append('\n');
        yaml.append("    decimal_separator: \".\"\n");
        yaml.append("    thousands_separator: \",\"\n");
        yaml.append("    amount_sign: ").append(profile.amountSign()).append('\n');
        if (!profile.creditMarkers().isEmpty()) {
            yaml.append("    credit_markers: ").append(quotedList(profile.creditMarkers())).append('\n');
        }

        Files.writeString(profilesFile, original + yaml, StandardCharsets.UTF_8);
        try {
            ConfigLoader.loadBankProfilesFile(profilesFile);
            Files.deleteIfExists(backup);
        } catch (ConfigException e) {
            Files.copy(backup, profilesFile, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(backup);
            throw new IOException("the generated profile did not parse; bank_profiles.yaml was left unchanged");
        }
    }

    private static String quotedList(List<String> values) {
        List<String> quoted = new ArrayList<>();
        for (String v : values) {
            quoted.add(quote(v));
        }
        return "[" + String.join(", ", quoted) + "]";
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
