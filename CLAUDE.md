# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

**ExpenseInNutshell** is a local, offline Monthly Expense Summary Tool: it parses bank/card
statements (PDF, CSV, Excel) dropped in a folder, categorizes transactions, and generates a
self-contained local HTML report with a spend-by-category chart. The spec is
[`src/main/resources/specs/spec.md`](src/main/resources/specs/spec.md) (Status Draft v1.0,
owner Madhubala Jadhav).

**Architecture and design are complete.** See:

- [`docs/architecture/`](docs/architecture/) — component design, data model, interface
  contracts, 20 ADRs, full requirements traceability, risk register, and the 8-slice
  implementation roadmap
  ([`07-implementation-roadmap.md`](docs/architecture/07-implementation-roadmap.md) is the
  build order to follow).
- [`design/`](design/) — design tokens, 8 screen specs, and a Figma plugin generator. Start
  with [`design/README.md`](design/README.md). A Figma file exists
  (its key is kept out of the repo - see `design/README.md`) with the design-system foundation (variables, text/effect
  styles, 12/17 components) built; screens are not yet built.

**Language: Java 17**, per [ADR-0014](docs/architecture/04-adr/ADR-0014-language-and-runtime-java-override.md)
(the owner overruled the architecture's original Python recommendation, ADR-0001, and kept
the Java/Maven skeleton). Every Java-specific library choice this triggered is resolved:
Apache Commons CSV + POI ([ADR-0015](docs/architecture/04-adr/ADR-0015-tabular-parsing-java-apache-poi.md)),
Apache PDFBox + tess4j ([ADR-0016](docs/architecture/04-adr/ADR-0016-pdf-parsing-java-pdfbox-tess4j.md)),
a Maven fat JAR ([ADR-0017](docs/architecture/04-adr/ADR-0017-packaging-java-fat-jar.md)),
`BigDecimal` money ([ADR-0018](docs/architecture/04-adr/ADR-0018-money-java-bigdecimal.md)),
Pebble templating ([ADR-0019](docs/architecture/04-adr/ADR-0019-report-rendering-java-pebble.md)),
Jackson YAML config ([ADR-0020](docs/architecture/04-adr/ADR-0020-config-parsing-java-jackson-yaml.md)).

See [`docs/architecture/06-risks-and-open-questions.md`](docs/architecture/06-risks-and-open-questions.md)
for assumptions still pending owner confirmation (A1–A6 — bank list, credit-card grouping,
transfer rules, etc.) — none of these block implementation, they refine later slices.

## Implementation status

**Slices 1-5 are done, CI is up, and config upgrades itself.** 147 tests, all passing
on Windows/macOS/Linux
(1 skipped: a permission-denied fault injection that cannot be forced on every platform).

- **Slice 1 - walking skeleton** (`1422adb`): CSV in, HTML report out. FR1 (CSV), FR4, FR7,
  FR8, FR10, FR12, FR16, FR22, FR23 (partial), NFR1, NFR4, **AC8**.
- **Slice 2 - resilience** (`8ed9dff`): error taxonomy, demote-and-continue, fault injection.
  FR5, NFR3, NFR6 (partial), EC5, **AC7**.
- **Slice 3 - real statements**: PDF parsing, full CSV/Excel sniffing, bank profiles,
  multi-file merge, dedupe, `transactions.csv`. FR1 (all types bar `.xls`), FR2, FR4, FR6,
  FR21, EC1, EC2, EC3, §11.1, §11.2, **AC1**, **AC9**.
- **Slice 4 - the answer**: the real S01 dashboard - chart, callout, drill-down, and the
  accuracy gap made visible. FR13, FR15, FR17, FR18, FR19, EC6, NFR6, **AC2**, **AC3**, **AC5**.
- **Slice 5 - learning**: the corrections loop, closed end to end. FR9, FR20, G6, US5, **AC4**.

### What Slice 5 added

The full ladder (ADR-0005): `L1` a transaction-scoped pin by id, `L2` a merchant-scoped
correction by `merchant_key`, `L3` `categories.yaml`'s `overrides:` mapping, then the L4/L5
that already existed. `01-components.md` C6 describes L1/L2 as "exact merchant_key" and
"pattern", which predates ADR-0005's more specific version; **the ADR is what is implemented**,
and the class javadoc says so.

`CorrectionsStore` (atomic write, last-write-wins, superseded values into `history[]`),
`PatchValidator` (whole-patch acceptance per §3.6), and `CorrectionHarvester` (C3) which runs
**before discovery**, so a patch dropped for this run applies to this run — ADR-0006 is explicit
that a loop whose result only shows up next month is one nobody would trust. Applied patches
move to `archive/corrections/`, which is what makes the harvest idempotent.

In the report (S03): a category select on every uncategorized row, the merchant-key line that
tells the user *what the correction will be remembered against*, a merchant/transaction scope
choice, optimistic recalculation of the chart, table and tiles, the sticky tray with a
`beforeunload` guard, and a `Blob` export of `corrections-YYYY-MM.json` followed by the
instruction sheet stating the literal input path.

**One duplication is deliberate and guarded.** The optimistic preview has to recompute slices
client-side, mirroring `ChartModel` in JavaScript. A headless check runs `report.js` against the
real data island and asserts that with no pending changes its slices equal the server-rendered
ones exactly, so drift is caught rather than discovered visually.

**A conflict surfaced and was resolved in favour of ADR-0006.** A Slice-2 test asserted no
absolute path appeared anywhere in the report; ADR-0006 requires the tray to state the literal
input folder ("so there is nothing to look up") and S01's footer states where the report was
saved. Paths the report shows the user are intended; paths inside a *diagnostic string* are what
§13 excludes, because those get pasted into bug reports. The test is now scoped to the
skipped-files panel.

### What Slice 4 added

Chart.js v4.4.4 UMD is **vendored** at `src/main/resources/assets/chart.umd.js` with its MIT
licence beside it, and inlined into every report (ADR-0007: a CDN reference would be "an
outbound request carrying the report's existence to a third party"). The report is now one
self-contained ~260 KB file with zero external references, still asserted before it is written.

`Analysis` gained `topCategory` (FR13/AC3), `topTransactions` (FR15), `needsReviewCount`,
`transfersTotal` and `accounts`; `MonthlySummary` is now the full §1.2 shape. `ChartModel`
applies ADR-0007's pie rules in one place, `SvgPie` is the `WARN-507` fallback, `DesignTokens`
generates the CSS custom properties, and `MinimalReport` is C8's guaranteed-renderable
last resort when the template itself fails.

**Design tokens are generated, never copied.** `design/design-system/tokens.json` is packaged
as a Maven resource (see the `<resources>` block in `pom.xml`) and turned into custom
properties at render time. Nothing in `report.css` hard-codes a palette value, which is
ADR-0007's anti-drift rule: the report and the Figma file cannot disagree about what `slot2`
is. `StylesheetContractTest` enforces the seams - every `var(--x)` resolves, both themes define
the same property set, and the data island carries every field `report.js` reads. Those
failures are all silent ones (a mistyped custom property renders transparent, not red), which
is why they are tested rather than eyeballed.

**A bug worth knowing about, since the rule reads deceptively simply.** ADR-0007 rule 4 caps
the chart at "8 coloured slices + Other". Capping by *rank among all categories* is wrong: two
unslotted categories ranking high pushed Transport (slot 4) and Healthcare (slot 8) into
"Other" while leaving their palette slots unused. The cap counts **coloured slices admitted**,
not list position - `ChartModelTest.theSliceCapCountsColouredSlicesNotCategoryRank` pins it.

### What Slice 3 added

`PdfTextParser` (C4a) per ADR-0016: PDFBox glyph extraction with bounding boxes, per-page
column boundaries derived from that page's own header row, wrapped-line reassembly (EC2),
header/footer crop, single password prompt (§11.1). `TabularParser` (C4c) completed with
encoding/delimiter/header-row sniffing and `.xlsx` via POI. `ColumnMapper` implements §11.2's
one-time interactive mapping, appending to `bank_profiles.yaml` as text (comments survive) and
rolling back if the append does not re-parse. `Deduplicator` implements FR6/EC1.
`TransactionsCsvWriter` writes F3's 15 frozen columns, UTF-8 with BOM.

**Validated against the real statement.** The owner's August statement turned out to be
an HDFC **Regalia credit-card** statement, not a savings account - so the shipped
`hdfc_credit_card_pdf` profile is modelled on it and verified against the two control totals
the statement prints on itself, PURCHASES/DEBIT and PAYMENTS/CREDITS, both reproduced to the
paisa. Neither the statement nor the figures are in the repository: `RealStatementRegressionTest`
reads the filename and the two expected totals from `input/regression-expected.properties`,
gitignored beside the statement, and skips wherever that file is absent. Three layout traps
that statement is full of, each now covered by a test: the table sits at a different x-offset
on page 1 than page 2 (so column geometry is never configured, only derived per page); amounts
are right-aligned and the widest one starts left of its own header; and money-in is flagged with a leading `+` rather than a trailing `Cr`. The rupee sign
arrives as the letter `C` in a symbol font (`ITFRupee`) and is mapped back by font name.

The real financial data stays out of the repository (§13). Test PDFs are **generated** by
`PdfFixtures` at test time, reproducing that geometry with invented amounts and no real name,
address, reference number or card number.

**Two things to know before the next slice:**

1. **Config used not to upgrade itself**, which is how the real PDF came to be silently
   excluded with `PARSE-204` until `config/bank_profiles.yaml` was refreshed by hand. Fixed —
   see "Config: built-ins and overlay" below.
2. **The card payment currently reads as income.** The monthly bill payment is a credit,
   and transfer detection (FR11, ADR-0012's "detected as an internal transfer") is Slice 7 -
   so until then it inflates `total_income`. Expected, documented, not a regression.

Three sections of the report render their **documented "unavailable" state** rather than being
omitted, because their data belongs to a later slice: the month-over-month strip and the table's
"vs last month" column (FR14, Slice 6), the transfers note (FR11, Slice 7) and the per-row
category select (FR20, Slice 5). S01 defines each of those states, so this is the screen
behaving as specified on a first run, not a stub.

Deliberately not yet built (see the roadmap for which slice each belongs to): OCR
(`PdfOcrParser`, Slice 7 - a scanned PDF is excluded with `PARSE-202` and an install hint),
transfer detection, month-over-month, `summary.json`, most CLI flags, and `setup.*`. Legacy `.xls` stays
unsupported by design (`PARSE-206` with a "re-save as .xlsx" hint), which
[05-traceability.md](docs/architecture/05-traceability.md) §8 records as FR1's documented
partial.

**`MerchantKey` was rewritten and frozen before Slice 5.** F6 makes this derivation a frozen
contract — changing it orphans every saved correction — so it was fixed while
`corrections.json` had still never been written, which was the last moment it was free. The
original implementation reproduced the two worked examples in `01-components.md` but failed a
third one in [`02-data-model.md`](docs/architecture/02-data-model.md) §3.7's own sample CSV
(`NEFT DR-HDFC0001234-RENT TRANSFER-AUG` → `RENT TRANSFER`), and the real statement exposed two
more shapes, reproduced here with invented merchants so no real one is committed (§13):
`IND*ADOBEhttps://www.` keyed as `IND` — the payment aggregator, not the merchant, so every
`IND*` merchant would have collided into one key — and a merchant welded to its city by a
missing space keyed as `BIGBASKETCHENNAI`. All three documented examples plus the real-statement
cases are now regression locks in `MerchantKeyTest`.

One limitation is inherent and worth knowing before Slice 5: the same merchant described
differently by two banks yields two keys (the card PDF's `BIGBASKET CHENNAI` vs the savings CSV's
`BIGBASKET`), so an L1 exact-key correction on one will not carry to the other. That is what
ADR-0005's **L2 pattern correction** layer exists for.

## Package layout

```
src/main/java/org/example/
  cli/            — C1: entry point (Main, Cli)
  config/         — C2: Config, CategorySet, BankProfile, Workspace, ConfigLoader (+ config/yaml/ Jackson binding types)
  ingest/         — C3: Discovery, StatementFile, FileKind, CorrectionHarvester
  parse/          — C4: StatementParser + Parsers registry, ParseOutcome, RawRow, PasswordPrompt;
                    C4a PdfTextParser (+ PdfWords, PdfColumns); C4c TabularParser, ColumnMapper.
                    C4b PdfOcrParser is Slice 7.
  normalize/      — C5: Transaction, MerchantKey, RowParser, Deduplicator, Direction, CategorySource
  categorize/     — C6: Categorizer (full L1-L5 ladder), CorrectionsStore, Correction,
                    CorrectionPatch, PatchValidator
  analysis/       — C7: Analysis, MonthlySummary, CategorySpend, TopCategory, TransactionRef,
                    AccountSummary
  report/         — C8: ReportRenderer (Pebble), ChartModel (ADR-0007 pie rules), SvgPie
                    (WARN-507 fallback), DesignTokens (tokens.json -> CSS vars), MinimalReport
                    (NFR3 last resort), TransactionsCsvWriter (F3), Html
src/main/resources/
  default-config/ — config.yaml, categories.yaml, accounts.yaml (ADR-0020 shapes);
                    bank_profiles.yaml is the built-in profile set, bank_profiles.overlay.yaml
                    is the thin file a fresh workspace actually gets
  templates/      — report.peb (S01 layout), report.css, report.js — all three inlined
  assets/         — chart.umd.js (vendored Chart.js v4.4.4, MIT) + its licence
src/test/java/org/example/...  — mirrors the main package layout, 26 test classes
src/test/resources/fixtures/   — fabricated CSV fixture. PDF fixtures are generated at test
                                 time by parse/PdfFixtures.java, never checked in (§13)
```

Component keys (C1–C9) are from [`01-components.md`](docs/architecture/01-components.md).

## Commands

- Build: `mvn compile`
- Test: `mvn test` (CI runs `mvn -B --no-transfer-progress verify` — see
  [`.github/workflows/ci.yml`](.github/workflows/ci.yml))
- Package (fat JAR): `mvn package` → `ExpenseInNutshell.jar` at repo root
- Run: `.\run.bat` (Windows) / `./run.command` (macOS) / `./run.sh` (Linux) — three-line
  launcher per ADR-0017, runs `java -jar ExpenseInNutshell.jar`. Processes whatever's in
  `input/`, writes `output/<YYYY-MM>/report.html`.

## Build configuration

- Java 17 (`maven.compiler.source`/`target` in `pom.xml`, `-parameters` compiler flag for
  Jackson record binding)
- `groupId`: `org.example`, `artifactId`: `ExpenseInNutshell`
- Dependencies: `commons-csv`, `poi-ooxml` (ADR-0015), `pdfbox` (ADR-0016 text half; tess4j
  lands in Slice 7), `jackson-databind` + `jackson-dataformat-yaml`, `pebble`, `junit-jupiter`
  (test), `maven-shade-plugin` (fat JAR, `Main-Class: org.example.cli.Main`)
- Chart.js is **vendored, not a dependency** - it is inlined into the artifact, so a version
  bump is a deliberate reviewed act (ADR-0007)
- `design/design-system/tokens.json` is packaged into the jar as `/design/tokens.json` via an
  extra `<resource>` entry; it stays the single source of truth rather than being copied
- `config/`, `input/`, `output/`, `archive/` are gitignored (real financial data lives there)

## Notes for future work

Next up per [`07-implementation-roadmap.md`](docs/architecture/07-implementation-roadmap.md):
**Slice 6 — "Memory: month-over-month"** (closes AC6): `write_summary_json` with
`schema_version` and version-refusal on read, `HistoryStore` with directory-glob discovery and
`previous_month()` (the most recent prior month, not strictly `month - 1`), `month_over_month`
including the `new`/`gone`/`flat`/`unavailable` cases, the MoM panel and table deltas per
[S04](design/screens/04-month-over-month.md), and the `--month` override.

Everything Slice 6 needs is already plumbed and rendering its "unavailable" state:
`MonthlySummary.previousMonth` is null, every `CategorySpend` carries
`momStatus == "unavailable"`, and the report's month-over-month card and "vs last month" column
say so in words rather than being hidden.

The one part of Slice 5's own scope not delivered is the **File System Access progressive
enhancement** — on Chromium the tray could write the patch straight into `input/` instead of
downloading it. The `Blob` download path (the honest fallback ADR-0006 specifies for Firefox and
Safari) is what ships, so the loop is complete on every browser; the enhancement would only
remove two manual steps on one of them.

## Config: built-ins and overlay

**Bank profiles live in the build; `config/bank_profiles.yaml` is an overlay on top of them.**
The file on disk used to be the whole truth, which is what let a real HDFC statement be excluded
with `PARSE-204` after Slice 3 shipped a profile that could read it — `ensureBootstrapped` only
writes a config file that is *missing*, so an existing workspace never saw the new profile.

- `ConfigLoader.loadBankProfiles` merges the shipped set (from `/default-config/bank_profiles.yaml`
  on the classpath) with the user's file. Same `name` replaces the built-in **and warns**, so a
  forgotten stale copy is visible instead of silently holding someone on an old profile. A new
  name is added and tried **before** the built-ins, because profile scoring keeps the first of
  two equal matches and the user's own mapping should win that tie.
- `disable_shipped_profiles: [name, ...]` switches a built-in off. Deleting one from the file
  cannot work — it was never in the file.
- A fresh workspace gets `bank_profiles.overlay.yaml`: comments and a how-to-add-a-bank guide,
  **no profile bodies**. Writing a full copy would shadow the built-ins permanently, so improving
  a shipped profile later would never reach anyone who had already run the tool once — the exact
  staleness this removes. `ColumnMapper` creates the `profiles:` key when it appends the first
  mapped profile.
- `ConfigLoader.loadBankProfilesFile` reads the file *without* built-ins; the column mapper uses
  it to prove its own append parses, since merging built-ins there would let a broken append look
  valid.

**The other three config files are the user's and are never merged into**, only reported on:

- A `schema_version` older than the build's (or missing) is **warned about, never migrated**. F8
  makes these files the user's to hand-edit and they carry the comments documenting how; a
  Jackson round-trip to migrate one would destroy exactly that.
- Unknown top-level keys now raise `CFG-005` instead of being silently dropped, which is what F8
  always said should happen ("unknown top-level keys are warned about, never fatal").
- `config.yaml`'s missing keys were already harmless — the loader supplies code defaults.

All of these reach `RunReport` through a `Consumer<Diagnostic>` passed to `loadConfig`, so they
land in `run-log.txt` and the report rather than vanishing. `ConfigOverlayTest` covers each rule,
including one that asserts **a pristine install produces zero warnings** — which caught a real
false positive, `accounts.yaml`'s documented `income_rules` key missing from the known-keys list.

## CI

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs `mvn verify` on
ubuntu/windows/macos-latest against Temurin 17 (ADR-0014's pinned runtime), with `fail-fast`
off so every OS reports — "broke on Windows only" and "broke everywhere" are different
investigations. It then runs the shaded jar against an empty input folder to prove the artifact
the user is actually handed starts and still writes a report (EC5), which `mvn verify` alone
does not check. `mvn verify` also carries the AC8 static checks the roadmap wanted enforced by
CI rather than audited afterwards: the import lint (`PrivacyStaticCheckTest`), the
no-external-references assertion on the rendered report, and the XXE check on POI
(`ExternalEntityTest`).

`RealStatementRegressionTest` skips wherever the real statement is absent, so CI is green
without it while the check still runs on the owner's machine.

Still absent from CI, by slice: the NFR2 performance benchmark (500 transactions, 5 files, under
30 s) belongs to Slice 7, and there is no lint/formatter step — ADR-0017 files that as a
Dev-tier item.
