package org.example.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.example.config.yaml.BankProfilesYamlRoot;
import org.example.config.yaml.CategoriesYamlRoot;
import org.example.config.yaml.ConfigYamlRoot;

import org.example.diagnostics.Diagnostic;
import org.example.diagnostics.ErrorCode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 01-components.md C2. {@code load_config} / {@code write_default_config} /
 * {@code resolve_workspace}, bound via Jackson's YAML module per ADR-0020.
 */
public final class ConfigLoader {

    private static final String[] DEFAULT_FILES = {
            "config.yaml", "categories.yaml", "bank_profiles.yaml", "accounts.yaml"
    };

    /** The bundled profile set. This resource is the source of truth, never the copy on disk. */
    private static final String SHIPPED_PROFILES = "/default-config/bank_profiles.yaml";

    /**
     * What a fresh workspace gets as its bank_profiles.yaml. Deliberately *not* the shipped
     * profile set: a full copy on disk would shadow the built-ins forever, so improving a
     * shipped profile in a later release would silently never reach anyone who had already run
     * the tool once - which is the exact staleness this overlay exists to remove.
     */
    private static final String PROFILES_OVERLAY_TEMPLATE = "/default-config/bank_profiles.overlay.yaml";

    /** The schema version this build writes and understands, per file (F8). */
    private static final int SCHEMA_VERSION = 1;

    /** F8: "unknown top-level keys are warned about, never fatal." */
    private static final Map<String, Set<String>> KNOWN_TOP_LEVEL_KEYS = Map.of(
            "config.yaml", Set.of("schema_version", "paths", "currency", "dedupe", "transfers",
                    "ocr", "report", "logging"),
            "categories.yaml", Set.of("schema_version", "categories", "overrides"),
            "bank_profiles.yaml", Set.of("schema_version", "profiles", "disable_shipped_profiles"),
            "accounts.yaml", Set.of("schema_version", "accounts", "income_rules"));

    private ConfigLoader() {
    }

    private static ObjectMapper yamlMapper() {
        YAMLMapper mapper = new YAMLMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }

    /**
     * Precedence per the C2 contract: explicit path {@literal >} EXPENSE_NUTSHELL_HOME env
     * var {@literal >} current working directory (which run.bat/run.sh/run.command already
     * {@code cd}'d into their own folder before invoking java, so this is "the folder
     * containing the run script" in practice). The documented fourth tier - a platform
     * user-data directory - is not implemented this slice, since the third tier always
     * resolves to *something* given the launcher's own {@code cd}; flagged as a known
     * simplification.
     */
    public static Workspace resolveWorkspace(Path explicit) {
        Path base;
        if (explicit != null) {
            base = explicit.toAbsolutePath().normalize();
        } else {
            String env = System.getenv("EXPENSE_NUTSHELL_HOME");
            if (env != null && !env.isBlank()) {
                base = Paths.get(env).toAbsolutePath().normalize();
            } else {
                base = Paths.get("").toAbsolutePath().normalize();
            }
        }
        return new Workspace(
                base.resolve("config"),
                base.resolve("input"),
                base.resolve("output"),
                base.resolve("archive")
        );
    }

    public static List<Path> writeDefaultConfig(Path configDir, boolean force) throws IOException {
        Files.createDirectories(configDir);
        List<Path> written = new ArrayList<>();
        for (String fileName : DEFAULT_FILES) {
            Path target = configDir.resolve(fileName);
            if (force || Files.notExists(target)) {
                String resourcePath = fileName.equals("bank_profiles.yaml")
                        ? PROFILES_OVERLAY_TEMPLATE
                        : "/default-config/" + fileName;
                try (InputStream in = ConfigLoader.class.getResourceAsStream(resourcePath)) {
                    if (in == null) {
                        throw new IllegalStateException("Missing bundled default resource: " + resourcePath);
                    }
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                written.add(target);
            }
        }
        return written;
    }

    public static Config loadConfig(Path configDir) {
        return loadConfig(configDir, warning -> { });
    }

    /**
     * @param warn receives non-fatal configuration diagnostics - an unknown top-level key (F8),
     *             a config file older than this build, a user profile shadowing a built-in.
     *             These are warnings precisely because none of them should stop a run (NFR3);
     *             the caller drains them into {@code RunReport} so they reach the run log and
     *             the report rather than vanishing.
     */
    public static Config loadConfig(Path configDir, Consumer<Diagnostic> warn) {
        ObjectMapper mapper = yamlMapper();
        for (String fileName : DEFAULT_FILES) {
            warnAboutUnknownKeys(mapper, configDir.resolve(fileName), fileName, warn);
        }

        ConfigYamlRoot configYaml = readYaml(mapper, configDir.resolve("config.yaml"), ConfigYamlRoot.class);
        if (configYaml.schemaVersion() != null && configYaml.schemaVersion() > SCHEMA_VERSION) {
            throw new ConfigException("config.yaml: unsupported schema_version " + configYaml.schemaVersion()
                    + " (this build understands up to " + SCHEMA_VERSION + ")");
        }
        if (configYaml.currency() == null || configYaml.currency().defaultCurrency() == null) {
            throw new ConfigException("config.yaml: currency.default is required");
        }

        CategoriesYamlRoot categoriesYaml = readYaml(mapper, configDir.resolve("categories.yaml"), CategoriesYamlRoot.class);
        if (categoriesYaml.categories() == null || categoriesYaml.categories().isEmpty()) {
            throw new ConfigException("categories.yaml: categories must declare at least one category");
        }

        List<Category> categories = new ArrayList<>();
        int order = 0;
        for (Map.Entry<String, CategoriesYamlRoot.CategoryYaml> entry : categoriesYaml.categories().entrySet()) {
            CategoriesYamlRoot.CategoryYaml c = entry.getValue();
            if (c.keywords() == null && (c.patterns() == null || c.patterns().isEmpty())) {
                throw new ConfigException("categories.yaml: categories." + entry.getKey()
                        + ".keywords must be a list of strings");
            }
            categories.add(new Category(
                    entry.getKey(),
                    c.keywords() == null ? List.of() : c.keywords(),
                    c.patterns() == null ? List.of() : c.patterns(),
                    c.isTransfer(),
                    c.isIncome(),
                    c.slot(),
                    order++
            ));
        }

        // CFG-004: two categories claiming the same chart slot is a config error.
        Map<Integer, String> seenSlots = new HashMap<>();
        for (Category c : categories) {
            if (c.slot() != null) {
                String prior = seenSlots.put(c.slot(), c.name());
                if (prior != null) {
                    throw new ConfigException("categories.yaml: categories '" + prior + "' and '" + c.name()
                            + "' both claim chart slot " + c.slot());
                }
            }
        }

        warnIfStale(configYaml.schemaVersion(), "config.yaml", warn);
        warnIfStale(categoriesYaml.schemaVersion(), "categories.yaml", warn);

        List<BankProfile> profiles = loadBankProfiles(configDir.resolve("bank_profiles.yaml"), warn);

        // accounts.yaml is required to exist (F8: all four config files are frozen file
        // names) but isn't bound into a Java type this slice - nothing consumes it yet.
        Path accountsFile = configDir.resolve("accounts.yaml");
        if (Files.notExists(accountsFile)) {
            throw new ConfigException("accounts.yaml: file is missing or unreadable");
        }

        Workspace workspace = resolveWorkspace(configDir.getParent());

        return new Config(
                workspace,
                configYaml.currency().defaultCurrency(),
                configYaml.currency().locale(),
                new CategorySet(categories, lowercaseKeys(categoriesYaml.overrides())),
                profiles,
                toDedupeSettings(configYaml.dedupe()),
                configYaml.report() == null || configYaml.report().openBrowser(),
                // ADR-0007 rule 4's ceiling: the validated categorical palette carries eight
                // distinguishable hues, so a report asking for more would be asking for a
                // colour that is not colour-blind-safe.
                configYaml.report() == null || configYaml.report().topNSlices() <= 0
                        ? 8 : Math.min(8, configYaml.report().topNSlices()),
                configYaml.report() == null ? 1.0 : configYaml.report().minSlicePercent()
        );
    }

    /**
     * Each key falls back to the §3.3 default independently, so a profile may override only
     * {@code crop_bottom_pct} without silently zeroing every other clustering tolerance.
     */
    private static BankProfile.PdfTable toPdfTable(BankProfilesYamlRoot.PdfTableYaml y) {
        BankProfile.PdfTable d = BankProfile.PdfTable.defaults();
        if (y == null) {
            return null; // profile declared no pdf_table at all; only PDF profiles need one
        }
        return new BankProfile.PdfTable(
                y.strategy() == null ? d.strategy() : y.strategy(),
                y.xTolerance() == null ? d.xTolerance() : y.xTolerance(),
                y.yTolerance() == null ? d.yTolerance() : y.yTolerance(),
                y.headerRowContains() == null || y.headerRowContains().isEmpty()
                        ? d.headerRowContains() : y.headerRowContains(),
                y.dropHeaderRowsAfterFirstPage() == null
                        ? d.dropHeaderRowsAfterFirstPage() : y.dropHeaderRowsAfterFirstPage(),
                y.cropTopPct() == null ? d.cropTopPct() : y.cropTopPct(),
                y.cropBottomPct() == null ? d.cropBottomPct() : y.cropBottomPct(),
                y.wrapMaxGap() == null ? d.wrapMaxGap() : y.wrapMaxGap()
        );
    }

    /**
     * §3.2 writes override keys in lowercase ("amazon pay": "Shopping") while a merchant_key is
     * uppercase, so the map is normalised once here rather than at every lookup.
     */
    private static Map<String, String> lowercaseKeys(Map<String, String> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : overrides.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                out.put(e.getKey().trim().toLowerCase(java.util.Locale.ROOT), e.getValue());
            }
        }
        return Map.copyOf(out);
    }

    private static DedupeSettings toDedupeSettings(ConfigYamlRoot.DedupeSection y) {
        DedupeSettings d = DedupeSettings.defaults();
        if (y == null) {
            return d;
        }
        return new DedupeSettings(
                y.crossFileOnly() == null ? d.crossFileOnly() : y.crossFileOnly(),
                y.windowDays() == null ? d.windowDays() : y.windowDays()
        );
    }

    /** The merged view: shipped built-ins overlaid with the user's file. */
    public static List<BankProfile> loadBankProfiles(Path profilesFile) {
        return loadBankProfiles(profilesFile, warning -> { });
    }

    /**
     * Bank profiles are <strong>built into the build and overlaid by the config file</strong>,
     * rather than living only on disk.
     *
     * <p>The alternative - the file on disk being the whole truth - is what let a real HDFC
     * statement be excluded with {@code PARSE-204} after Slice 3 shipped a profile that would
     * have read it: {@code ensureBootstrapped} only writes a config file that is <em>missing</em>,
     * so an existing workspace never saw the new profile. Reference data the tool ships and the
     * user is not expected to maintain belongs in the build; the config file overlays it.</p>
     *
     * <p>Merge rules: a user profile with the same {@code name} as a built-in replaces it in
     * place, and is reported as a shadow so an unintended stale copy is visible rather than
     * silent. A user profile with a new name is tried <em>before</em> the built-ins, because
     * profile scoring keeps the first of two equal matches and the user's own mapping should win
     * that tie. Names listed in {@code disable_shipped_profiles} are dropped outright.</p>
     */
    public static List<BankProfile> loadBankProfiles(Path profilesFile, Consumer<Diagnostic> warn) {
        BankProfilesYamlRoot shipped = readYamlResource(yamlMapper(), SHIPPED_PROFILES);
        BankProfilesYamlRoot user = readYaml(yamlMapper(), profilesFile, BankProfilesYamlRoot.class);
        warnIfStale(user.schemaVersion(), profilesFile.getFileName().toString(), warn);

        Set<String> disabled = new LinkedHashSet<>(
                user.disableShippedProfiles() == null ? List.of() : user.disableShippedProfiles());

        Map<String, BankProfilesYamlRoot.BankProfileYaml> merged = new LinkedHashMap<>();
        for (BankProfilesYamlRoot.BankProfileYaml p : profilesOf(shipped)) {
            if (!disabled.contains(p.name())) {
                merged.put(p.name(), p);
            }
        }

        List<BankProfilesYamlRoot.BankProfileYaml> additions = new ArrayList<>();
        for (BankProfilesYamlRoot.BankProfileYaml p : profilesOf(user)) {
            if (merged.containsKey(p.name())) {
                merged.put(p.name(), p);
                warn.accept(new Diagnostic("config", null, profilesFile.getFileName()
                        + " overrides the built-in bank profile '" + p.name()
                        + "'; delete it there to pick up improvements shipped with the tool"));
            } else {
                additions.add(p);
            }
        }

        List<BankProfile> profiles = new ArrayList<>();
        for (BankProfilesYamlRoot.BankProfileYaml p : additions) {
            profiles.add(toBankProfile(p));
        }
        for (BankProfilesYamlRoot.BankProfileYaml p : merged.values()) {
            profiles.add(toBankProfile(p));
        }
        return profiles;
    }

    /**
     * Reads only the file, with no built-ins merged in. The column mapper (§11.2) uses this to
     * prove the profile it just appended parses - merging built-ins there would let a broken
     * append look valid because the built-ins loaded fine.
     */
    public static List<BankProfile> loadBankProfilesFile(Path profilesFile) {
        List<BankProfile> profiles = new ArrayList<>();
        for (BankProfilesYamlRoot.BankProfileYaml p
                : profilesOf(readYaml(yamlMapper(), profilesFile, BankProfilesYamlRoot.class))) {
            profiles.add(toBankProfile(p));
        }
        return profiles;
    }

    private static List<BankProfilesYamlRoot.BankProfileYaml> profilesOf(BankProfilesYamlRoot root) {
        return root == null || root.profiles() == null ? List.of() : root.profiles();
    }

    private static BankProfile toBankProfile(BankProfilesYamlRoot.BankProfileYaml p) {
        BankProfile.Match match = new BankProfile.Match(
                p.match() == null || p.match().headersAny() == null ? List.of() : p.match().headersAny(),
                p.match() == null || p.match().filenameContains() == null
                        ? List.of() : p.match().filenameContains());
        return new BankProfile(
                p.name(),
                p.appliesTo() == null ? List.of() : p.appliesTo(),
                match,
                p.columns() == null ? Map.of() : p.columns(),
                p.dateOrder(),
                p.dateFormats() == null ? List.of() : p.dateFormats(),
                p.decimalSeparator() == null ? "." : p.decimalSeparator(),
                p.thousandsSeparator() == null ? "," : p.thousandsSeparator(),
                p.amountSign(),
                p.creditMarkers() == null ? List.of() : p.creditMarkers(),
                p.accountNumberRegex(),
                p.skipRowsMatching() == null ? List.of() : p.skipRowsMatching(),
                toPdfTable(p.pdfTable()));
    }

    /**
     * A config file written by an older build. Warned, never migrated: F8 makes these the user's
     * files to hand-edit, and rewriting one to migrate it would destroy the comments that
     * document how to edit it. Naming the file is enough for the user to decide.
     */
    private static void warnIfStale(Integer declaredVersion, String fileName, Consumer<Diagnostic> warn) {
        if (declaredVersion == null) {
            warn.accept(new Diagnostic("config", null, fileName
                    + " does not declare a schema_version, so this build cannot tell how old it is"));
            return;
        }
        if (declaredVersion < SCHEMA_VERSION) {
            warn.accept(new Diagnostic("config", null, fileName + " declares schema_version "
                    + declaredVersion + " but this build writes " + SCHEMA_VERSION
                    + "; it may be missing settings added since it was created"));
        }
    }

    /** F8: "unknown top-level keys are warned about, never fatal." */
    private static void warnAboutUnknownKeys(ObjectMapper mapper, Path file, String fileName,
                                             Consumer<Diagnostic> warn) {
        Set<String> known = KNOWN_TOP_LEVEL_KEYS.get(fileName);
        if (known == null || Files.notExists(file)) {
            return;
        }
        Map<String, Object> raw;
        try {
            raw = mapper.readValue(file.toFile(), Map.class);
        } catch (IOException | RuntimeException e) {
            return; // a genuinely unparseable file is reported by the typed read that follows
        }
        if (raw == null) {
            return;
        }
        for (String key : raw.keySet()) {
            if (!known.contains(key)) {
                warn.accept(new Diagnostic("config", ErrorCode.CFG_005.code(),
                        fileName + ": unknown key '" + key + "' was ignored"));
            }
        }
    }

    private static <T> T readYamlResource(ObjectMapper mapper, String resource) {
        try (InputStream in = ConfigLoader.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new ConfigException("bundled resource missing from the build: " + resource);
            }
            @SuppressWarnings("unchecked")
            T value = (T) mapper.readValue(in, BankProfilesYamlRoot.class);
            return value;
        } catch (IOException e) {
            throw new ConfigException("bundled resource could not be read: " + resource, e);
        }
    }

    private static <T> T readYaml(ObjectMapper mapper, Path path, Class<T> type) {
        if (Files.notExists(path)) {
            throw new ConfigException(path.getFileName() + ": file is missing or unreadable");
        }
        try {
            return mapper.readValue(path.toFile(), type);
        } catch (IOException e) {
            throw new ConfigException(path.getFileName() + ": " + e.getMessage(), e);
        }
    }
}
