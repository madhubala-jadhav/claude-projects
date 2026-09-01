package org.example.ingest;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.categorize.CorrectionPatch;
import org.example.categorize.CorrectionsStore;
import org.example.categorize.PatchValidator;
import org.example.config.CategorySet;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 01-components.md C3 {@code harvest_correction_patches} (FR20 → FR9 → AC4).
 *
 * <p>Two properties of this class are load-bearing, both from ADR-0006:</p>
 *
 * <ul>
 *   <li><strong>It runs before discovery and parsing.</strong> "Harvest happens first ... so
 *       corrections apply to the run that harvests them, not the one after. If the user drops
 *       the patch and re-runs the same month, they see the fix immediately." A correction loop
 *       whose result only appears next month is one nobody would trust enough to use.</li>
 *   <li><strong>Applied patches are moved, not deleted.</strong> That is what makes the harvest
 *       idempotent — a patch cannot be applied twice — and it leaves an audit trail of what was
 *       learned and when.</li>
 * </ul>
 */
public final class CorrectionHarvester {

    /** §3.6's filename shape: {@code input/corrections-YYYY-MM.json}. */
    private static final String PATCH_PREFIX = "corrections-";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** What the run needs to report: what was applied, and what was refused and why. */
    public record HarvestResult(
            int patchesApplied,
            int correctionsApplied,
            List<Rejection> rejected,
            List<Path> archived
    ) {
        public boolean changedAnything() {
            return correctionsApplied > 0;
        }
    }

    public record Rejection(String file, String reason) {
    }

    private CorrectionHarvester() {
    }

    public static HarvestResult harvest(Path inputDir, Path correctionsFile, Path archiveDir,
                                        CategorySet categories) throws IOException {
        List<Path> patches = findPatches(inputDir);
        List<Rejection> rejected = new ArrayList<>();
        List<Path> archived = new ArrayList<>();
        if (patches.isEmpty()) {
            return new HarvestResult(0, 0, rejected, archived);
        }

        CorrectionsStore store = CorrectionsStore.load(correctionsFile);
        LocalDateTime now = LocalDateTime.now();
        int applied = 0;
        int patchesApplied = 0;

        for (Path patchFile : patches) {
            String name = patchFile.getFileName().toString();
            CorrectionPatch patch;
            try {
                patch = MAPPER.readValue(patchFile.toFile(), CorrectionPatch.class);
            } catch (IOException e) {
                // Never surface the parser's own message: Jackson embeds the absolute path (§13).
                rejected.add(new Rejection(name, "it is not readable as JSON"));
                continue;
            }
            PatchValidator.Result validation = PatchValidator.validate(patch, categories);
            if (!validation.valid()) {
                // Left in place, not archived: the user can fix and re-drop it, and a rejected
                // patch silently vanishing would look exactly like a successful one.
                rejected.add(new Rejection(name, validation.reason()));
                continue;
            }
            applied += store.apply(patch, now);
            patchesApplied++;
            archived.add(archive(patchFile, archiveDir, now));
        }

        if (applied > 0) {
            store.save(correctionsFile, now);
        }
        return new HarvestResult(patchesApplied, applied, rejected, archived);
    }

    /**
     * Deterministic order so two patches touching the same merchant resolve last-write-wins the
     * same way on every run.
     */
    private static List<Path> findPatches(Path inputDir) throws IOException {
        List<Path> patches = new ArrayList<>();
        if (Files.notExists(inputDir)) {
            return patches;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(inputDir)) {
            for (Path path : stream) {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!Files.isDirectory(path) && name.startsWith(PATCH_PREFIX) && name.endsWith(".json")) {
                    patches.add(path);
                }
            }
        }
        patches.sort(Comparator.comparing(p -> p.getFileName().toString()));
        return patches;
    }

    private static Path archive(Path patchFile, Path archiveDir, LocalDateTime now) throws IOException {
        Path target = archiveDir.resolve("corrections");
        Files.createDirectories(target);
        String base = patchFile.getFileName().toString().replaceAll("\\.json$", "");
        Path destination = target.resolve(base + ".applied-" + now.format(STAMP) + ".json");
        Files.move(patchFile, destination, StandardCopyOption.REPLACE_EXISTING);
        return destination;
    }
}
