# 01 — Components

Python package root: `expense_nutshell`. Every signature below is normative — the
implementation may change the body, not the shape (see [03](03-interfaces-and-contracts.md)
for which contracts are frozen).

Shared type aliases used throughout:

```python
from decimal import Decimal
from datetime import date
from pathlib import Path
from typing import Iterable, Literal, Protocol

Money = Decimal                      # always exact; never float (ADR-0008)
CurrencyCode = str                   # ISO-4217, uppercase, e.g. "INR"
Direction = Literal["debit", "credit"]
CategorySource = Literal["rule", "user_override", "uncategorized"]
MonthKey = str                       # "YYYY-MM"
```

---

## C1 · CLI / Orchestrator — `expense_nutshell.cli`

**Responsibility.** Sequence the pipeline end to end for one month and own the run's
`RunReport`; it contains no business logic of its own.

**Inputs.** Command-line arguments (all optional), the config directory, the current clock.
**Outputs.** Process exit code, console progress lines, and the side effects of C8.

**Public interface**

```python
def main(argv: list[str] | None = None) -> int:
    """Entry point. Returns 0 on a completed run (even a degraded one),
    2 on a configuration error, 3 on an unusable workspace. Never raises
    to the shell for data problems - those are reported, not fatal (NFR3)."""

def run(config: Config, *, month: MonthKey | None = None,
        open_browser: bool = True, clock: date | None = None) -> RunResult:
    """The whole pipeline as one callable, so it is testable without a shell.
    month=None means 'infer from the transactions found' (see below)."""
```

```python
@dataclass(frozen=True)
class RunResult:
    summary: MonthlySummary
    transactions: list[Transaction]
    run_report: RunReport
    report_path: Path
    csv_path: Path
    summary_path: Path
```

**Accepted arguments** (none are required — FR23):
`--config <dir>`, `--input <dir>`, `--output <dir>`, `--month YYYY-MM`,
`--no-open`, `--no-ocr`, `--verbose`, `--dry-run`.

**Month inference.** If `--month` is absent, group the parsed transactions by
`date.strftime("%Y-%m")` and select the month with the most transactions. If a run spans
more than one month, the other months are reported in `RunReport.warnings` and their
transactions are excluded from this month's summary but still written to `transactions.csv`
with their true dates. Rationale: the user's mental model is "this month's statements",
and silently blending two months would corrupt FR12 totals.

**Owned state.** The `RunReport` instance for the current run. Nothing persistent.

**Failure modes.**
- Config invalid or missing → print the specific file, key and expected shape; exit 2. This
  is the only *fatal* class, because with no config there is no defined behaviour (FR22).
- Output directory not writable → exit 3 with the path.
- Any stage raising an unexpected exception → caught, recorded as `RunReport.fatal_stage`,
  and C8 is still invoked so the user gets a report explaining the failure (NFR3).

**Satisfies:** FR23, NFR3, NFR5.

---

## C2 · Config — `expense_nutshell.config`

**Responsibility.** Load, validate, and merge the four user-editable configuration files
into one immutable `Config` object, so no other component ever reads YAML.

**Inputs.** `config/config.yaml`, `config/categories.yaml`, `config/bank_profiles.yaml`,
`config/accounts.yaml`.
**Outputs.** A validated `Config`; or a `ConfigError` naming file, key and expected type.

**Public interface**

```python
def load_config(config_dir: Path) -> Config:
    """Reads and validates all four files. Raises ConfigError with a
    human-readable path like 'categories.yaml: categories.Groceries.keywords
    must be a list of strings, got str'."""

def write_default_config(config_dir: Path, *, force: bool = False) -> list[Path]:
    """First-run bootstrap. Writes the shipped defaults (including the 12
    categories of FR10) for any file that does not exist. Returns what it wrote."""

def resolve_workspace(explicit: Path | None) -> Workspace:
    """Determines config/, input/ and output/ locations. Precedence:
    CLI flag > EXPENSE_NUTSHELL_HOME env var > ./ (the folder containing the
    run script) > platform user-data dir."""
```

```python
@dataclass(frozen=True)
class Config:
    workspace: Workspace
    currency_default: CurrencyCode          # "INR"
    locale: str                             # "en-IN" - number/date formatting only
    categories: CategorySet                 # FR10, incl. slot bindings for the chart
    bank_profiles: list[BankProfile]        # FR4
    accounts: list[AccountRef]              # OQ3 / FR11 transfer detection
    ocr: OcrSettings                        # enabled, dpi, lang, confidence_threshold
    report: ReportSettings                  # theme, top_n_slices, open_browser
    dedupe: DedupeSettings                  # FR6 key fields and tolerance
```

**Owned state.** None at runtime. `write_default_config` is the only writer, and only for
files that do not yet exist.

**Failure modes.** Malformed YAML → `ConfigError` with line/column. Unknown top-level key
→ warning, not an error (forward compatibility). A category referencing a chart slot
already taken → `ConfigError`, because silently reassigning would make colours unstable
month to month.

**Satisfies:** FR10, FR22, NFR5.

---

## C3 · Ingest — `expense_nutshell.ingest`

**Responsibility.** Find the files to process, decide what each one is, and harvest any
correction patches the user dropped in.

**Inputs.** The input directory.
**Outputs.** `list[StatementFile]`, plus merged corrections as a side effect.

**Public interface**

```python
def discover(input_dir: Path, *, recursive: bool = False) -> list[StatementFile]:
    """Lists candidate statements. Ignores dotfiles, ~$ Office lock files,
    zero-byte files, and anything already recorded as processed in this run."""

def detect_kind(path: Path) -> FileKind:
    """FileKind: PDF | CSV | XLSX | XLS | CORRECTION_PATCH | UNSUPPORTED.
    Decided by magic bytes first, extension second - a .csv that is really a
    PDF must not reach the CSV parser (FR1)."""

def harvest_correction_patches(input_dir: Path, corrections_path: Path,
                               archive_dir: Path) -> HarvestResult:
    """Finds corrections-*.json dropped by the user (FR20), validates and merges
    them into corrections.json, then moves each patch into archive_dir so it is
    applied exactly once. Returns counts and any rejected entries (FR9, AC4)."""
```

```python
@dataclass(frozen=True)
class StatementFile:
    path: Path
    kind: FileKind
    size_bytes: int
    profile_hint: str | None    # from filename convention, e.g. "hdfc_aug2026.pdf" -> "hdfc"
```

**Owned state.** None. `harvest_correction_patches` mutates `corrections.json` and the
archive directory, and is idempotent by virtue of moving each patch after applying it.

**Failure modes.** Unreadable/locked file → `ExcludedFile(reason="permission denied")`,
run continues (FR5). Malformed correction patch → whole patch rejected with a reason and
left in place rather than half-applied; a partial merge would be worse than none.

**Satisfies:** FR1, FR5, FR9, FR20, AC4, AC7.

---

## C4 · Parser Layer — `expense_nutshell.parsers`

**Responsibility.** Turn one statement file into raw rows, using the most exact method
that works for that file.

**Inputs.** A `StatementFile`, the `Config`, and an optional password callback.
**Outputs.** `ParseOutcome` — either rows plus per-row confidence, or a failure reason.

**Public interface**

```python
class StatementParser(Protocol):
    name: str
    def supports(self, file: StatementFile) -> bool: ...
    def parse(self, file: StatementFile, config: Config,
              ask_password: PasswordPrompt | None = None) -> ParseOutcome: ...

def get_parser(file: StatementFile, config: Config) -> StatementParser | None:
    """Registry lookup. Order: TabularParser (CSV/XLSX/XLS),
    PdfTextParser, PdfOcrParser. Returns None for UNSUPPORTED (FR1)."""
```

```python
@dataclass(frozen=True)
class RawRow:
    source_file: str
    source_page: int | None
    row_index: int
    date_text: str
    description_text: str
    debit_text: str | None
    credit_text: str | None
    amount_text: str | None      # for single-amount-column formats
    balance_text: str | None
    account_hint: str | None
    confidence: float            # 1.0 for text extraction; OCR mean word conf otherwise
    extractor: Literal["pdf_text", "pdf_ocr", "tabular"]

@dataclass(frozen=True)
class ParseOutcome:
    rows: list[RawRow]
    failed: ExcludedFile | None       # non-None ⇒ file contributed nothing
    warnings: list[Diagnostic]
    profile_used: str | None
```

### C4a · PdfTextParser

```python
def extract_tables(pdf_path: Path, template: PdfTableTemplate,
                   password: str | None = None) -> list[RawRow]:
    """pdfplumber table extraction against a named column template.
    Default template covers the common Indian layout: Date | Narration |
    Withdrawal | Deposit | Balance (spec §11.1)."""

def has_text_layer(pdf_path: Path, *, min_chars_per_page: int = 40) -> bool:
    """Decides text-vs-OCR per document; a per-page variant handles mixed PDFs."""
```

Reassembles wrapped description lines before emitting a row: a row whose date and amount
cells are both empty but whose description cell is populated is appended to the previous
row's description (EC2).

**Failure modes.** Encrypted PDF → calls `ask_password` once (in-memory only, never
logged or persisted, §13); a wrong or refused password becomes
`ExcludedFile(reason="password-protected")`. No table found on any page → falls through
to C4b rather than failing.

### C4b · PdfOcrParser

```python
def ocr_pages(pdf_path: Path, settings: OcrSettings) -> list[OcrPage]:
    """pdf2image -> pytesseract with word-level confidence. Only invoked when
    has_text_layer() is False, or the text parser found zero rows (FR3)."""
```

Any row whose mean word confidence is below `ocr.confidence_threshold` (default 0.75) is
emitted with that confidence so C5/C6 can set `needs_review=True` (FR3, NFR6).

**Failure modes.** Tesseract binary missing → the whole OCR path is skipped and the file
becomes `ExcludedFile(reason="scanned PDF and OCR is not installed")` with an actionable
install hint. This is a **documented degradation of FR3**, not a crash (NFR3, QA5).

### C4c · TabularParser

```python
def sniff_layout(path: Path) -> TabularLayout:
    """Detects delimiter, encoding, and header row index for CSV; sheet
    selection and header row for Excel (spec §11.2)."""

def resolve_profile(headers: list[str], profiles: list[BankProfile],
                    hint: str | None) -> BankProfile | None:
    """Scores each profile by normalized header-set overlap; returns the best
    match above threshold 0.8, else None (FR4)."""

def map_columns(rows, profile: BankProfile) -> list[RawRow]: ...

def prompt_and_save_profile(headers: list[str], path: Path,
                            profiles_file: Path) -> BankProfile:
    """Interactive one-time column mapping when no profile matches;
    persists the answer as a new named profile (spec §11.2, FR4)."""
```

**Failure modes.** Unknown encoding → retries utf-8-sig, cp1252, latin-1 in order before
giving up. No profile match in non-interactive mode (`--dry-run`, or no TTY) →
`ExcludedFile(reason="no matching bank profile; run interactively once to map columns")`.

**Satisfies:** FR1, FR2, FR3, FR4, FR5, EC2, AC7, §11.1, §11.2, §13.

---

## C5 · Normalization & Transfer Detection — `expense_nutshell.normalize`

**Responsibility.** Turn heterogeneous `RawRow`s into a clean, de-duplicated, canonical
`Transaction` list.

**Inputs.** `list[RawRow]`, `Config`.
**Outputs.** `list[Transaction]` plus diagnostics for every row it had to drop or flag.

**Public interface**

```python
def normalize_rows(rows: list[RawRow], config: Config,
                   report: RunReport) -> list[Transaction]:
    """The full C5 pipeline in order:
    parse_row -> deduplicate -> detect_transfers."""

def parse_row(row: RawRow, config: Config) -> Transaction | RowError:
    """Field-level parsing. A RowError is recorded and the row dropped;
    it never propagates as an exception (NFR3)."""

def parse_amount(text: str, default_currency: CurrencyCode) -> tuple[Money, CurrencyCode]:
    """Handles 1,234.56 / 1.234,56 / (123.45) negatives / trailing Cr|Dr /
    embedded currency symbols and codes. Returns exact Decimal (ADR-0008)."""

def parse_statement_date(text: str, hints: list[str]) -> date:
    """Tries the profile's declared formats first, then a fixed ordered list.
    Ambiguous DD/MM vs MM/DD is resolved by the profile's date_order, never guessed."""

def deduplicate(txns: list[Transaction], settings: DedupeSettings
                ) -> tuple[list[Transaction], list[Duplicate]]:
    """FR6/EC1. Key = (date, normalized_description, amount, direction).
    Same key from the SAME source_file is kept (a real repeated charge);
    same key from a DIFFERENT source_file is dropped as a re-added file."""

def detect_transfers(txns: list[Transaction], accounts: list[AccountRef],
                     categories: CategorySet) -> list[Transaction]:
    """FR11/OQ3. Sets is_transfer via three signals, in order:
    1. a matched debit/credit PAIR across two known own-accounts within
       transfer_window_days (default 3), equal absolute amount;
    2. the counterparty account number appears in accounts.yaml;
    3. a category flagged is_transfer: true in categories.yaml."""
```

**Owned state.** None; pure functions over the input list.

**Failure modes.** Unparseable date or amount → row dropped, `RowError` recorded with the
file, page and row index, and surfaced in the report (NFR3, NFR6). Foreign-currency row
with no home-currency equivalent in the statement → kept with its original currency and
`needs_review=True`; **excluded from category totals** so a ₹ total is never silently
polluted by a $ amount (EC4).

**Refunds/reversals (EC3):** a credit is never netted against an earlier debit. It is
recorded as `direction="credit"`, counted in `total_income`, and shown in the drill-down
for its category — both sides stay visible, exactly as the spec requires.

**Satisfies:** FR6, FR11, EC1, EC2, EC3, EC4, NFR3.

---

## C6 · Categorization Engine — `expense_nutshell.categorize`

**Responsibility.** Assign exactly one category to every transaction, deterministically,
and remember what the user teaches it.

**Inputs.** `list[Transaction]`, `CategorySet`, `CorrectionsStore`.
**Outputs.** The same transactions with `category`, `category_source`, `needs_review` set.

**Public interface**

```python
def categorize_all(txns: list[Transaction], categories: CategorySet,
                   corrections: CorrectionsStore) -> list[Transaction]: ...

def categorize_one(txn: Transaction, categories: CategorySet,
                   corrections: CorrectionsStore) -> CategoryDecision:
    """Deterministic precedence ladder - first match wins, and the layer that
    matched is recorded so the report can explain itself (ADR-0005):

      L1 user correction, exact merchant_key   -> category_source="user_override"
      L2 user correction, pattern              -> category_source="user_override"
      L3 categories.yaml overrides: mapping    -> category_source="rule"
      L4 categories.yaml keyword/regex rules   -> category_source="rule"
      L5 no match                              -> "Uncategorized",
                                                  category_source="uncategorized",
                                                  needs_review=True   (FR8)
    Within L4, the longest matching keyword wins; ties break by the category's
    declared order in categories.yaml. Never random, never first-file-wins."""

def merchant_key(description: str) -> str:
    """The stable identity a correction is remembered against (FR9, AC4).
    Uppercase, strip POS/UPI/NEFT/IMPS/ATM prefixes, strip trailing reference
    numbers and dates, collapse whitespace, keep the first 3 significant tokens.
    'POS SWIGGY*ORDER 4471 BANGALORE IN' -> 'SWIGGY'
    'UPI/AMAZON PAY/9928311/PAYMENT'     -> 'AMAZON PAY'"""
```

```python
class CorrectionsStore:
    @classmethod
    def load(cls, path: Path) -> "CorrectionsStore": ...
    def lookup(self, txn: Transaction) -> str | None: ...
    def apply_patch(self, patch: CorrectionPatch) -> PatchResult:
        """Merge a browser-exported patch. Last-write-wins per merchant_key,
        with the prior value kept in history[] so a mistaken correction is
        recoverable (FR9)."""
    def save(self, path: Path) -> None:
        """Atomic: write to a .tmp sibling, fsync, os.replace. A crash mid-save
        must never leave the user with a truncated corrections file."""
```

**Owned state.** `corrections.json` — the only file the tool mutates in place across runs.

**Failure modes.** Correction naming a category that no longer exists in `categories.yaml`
→ correction kept in the file but not applied; transaction falls through to L3/L4 and the
report lists the stale correction so the user can fix it. A regex in `categories.yaml` that
fails to compile → that one rule is skipped with a warning; the rest of the file still works
(NFR3).

**Explicit non-goal.** No machine learning, no fuzzy scoring, no cloud lookup — see
[ADR-0005](04-adr/ADR-0005-categorization-strategy.md). Determinism is the feature: the
same input must produce the same categories on every run, or the user cannot trust a
correction to stick.

**Satisfies:** FR7, FR8, FR9, FR10, FR11, NG5, NFR1, NFR6, AC4.

---

## C7 · Analysis — `expense_nutshell.analysis`

**Responsibility.** Compute every number the report shows, and nothing the report does not.

**Inputs.** Categorized `list[Transaction]`, the target month, prior months' `summary.json`.
**Outputs.** One `MonthlySummary`.

**Public interface**

```python
def summarize(txns: list[Transaction], month: MonthKey, config: Config,
              history: HistoryStore, report: RunReport) -> MonthlySummary: ...

def compute_totals(txns) -> Totals:
    """total_spend = sum of debits where is_transfer is False and the
    transaction's currency == the run currency (FR12, FR11).
    total_income = sum of credits where is_transfer is False.
    net = total_income - total_spend."""

def spend_by_category(txns, categories: CategorySet) -> list[CategorySpend]:
    """Amount and percent_of_spend per category. percent is computed against
    total_spend as an exact Decimal; the 1-decimal DISPLAY values are then
    apportioned by largest remainder so the rendered column sums to exactly
    100.0. The raw Decimal is kept so the pie chart geometry never shows a
    rounding gap. Zero-spend
    categories are returned with amount=0 and excluded from the chart by the
    renderer, not dropped here - FR14 still needs them for history (EC6)."""

def top_category(by_category) -> TopCategory | None:
    """FR13. Highest spend. Ties are broken by the larger transaction count,
    then alphabetically, so the callout is stable across reruns.
    Returns None when total_spend == 0 (EC5)."""

def top_transactions(txns, n: int = 5) -> list[TransactionRef]:
    """FR15. Largest debits by absolute amount regardless of category,
    transfers excluded, ties broken by date then description."""

def month_over_month(current: MonthlySummary,
                     previous: MonthlySummary | None) -> MonthlySummary:
    """FR14. Adds mom_change_percent per category and a direction flag.
    Rules: previous absent -> None (report renders 'no prior month').
    previous == 0 and current > 0 -> 'new' rather than an infinite percentage.
    current == 0 and previous > 0 -> -100.0."""
```

```python
class HistoryStore:
    def __init__(self, output_dir: Path): ...
    def previous_month(self, month: MonthKey) -> MonthlySummary | None:
        """The most recent stored month strictly before `month` - not
        necessarily month-1, so a skipped month still compares to something."""
    def all_months(self) -> list[MonthKey]: ...
```

**Owned state.** None. `HistoryStore` reads only.

**Failure modes.** A prior `summary.json` written by an older schema version → migrated if
possible, otherwise ignored with a warning; MoM degrades to "unavailable" rather than
crashing the run (NFR3). Zero transactions → a valid `MonthlySummary` with zeroed totals,
`top_category=None`, and the excluded-file list populated (EC5).

**Satisfies:** FR12, FR13, FR14, FR15, EC5, EC6, AC3, AC6.

---

## C8 · Report Generator — `expense_nutshell.report`

**Responsibility.** Produce the two artifacts the user consumes, and open one of them.

**Inputs.** `MonthlySummary`, `list[Transaction]`, `RunReport`, `Config`.
**Outputs.** `report.html`, `transactions.csv`, `summary.json`, and a browser window.

**Public interface**

```python
def render_report(summary: MonthlySummary, txns: list[Transaction],
                  run_report: RunReport, config: Config,
                  out_dir: Path) -> Path:
    """Jinja2 template + inlined CSS + vendored Chart.js + a JSON data island.
    The result is one file with zero external references - verified by an
    assertion that the output contains no http:// or https:// src/href
    (NFR1, AC8, FR16)."""

def write_transactions_csv(txns: list[Transaction], out_dir: Path) -> Path:
    """FR21. UTF-8 with BOM so Excel opens it correctly on Windows;
    column order frozen by contract (see 03)."""

def write_summary_json(summary: MonthlySummary, out_dir: Path) -> Path:
    """The machine-readable twin of the report; the input to next month's
    FR14 comparison."""

def open_in_browser(path: Path) -> bool:
    """webbrowser.open(path.as_uri()). Returns False if no browser could be
    launched (headless/WSL/SSH); the CLI then prints the absolute path
    instead of failing (FR16, NFR3)."""
```

**Report structure (the HTML contract).** Sections in DOM order, matching the design
package's screen 01: header with month and run stamp → four stat tiles (total spend, total
income, net, uncategorized count) → top-category callout (FR13/US4) → pie chart with the
top slice pulled out (FR17) + legend → category table with amount, % and MoM delta
(FR18) → month-over-month panel (FR14) → top 5 transactions (FR15) → uncategorized review
list (NFR6) → accounts and skipped-files panel (FR5) → corrections tray (FR20) → footer
with the CSV link (FR21).

**Interactivity (all client-side, no network).**
- Hover a slice → tooltip with category, amount, % (FR19).
- Click a slice or a table row → the drill-down panel lists that category's transactions
  (FR19).
- A `<select>` on each transaction row changes its category; changes are staged in memory,
  shown in a sticky "N unsaved corrections" tray, and exported via a **Save corrections**
  button that triggers a `Blob` download of `corrections-YYYY-MM.json` (FR20 → FR9 → AC4).

**Owned state.** Writes only into `output/<month>/`, never overwriting a prior month
(NFR7). Re-running the same month overwrites *that month's* files, which is intended, and
the previous version is kept as `report.<timestamp>.html` when
`report.keep_reruns: true`.

**Failure modes.** Template error → falls back to a minimal, guaranteed-renderable HTML
that shows the summary numbers and the diagnostics, so the user always gets something
(NFR3). Browser launch failure → path printed to stdout. Output directory read-only →
this is one of the few fatal cases (exit 3), because there is nowhere to put the answer.

**Satisfies:** FR16, FR17, FR18, FR19, FR20, FR21, NFR1, NFR6, NFR7, AC2, AC3, AC5, AC9.

---

## C9 · Diagnostics — `expense_nutshell.diagnostics`

**Responsibility.** Be the single place where "something went wrong but the run continues"
is recorded, and guarantee nothing sensitive is ever written to a log.

**Inputs.** Calls from every other component.
**Outputs.** A `RunReport` consumed by C8, and `run-log.txt`.

**Public interface**

```python
class RunReport:
    started_at: datetime
    excluded_files: list[ExcludedFile]     # FR5, AC7 - shown by name + reason
    row_errors: list[RowError]
    duplicates_removed: list[Duplicate]    # FR6
    warnings: list[Diagnostic]
    stage_timings: dict[str, float]        # NFR2 evidence
    fatal_stage: str | None

    def exclude_file(self, path: Path, reason: str, detail: str = "") -> None: ...
    def warn(self, stage: str, message: str, **context) -> None: ...
    def timed(self, stage: str) -> ContextManager[None]: ...
    def to_dict(self) -> dict: ...
```

```python
@dataclass(frozen=True)
class ExcludedFile:
    file: str            # basename only - never the full path, which may contain a username
    reason: str          # a member of the error taxonomy in 03
    detail: str = ""

def redact(text: str) -> str:
    """Masks anything that looks like an account number (>=8 consecutive digits
    -> last 4 kept), a card PAN, or a password argument, before it reaches a
    log line. Applied by the logging handler, not by callers (NFR1, §13)."""
```

**Owned state.** `output/<month>/run-log.txt`. Log level defaults to INFO; `--verbose`
raises it to DEBUG but the redaction filter is **not** removable by a flag.

**Failure modes.** Log file not writable → logging degrades to stderr, run continues. The
diagnostics component itself must never be able to fail the run.

**Satisfies:** FR5, FR6, NFR1, NFR2, NFR3, NFR6, AC7, AC8, §13.

---

## Cross-component invariants

1. **No component below C3 performs I/O.** C4–C7 receive data and return data. This is the
   mechanical guarantee behind AC8 and is enforced by an import-lint rule forbidding
   `socket`, `urllib`, `requests`, `httpx` and `http.client` anywhere in the package.
2. **Money never becomes a float.** `Decimal` from parse to render; formatting to a string
   happens only in C8 (ADR-0008).
3. **Every dropped or demoted unit is recorded.** A transaction that vanishes without an
   entry in `RunReport` is a bug, not a design choice.
4. **Categorization is a pure function of (transaction, categories, corrections).** No
   clock, no randomness, no file order dependence — otherwise AC4 cannot be tested.
