# ExpenseInNutshell

A local, offline **monthly expense summary tool**. Drop your bank and card statements into a
folder, run one command, and get a single self-contained HTML report: spend by category, a pie
chart, your largest category stated in words, drill-downs into every transaction, and a
correction loop that teaches the tool your merchants.

**Nothing leaves your machine.** No network calls, no cloud, no telemetry — the report is one
HTML file with the chart library inlined, and the build fails if anything in it points at a
URL. Your statements and the reports made from them are gitignored.

- **Spec:** [`src/main/resources/specs/spec.md`](src/main/resources/specs/spec.md)
- **Architecture:** [`docs/architecture/`](docs/architecture/) — components, data model,
  contracts, 20 ADRs, traceability, roadmap
- **Design:** [`design/`](design/) — tokens, 8 screen specs, Figma plugin generator
- **Working notes for Claude Code:** [`CLAUDE.md`](CLAUDE.md)

---

## Quick start

```bash
mvn package          # builds ExpenseInNutshell.jar at the repo root
```

Put statements in `input/`, then:

| Platform | Command |
|---|---|
| Windows | `.\run.bat` |
| macOS | `./run.command` |
| Linux | `./run.sh` |

Or directly, from the repo root:

```bash
java -jar ExpenseInNutshell.jar
```

It reads everything in `input/`, writes `output/<YYYY-MM>/report.html`, and opens it in your
browser. Alongside the report you get `transactions.csv` (the full categorized list) and
`run-log.txt` (what happened, including every file and row that was skipped and why).

### Options

Zero arguments are required. What is implemented today:

| Flag | Effect |
|---|---|
| `--input <dir>` | folder to read statements from (default `input/`) |
| `--output <dir>` | folder to write reports into (default `output/`) |
| `--config <dir>` | folder holding the YAML config (default `config/`) |
| `--no-open` | write the report but don't launch a browser |

`--month`, `--dry-run`, `--verbose`, `--version` and `--no-ocr` are specified but not yet
built; an unrecognised flag prints a warning and the run continues.

### Supported statements

PDF (text-based), CSV, and `.xlsx`. Legacy `.xls` is unsupported by design — re-save it as
`.xlsx`. Scanned PDFs need OCR, which is not built yet; they are skipped with `PARSE-202` and
an install hint. Encrypted PDFs prompt once for a password.

A file the tool cannot read never stops the run: it is listed by name, with a reason, in both
the report and `run-log.txt`.

---

## How it works

```
input/*.pdf|csv|xlsx
   │
   ├─ C3 discovery ──── also harvests any corrections-YYYY-MM.json you dropped in,
   │                    so a correction applies to *this* run, not next month's
   ├─ C4 parse ─────── PDF text with column geometry derived per page; CSV/Excel with
   │                    encoding, delimiter and header sniffing; bank profiles
   ├─ C5 normalize ─── one Transaction shape, BigDecimal money, merchant_key, dedupe
   ├─ C6 categorize ── a five-level ladder: pinned transaction → merchant correction →
   │                    categories.yaml overrides → keyword/pattern rules → Uncategorized
   ├─ C7 analysis ──── totals, spend by category, top category, largest transactions
   └─ C8 report ────── one self-contained HTML file + transactions.csv + run-log.txt
```

Anything the tool is unsure about is shown rather than hidden: uncategorized spend is counted
in a tile, hatched in the chart, and listed in a review table where you can fix it.

### The correction loop

In the report, every uncategorized row has a category dropdown. Pick one and the report tells
you what the correction will be remembered against (the *merchant key*), lets you choose
whether it applies to that one transaction or every future purchase from that merchant, and
recalculates the chart and tables immediately so you see the corrected picture before
committing.

"Save corrections" downloads `corrections-YYYY-MM.json`. Move it into `input/` and run again —
the tool absorbs it, archives the patch, and remembers the mapping from then on.

---

## Configuration

The first run creates `config/` with commented YAML you are meant to hand-edit:

| File | What it holds |
|---|---|
| `categories.yaml` | your categories, their keywords/patterns, chart slots, and exact merchant overrides |
| `accounts.yaml` | account naming and income rules |
| `config.yaml` | currency, chart limits, general settings |
| `bank_profiles.overlay.yaml` | your own bank layouts, layered on top of the built-in ones |
| `corrections.json` | written by the tool from your saved corrections; not meant to be edited by hand |

**Bank profiles are an overlay, not a copy.** The shipped profiles live in the build; your file
only adds to or replaces them. Reusing a shipped `name` replaces it *and warns*, so a stale
copy is visible instead of silently pinning you to an old profile. `disable_shipped_profiles:
[name, ...]` switches one off.

The other three files are yours. A `schema_version` older than the build's is warned about,
never silently migrated — a round-trip through the parser would destroy the comments that
document how to edit them. Unknown top-level keys warn (`CFG-005`) rather than vanishing.

---

## Repository layout

```
src/main/java/org/example/
  cli/         C1  entry point and the run loop
  config/      C2  YAML loading, the built-in/overlay merge, workspace bootstrap
  ingest/      C3  file discovery, correction-patch harvesting
  parse/       C4  PDF text extraction, CSV/Excel parsing, interactive column mapping
  normalize/   C5  the Transaction shape, merchant keys, deduplication
  categorize/  C6  the L1–L5 ladder, corrections store, patch validation
  analysis/    C7  totals, per-category spend, top category, largest transactions
  report/      C8  Pebble rendering, chart model, design tokens, CSV writer
src/main/resources/
  default-config/  the YAML a fresh workspace is bootstrapped from
  templates/       report.peb, report.css, report.js — all inlined into the output
  assets/          vendored Chart.js (MIT) + licence
src/test/java/...  mirrors the main tree; report/e2e/ holds the browser tests
docs/architecture/ the binding design decisions
design/            tokens, screen specs, Figma plugin
config/ input/ output/ archive/   yours, and gitignored
```

---

## Development

```bash
mvn compile          # build
mvn test             # run the suite
mvn verify           # what CI runs — tests plus the privacy/XXE/no-external-reference checks
mvn package          # fat JAR at the repo root
```

**199 tests, 2 skipped.** One skip is a permission-denied fault injection that cannot be forced
on every platform; the other is a known chart-sizing defect whose test is written and disabled
with the diagnosis in its `@Disabled` reason.

### Browser tests

`src/test/java/org/example/report/e2e/` drives the rendered report in real Chromium via
Playwright — the chart actually instantiating and drawing, drill-downs, the corrections loop
end to end including the downloaded patch file, and a runtime proof that the open report makes
no network request at all.

They **skip themselves** when no browser is installed, so a fresh clone is never blocked.
Install one with:

```bash
mvn compile exec:java -Dexec.mainClass=com.microsoft.playwright.CLI \
  -Dexec.classpathScope=test -Dexec.args="install chromium"
```

CI installs the browser and then runs `mvn verify -De2e.strict=true`, which turns that skip
back into a failure so the browser half can never go quietly unrun.

### Test report

Playwright's Java binding has no `show-report` of its own; the equivalent is the Surefire HTML
report.

```bash
# the whole suite
mvn test
mvn surefire-report:report-only

# just the browser tests
mvn test -Dtest='*E2ETest' -De2e.strict=true
mvn surefire-report:report-only -DoutputName=playwright-report
```

Output lands in `target/site/` — open `surefire-report.html` or `playwright-report.html`.

### CI

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs `mvn verify` on Ubuntu, Windows and
macOS against Temurin 17, with `fail-fast` off so every OS reports — "broke on Windows only" and
"broke everywhere" are different investigations. It then runs the shaded jar against an empty
input folder, proving the artifact you are actually handed starts and still writes a report.

---

## Working with Claude Code on this project

This repo is set up for [Claude Code](https://claude.com/claude-code). Two things do the work:

### 1. `CLAUDE.md` — always-loaded context

[`CLAUDE.md`](CLAUDE.md) is read automatically at the start of every session. It carries the
current state: which roadmap slices are done, what each one added, the traps already
discovered, and what is deliberately not built yet. Keep it current — it is the difference
between an agent that continues the work and one that re-derives it.

### 2. `.claude/agents/` — the project's specialist agents

Agent definitions are Markdown files with YAML frontmatter (`name`, `description`, `tools`,
`model`) followed by the agent's instructions.

| Agent | Model | Use it for |
|---|---|---|
| **`solution-architect`** | opus | Turning a spec into an architecture package (C4 diagrams, component contracts, data model, ADRs, FR/NFR traceability, roadmap) **and** a Figma design package (tokens, screen specs, a runnable Figma plugin). It does not write application code. |
| **`java-implementer`** | sonnet | Building one roadmap slice at a time, strictly against `docs/architecture/` and `design/`. It does not make architecture decisions — if the ADRs and roadmap do not already answer a question, it stops and reports the gap rather than guessing. |
| **`playwright-tester`** | sonnet | Writing and maintaining the browser-level tests in `src/test/java/org/example/report/e2e/`. It does not change production code — a defect it finds is reported with evidence and pinned by a disabled test, never patched or asserted away. |

**How to invoke one.** Just ask for it by name in the Claude Code prompt:

```
Use the java-implementer agent to implement Slice 6.
```

```
Have the solution-architect turn src/main/resources/specs/spec.md into an
architecture and design package.
```

```
Use the playwright-tester agent to cover FR14's month-over-month panel in the browser.
```

Claude will also reach for them on its own when a request obviously matches — "implement slice
N", "build the CSV parser", "architect this spec", "create the Figma designs".

**The division of labour matters.** The architect decides; the implementer builds what was
decided. When the implementer hits something the architecture does not cover, the right move
is a new ADR from the architect, not an improvised choice in the code. That is why the
implementer is instructed to escalate instead of guessing.

### Conventions both agents follow

- **Windows absolute paths** for every file operation (`C:\...\pom.xml`), never relative or
  `/c/...` style.
- **The architecture documents are binding.** `docs/architecture/` and `design/screens/` are as
  binding as a compiler error; ADRs 0014–0020 supersede their pre-Java originals **for library
  choices only** — everything else in the originals still holds.
- **`design/design-system/tokens.json` is the only source of colour and spacing.** It is
  packaged into the jar and turned into CSS custom properties at render time, so the report and
  the Figma file cannot drift. Nothing hard-codes a palette value.
- **No real financial data in the repository.** Test PDFs are generated at test time; the
  regression test against the owner's real statement reads its filename and expected totals
  from a gitignored properties file and skips wherever that file is absent.

### Adding your own agent

Drop a new `.claude/agents/<name>.md` with the same frontmatter shape. Keep `description`
concrete about *when* to use it — that text is what Claude matches a request against. Give it
the narrowest `tools` list that lets it finish the job.

### Permissions

`.claude/settings.local.json` holds pre-approved commands so routine builds don't prompt every
time. Add to the `permissions.allow` list as you find yourself approving the same command
repeatedly.

---

## Status

Slices 1–5 are done: CSV/PDF/Excel parsing with real bank profiles, resilient degradation, the
full dashboard with chart and drill-downs, and the corrections loop closed end to end.

| Slice | | |
|---|---|---|
| 1 | Walking skeleton: CSV in, HTML out | ✅ |
| 2 | Resilience: degrade, never crash | ✅ |
| 3 | Real statements: PDF, bank profiles, multi-file merge | ✅ |
| 4 | The answer: chart, callout, visible accuracy gaps | ✅ |
| 5 | Learning: the corrections loop | ✅ |
| 6 | Memory: month-over-month | next |
| 7 | Hard cases: OCR, transfers, currency | |
| 8 | Handover: packaging, docs, first-run experience | |

Three sections of the report render their documented *"unavailable"* state rather than being
hidden, because their data belongs to a later slice: the month-over-month comparison and the
"vs last month" column (Slice 6), and the transfers note (Slice 7). That is the screen behaving
as specified on a first run, not a stub.

See [`docs/architecture/07-implementation-roadmap.md`](docs/architecture/07-implementation-roadmap.md)
for what each remaining slice contains.
