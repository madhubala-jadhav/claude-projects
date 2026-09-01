# ADR-0014 — Language and runtime: Java 17, overruling ADR-0001's Python recommendation

**Status:** Accepted
**Date:** 2026-08-28
**Deciders:** Project owner (Madhubala Jadhav), overruling the architect's recommendation in ADR-0001
**Supersedes:** [ADR-0001](ADR-0001-language-and-runtime.md)

---

## Context

[ADR-0001](ADR-0001-language-and-runtime.md) recommended retiring the Java/Maven skeleton
(`pom.xml`, `src/main/java/org/example/Main.java`) and building the tool in Python 3.11+,
citing NFR4's parenthetical ("Python + standard cross-platform libraries") and the stronger
PDF-table/OCR library ecosystem for R1, the top-ranked risk in
[06-risks-and-open-questions.md](../06-risks-and-open-questions.md) (bank statement format
diversity).

ADR-0001 explicitly flagged itself as pending owner confirmation and named this exact
scenario in its own "Neutral" consequences section: *"If the owner overrules this and
mandates Java, every other ADR in this package still holds... only ADR-0003, ADR-0004 and
ADR-0010 would need their library choices re-picked."* It also named the concrete cost:
*"the JVM's PDF-table and OCR ecosystems are materially thinner than Python's, which matters
because R1 is the top risk"* (paraphrased from ADR-0001 §Alternatives A).

The owner has now given that answer: **keep Java.** This ADR records that decision formally,
since [06-risks-and-open-questions.md](../06-risks-and-open-questions.md) A5 required an
explicit yes/no before Slice 1 could begin, and CLAUDE.md has already been updated to
reflect it.

## Decision

**Build the tool in Java 17 on the existing Maven skeleton. Do not delete `pom.xml` or
`src/main/java`.**

Concretely:

1. The implementation grows under `src/main/java/org/example/...`, organized by the C1–C9
   component keys from [01-components.md](../01-components.md) (e.g.
   `org.example.ingest`, `org.example.parse`, `org.example.categorize`,
   `org.example.report`), rather than the `expense_nutshell/` Python package ADR-0001
   proposed.
2. `pom.xml` stays and gains dependencies as they are chosen; `groupId org.example` /
   `artifactId ExpenseInNutshell` are kept as-is unless the owner asks otherwise.
3. Every language-neutral artifact in this package is unaffected and still applies as
   written: [02-data-model.md](../02-data-model.md), the frozen contracts in
   [03-interfaces-and-contracts.md](../03-interfaces-and-contracts.md) (file formats, the
   15-column `transactions.csv`, the error taxonomy, `merchant_key` normalization), the
   categorization ladder, the requirements traceability matrix, and the entire
   [`design/`](../../../design/) package (tokens, screens, the report DOM contract). None of
   these encode a language choice.
4. Three downstream ADRs named Python-specific libraries as their decision and must be
   **re-decided for Java before Slice 3 of the roadmap begins** (Slice 3 is where PDF/CSV
   parsing is actually built):
   - [ADR-0003](ADR-0003-pdf-parsing-strategy.md) (PDF parsing) — `pdfplumber` needs a Java
     replacement. ADR-0001's own survey named Apache PDFBox (text + positions, table
     reconstruction is custom code) and `tabula-java` (heuristic table extraction, less
     controllable per-bank) as the candidates it rejected only because Python was available;
     neither was rejected on technical merit against Java alternatives.
   - [ADR-0004](ADR-0004-tabular-parsing-and-bank-profiles.md) (CSV/Excel + bank profiles) —
     `pandas`/`openpyxl` needs a Java replacement; Apache POI is the named candidate.
   - [ADR-0010](ADR-0010-packaging-and-distribution.md) (packaging/distribution) — the
     venv-bootstrap approach doesn't apply; a fat JAR (`maven-shade-plugin`) or a `jlink`
     custom runtime image needs deciding, weighed against NFR5 (non-programmer usability) and
     the JRE-availability question ADR-0001 raised (R8 in the risk register: "Python not
     present, or blocked, on the user's machine" becomes "JRE not present, or blocked").
   These are **not decided by this ADR** — each needs its own ADR update, tracked as a
   follow-up.
5. OCR: `tess4j` (a JNI wrapper over the same Tesseract binary `pytesseract` shells out to)
   remains the only realistic option and carries the same native-binary dependency and
   optional-install story as ADR-0003 originally described — this doesn't change with the
   language, only the JNI-loading risk ADR-0001 flagged as "strictly more portability
   surface than `pytesseract`" needs owner awareness.
6. [07-implementation-roadmap.md](../07-implementation-roadmap.md)'s Slice 1 scope text
   ("Delete `pom.xml` and `src/main/java`; create `pyproject.toml`...") is now inaccurate and
   needs a follow-up edit: Slice 1 keeps the Maven skeleton and adds dependencies instead of
   replacing the toolchain. NFR4's requirement text ("Python + standard cross-platform
   libraries") also needs an owner-acknowledged reading as non-binding guidance rather than a
   literal constraint, since it names Python inside a numbered requirement in
   `spec.md` — an edit to the spec itself is the owner's call, not this ADR's.

## Alternatives considered

### A. Python 3.11+ per ADR-0001 — rejected (by the owner, not on technical grounds)

ADR-0001's technical case for Python stands unrebutted here — it is not being overturned
because it was wrong, but because the owner values keeping the existing Java investment and
tooling over the library-ecosystem advantage Python offers for R1. See ADR-0001 for the full
argument; nothing in it is superseded on the merits, only on the outcome.

### B. Keep both — Java "shell" calling a Python core — rejected

Same reasoning as ADR-0001 §B: two toolchains, two packaging stories, an IPC boundary, and a
heavier NFR5 setup burden, all to get Python's PDF ecosystem without fully committing to it.
Not worth it for a tool this size.

### C. Kotlin on the JVM instead of Java — not considered

Not raised by the owner and not evaluated; Java 17 on the existing skeleton is the stated
preference.

## Consequences

**Positive**

- The existing `pom.xml` / Java 17 skeleton is preserved — no repository identity is
  discarded, and Slice 1 starts from a working `mvn compile` instead of a toolchain swap.
- Developer familiarity and JVM tooling (debuggers, profilers, IDE support) apply directly.
- CLAUDE.md's Java "Commands" section required no rewrite beyond the note this ADR
  formalizes.

**Negative / accepted costs**

- **R1 (bank format diversity), the top-ranked risk, gets less mature tooling.** Java's
  PDF-table and Excel ecosystems are real but comparatively thinner than Python's
  `pdfplumber`/`pandas`/`openpyxl`. This was ADR-0001's central objection and is not
  mitigated by this ADR — it is accepted as a cost of the owner's choice. The mitigations
  already in [06-risks-and-open-questions.md](../06-risks-and-open-questions.md) R1
  (declarative bank profiles, interactive column-mapping escape hatch, per-file failure
  isolation) apply regardless of language and remain the primary defense.
- ADR-0003, ADR-0004 and ADR-0010 are now stale and must each be re-decided for Java before
  Slice 3 can begin — tracked as open follow-up work, not resolved here.
- NFR4's "(Python + standard cross-platform libraries)" parenthetical in `spec.md` no longer
  matches the implementation; a spec addendum is recommended but left to the owner.
- The roadmap's Slice 1 scope line needs a follow-up edit to stop instructing deletion of the
  Maven skeleton.

**Neutral**

- Every other ADR in the package (0002, 0005–0009, 0011–0013) is language-neutral and
  requires no change.

## Verification

- Slice 1 exit criteria are revised to: `pom.xml` and `src/main/java` **retained**, `mvn
  compile` succeeds, and CLAUDE.md's Java commands work, on Windows, macOS and Linux (NFR4,
  read as "same codebase," independent of the Python parenthetical).
- Before Slice 3 begins: ADR-0003, ADR-0004 and ADR-0010 each have a Java-specific
  successor decision recorded.
