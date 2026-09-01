# 05 — Requirements Traceability

Every numbered identifier in the spec appears below. **Nothing is omitted**; items the
architecture deliberately defers or only partially satisfies are called out in §8 rather
than quietly dropped.

**ID note.** The spec numbers G, NG, US, FR, NFR and AC explicitly. §12 (Edge Cases) and
§16 (Open Questions) are bulleted but unnumbered, so this architecture assigns **EC1–EC6**
and **OQ1–OQ3** in the order they appear in the spec. That numbering is used consistently
across the whole package.

**Component keys** are from [01-components.md](01-components.md):
C1 CLI · C2 Config · C3 Ingest · C4 Parsers · C5 Normalize · C6 Categorize · C7 Analysis ·
C8 Report · C9 Diagnostics.
**Screen keys** S01–S08 are from [`design/screens/`](../../design/screens/).

---

## 1. Goals (G1–G7)

| ID | Summary | Component(s) | Artifact / ADR | Verified by |
|---|---|---|---|---|
| **G1** | Parse PDF and CSV/Excel statements into a normalized transaction list | C3, C4, C5 | [ADR-0003](04-adr/ADR-0003-pdf-parsing-strategy.md), [ADR-0004](04-adr/ADR-0004-tabular-parsing-and-bank-profiles.md); [02](02-data-model.md) §1.1 | Fixture PDFs (text + scanned) and CSV/XLSX exports parse to the frozen `Transaction` shape; AC1 test |
| **G2** | Categorize each transaction | C6 | [ADR-0005](04-adr/ADR-0005-categorization-strategy.md); [02](02-data-model.md) §3.2 | Every transaction has a non-null `category` and a `category_source`; unit tests per ladder layer |
| **G3** | Monthly summary: total spend, income, by-category, top category, MoM | C7 | [02](02-data-model.md) §1.2 | `MonthlySummary` snapshot test; AC3, AC6 |
| **G4** | Pie chart of spend-by-category with the largest slice emphasized | C8, S01 | [ADR-0007](04-adr/ADR-0007-report-and-chart-rendering.md); [design-system.md](../../design/design-system/design-system.md) | AC2 test; visual review against [S01](../../design/screens/01-monthly-dashboard.md); emphasis is a pulled slice + label, not colour alone |
| **G5** | Local HTML report/dashboard that opens automatically; not CLI-only | C8 | [ADR-0002](04-adr/ADR-0002-execution-model-local-batch-cli.md), [ADR-0007](04-adr/ADR-0007-report-and-chart-rendering.md) | AC2 test; `open_in_browser()` invoked and its failure path prints the path |
| **G6** | User corrections remembered for future runs | C3, C6, C8 | [ADR-0006](04-adr/ADR-0006-persisting-user-corrections.md); [02](02-data-model.md) §3.5, §3.6 | AC4 test (two-run) |
| **G7** | Run entirely offline; no data uploaded | all | [ADR-0002](04-adr/ADR-0002-execution-model-local-batch-cli.md); [03](03-interfaces-and-contracts.md) F10 | Import-lint bans network modules; render-time assertion bans `http(s)` refs in `report.html`; AC8 |

## 2. Non-Goals (NG1–NG5) — verified by *absence*

| ID | Summary | How the architecture honours it | Verified by |
|---|---|---|---|
| **NG1** | Not a budgeting app (goals, alerts, forecasting) | No budget entity, no threshold config, no notification path anywhere in [02](02-data-model.md). MoM (FR14) reports change; it never judges it | Data-model review: no `budget`, `target` or `alert` field exists |
| **NG2** | Not multi-user or cloud-synced in v1 | Single-process CLI, single local workspace, no auth, no sync, no user entity. Explicitly used in [ADR-0009](04-adr/ADR-0009-monthly-artifact-storage.md) to reject SQLite's concurrency argument | No user/session/sync concept in any contract |
| **NG3** | No bank API / screen-scraping | Input is exclusively files the user placed in `input/` (C3). No credential storage, no HTTP client (F10) | Same static check as AC8 |
| **NG4** | No investment/portfolio statements | Parsers target bank/card transaction tables only. `Investments/Savings` is a *category* for bank transactions, not a brokerage parser | Explicitly re-affirmed in [ADR-0012](04-adr/ADR-0012-credit-card-statements.md) (cards yes, brokerage no) |
| **NG5** | Not 100% automatic accuracy; manual review is normal | `Uncategorized` is a designed state (FR8), `needs_review` is a first-class field, and [S06](../../design/screens/06-uncategorized-review.md) makes review a workflow rather than an error screen | [ADR-0005](04-adr/ADR-0005-categorization-strategy.md) rejects ML partly on these grounds |

## 3. User Stories (US1–US7)

| ID | Summary | Component(s) | Screen | Artifact / ADR | Verified by |
|---|---|---|---|---|---|
| **US1** | Drop statement files in a folder and run | C1, C3 | [S08](../../design/screens/08-setup-and-run-console.md) | [ADR-0010](04-adr/ADR-0010-packaging-and-distribution.md) | Double-click `run` with files in `input/` produces a report; FR23 test |
| **US2** | See total spend broken down by category | C7, C8 | [S01](../../design/screens/01-monthly-dashboard.md) | [02](02-data-model.md) §1.2 | Category table renders every non-zero category with amount and % (FR18) |
| **US3** | Pie chart with the largest category clearly highlighted | C8 | [S01](../../design/screens/01-monthly-dashboard.md), [S02](../../design/screens/02-category-drilldown.md) | [ADR-0007](04-adr/ADR-0007-report-and-chart-rendering.md) | AC2; top slice has `offset: 12` and a bold direct label |
| **US4** | Report explicitly calls out the top category and its share | C7, C8 | [S01](../../design/screens/01-monthly-dashboard.md) | FR13 → callout component | AC3 — literal text assertion, not a visual one |
| **US5** | Reassign a transaction's category | C6, C8, C3 | [S03](../../design/screens/03-recategorize-and-corrections-tray.md), [S06](../../design/screens/06-uncategorized-review.md) | [ADR-0006](04-adr/ADR-0006-persisting-user-corrections.md) | AC4 |
| **US6** | Combine statements from more than one bank/account | C3, C5, C7 | [S05](../../design/screens/05-sources-and-top-transactions.md) | FR6 dedupe; [ADR-0012](04-adr/ADR-0012-credit-card-statements.md) | AC1; `summary.accounts` has one entry per source account |
| **US7** | Compare this month to last month by category | C7, C8 | [S04](../../design/screens/04-month-over-month.md) | [ADR-0009](04-adr/ADR-0009-monthly-artifact-storage.md) | AC6 |

## 4. Functional Requirements (FR1–FR23)

### 4.1 Input & Parsing

| ID | Summary | Component(s) | Artifact / ADR | Verified by |
|---|---|---|---|---|
| **FR1** | Detect file type and select the matching parser | C3 `detect_kind`, C4 `get_parser` | [ADR-0003](04-adr/ADR-0003-pdf-parsing-strategy.md), [ADR-0004](04-adr/ADR-0004-tabular-parsing-and-bank-profiles.md) | Magic-byte detection test incl. a PDF misnamed `.csv`; unsupported → `PARSE-206` |
| **FR2** | Extract rows from text-based PDF tables | C4a `PdfTextParser` | [ADR-0003](04-adr/ADR-0003-pdf-parsing-strategy.md) | Fixture text PDF yields the expected row count and field values |
| **FR3** | OCR fallback for scanned PDFs; flag low-confidence rows | C4b `PdfOcrParser` | [ADR-0003](04-adr/ADR-0003-pdf-parsing-strategy.md) | Scanned fixture yields rows with `confidence < 1.0`; rows under threshold have `needs_review=true`. **Partial — see §8** |
| **FR4** | Map CSV/Excel columns via a per-bank profile | C4c `TabularParser` | [ADR-0004](04-adr/ADR-0004-tabular-parsing-and-bank-profiles.md), [ADR-0011](04-adr/ADR-0011-day-one-bank-format-support.md); [02](02-data-model.md) §3.3 | Two different banks' CSVs both normalize correctly; unknown headers trigger the mapping flow |
| **FR5** | Skip an unparseable file, continue, and list it with a reason | C3, C4, C9 | [03](03-interfaces-and-contracts.md) §6 error taxonomy | AC7 |
| **FR6** | Merge multi-file months and de-duplicate exact repeats | C5 `deduplicate` | [02](02-data-model.md) §3.1 `dedupe` | Same file added twice ⇒ `duplicates_removed` populated, totals unchanged; EC1 |

### 4.2 Categorization

| ID | Summary | Component(s) | Artifact / ADR | Verified by |
|---|---|---|---|---|
| **FR7** | Rule-based categorization from a user-editable mapping file | C6 L3/L4 | [ADR-0005](04-adr/ADR-0005-categorization-strategy.md); [02](02-data-model.md) §3.2 | The spec's own §10.3 YAML parses and matches; longest-keyword tie-break test |
| **FR8** | No rule match ⇒ `Uncategorized`, never a silent guess | C6 L5 | [ADR-0005](04-adr/ADR-0005-categorization-strategy.md) | AC5; unmatched description ⇒ `category_source == "uncategorized"` and `needs_review` |
| **FR9** | Persist user reassignments by merchant pattern for future months | C6 `CorrectionsStore`, C3 harvest | [ADR-0006](04-adr/ADR-0006-persisting-user-corrections.md); F6 `merchant_key` | AC4 |
| **FR10** | 12 default categories; user may add/rename/remove | C2, C6 | [02](02-data-model.md) §1.3 | Default `categories.yaml` contains all 12; adding a 13th and renaming one both work without code change |
| **FR11** | Exclude or separately label internal transfers | C5 `detect_transfers`, C7 | [ADR-0013](04-adr/ADR-0013-income-versus-internal-transfer.md), [ADR-0012](04-adr/ADR-0012-credit-card-statements.md) | Paired self-transfer excluded from `total_spend` *and* present in `transfers_total` and [S05](../../design/screens/05-sources-and-top-transactions.md) |

### 4.3 Summary & Analysis

| ID | Summary | Component(s) | Artifact / ADR | Verified by |
|---|---|---|---|---|
| **FR12** | Compute total spend, income, net, and spend-per-category (amount + %) | C7 `compute_totals`, `spend_by_category` | [ADR-0008](04-adr/ADR-0008-money-and-currency.md) | Property test: `sum(by_category.amount) == total_spend` exactly (Decimal, not float); the **displayed** 1-dp percent column sums to exactly 100.0 via largest-remainder apportionment |
| **FR13** | Identify the top expense category; state amount and % explicitly | C7 `top_category`, C8 callout | [02](02-data-model.md) §1.2 | AC3 |
| **FR14** | Month-over-month change per category (▲/▼ and %) | C7 `month_over_month`, `HistoryStore` | [ADR-0009](04-adr/ADR-0009-monthly-artifact-storage.md) | AC6; `mom_status` covers new/gone/flat/unavailable |
| **FR15** | List the top 5 individual largest transactions of the month | C7 `top_transactions` | [02](02-data-model.md) §1.2 | Exactly `min(5, n)` non-transfer debits, sorted, deterministic ties |

### 4.4 Output / UI

| ID | Summary | Component(s) | Screen | Artifact / ADR | Verified by |
|---|---|---|---|---|---|
| **FR16** | Generate a self-contained local HTML report and open it automatically | C8 | [S01](../../design/screens/01-monthly-dashboard.md) | [ADR-0002](04-adr/ADR-0002-execution-model-local-batch-cli.md), [ADR-0007](04-adr/ADR-0007-report-and-chart-rendering.md) | AC2; offline assertion (no external refs); `webbrowser.open` called |
| **FR17** | Pie chart with the top category visually distinguished | C8 | [S01](../../design/screens/01-monthly-dashboard.md) | [ADR-0007](04-adr/ADR-0007-report-and-chart-rendering.md) §"the pie chart"; [design-system.md](../../design/design-system/design-system.md) | Top slice `offset` + bold direct label + surface ring; distinguishable in greyscale |
| **FR18** | Show summary table, top-category callout, and MoM alongside the chart | C8 | [S01](../../design/screens/01-monthly-dashboard.md), [S04](../../design/screens/04-month-over-month.md) | [02](02-data-model.md) §3.9 DOM order | All three regions present in the rendered DOM |
| **FR19** | Hover/click a slice to see contributing transactions (drill-down) | C8 (client JS) | [S02](../../design/screens/02-category-drilldown.md) | [ADR-0007](04-adr/ADR-0007-report-and-chart-rendering.md) | Chart.js `onHover`/`onClick` wired to the drill-down panel; keyboard-equivalent via table row activation |
| **FR20** | In-report control to change a transaction's category | C8 (client JS), C3 | [S03](../../design/screens/03-recategorize-and-corrections-tray.md) | [ADR-0006](04-adr/ADR-0006-persisting-user-corrections.md) | AC4; `<select>` per row + corrections tray + patch export |
| **FR21** | Also write categorized data to CSV alongside the HTML | C8 `write_transactions_csv` | [S01](../../design/screens/01-monthly-dashboard.md) footer | [03](03-interfaces-and-contracts.md) F3 | AC9; 15 frozen columns in order, UTF-8 BOM |

### 4.5 Configuration & Repeatability

| ID | Summary | Component(s) | Artifact / ADR | Verified by |
|---|---|---|---|---|
| **FR22** | All user settings in a single human-editable config, not hardcoded | C2 | [02](02-data-model.md) §3.1–§3.4 | Grep test: no hardcoded path, category name, keyword or bank column name in the package |
| **FR23** | Runnable with one command/double-click, no code changes | C1, launcher scripts | [ADR-0010](04-adr/ADR-0010-packaging-and-distribution.md) | `python -m expense_nutshell` with zero args completes a run; `run.bat`/`run.command` work |

## 5. Non-Functional Requirements (NFR1–NFR7)

| ID | Summary | Component(s) | Artifact / ADR | Verified by |
|---|---|---|---|---|
| **NFR1** | Privacy: all data stays local; never transmitted | all | [ADR-0002](04-adr/ADR-0002-execution-model-local-batch-cli.md), [ADR-0003](04-adr/ADR-0003-pdf-parsing-strategy.md) (no LLM), [ADR-0007](04-adr/ADR-0007-report-and-chart-rendering.md) (no CDN), [ADR-0008](04-adr/ADR-0008-money-and-currency.md) (no FX API); F10 | Static import ban on `socket`/`urllib`/`requests`/`httpx`/`http.client`; render-time no-external-refs assertion; AC8 |
| **NFR2** | ≤ 30 s for ~500 transactions across ≤ 5 files on a standard laptop | C1, C4, C9 | [ADR-0004](04-adr/ADR-0004-tabular-parsing-and-bank-profiles.md) (no pandas import cost) | Benchmark test asserting wall clock < 30 s; `RunReport.stage_timings` recorded every run. OCR-heavy runs excepted — see §8 |
| **NFR3** | A malformed row or unparseable file must not crash the run | C9 + every stage | [03](03-interfaces-and-contracts.md) §6 severity levels | AC7; fault-injection tests at file, row and field scope; exit code 0 on a degraded run |
| **NFR4** | Same codebase on Windows, macOS, Linux | C1, C8, launchers | [ADR-0001](04-adr/ADR-0001-language-and-runtime.md), [ADR-0010](04-adr/ADR-0010-packaging-and-distribution.md) | CI matrix across 3 OSes; `pathlib` everywhere; no OS-specific shell-outs in the core path |
| **NFR5** | A non-programmer can run the workflow after a one-time setup | C1, C2 `write_default_config`, launchers | [ADR-0010](04-adr/ADR-0010-packaging-and-distribution.md) | [S08](../../design/screens/08-setup-and-run-console.md) copy deck reviewed; every error message is prose with a next action |
| **NFR6** | Uncategorized transactions visually obvious | C6, C8 | [ADR-0005](04-adr/ADR-0005-categorization-strategy.md); [design-system.md](../../design/design-system/design-system.md) uncategorized treatment | AC5; [S06](../../design/screens/06-uncategorized-review.md); count + amount + row badge + hatched chart fill |
| **NFR7** | Each month kept as a separate dated artifact, not overwritten | C8, C7 `HistoryStore` | [ADR-0009](04-adr/ADR-0009-monthly-artifact-storage.md) | Run two months; both `output/<YYYY-MM>/` folders intact with all four files |

## 6. Edge Cases (EC1–EC6)

*Numbering assigned by this architecture; spec §12 lists these unnumbered, in this order.*

| ID | Spec text (abbreviated) | Component(s) | Handling | Verified by |
|---|---|---|---|---|
| **EC1** | Duplicate transactions from re-adding the same file → de-duplicated | C5 `deduplicate` | Key = (date, normalized description, amount, direction). Same key from the **same** file is kept (a genuine repeat charge); from a **different** file it is dropped as a re-add | Add the same CSV twice; totals unchanged, `duplicates_removed` non-empty |
| **EC2** | A transaction split across two PDF lines → reassemble before categorization | C4a | A row with empty date and amount but populated description is appended to the previous row's description | Fixture PDF with a wrapped merchant name categorizes correctly |
| **EC3** | Refunds/reversals shown as income/credit, not netted silently | C5, C7 | Credits are never netted against a prior debit; counted in `total_income`, visible in the category drill-down | Refund fixture: original debit still in its category, credit in income, both visible |
| **EC4** | Multi-currency → note original currency; use converted amount if present, else flag `needs_review` | C5 `parse_amount`, C7 | Home-currency equivalent present ⇒ used, foreign values kept in `original_*`. Absent ⇒ kept in its own currency, `needs_review`, `FIELD-402`, **excluded from totals**. No FX call | [ADR-0008](04-adr/ADR-0008-money-and-currency.md) §3; fixture with a USD row on an INR statement |
| **EC5** | A month with zero parseable transactions → report still generates, says so, lists why each file failed | C7, C8 | `MonthlySummary` with zeroed totals and `top_category = None`; the report renders the empty state and the skipped-files panel | [S07](../../design/screens/07-empty-and-error-states.md); run against a folder of only corrupt files ⇒ exit 0 + a real report |
| **EC6** | A ₹0 category excluded from the pie but kept for MoM history | C7, C8 | `by_category` retains zero rows for FR14; the chart filters `amount == 0`; the table shows them muted with `mom_status: "gone"` | Chart dataset length < `by_category` length when a zero category exists |

## 7. Acceptance Criteria (AC1–AC9)

Each maps to an executable assertion in
[03-interfaces-and-contracts.md](03-interfaces-and-contracts.md) §7.

| ID | Criterion | Component(s) | Closed in slice | Verified by |
|---|---|---|---|---|
| **AC1** | One PDF + one CSV for the same month ⇒ a single merged report | C3, C4, C5, C7 | 3 | `transaction_count` = both files minus duplicates; `accounts` has 2 entries |
| **AC2** | Report opens automatically and displays a spend-by-category pie chart | C8 | 1 (basic), 4 (full) | `<canvas id="category-pie">` present; data island parses; `webbrowser.open` called |
| **AC3** | Report explicitly states top category, amount and % of total spend | C7, C8 | 4 | Literal text assertion on the callout, not a visual check |
| **AC4** | A reassignment persists and applies to a new statement from the same merchant | C3, C6, C8 | 5 | Two-run test with different files; second run gives `category_source == "user_override"` |
| **AC5** | Uncategorized transactions visibly flagged in the table and as a count/callout | C6, C8 | 4 | `uncategorized_count` rendered; rows carry the badge; hatched chart fill |
| **AC6** | A second month shows MoM % change per category vs the first | C7 | 6 | `mom_change_percent` non-null; `previous_month` set |
| **AC7** | A corrupt file does not stop the run and is listed by name with a reason | C3, C4, C9 | 2 | Exit 0; valid file's transactions present; `excluded_files` names the bad one |
| **AC8** | No statement data leaves the machine | all | 1 (enforced from the start) | Import-lint + no-external-refs assertion; both run in CI |
| **AC9** | Categorized transaction list also available as CSV | C8 | 3 | `transactions.csv` exists with the 15 frozen columns in order |

## 8. Deliberately deferred or partially satisfied

Listed here rather than hidden. Each names the requirement, the gap, and why.

| ID | Status | What is deferred, and why |
|---|---|---|
| **FR3** | **Partial** | OCR requires the native Tesseract + Poppler binaries, which no Python packaging model can bundle ([ADR-0010](04-adr/ADR-0010-packaging-and-distribution.md)). When absent, scanned PDFs are excluded with `PARSE-202` and an install hint rather than silently failing. The *code path* is fully implemented and tested; the *runtime capability* is conditional on a documented optional install. |
| **FR1** (`.xls` only) | **Partial** | Legacy BIFF `.xls` depends on `xlrd>=2.0` and is fragile. `.pdf`, `.csv` and `.xlsx` are fully supported. `.xls` degrades to `PARSE-206` with "re-save as .xlsx or .csv". Modern bank exports are overwhelmingly `.xlsx`/`.csv`. |
| **FR20** (write-back) | **Deferred mechanism** | The in-report control is fully delivered, but a `file://` page cannot write to `config/corrections.json` on Firefox/Safari, so the change travels as a downloaded patch the user moves into `input/` ([ADR-0006](04-adr/ADR-0006-persisting-user-corrections.md)). On Chromium the File System Access API removes the manual step. AC4 is satisfied either way; the *ergonomics* are deferred pending real use. |
| **NFR2** | **Scoped** | The 30 s budget is asserted for the text-extraction path at the spec's stated scale (~500 transactions, ≤ 5 files). **OCR runs are explicitly out of that budget** — 300 DPI OCR of a 12-page scan is minutes, not seconds, in any language. The tool prints an OCR progress line so the wait is explained rather than looking hung. Flagged for owner confirmation as **A4**. |
| **EC4** (conversion) | **Intentionally not implemented** | The spec asks for conversion only *"if available in the statement"*. Fetching a rate would need a network call (NFR1/AC8) and a hardcoded rate would be wrong by an unknown amount. Foreign rows are preserved, flagged and excluded from totals. |
| **US7 / FR14** (multi-month trend) | **Deferred to §15** | Comparison is exactly one month vs the previous stored month, as FR14 specifies. Multi-month trend lines are a spec §15 future enhancement. [ADR-0009](04-adr/ADR-0009-monthly-artifact-storage.md) keeps the data available for it. |
| **OQ1, OQ2, OQ3** | **Answered by assumption** | Resolved in [ADR-0011](04-adr/ADR-0011-day-one-bank-format-support.md), [ADR-0012](04-adr/ADR-0012-credit-card-statements.md), [ADR-0013](04-adr/ADR-0013-income-versus-internal-transfer.md) respectively, and flagged as A1–A3 in [06](06-risks-and-open-questions.md) as pending owner confirmation. |
| **§15 items** | **Out of scope, as stated** | Budget targets, bank API download, household/multi-user, forecasting, mobile/PWA, and first-class investment/credit-card-only statements. None influenced the v1 design except that [ADR-0009](04-adr/ADR-0009-monthly-artifact-storage.md) and [ADR-0012](04-adr/ADR-0012-credit-card-statements.md) deliberately leave room for them. |

## 9. Coverage summary

| Category | IDs | Count | Fully addressed | Partial / deferred |
|---|---|---|---|---|
| Goals | G1–G7 | 7 | 7 | 0 |
| Non-Goals | NG1–NG5 | 5 | 5 (by construction) | 0 |
| User Stories | US1–US7 | 7 | 7 | 0 |
| Functional | FR1–FR23 | 23 | 21 | 2 (FR3 OCR-conditional, FR1 `.xls`) |
| Non-Functional | NFR1–NFR7 | 7 | 6 | 1 (NFR2 scoped to non-OCR runs) |
| Edge Cases | EC1–EC6 | 6 | 6 | 0 |
| Acceptance | AC1–AC9 | 9 | 9 | 0 |
| Open Questions | OQ1–OQ3 | 3 | 3 (by assumption) | flagged A1–A3 |
| **Total** | | **67** | | |
