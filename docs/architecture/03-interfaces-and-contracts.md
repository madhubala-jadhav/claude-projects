# 03 — Interfaces & Contracts

The spec (§9) is explicit: *"the interfaces below (data model, file layout) should stay
stable regardless of implementation choice."* This document says exactly which things are
frozen and which are free.

---

## 1. Stability classification

| Class | Meaning | Change policy |
|---|---|---|
| **FROZEN** | External or cross-run. Breaking it breaks a user's saved data, a prior month's history, or a downstream spreadsheet. | Requires a `schema_version` bump **and** a migration path. Additive optional fields are allowed at the same version. |
| **STABLE** | Module-to-module inside the process. Breaking it is a refactor across components. | May change in a single coordinated commit; must stay in sync with [01-components.md](01-components.md). |
| **INTERNAL** | Implementation detail. | Change freely; not documented as a contract. |

---

## 2. FROZEN contracts

### F1 — `Transaction` JSON shape
The 12 fields from spec §10.1 keep their names, types and semantics forever. The 7 fields
added in [02](02-data-model.md) §1.1 are frozen from v1 onward. Amounts are JSON **strings**.
*Consumers:* `report.html` data island, `transactions.csv`, any future importer.

### F2 — `MonthlySummary` JSON shape (`summary.json`)
The 8 fields from spec §10.2 plus the additions in [02](02-data-model.md) §1.2.
*Consumers:* **next month's own run** (FR14). This is the highest-risk contract in the
system — breaking it silently destroys month-over-month history that cannot be regenerated
without the original statements.
*Rule:* a reader must tolerate unknown fields and a **missing** optional field, and must
refuse to parse a `schema_version` greater than its own (reporting "history unavailable"
rather than guessing).

### F3 — `transactions.csv` column set and order
The 15 columns in [02](02-data-model.md) §3.7, in that order, UTF-8-with-BOM, RFC 4180.
*Consumers:* the user's spreadsheet (FR21, AC9). New columns append to the right only;
existing columns never move, rename or change meaning.

### F4 — `corrections.json` shape
[02](02-data-model.md) §3.5. *Consumer:* every future run (FR9, AC4). This file represents
accumulated user effort and is the one thing the tool cannot recreate.
*Rule:* the loader must be able to read every prior version. Corrections are never deleted
on migration — at worst they move to `history[]`.

### F5 — Correction patch shape
[02](02-data-model.md) §3.6, discriminated by
`"kind": "expense-nutshell-corrections-patch"`. *Producer:* the browser (FR20).
*Consumer:* C3 (`harvest_correction_patches`). Frozen because a report from March must
still be usable in September — old reports are archived artifacts, not upgradable clients.

### F6 — `merchant_key` derivation
The normalization algorithm in [01](01-components.md) C6. Changing it **orphans every
saved correction**, because keys stop matching. If it must change, the migration
re-derives keys for existing corrections using `example_description` — which is precisely
why that field is stored.

### F7 — Workspace directory layout
`config/`, `input/`, `output/<YYYY-MM>/`, `archive/`. Names and the `YYYY-MM` folder
pattern are frozen; `HistoryStore` discovers months by globbing them (FR14, NFR7).

### F8 — Config file names and top-level keys
`config.yaml`, `categories.yaml`, `bank_profiles.yaml`, `accounts.yaml` and their
documented keys (FR22). The spec's own §10.3 YAML must keep parsing unchanged.
*Rule:* unknown top-level keys are warned about, never fatal.

### F9 — CLI entry-point contract
See §5 below (FR23, NFR5).

### F10 — The offline invariant
No component may open a network socket. This is a contract with the **user**, asserted by
NFR1, AC8 and §13, and it outranks every other consideration in this document.

---

## 3. STABLE module-to-module contracts

| # | Producer → Consumer | Contract | Notes |
|---|---|---|---|
| S1 | C2 → all | `Config` (immutable dataclass) | Nobody else parses YAML |
| S2 | C3 → C1 | `list[StatementFile]` | Typed by magic bytes, not extension |
| S3 | C3 → C6 | `HarvestResult` + a mutated `corrections.json` | Idempotent: patches are moved to `archive/` after applying |
| S4 | C4 → C5 | `ParseOutcome{rows, failed, warnings, profile_used}` | `failed is not None` ⇒ `rows == []` |
| S5 | C5 → C6 | `list[Transaction]` with `category` unset | Every field except `category`/`category_source` is final |
| S6 | C6 → C7 | `list[Transaction]` fully populated | `category_source == "uncategorized"` ⟺ `category == "Uncategorized"` |
| S7 | C7 → C8 | `MonthlySummary` | C8 formats; it never computes a number that is not already in the summary |
| S8 | all → C9 | `RunReport` mutation methods | The only permitted shared mutable state in the pipeline |
| S9 | C8 → browser | The data island `#expense-data` | `{summary, transactions, run_report}`; the in-page JS reads nothing else |

### The `StatementParser` protocol (the extension point)

Adding support for a new statement medium means adding one class; nothing else changes.

```python
class StatementParser(Protocol):
    name: str
    def supports(self, file: StatementFile) -> bool: ...
    def parse(self, file: StatementFile, config: Config,
              ask_password: PasswordPrompt | None = None) -> ParseOutcome: ...
```

**Obligations on any implementation:**
1. Never raise for data problems — return `ParseOutcome(failed=ExcludedFile(...))` (NFR3, FR5).
2. Never perform network I/O (F10).
3. Set `confidence` honestly; `1.0` means "exact extraction", not "probably fine" (FR3).
4. Emit basenames in `source_file`, never absolute paths (§13).
5. Be deterministic: same file in, same rows out, same order.

---

## 4. File-format contracts

| Artifact | Direction | Class | Breaking change means |
|---|---|---|---|
| `config.yaml`, `categories.yaml`, `bank_profiles.yaml`, `accounts.yaml` | user → tool | FROZEN (F8) | The user's hand-written config stops working |
| `corrections.json` | tool ↔ tool | FROZEN (F4) | Months of accumulated corrections are lost |
| `corrections-*.json` patch | browser → tool | FROZEN (F5) | Old archived reports can no longer feed corrections back |
| `summary.json` | tool → tool (next month) | FROZEN (F2) | FR14/AC6 silently break |
| `transactions.csv` | tool → spreadsheet | FROZEN (F3) | The user's own pivot tables break |
| `report.html` | tool → browser | STABLE | Only ever read by the JS bundled inside it |
| `run-log.txt` | tool → human | INTERNAL | Diagnostic only; nothing parses it |

---

## 5. CLI / entry-point contract (F9)

**The default invocation takes no arguments** (FR23, NFR5):

```
run.bat            (Windows)
./run.command      (macOS)
./run.sh           (Linux)
python -m expense_nutshell
```

| Flag | Type | Default | Effect |
|---|---|---|---|
| `--config <dir>` | path | resolved workspace | Where the four config files live |
| `--input <dir>` | path | `config.paths.input` | Statement folder |
| `--output <dir>` | path | `config.paths.output` | Artifact root |
| `--month YYYY-MM` | str | inferred | Force the reporting month |
| `--no-open` | flag | off | Do not launch a browser (CI, headless) |
| `--no-ocr` | flag | off | Skip OCR entirely; scanned PDFs become excluded files |
| `--dry-run` | flag | off | Parse, categorize and analyse; write nothing, print the summary |
| `--verbose` | flag | off | DEBUG logging. **Does not disable redaction** |
| `--version` | flag | — | Print version and exit 0 |

**Exit codes:**

| Code | Meaning | Notes |
|---|---|---|
| `0` | Run completed — including a degraded run with excluded files or zero transactions | NFR3, EC5, AC7. A partial success is a success |
| `2` | Configuration error | The only fatal *data* class: no valid config ⇒ no defined behaviour |
| `3` | Workspace error — input or output directory missing/unwritable | |
| `4` | Internal error — an unhandled exception escaped the pipeline | A bug. The log holds the traceback; a minimal report is still written when possible |
| `130` | Interrupted (Ctrl-C) | |

**stdout is human-readable progress**, one line per stage, and always ends with the
absolute path of `report.html` so the user can find it even if the browser did not open.
**stderr carries warnings and errors only.**

**Interactive prompts** — the only two, both from the spec:
1. PDF password (§11.1) — asked once per file, read without echo, held in memory only,
   never written to disk or log.
2. Unknown column layout (§11.2) — asked once per new bank, and the answer is saved as a
   named profile so it is never asked again.

When there is no TTY (double-click on some systems, CI, cron), both prompts are skipped and
the affected file becomes an `ExcludedFile` with an actionable reason. **The tool never
blocks forever waiting for input that cannot arrive.**

---

## 6. Error taxonomy

Every failure carries one of these codes. The code is what appears in `run-log.txt`, in
`ExcludedFile.reason`, and in the report's skipped-files panel — so a user searching the
docs for the string they saw on screen finds the right entry.

### 6.1 Severity levels

| Level | Behaviour | Example |
|---|---|---|
| **FATAL** | Run aborts, non-zero exit | `CFG-001` |
| **FILE** | This file is skipped, run continues (FR5) | `PARSE-201` |
| **ROW** | This row is dropped, file continues (NFR3) | `ROW-301` |
| **FIELD** | Row kept, field degraded, `needs_review=True` (NFR6) | `FIELD-401` |
| **WARN** | Nothing dropped; user should know | `WARN-501` |

### 6.2 Codes

| Code | Level | Reason string (user-facing) | Raised by |
|---|---|---|---|
| `CFG-001` | FATAL | `config file is missing or unreadable` | C2 |
| `CFG-002` | FATAL | `config file has invalid YAML` | C2 |
| `CFG-003` | FATAL | `config value has the wrong type or an unknown enum` | C2 |
| `CFG-004` | FATAL | `two categories claim the same chart slot` | C2 |
| `CFG-005` | WARN | `unknown configuration key ignored` | C2 |
| `WS-101` | FATAL | `input folder not found` | C1 |
| `WS-102` | FATAL | `output folder is not writable` | C1 |
| `PARSE-201` | FILE | `unreadable / password-protected` | C4a |
| `PARSE-202` | FILE | `scanned PDF and OCR is not installed` | C4b |
| `PARSE-203` | FILE | `no transaction table found in this PDF` | C4a/b |
| `PARSE-204` | FILE | `no matching bank profile; run interactively once to map columns` | C4c |
| `PARSE-205` | FILE | `file is empty` | C3 |
| `PARSE-206` | FILE | `unsupported file type` | C3 |
| `PARSE-207` | FILE | `text encoding could not be determined` | C4c |
| `PARSE-208` | FILE | `file is corrupt or truncated` | C4 |
| `PARSE-209` | FILE | `permission denied` | C3 |
| `ROW-301` | ROW | `date could not be parsed` | C5 |
| `ROW-302` | ROW | `amount could not be parsed` | C5 |
| `ROW-303` | ROW | `row has neither a debit nor a credit amount` | C5 |
| `ROW-304` | ROW | `description was empty after cleaning` | C5 |
| `ROW-305` | ROW | `duplicate of a row from another file — removed` (FR6) | C5 |
| `ROW-306` | ROW | `transaction is outside the reporting month` | C1 |
| `FIELD-401` | FIELD | `low OCR confidence — please verify` (FR3) | C4b |
| `FIELD-402` | FIELD | `foreign currency with no converted amount` (EC4) | C5 |
| `FIELD-403` | FIELD | `no category rule matched` (FR8) | C6 |
| `FIELD-404` | FIELD | `account number not found in the statement header` | C4 |
| `WARN-501` | WARN | `a saved correction points at a category that no longer exists` | C6 |
| `WARN-502` | WARN | `a category regex failed to compile and was skipped` | C6 |
| `WARN-503` | WARN | `no prior month found — month-over-month unavailable` (FR14) | C7 |
| `WARN-504` | WARN | `correction patch rejected` (with per-entry detail) | C3 |
| `WARN-505` | WARN | `statements span more than one month; other months were excluded` | C1 |
| `WARN-506` | WARN | `browser could not be opened — report path printed instead` | C8 |
| `WARN-507` | WARN | `Chart.js asset missing — report rendered without the pie chart` | C8 |
| `INT-901` | — | `internal error` (exit 4) | anywhere |

### 6.3 Rules the taxonomy imposes

1. **A FILE-level error never aborts the run.** This is FR5 and AC7 stated as a mechanism.
2. **Every FILE-level code appears in the report by filename and reason.** The user must be
   able to see what was skipped without opening the log (FR5, AC7).
3. **FIELD-level codes set `needs_review`, never drop data.** Degrade, do not discard (NFR6).
4. **Reason strings are user-facing prose, not exception class names.** `"unreadable /
   password-protected"` — exactly the spec's own §10.2 example — not `PdfReadError`.
5. **No reason string contains an absolute path, an account number, or a password.**
   Basename only (§13).

---

## 7. Testability contract

Each of these is a black-box assertion against the contracts above, and maps to a spec
acceptance criterion:

| Assertion | Verifies |
|---|---|
| Given one PDF + one CSV for the same month, `summary.transaction_count` equals the sum of both files' rows minus duplicates, and `accounts` has 2 entries | AC1, FR6, US6 |
| `report.html` contains `<canvas id="category-pie">` and the data island parses | AC2, FR16, FR17 |
| `report.html` contains `summary.top_category.name`, its formatted amount, and its percent as literal text | AC3, FR13, US4 |
| Apply a patch mapping `AMAZON`→`Groceries`, re-run on a *different* file containing `UPI/AMAZON PAY/…`, assert the category is `Groceries` and `category_source == "user_override"` | AC4, FR9, FR20 |
| A transaction with no matching rule appears in `by_category` under `Uncategorized`, and `uncategorized_count > 0` | AC5, FR8, NFR6 |
| Run month 1 then month 2; month 2's `by_category[*].mom_change_percent` is non-null and `previous_month` is month 1 | AC6, FR14 |
| Place a zero-byte `.pdf` alongside a valid CSV; exit code is 0, the CSV's transactions are present, and `excluded_files` names the bad file with a reason | AC7, FR5, NFR3 |
| Static check: no import of `socket`/`urllib`/`requests`/`httpx`/`http.client` in the package; and `report.html` contains no `http://`/`https://` `src`/`href` | AC8, NFR1 |
| `transactions.csv` exists with the 15 frozen columns in order | AC9, FR21 |
| 500 transactions across 5 files complete in < 30 s wall clock, measured from `RunReport.stage_timings` | NFR2 |
