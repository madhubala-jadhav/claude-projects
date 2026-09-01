package org.example.categorize;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.config.CategorySet;
import org.example.normalize.Transaction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 01-components.md C6 {@code CorrectionsStore} — the durable record of what the user taught the
 * tool (FR9, AC4).
 *
 * <p>ADR-0006 calls this "the only file the tool mutates across runs", and everything unusual
 * about this class follows from that: it is written atomically because a crash mid-save would
 * destroy accumulated effort that cannot be reconstructed, and superseded entries move to
 * {@code history[]} rather than being overwritten because "a mistaken correction is
 * recoverable" is the difference between a feature the user trusts and one they are afraid
 * of.</p>
 */
public final class CorrectionsStore {

    public static final String FILE_NAME = "corrections.json";
    private static final int SCHEMA_VERSION = 1;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    /** The on-disk shape, 02-data-model.md §3.5. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    record Document(
            @JsonProperty("schema_version") Integer schemaVersion,
            @JsonProperty("updated_at") LocalDateTime updatedAt,
            List<Correction> corrections,
            List<HistoryEntry> history
    ) {
    }

    /** A correction that was replaced. Kept so a wrong correction can be undone by hand. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record HistoryEntry(
            @JsonProperty("merchant_key") String merchantKey,
            String category,
            String scope,
            @JsonProperty("created_at") LocalDateTime createdAt,
            @JsonProperty("superseded_at") LocalDateTime supersededAt
    ) {
    }

    /** Keyed by {@link Correction#identity()} so last-write-wins is a map put. */
    private final Map<String, Correction> corrections;
    private final List<HistoryEntry> history;

    private CorrectionsStore(Map<String, Correction> corrections, List<HistoryEntry> history) {
        this.corrections = corrections;
        this.history = history;
    }

    public static CorrectionsStore empty() {
        return new CorrectionsStore(new LinkedHashMap<>(), new ArrayList<>());
    }

    /**
     * F4: "the loader must be able to read every prior version. Corrections are never deleted
     * on migration." A file this build cannot understand is therefore never overwritten —
     * {@link #load} throws rather than quietly starting from empty, because starting from empty
     * and then saving would erase the user's accumulated work.
     */
    public static CorrectionsStore load(Path path) throws IOException {
        if (Files.notExists(path)) {
            return empty();
        }
        Document doc = MAPPER.readValue(path.toFile(), Document.class);
        if (doc.schemaVersion() != null && doc.schemaVersion() > SCHEMA_VERSION) {
            throw new IOException(FILE_NAME + " was written by a newer version of this tool"
                    + " (schema_version " + doc.schemaVersion() + "); refusing to read it rather than"
                    + " risk overwriting corrections this build does not understand");
        }
        Map<String, Correction> byIdentity = new LinkedHashMap<>();
        if (doc.corrections() != null) {
            for (Correction c : doc.corrections()) {
                byIdentity.put(c.identity(), c);
            }
        }
        return new CorrectionsStore(byIdentity,
                doc.history() == null ? new ArrayList<>() : new ArrayList<>(doc.history()));
    }

    public List<Correction> corrections() {
        return List.copyOf(corrections.values());
    }

    public List<HistoryEntry> history() {
        return List.copyOf(history);
    }

    public boolean isEmpty() {
        return corrections.isEmpty();
    }

    /**
     * The ladder's L1 and L2 lookup (ADR-0005): a transaction-scoped pin beats a merchant-wide
     * rule, "because it is the more specific statement".
     *
     * @return the correction that claims this transaction, or {@code null}.
     */
    public Correction lookup(Transaction txn) {
        Correction pinned = corrections.get(Correction.SCOPE_TRANSACTION + ":" + txn.id());
        if (pinned != null) {
            return pinned;
        }
        if (txn.merchantKey() == null || txn.merchantKey().isBlank()) {
            return null;
        }
        return corrections.get(Correction.SCOPE_MERCHANT + ":" + txn.merchantKey());
    }

    /**
     * Merges one validated patch. Last-write-wins per identity, with the prior value moved into
     * {@code history[]}. Validation happens in {@link PatchValidator} before this is called —
     * by the time a patch reaches here it is known to be applicable in full, because §3.6 is
     * explicit that "a half-applied patch is worse than none".
     */
    public int apply(CorrectionPatch patch, LocalDateTime now) {
        int applied = 0;
        for (CorrectionPatch.PatchEntry entry : patch.corrections()) {
            String scope = entry.scope() == null ? Correction.SCOPE_MERCHANT : entry.scope();
            Correction incoming = new Correction(
                    entry.merchantKey(),
                    entry.category(),
                    scope,
                    entry.transactionId(),
                    now,
                    patch.month(),
                    entry.exampleDescription());
            Correction previous = corrections.put(incoming.identity(), incoming);
            if (previous != null) {
                history.add(new HistoryEntry(previous.merchantKey(), previous.category(),
                        previous.scope(), previous.createdAt(), now));
            }
            applied++;
        }
        return applied;
    }

    /**
     * WARN-501: a correction naming a category that no longer exists is "kept in the file but
     * not applied", so renaming a category cannot silently discard what the user taught.
     */
    public List<Correction> staleAgainst(CategorySet categories) {
        List<Correction> stale = new ArrayList<>();
        for (Correction c : corrections.values()) {
            if (categories.byName(c.category()) == null) {
                stale.add(c);
            }
        }
        return stale;
    }

    /**
     * Atomic write: a temp sibling, forced to disk, then moved into place. ADR-0006 requires it
     * because "a crash can never truncate accumulated user effort" — this file is the one thing
     * in the workspace the tool cannot rebuild from the statements.
     */
    public void save(Path path, LocalDateTime now) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Document doc = new Document(SCHEMA_VERSION, now, List.copyOf(corrections.values()), List.copyOf(history));
        byte[] json = MAPPER.writeValueAsBytes(doc);

        Path tmp = Files.createTempFile(parent, FILE_NAME, ".tmp");
        try {
            Files.write(tmp, json);
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // Some filesystems cannot move atomically; a plain replace is still far better
                // than writing the real file in place and being interrupted part-way.
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** Renders the store the way the report's "your rule" tooltip needs it. */
    public String describe(Correction correction) {
        return "You moved " + correction.merchantKey() + " to " + correction.category()
                + (correction.createdAt() == null ? "" : " on "
                + correction.createdAt().toLocalDate());
    }

    @Override
    public String toString() {
        return "CorrectionsStore[" + corrections.size() + " corrections, " + history.size() + " superseded]";
    }

    static String utf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
