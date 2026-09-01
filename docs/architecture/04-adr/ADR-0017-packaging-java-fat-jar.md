# ADR-0017 — Packaging in Java: a Maven-built fat JAR behind a double-clickable launcher

**Status:** Accepted
**Date:** 2026-08-28
**Deciders:** Project owner, following [ADR-0014](ADR-0014-language-and-runtime-java-override.md)'s Java decision
**Supersedes:** [ADR-0010](ADR-0010-packaging-and-distribution.md) (distribution mechanism only — the FR23/NFR5 requirements it satisfies, and the shape of the answer, are kept, see below)

---

## Context

FR23, NFR5, NFR4 and §5's persona description are unchanged by the language choice and
restated here only for reference — see [ADR-0010](ADR-0010-packaging-and-distribution.md)'s
Context for the full requirement text. The complication ADR-0010 opened with —
[ADR-0003](ADR-0003-pdf-parsing-strategy.md)'s OCR fallback needing a native binary no
packaging tool can bundle — is now smaller, not gone:
[ADR-0016](ADR-0016-pdf-parsing-java-pdfbox-tess4j.md) replaced `pdf2image`'s Poppler
dependency with PDFBox's built-in `PDFRenderer`, so **Tesseract is now the only unbundleable
native dependency**, down from two.

ADR-0010's actual decision — a two-tier "one-time setup script + monthly double-click
launcher" — was shaped by Python's runtime model (an interpreter that needs a venv and a
`pip install` step). Java's runtime model is different in a way that matters here: a Maven
build already produces one self-contained artifact (a fat JAR) at **build time**, done once
by the maintainer, not by the end user. There is no Java equivalent of "the user's machine
resolves and installs dependencies" — that step disappears entirely.

## Decision

**A single fat JAR built by Maven (`maven-shade-plugin`) and published with each release,
launched by a `run.bat`/`run.command`/`run.sh` script that calls `java -jar`. A lightweight
`setup.*` checks for a JRE ≥ 17 and bootstraps default config, exactly mirroring ADR-0010's
role for that script but doing far less work. `jlink`/`jpackage` self-contained runtime
images are kept in reserve for a later single-executable release, the same role ADR-0010 gave
PyInstaller/Nuitka.**

```
ExpenseInNutshell/
├─ setup.bat  / setup.command  / setup.sh     run once — checks JRE, runs --init
├─ run.bat    / run.command    / run.sh       run monthly (FR23) — java -jar ExpenseInNutshell.jar "$@"
├─ pom.xml
├─ src/main/java/org/example/...
├─ config/   input/   output/   archive/
└─ ExpenseInNutshell.jar                      built by the maintainer (`mvn package`), shipped in the release — the end user never runs Maven
```

### `setup.*` — the one-time setup NFR5 permits

1. Locate a JRE ≥ 11.0.17 (`java -version` parse, or check `JAVA_HOME`). If none, print a
   direct download link (Eclipse Temurin) and the two clicks required, then stop — **not** a
   stack trace. Same failure-mode discipline as ADR-0010 step 1.
2. Run `java -jar ExpenseInNutshell.jar --init` → write default `config.yaml`,
   `categories.yaml` (the 12 FR10 categories), `bank_profiles.yaml`, `accounts.yaml`, and
   create `input/`, `output/`, `archive/`. Identical in effect to ADR-0010 step 4.
3. **Probe optional OCR**: is the native Tesseract library discoverable (on `PATH`, or via a
   configured library path tess4j can load)? Print "OCR available ✓ / not installed —
   scanned PDFs will be skipped, install instructions: …" — informational, never a failure.
   Same as ADR-0010 step 5, minus the now-unnecessary Poppler probe.
4. Print exactly what to do next, same wording as ADR-0010.

**There is no dependency-install step.** ADR-0010's `python -m venv .venv` +
`pip install -e .` has no counterpart: the fat JAR already contains PDFBox, Apache POI,
Commons CSV and every other Core dependency, compiled and bundled at release time. `setup.*`
is structurally simpler than its Python predecessor because of this, not because less is
being verified.

### `run.*` — the monthly action (FR23)

Same three-line shape as ADR-0010: `java -jar ExpenseInNutshell.jar "$@"`, and on **any**
non-zero exit keep the window open (`pause` / `read`) so a double-clicking user sees the
message. Zero required arguments.

### Dependency tiers

| Tier | Libraries | If missing |
|---|---|---|
| **Core** (bundled in the fat JAR — nothing to install) | Apache PDFBox, Apache POI, Apache Commons CSV, plus the templating and YAML-config libraries chosen by C8/C2's own Java follow-up ADRs (not decided here) | N/A — always present once the JAR runs |
| **OCR** (external, cannot be bundled) | tess4j is bundled; the native Tesseract binary + `tessdata` language files are not | FR3 degrades: scanned PDFs → `PARSE-202` with an install hint (NFR3) |
| **Dev** | JUnit 5, a static-analysis tool (Checkstyle/SpotBugs/PMD — the rough equivalent of `ruff`/`mypy`) | not shipped |

The **Legacy Excel tier from ADR-0010 disappears entirely.** ADR-0015's Apache POI choice
handles `.xls` unconditionally in the same dependency as `.xlsx`, so there is no Java
counterpart to the Python `[xls]` optional extra.

## Alternatives considered

### A. `jlink` + `jpackage` self-contained runtime image — rejected for v1, kept in reserve

*Attraction:* the closest Java analog to ADR-0010 §A's PyInstaller pitch — a bundled minimal
JRE plus the app as one native installer (`.exe`/`.msi`, `.dmg`, `.deb`/`.rpm`/AppImage), so
the user needs no JRE at all. Unlike PyInstaller, `jpackage` is a **first-party JDK tool**
(since JDK 14), not a third-party heuristic packager, so it doesn't carry PyInstaller's
antivirus-false-positive reputation — that specific ADR-0010 §A objection does not transfer.

*Why still rejected for v1:*
- **It cannot bundle Tesseract**, same as PyInstaller couldn't bundle Tesseract/Poppler — the
  OCR caveat survives regardless of packaging tool, because it is a native-binary problem, not
  a language-runtime problem.
- **Three build machines**, unchanged from ADR-0010 §A: `jlink` images are platform-specific
  and not cross-compilable, so CI needs a build job per OS before the product has users.
- **macOS notarization and Windows SmartScreen** still apply to an unsigned native installer,
  identical to ADR-0010 §A's Gatekeeper/antivirus concerns.
- **Tens of MB per platform** (a trimmed custom JRE plus the app), versus a fat JAR that needs
  a JRE the user installs once. Same size trade-off ADR-0010 §A weighed, just with different
  absolute numbers.

**Revisit when** there are real non-technical users beyond the owner — the exact condition
ADR-0010 §A named. Purely additive: `org.example`'s package structure and every contract stay
identical, so this is a distribution change, not an architecture change, and it is a *safer*
bet than PyInstaller was, since `jpackage` is officially maintained as part of the JDK.

### B. `jpackage` without a trimmed `jlink` image (bundle a full JRE) — rejected

Strictly worse than a proper `jlink` custom runtime: larger for no benefit. Folded into
alternative A's rejection rather than treated separately.

### C. An unshaded fat-jar-adjacent layout — dependencies as loose JARs in a `lib/` folder next to a thin JAR with a `Class-Path` manifest entry — rejected

Avoids the shade/relocate build step, but reintroduces exactly the path-resolution fragility
across three OSes that ADR-0010 §E rejected a vendored-wheels `.zip` for: relative
`Class-Path` entries behave inconsistently across shells and mounted-drive scenarios. One
true fat JAR that `java -jar` can run standalone is simpler and more robust for a
double-click launcher.

### D. Docker — rejected, same reasoning as ADR-0010 §D

Unchanged by language: needs Docker Desktop, can't open the host browser for FR16, wrong tool
for a personal desktop utility.

### E. OS-native package managers (a `winget` manifest, a Homebrew formula, an `apt`/`.deb`) as the primary channel — rejected as primary, same reasoning as ADR-0010 §B's `pipx` rejection

Each requires the user to already know that specific tool, and isn't the `.bat`/`.command`
double-click §5's persona was promised. Worth offering as a secondary channel later, same
treatment ADR-0010 gave `pipx`/PyPI publishing.

## Consequences

**Positive**

- FR23 satisfied identically to ADR-0010: double-click `run`.
- NFR5's one-time setup is **simpler than the Python version** — no local dependency
  resolution/installation step exists to fail or need network access; `setup.*` only checks
  for a JRE and bootstraps config files.
- NFR4 satisfied with no per-platform build pipeline for v1: bytecode portability means the
  *same* `ExpenseInNutshell.jar` runs unmodified on Windows, macOS and Linux with a JRE —
  arguably a stronger version of ADR-0010's "same codebase everywhere" property, since even
  the build artifact itself (not just the source) is platform-independent.
- **The end user's machine never contacts a package repository.** The JAR ships prebuilt in
  the release; ADR-0010's `pip install -e .` needed PyPI access at setup time. A small
  improvement adjacent to NFR1's spirit, even though NFR1 is specifically about statement
  data, not build tooling.
- The optional `[xls]` tier vanishes (ADR-0015), one less thing for `setup.*` to probe and
  explain.
- `jlink`/`jpackage` remains a credible, officially-supported upgrade path if a true
  single-executable experience is wanted later — a stronger reserve option than PyInstaller
  was, precisely because it ships with the JDK rather than being a third-party tool.

**Negative / accepted costs**

- **A JRE ≥ 11.0.17 must exist on the machine.** Direct analog of ADR-0010's "Python must
  exist" cost — this is R8 in [06-risks-and-open-questions.md](../06-risks-and-open-questions.md),
  restated for Java as flagged in [ADR-0014](ADR-0014-language-and-runtime-java-override.md).
  Mitigated identically: detect absence, print a direct Temurin download link, never a stack
  trace.
- **OCR still requires one native install** (Tesseract) — down from two (Tesseract + Poppler)
  thanks to [ADR-0016](ADR-0016-pdf-parsing-java-pdfbox-tess4j.md), but not zero. Mitigated
  the same way ADR-0010 mitigated it: optional, probed at setup, degrades FR3 with an
  actionable message (`PARSE-202`).
- **The maintainer must build and publish the fat JAR per release** (`mvn package` with the
  shade plugin, in CI). This step has no Python analog — ADR-0010's equivalent work happened
  on the *user's* machine at setup time instead. Mitigated: this is one CI job producing one
  portable artifact, not a per-OS build, so it does not reintroduce the "three build
  machines" cost alternative A was rejected for.
- Corporate machines may still block script execution (PowerShell policy, macOS quarantine on
  `.command`) — unchanged from ADR-0010; same README mitigation.
- No auto-update — unchanged from ADR-0010, same NFR1-adjacent reasoning (an updater needs
  network access).

## Verification

- On a clean machine with a JRE ≥ 11.0.17 and nothing else installed: `setup` completes
  (writes default config files, reports OCR availability), then `run` with a file in
  `input/` produces a report. Verified on Windows, macOS and Linux (NFR4).
- On a machine with no JRE: `setup` prints a direct download link and stops; no stack trace.
- CI's `mvn package` produces exactly one fat JAR per release, and that artifact — unmodified
  — is what `run.*` launches; its checksum is recorded in the release notes.
- With the native Tesseract library absent: `setup` reports OCR unavailable as an
  informational result, and a run against a scanned-PDF fixture completes with exit code 0,
  the file excluded and named with `PARSE-202`.
