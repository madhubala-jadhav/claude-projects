# ADR-0010 — Packaging: a bundled virtualenv behind a double-clickable launcher

**Status:** Superseded (distribution mechanism only) by
[ADR-0017](ADR-0017-packaging-java-fat-jar.md), which re-picks the venv/pip model as a
Maven-built fat JAR for Java. FR23/NFR5's requirements and the two-tier
setup-script/monthly-launcher shape are kept; only how dependencies get onto the user's
machine changes.
**Date:** 2026-08-26
**Superseded by:** [ADR-0017](ADR-0017-packaging-java-fat-jar.md) (2026-08-28)

---

## Context

- **FR23:** runnable with a **single command / double-click action** per month, no code
  changes for routine use.
- **NFR5:** a non-programmer must be able to run the monthly workflow *"without reading
  source code, after a one-time setup."* Note the explicit allowance for a one-time setup —
  the spec is not demanding a zero-install experience.
- **NFR4:** Windows, macOS and Linux from one codebase.
- **§5:** the persona is *"comfortable running a local script (e.g., double-clicking a
  `.bat`/`.command` file or running one command); not necessarily a programmer."* The spec
  literally names the artifact.

The complication is [ADR-0003](ADR-0003-pdf-parsing-strategy.md)'s OCR fallback: `pytesseract`
is a thin wrapper around the **native Tesseract binary**, and `pdf2image` wraps **Poppler**.
Neither can be bundled by any Python packaging tool. So no packaging choice can deliver a
truly single-artifact install that includes OCR.

## Decision

**A two-tier distribution: a one-time `setup` script that creates a local virtualenv, and a
`run` launcher the user double-clicks every month. OCR dependencies are optional and
checked for, never assumed.**

```
ExpenseInNutshell/
├─ setup.bat  / setup.command  / setup.sh     run once
├─ run.bat    / run.command    / run.sh       run monthly  (FR23)
├─ pyproject.toml
├─ expense_nutshell/
├─ config/   input/   output/   archive/
└─ .venv/                                     created by setup
```

### `setup.*` — the one-time setup NFR5 permits

1. Locate a Python ≥ 3.11 (`py -3` on Windows, `python3` elsewhere). If none, print a
   direct download link and the two clicks required, then stop — **not** a stack trace.
2. `python -m venv .venv`
3. `.venv/bin/pip install -e .` (pinned versions from `pyproject.toml`)
4. `python -m expense_nutshell --init` → write default `config.yaml`, `categories.yaml`
   (the 12 FR10 categories), `bank_profiles.yaml`, `accounts.yaml`, and create
   `input/`, `output/`, `archive/`.
5. **Probe optional OCR**: is `tesseract` on PATH? is Poppler available? Print a clear
   "OCR available ✓ / not installed — scanned PDFs will be skipped, install instructions:
   …" — an informational result, never a failure.
6. Print exactly what to do next: *"Put your statements in `<abs path>/input` and
   double-click `run`."*

### `run.*` — the monthly action (FR23)

A three-line script: activate `.venv`, `python -m expense_nutshell "$@"`, and on **any**
non-zero exit keep the window open (`pause` / `read`) so a double-clicking user actually
sees the message. Zero required arguments.

### Dependency tiers

| Tier | Packages | If missing |
|---|---|---|
| **Core** (required) | `pdfplumber`, `openpyxl`, `Jinja2`, `PyYAML` | setup fails loudly |
| **OCR** (optional extra `[ocr]`) | `pytesseract`, `pdf2image` + native Tesseract + Poppler | FR3 degrades: scanned PDFs → `PARSE-202` with an install hint (NFR3) |
| **Legacy Excel** (optional `[xls]`) | `xlrd>=2.0` | `.xls` → `PARSE-206` "re-save as .xlsx or .csv" |
| **Dev** | `pytest`, `ruff`, `mypy` | not shipped |

`.venv/` is local to the project folder, so the tool never touches the system Python and
uninstalling is deleting one directory.

## Alternatives considered

### A. PyInstaller / Nuitka single executable — **rejected for v1, kept in reserve**

*Attraction:* the best possible NFR5 story — no Python install at all, one file to
double-click.

*Why rejected now:*
- **It cannot bundle Tesseract or Poppler**, so the OCR caveat survives anyway. The headline
  benefit is real but partial.
- **Three build machines.** A one-file build must be produced on each of Windows, macOS and
  Linux; cross-compilation is not supported. That is a CI pipeline before the product has
  users, and it directly taxes NFR4.
- **macOS notarization.** An unsigned binary is blocked by Gatekeeper with a message that
  reads like malware. Signing needs a paid Apple Developer account.
- **Antivirus false positives** on unsigned PyInstaller executables on Windows are common
  and hard to explain to the persona in §5.
- **80–150 MB per platform**, versus a repository plus a venv the user creates.

**Revisit when** there are real non-technical users beyond the owner. It is purely additive —
the `expense_nutshell` package and every contract stay identical, so this is a distribution
change, not an architecture change.

### B. `pipx install expense-nutshell` from PyPI — **rejected as primary**

Clean and idiomatic, and worth publishing as a secondary channel for technical users. But it
requires `pipx` (another install), it puts the workspace somewhere the user must then find,
and "run `pipx install`" is not the `.bat`/`.command` the spec's persona was promised.

### C. Global `pip install .` into the system Python — **rejected**

Dependency conflicts with whatever else the user has, needs admin on some systems, and is
externally-managed-environment-blocked on modern Linux and macOS. A local venv is strictly
safer.

### D. Docker — **rejected**

Solves reproducibility perfectly and portability not at all for this persona: it requires
Docker Desktop, volume mounts for `input/`/`output/`, and it cannot open the host's browser
for FR16. Wrong tool for a personal desktop utility.

### E. A `.zip` with vendored wheels and no venv (`sys.path` manipulation) — **rejected**

Avoids the venv step but breaks on any package with compiled extensions and produces baffling
import errors. The venv step is one double-click.

## Consequences

**Positive**

- FR23 satisfied exactly as §5 describes: double-click `run`.
- NFR5's "one-time setup" is used honestly, and that setup is one double-click too.
- NFR4 satisfied with no per-platform build pipeline — the same repository works everywhere.
- Optional OCR keeps the core install small and dependency-light.
- Trivial uninstall; no system-Python pollution.
- Upgrades are `git pull` + re-run setup.

**Negative / accepted costs**

- **Python must exist on the machine.** This is the real cost of deferring PyInstaller.
  Mitigated by detecting its absence and giving a direct download link instead of an error.
  Windows 11 makes this a single `winget`/Store step.
- **OCR requires two native installs.** Unavoidable in any packaging model; mitigated by
  making it optional, probing for it at setup, and degrading FR3 with an actionable message.
- The user sees a `.venv/` folder they should not touch. Documented; harmless.
- Corporate machines may block script execution (PowerShell policy, macOS quarantine on
  `.command`). The README documents the one-time unblock for each.
- No auto-update. Appropriate for a local, private tool — an auto-updater would need network
  access, against the spirit of NFR1.
