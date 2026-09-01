# ADR-0001 — Language and runtime: Python 3.11+, retiring the Java skeleton

**Status:** Superseded by [ADR-0014](ADR-0014-language-and-runtime-java-override.md) — the
owner chose to keep Java. The analysis below is preserved as-is for reference; only the
outcome changed, not the technical argument.
**Date:** 2026-08-26
**Deciders:** Solution architect (pending owner confirmation — this ADR discards existing repo scaffolding)
**Supersedes:** the implicit choice embodied in `pom.xml`
**Superseded by:** [ADR-0014](ADR-0014-language-and-runtime-java-override.md) (2026-08-28)

---

## Context

There is a direct conflict between what the repository currently is and what the spec asks
for, and it has to be settled before any other decision is meaningful.

**What the repository is today.** A completely unmodified IntelliJ IDEA Java project
template:

- `pom.xml` — `groupId org.example`, `artifactId ExpenseInNutshell`, `1.0-SNAPSHOT`,
  `maven.compiler.source/target = 17`, **zero dependencies**, zero plugins.
- `src/main/java/org/example/Main.java` — IDE-generated sample code (the "Hello and
  welcome!" printout and a counting loop). No domain logic of any kind.
- No tests, no `src/test/java`, no README, no `docs/`.
- `CLAUDE.md` states this plainly: *"There is no actual application logic, no tests, and no
  README yet — the real architecture has not been built."*

**What the spec asks for.** §9 proposes Python 3.11+ with `pdfplumber`, `pytesseract` +
`pdf2image`, `pandas`/`openpyxl`, `Jinja2`, `PyYAML`, and Plotly or Chart.js. It softens
this: *"This is a suggestion, not a mandate — any stack meeting the functional/non-functional
requirements is acceptable."*

**But NFR4 is not a suggestion.** It reads:

> NFR4 (Portability): THE SYSTEM SHALL run on Windows, macOS, and Linux with the same
> codebase (**Python + standard cross-platform libraries**).

The stack named in §9 is advisory; the same stack named inside a numbered non-functional
requirement is not. A deviation to Java would have to be justified *against NFR4 as
written*, and there is no functional or non-functional requirement that Java serves better.

So the real question is narrow: **is the existing Java skeleton a reason to deviate?**

## Decision

**Build the tool in Python 3.11+, as the spec's NFR4 requires. Retire the Java skeleton
rather than keep both.**

Concretely:

1. The implementation lives in a `expense_nutshell/` Python package at the repository root,
   with `pyproject.toml` as the project manifest.
2. `pom.xml` and `src/main/java/org/example/Main.java` are **deleted** in implementation
   slice 1, not left in place.
3. `src/main/resources/specs/spec.md` — the spec itself, which currently sits inside the
   Maven directory convention — moves to `docs/spec.md`, with the original path left as a
   pointer for one release so existing links do not rot.
4. `CLAUDE.md` is rewritten in the same slice to describe the Python layout, since its
   current "Commands" section (`mvn compile`, `mvn test`) will be actively misleading.
5. Baseline: **Python 3.11+**. 3.11 gives `tomllib`, `datetime.UTC`, exception groups, and
   the `Self` type; more practically it is the oldest version still receiving security
   fixes for the lifetime of this tool. Upper bound is left open, but the dependency set is
   pinned in `pyproject.toml`.

## Alternatives considered

### A. Java 17 on the existing Maven skeleton — **rejected**

*What it would look like:* Apache PDFBox or `tabula-java` for PDF, Apache POI for
CSV/Excel, `tess4j` (a JNI wrapper over the same Tesseract binary) for OCR, SnakeYAML for
config, Thymeleaf or Freemarker for HTML, and a fat JAR via `maven-shade-plugin`.

*Why rejected:*

- **It contradicts NFR4's parenthetical**, and nothing in FR1–FR23 or NFR1–NFR7 is served
  better by the JVM. Deviating from a written NFR needs a requirement-based argument; there
  isn't one.
- **The sunk cost is genuinely zero.** The skeleton contains no business logic, no tests, no
  dependencies and no plugins. Keeping it preserves nothing but a directory shape. Weighing
  a 15-line generated `Main.java` against a whole architecture would be the sunk-cost
  fallacy in its purest form.
- **NFR5 (non-programmer usability) is worse.** A JAR needs a JRE the user must install and
  keep current; `java -jar` from a `.bat` is not obviously easier than a bundled venv, and
  JRE version mismatches produce error messages far more hostile than Python's.
- **The library gap is real, and it is exactly where this project's risk lives.**
  `pdfplumber` gives word-level bounding boxes and a table extractor tuned for exactly the
  "Date | Narration | Withdrawal | Deposit | Balance" layouts the spec names in §11.1.
  PDFBox gives text and positions; the table reconstruction is code you write yourself.
  `tabula-java` is closer but is heuristic and less controllable per-bank. Since bank-format
  diversity is the top risk in this product (R1 in
  [06](../06-risks-and-open-questions.md)), spending the risk budget on a weaker extraction
  library is a bad trade.
- **OCR is a wash at best.** `tess4j` still requires the native Tesseract binary, plus JNI
  loading problems that vary by OS — strictly more portability surface than `pytesseract`,
  which shells out to the same binary.

### B. Keep both — Java "shell" calling a Python core — **rejected**

Two toolchains, two dependency managers, two packaging stories, and an IPC boundary, all to
avoid deleting a file that prints "Hello and welcome!". It doubles the setup burden on a
user NFR5 describes as "not necessarily a programmer".

### C. TypeScript/Node — **rejected**

Node ships neither a credible PDF *table* extractor nor a mature spreadsheet reader at
`openpyxl`'s level, and would contradict NFR4 just as Java does — with none of Java's
"it's already here" argument.

### D. Rust / Go — **rejected**

Single-binary distribution is genuinely attractive for NFR5, and would remove the
interpreter entirely. But the PDF-table and OCR ecosystems are far thinner, and the
spec-named libraries do not exist there. This is the right conversation to have again only
if packaging (ADR-0010) turns out to be the dominant pain point in practice.

### E. Python, but keep `pom.xml` around "just in case" — **rejected**

A `pom.xml` that builds nothing is a trap: it makes the repo look like a Java project to
every tool and every new reader, `mvn test` reports success on zero tests, and CLAUDE.md
keeps documenting commands nobody should run. Delete it, and let git history be the archive.

## Consequences

**Positive**

- The architecture matches the spec's own non-functional requirement instead of arguing
  with it; NFR4 is satisfied by construction.
- The libraries the spec named are available and are the best-in-class options for the
  hardest part of the problem (PDF table extraction).
- Fast iteration on parsing heuristics, which is where most of the real work will be.
- No JRE dependency for the end user (they get a bundled venv instead — ADR-0010).

**Negative / accepted costs**

- **The repository's current identity is discarded.** `pom.xml`, `Main.java` and the
  `src/main/java` tree go away. This is intentional and irreversible-ish (recoverable from
  git history), which is why this ADR is flagged for owner confirmation in
  [06](../06-risks-and-open-questions.md).
- **`CLAUDE.md` becomes wrong the moment this lands** and must be rewritten in the same
  slice. Its own "Notes for future work" section anticipates exactly this: *"this section
  should be replaced once that structure exists."*
- **The spec's own path moves.** `src/main/resources/specs/spec.md` is a Maven convention
  that stops making sense without Maven.
- Python needs an interpreter on the user's machine. ADR-0010 handles this with a
  one-time bootstrap script and keeps PyInstaller in reserve.
- Runtime performance is lower than the JVM's — irrelevant here: NFR2 allows 30 s for ~500
  transactions, and the workload is I/O- and OCR-bound, not CPU-bound.

**Neutral**

- If the owner overrules this and mandates Java, **every other ADR in this package still
  holds.** The pipeline shape, the file formats, the error taxonomy, the categorization
  ladder and the whole design package are language-neutral; only ADR-0003, ADR-0004 and
  ADR-0010 would need their library choices re-picked. That is the practical benefit of
  freezing the interfaces ([03](../03-interfaces-and-contracts.md)) rather than the
  implementation — and it is exactly what spec §9 asked for.

## Verification

- Slice 1 exit criteria include: `pom.xml` and `src/main/java` removed, `pyproject.toml`
  present, `CLAUDE.md` rewritten, and `python -m expense_nutshell --version` working on
  Windows, macOS and Linux (NFR4).
- CI runs the test suite on all three OSes for the supported Python versions.
