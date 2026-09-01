# 02 — Data Model

Currency amounts are **exact decimals** everywhere (ADR-0008). "Units" below means the
minor-unit precision of the currency — 2 for INR/USD/EUR, 0 for JPY.

Schema version for all persisted artifacts: **`schema_version: 1`**.

---

## 1. Entities

### 1.1 `Transaction` — the normalized atom

The spec's §10.1 shape is **frozen** (see [03](03-interfaces-and-contracts.md)). Fields
marked *(added)* are architect's additions; each names the requirement it serves.

| Field | Type | Null? | Units / domain | Validation | Lifecycle |
|---|---|---|---|---|---|
| `date` | `date` | no | ISO `YYYY-MM-DD`, local calendar date, no time, no timezone | Must parse under the source profile's `date_order`; must fall within `[statement_start - 5d, today]` or the row is flagged `needs_review` | Set in C5 from `RawRow.date_text`; never changed afterwards |
| `description` | `str` | no | 1–200 chars, whitespace-collapsed, cleaned | Non-empty after cleaning, else `RowError` | C5 derives from `raw_description` |
| `raw_description` | `str` | no | verbatim from the statement, incl. wrapped-line joins | Non-empty | C4 → C5; preserved for auditability and re-categorization |
| `amount` | `Decimal` | no | **positive magnitude**, currency minor units | `> 0`; direction carries the sign. A statement showing `-487.00` in a single amount column becomes `amount=487.00, direction="debit"` | C5 |
| `currency` | `str` | no | ISO-4217 uppercase; defaults to `config.currency_default` | 3 uppercase letters | C5 |
| `direction` | `"debit" \| "credit"` | no | — | Exactly one of the two | C5, from the debit/credit columns or the amount sign |
| `source_account` | `str` | yes | e.g. `HDFC-XXXX1234`; `null` when the statement does not disclose it | Masked to last 4 digits at parse time (§13) | C4 hint → C5 canonical |
| `source_file` | `str` | no | basename only, e.g. `hdfc_aug2026.pdf` | Non-empty | C4 |
| `category` | `str` | no | a key of `CategorySet`, or `"Uncategorized"` | Must exist in `categories.yaml` | C6 |
| `category_source` | `"rule" \| "user_override" \| "uncategorized"` | no | — | Consistent with `category`: `Uncategorized` ⇒ `uncategorized` | C6 |
| `is_transfer` | `bool` | no | — | `true` ⇒ excluded from `total_spend` and `total_income` (FR11) | C5, may be confirmed by C6 rule flag |
| `needs_review` | `bool` | no | — | `true` when OCR confidence < threshold (FR3), no rule matched (FR8), or currency ≠ run currency (EC4) | C4/C5/C6 |
| `id` *(added)* | `str` | no | 16-hex-char digest of `(date, raw_description, amount, direction, source_file, row_index)` | Stable across reruns of the same input | C5. **Serves FR20/FR19** — the browser needs a key to attach a correction to, and it must survive a re-run |
| `merchant_key` *(added)* | `str` | no | normalized merchant identity, e.g. `SWIGGY` | Uppercase, ≤ 60 chars | C6. **Serves FR9/AC4** — this is what a correction is remembered against, so it generalizes to next month's different reference number |
| `confidence` *(added)* | `float` | no | `0.0`–`1.0`; `1.0` for non-OCR | — | C4. **Serves FR3/NFR6** — drives the review flag and the report's confidence badge |
| `original_amount` *(added)* | `Decimal` | yes | present only for foreign-currency rows | `> 0` when present | C5. **Serves EC4** — retains the foreign amount when a home-currency equivalent was found |
| `original_currency` *(added)* | `str` | yes | ISO-4217 | 3 uppercase letters when present | C5. **Serves EC4** |
| `row_index` *(added)* | `int` | no | 0-based position within its source file | `>= 0` | C4. **Serves FR5/NFR6** — lets a diagnostic point at an exact row |

**Example** (the spec's own, with the added fields populated):

```json
{
  "schema_version": 1,
  "id": "8f2c41ad9be07c53",
  "date": "2026-08-14",
  "description": "SWIGGY*ORDER 4471",
  "raw_description": "POS SWIGGY*ORDER 4471 BANGALORE IN",
  "merchant_key": "SWIGGY",
  "amount": "487.00",
  "currency": "INR",
  "original_amount": null,
  "original_currency": null,
  "direction": "debit",
  "source_account": "HDFC-XXXX1234",
  "source_file": "hdfc_aug2026.pdf",
  "row_index": 42,
  "category": "Food & Dining",
  "category_source": "rule",
  "is_transfer": false,
  "needs_review": false,
  "confidence": 1.0
}
```

> Amounts serialize as JSON **strings**, not numbers. `487.00` as a JSON number round-trips
> through a float in most parsers and can come back as `486.99999999999994`. The string
> form is exact and is what `Decimal` consumes directly (ADR-0008).

**Lifecycle.** Created in C5, enriched in C6, read-only thereafter. Not persisted
individually — persisted as rows of `transactions.csv` and as the JSON data island inside
`report.html`.

---

### 1.2 `MonthlySummary` — the derived answer

The spec's §10.2 shape is **frozen**; additions are marked.

| Field | Type | Null? | Notes |
|---|---|---|---|
| `month` | `str` | no | `YYYY-MM` |
| `currency` *(added)* | `str` | no | The run currency all totals are expressed in. **Serves EC4** — without it, `total_spend: 42350.00` is ambiguous |
| `total_spend` | `Decimal` | no | Sum of non-transfer debits in `currency` (FR12) |
| `total_income` | `Decimal` | no | Sum of non-transfer credits; `0` when the statement has none (FR12) |
| `net` | `Decimal` | no | `total_income - total_spend`; may be negative |
| `top_category` | `TopCategory` | yes | `null` only when `total_spend == 0` (EC5). Otherwise `{name, amount, percent_of_spend}` (FR13) |
| `by_category` | `list[CategorySpend]` | no | Sorted by `amount` descending. Includes zero-spend categories with `amount: 0` so FR14 history stays complete; the chart filters them (EC6) |
| `top_transactions` | `list[TransactionRef]` | no | Exactly `min(5, n)` largest non-transfer debits (FR15) |
| `uncategorized_count` | `int` | no | Count of `category == "Uncategorized"` (NFR6, AC5) |
| `uncategorized_amount` *(added)* | `Decimal` | no | **Serves NFR6/AC5** — a count alone hides whether the gap is ₹50 or ₹5,000 |
| `excluded_files` | `list[ExcludedFile]` | no | `{file, reason}` (FR5, AC7) |
| `needs_review_count` *(added)* | `int` | no | **Serves FR3/NFR6** — low-OCR-confidence and foreign-currency rows, which are a different problem from "uncategorized" |
| `transfers_total` *(added)* | `Decimal` | no | **Serves FR11** — proves to the user what was excluded from spend, rather than it silently vanishing |
| `transaction_count` *(added)* | `int` | no | **Serves NFR2/AC1** — the merge evidence for multi-file runs |
| `accounts` *(added)* | `list[AccountSummary]` | no | `{source_account, source_file, txn_count, spend}`. **Serves US6/FR6/AC1** — shows the merge actually happened |
| `previous_month` *(added)* | `str \| null` | yes | Which month FR14 compared against; `null` on a first run |
| `generated_at` *(added)* | `datetime` | no | Local ISO-8601. **Serves NFR7** — dates the artifact |
| `schema_version` *(added)* | `int` | no | `1`. **Serves FR14/NFR7** — next year's build must still read this year's history |

`CategorySpend`:

| Field | Type | Null? | Notes |
|---|---|---|---|
| `name` | `str` | no | Category key |
| `amount` | `Decimal` | no | `>= 0` |
| `percent` | `float` | no | Of `total_spend`, 1 dp. `0.0` when total is 0. Displayed values are apportioned by **largest remainder** so the rendered column sums to exactly 100.0 — naive per-row rounding sums to 100.1 on the example data below, and a summary table that does not add up destroys trust in every other number on the page. Chart geometry uses the exact `Decimal`, never the rounded value |
| `mom_change_percent` | `float` | yes | `null` when no prior month, or when prior was 0 and current > 0 (`mom_status: "new"`) (FR14) |
| `mom_status` *(added)* | `"up" \| "down" \| "flat" \| "new" \| "gone" \| "unavailable"` | no | **Serves FR14** — encodes the cases a bare percentage cannot express |
| `txn_count` *(added)* | `int` | no | **Serves FR19** — the drill-down needs to promise a count before opening |
| `slot` *(added)* | `int \| null` | yes | Chart colour slot 1–8, or `null` ⇒ rendered as "Other"/neutral. **Serves FR17/G4** — see [design-system.md](../../design/design-system/design-system.md) |

**Example** (the spec's own, completed):

```json
{
  "schema_version": 1,
  "month": "2026-08",
  "currency": "INR",
  "generated_at": "2026-09-01T20:14:07",
  "previous_month": "2026-07",
  "transaction_count": 214,
  "total_spend": "42350.00",
  "total_income": "65000.00",
  "net": "22650.00",
  "transfers_total": "18000.00",
  "top_category": {
    "name": "Rent/Housing",
    "amount": "15000.00",
    "percent_of_spend": 35.4
  },
  "by_category": [
    {"name": "Rent/Housing",  "amount": "15000.00", "percent": 35.4, "mom_change_percent": 0.0,  "mom_status": "flat", "txn_count": 1,  "slot": 1},
    {"name": "Food & Dining", "amount": "14400.00", "percent": 34.0, "mom_change_percent": 12.5, "mom_status": "up",   "txn_count": 63, "slot": 2},
    {"name": "Groceries",     "amount": "5820.00",  "percent": 13.7, "mom_change_percent": -8.2, "mom_status": "down", "txn_count": 11, "slot": 3},
    {"name": "Shopping",      "amount": "2100.00",  "percent": 5.0,  "mom_change_percent": null, "mom_status": "new",  "txn_count": 4,  "slot": 5},
    {"name": "Utilities",     "amount": "1850.00",  "percent": 4.4,  "mom_change_percent": 2.1,  "mom_status": "up",   "txn_count": 3,  "slot": 6},
    {"name": "Transport",     "amount": "1760.00",  "percent": 4.2,  "mom_change_percent": 41.0, "mom_status": "up",   "txn_count": 28, "slot": 4},
    {"name": "Uncategorized", "amount": "1420.00",  "percent": 3.3,  "mom_change_percent": -22.4,"mom_status": "down", "txn_count": 6,  "slot": null},
    {"name": "Entertainment", "amount": "0.00",     "percent": 0.0,  "mom_change_percent": -100.0, "mom_status": "gone", "txn_count": 0, "slot": null}
  ],
  "top_transactions": [
    {"id": "1a4b...", "date": "2026-08-02", "description": "RENT TRANSFER",     "amount": "15000.00", "category": "Rent/Housing"},
    {"id": "2c9d...", "date": "2026-08-11", "description": "CROMA ELECTRONICS", "amount": "2100.00",  "category": "Shopping"},
    {"id": "3e1f...", "date": "2026-08-05", "description": "BIGBASKET",         "amount": "1980.00",  "category": "Groceries"},
    {"id": "4b7a...", "date": "2026-08-18", "description": "BESCOM ELECTRICITY","amount": "1415.00",  "category": "Utilities"},
    {"id": "5d2c...", "date": "2026-08-23", "description": "SWIGGY*ORDER 8812", "amount": "1180.00",  "category": "Food & Dining"}
  ],
  "uncategorized_count": 6,
  "uncategorized_amount": "1420.00",
  "needs_review_count": 3,
  "accounts": [
    {"source_account": "HDFC-XXXX1234", "source_file": "hdfc_aug2026.pdf",  "txn_count": 168, "spend": "31200.00"},
    {"source_account": "ICICI-XXXX9087","source_file": "icici_aug2026.csv", "txn_count": 46,  "spend": "11150.00"}
  ],
  "excluded_files": [
    {"file": "corrupt_statement.pdf", "reason": "unreadable / password-protected"}
  ]
}
```

---

### 1.3 `Category` / `CategorySet`

| Field | Type | Null? | Notes |
|---|---|---|---|
| `name` | `str` | no | Display name **and** key, e.g. `Food & Dining`. Unique, case-sensitive |
| `keywords` | `list[str]` | no | Case-insensitive substring matches; may be empty |
| `patterns` *(added)* | `list[str]` | no | Python regexes, tried after keywords. **Serves FR7** — `"ATM WDL \d+"` cannot be a keyword |
| `is_transfer` | `bool` | no | Default `false`. `true` ⇒ excluded from spend (FR11) |
| `is_income` *(added)* | `bool` | no | Default `false`. **Serves OQ3/FR12** — marks a category whose credits count as income rather than transfers |
| `slot` *(added)* | `int \| null` | yes | Chart colour slot 1–8. **Serves FR17/G4** — see below |
| `order` *(added)* | `int` | no | Declaration order; the tie-breaker in the rule ladder. **Serves FR7 determinism** |

**The 12 default categories (FR10)**, with their default chart slots:

| Category | Slot | `is_transfer` | `is_income` |
|---|---|---|---|
| Rent/Housing | 1 | false | false |
| Food & Dining | 2 | false | false |
| Groceries | 3 | false | false |
| Transport | 4 | false | false |
| Shopping | 5 | false | false |
| Utilities | 6 | false | false |
| EMI/Loan Payments | 7 | false | false |
| Healthcare | 8 | false | false |
| Entertainment | `null` | false | false |
| Investments/Savings | `null` | false | false |
| Transfers/Self | `null` | **true** | false |
| Uncategorized | `null` (reserved style) | false | false |

**Why only 8 slots.** The validated categorical palette carries 8 distinguishable hues; a
9th generated hue is not colour-blind-safe (measured, see
[design-system.md](../../design/design-system/design-system.md)). Categories with
`slot: null` render in the neutral "Other" fill in the pie and keep their own row in the
table, where the name — not the colour — carries identity. The user can rebind slots in
`categories.yaml`, which is required anyway because FR10 lets them add and rename
categories.

---

### 1.4 `Correction`

| Field | Type | Null? | Notes |
|---|---|---|---|
| `merchant_key` | `str` | no | The lookup key (FR9). Uppercase |
| `category` | `str` | no | Target category; must exist in `categories.yaml` |
| `scope` | `"merchant" \| "transaction"` | no | `merchant` (default) applies to all future matches — this is what AC4 requires. `transaction` pins one `Transaction.id` only |
| `transaction_id` | `str` | yes | Required when `scope == "transaction"` |
| `created_at` | `datetime` | no | For last-write-wins and for the audit trail |
| `source_month` | `str` | no | Which report the correction came from |
| `example_description` | `str` | no | A real `raw_description` that produced this key, so the file is human-readable |

**Lifecycle.** Created in the browser (FR20) → exported as a patch → harvested by C3 →
merged into `corrections.json` → applied by C6 on this and every later run (AC4). Superseded
entries move into `history[]` rather than being deleted, so a wrong correction is
recoverable.

---

### 1.5 `BankProfile`

| Field | Type | Null? | Notes |
|---|---|---|---|
| `name` | `str` | no | Profile key, e.g. `hdfc_savings_csv` |
| `applies_to` | `list[str]` | no | `["csv", "xlsx"]` or `["pdf"]` |
| `match.headers_any` | `list[str]` | no | Headers whose presence identifies this profile (FR4) |
| `match.filename_contains` | `list[str]` | no | Secondary hint, e.g. `["hdfc"]` |
| `columns.*` | `str \| null` | — | Maps `date`, `description`, `debit`, `credit`, `amount`, `balance`, `reference` to source header names |
| `date_order` | `"DMY" \| "MDY" \| "YMD"` | no | Never inferred — ambiguity here silently corrupts every date |
| `date_formats` | `list[str]` | no | strptime patterns tried in order |
| `decimal_separator` | `"." \| ","` | no | Default `.` |
| `thousands_separator` | `str` | no | Default `,` |
| `amount_sign` | `"debit_negative" \| "separate_columns" \| "dr_cr_suffix"` | no | How the file encodes direction |
| `account_number_regex` | `str \| null` | yes | Extracts `source_account` from a header line |
| `skip_rows_matching` | `list[str]` | no | Regexes for footer/summary rows like `"Opening Balance"` |
| `pdf_table.*` | object | yes | For PDF profiles: expected column headers, x-tolerance, and the header/footer crop |

---

### 1.6 `AccountRef`

Serves FR11 and OQ3 — the tool cannot tell "salary landing in my savings account" from
"spend" unless it knows which accounts are the user's own.

| Field | Type | Null? | Notes |
|---|---|---|---|
| `id` | `str` | no | `HDFC-XXXX1234` |
| `label` | `str` | no | `HDFC Savings` |
| `kind` | `"bank" \| "credit_card"` | no | Credit-card statements are a linked account in the same monthly view (ADR-0012) |
| `own` | `bool` | no | `true` ⇒ movements to/from it are internal transfers, not spend or income |
| `aliases` | `list[str]` | no | Strings that appear in counterparty descriptions, e.g. `["XXXX1234", "MADHUBALA J"]` |

---

## 2. On-disk layout

```
<workspace>/
├─ config/
│  ├─ config.yaml            settings                            (FR22)
│  ├─ categories.yaml        rules                               (FR7, FR10)
│  ├─ bank_profiles.yaml     column maps + PDF templates         (FR4, §11)
│  ├─ accounts.yaml          the user's own accounts             (FR11, OQ3)
│  └─ corrections.json       learned overrides  [tool-written]   (FR9)
├─ input/
│  ├─ hdfc_aug2026.pdf
│  ├─ icici_aug2026.csv
│  └─ corrections-2026-08.json     dropped from the browser      (FR20 -> AC4)
├─ output/
│  ├─ 2026-07/  report.html · transactions.csv · summary.json · run-log.txt
│  └─ 2026-08/  report.html · transactions.csv · summary.json · run-log.txt
└─ archive/
   └─ corrections/ corrections-2026-08.applied-20260901-201407.json
```

`output/<YYYY-MM>/` is **append-only across months** (NFR7). Re-running the same month
replaces that month's files only.

---

## 3. File formats, with concrete examples

### 3.1 `config/config.yaml` (FR22)

```yaml
schema_version: 1

paths:
  input: ./input
  output: ./output
  archive: ./archive

currency:
  default: INR
  locale: en-IN

dedupe:                                   # FR6, EC1
  key: [date, description_normalized, amount, direction]
  cross_file_only: true                   # same key within ONE file = a real repeat charge
  window_days: 0

transfers:                                # FR11, OQ3
  detect_pairs: true
  window_days: 3
  amount_tolerance: 0.00

ocr:                                      # FR3
  enabled: true
  language: eng
  dpi: 300
  confidence_threshold: 0.75
  tesseract_path: null                    # null = look on PATH

report:                                   # FR16, FR17
  open_browser: true
  theme: auto                             # auto | light | dark
  top_n_slices: 8                         # beyond this, fold into "Other"
  min_slice_percent: 1.0                  # smaller slices fold into "Other" too
  show_transfers_panel: true
  keep_reruns: false

logging:
  level: INFO
  redact_account_numbers: true            # NOT overridable at runtime (§13)
```

### 3.2 `config/categories.yaml` (FR7, FR10)

A superset of the spec's §10.3 example — the spec's exact keys still parse unchanged.

```yaml
schema_version: 1

categories:
  Rent/Housing:
    slot: 1
    keywords: ["rent", "landlord", "society maintenance", "brokerage"]
  Food & Dining:
    slot: 2
    keywords: ["swiggy", "zomato", "restaurant", "cafe", "dominos", "starbucks"]
  Groceries:
    slot: 3
    keywords: ["bigbasket", "dmart", "grocery", "blinkit", "zepto", "reliance fresh"]
  Transport:
    slot: 4
    keywords: ["uber", "ola", "irctc", "fuel", "petrol", "rapido", "metro"]
    patterns: ["FASTAG\\s*RECHARGE"]
  Shopping:
    slot: 5
    keywords: ["myntra", "flipkart", "croma", "decathlon", "nykaa"]
  Utilities:
    slot: 6
    keywords: ["bescom", "electricity", "airtel", "jio", "broadband", "gas", "water"]
  EMI/Loan Payments:
    slot: 7
    keywords: ["emi", "loan", "hdfc ergo emi"]
    patterns: ["ACH\\s*D[Rr].*LOAN"]
  Healthcare:
    slot: 8
    keywords: ["apollo", "pharmacy", "hospital", "clinic", "practo", "1mg"]
  Entertainment:
    slot: null
    keywords: ["netflix", "spotify", "bookmyshow", "hotstar", "prime video"]
  Investments/Savings:
    slot: null
    keywords: ["zerodha", "groww", "sip", "mutual fund", "recurring deposit"]
  Transfers/Self:
    slot: null
    is_transfer: true                     # FR11
    keywords: ["self transfer", "own account", "credit card payment", "cc bill"]
  Uncategorized:
    slot: null
    keywords: []                          # never matched by rule; the FR8 fallback

overrides:                                # exact merchant_key -> category, beats keywords
  "amazon pay": "Shopping"
  "amazon": "Shopping"
```

### 3.3 `config/bank_profiles.yaml` (FR4, §11.2)

```yaml
schema_version: 1

profiles:
  - name: hdfc_savings_csv
    applies_to: [csv, xlsx]
    match:
      headers_any: ["Narration", "Withdrawal Amt.", "Deposit Amt.", "Chq./Ref.No."]
      filename_contains: ["hdfc"]
    columns:
      date: "Date"
      description: "Narration"
      debit: "Withdrawal Amt."
      credit: "Deposit Amt."
      balance: "Closing Balance"
      reference: "Chq./Ref.No."
    date_order: DMY
    date_formats: ["%d/%m/%y", "%d/%m/%Y"]
    amount_sign: separate_columns
    account_number_regex: "Account No\\s*[:\\-]\\s*(\\d{6,})"
    skip_rows_matching: ["^\\s*$", "Opening Balance", "STATEMENT SUMMARY"]

  - name: icici_savings_csv
    applies_to: [csv, xlsx]
    match:
      headers_any: ["Transaction Date", "Transaction Remarks", "Withdrawal Amount (INR )"]
      filename_contains: ["icici", "optransactionhistory"]
    columns:
      date: "Transaction Date"
      description: "Transaction Remarks"
      debit: "Withdrawal Amount (INR )"
      credit: "Deposit Amount (INR )"
      balance: "Balance (INR )"
    date_order: DMY
    date_formats: ["%d/%m/%Y"]
    amount_sign: separate_columns

  - name: generic_pdf_indian_savings           # the day-one default (ADR-0011)
    applies_to: [pdf]
    match:
      headers_any: ["Date", "Narration", "Withdrawal", "Deposit", "Balance"]
    columns:
      date: "Date"
      description: "Narration"
      debit: "Withdrawal"
      credit: "Deposit"
      balance: "Balance"
    date_order: DMY
    date_formats: ["%d/%m/%Y", "%d-%m-%Y", "%d %b %Y"]
    amount_sign: separate_columns
    pdf_table:
      strategy: lines                          # pdfplumber table strategy
      x_tolerance: 2
      header_row_contains: ["Date", "Balance"]
      drop_header_rows_after_first_page: true
      crop_top_pct: 0.0
      crop_bottom_pct: 0.06                    # strip the page footer
```

### 3.4 `config/accounts.yaml` (FR11, OQ3)

```yaml
schema_version: 1
accounts:
  - id: HDFC-XXXX1234
    label: HDFC Savings
    kind: bank
    own: true
    aliases: ["XXXX1234", "MADHUBALA J", "MADHUBALA JADHAV"]
  - id: ICICI-XXXX9087
    label: ICICI Savings
    kind: bank
    own: true
    aliases: ["XXXX9087"]
  - id: HDFC-CC-XXXX4412
    label: HDFC Credit Card
    kind: credit_card
    own: true
    aliases: ["XXXX4412", "CC PAYMENT"]
income_rules:                     # OQ3, see ADR-0013
  salary_keywords: ["salary", "sal cr", "payroll"]
  treat_unmatched_credit_from_own_account_as: transfer
```

### 3.5 `config/corrections.json` — tool-written (FR9)

```json
{
  "schema_version": 1,
  "updated_at": "2026-09-01T20:14:07",
  "corrections": [
    {
      "merchant_key": "AMAZON",
      "category": "Groceries",
      "scope": "merchant",
      "transaction_id": null,
      "created_at": "2026-09-01T20:12:55",
      "source_month": "2026-08",
      "example_description": "UPI/AMAZON PAY/9928311/PAYMENT"
    },
    {
      "merchant_key": "CROMA ELECTRONICS",
      "category": "Shopping",
      "scope": "transaction",
      "transaction_id": "2c9d5f10ab34ee71",
      "created_at": "2026-09-01T20:13:20",
      "source_month": "2026-08",
      "example_description": "POS CROMA ELECTRONICS BLR"
    }
  ],
  "history": [
    {
      "merchant_key": "AMAZON",
      "category": "Shopping",
      "scope": "merchant",
      "created_at": "2026-08-01T09:41:02",
      "superseded_at": "2026-09-01T20:12:55"
    }
  ]
}
```

### 3.6 `input/corrections-YYYY-MM.json` — browser-exported patch (FR20 → AC4)

Deliberately a *subset* of 3.5, so the browser never has to reproduce the full store.

```json
{
  "schema_version": 1,
  "kind": "expense-nutshell-corrections-patch",
  "month": "2026-08",
  "exported_at": "2026-09-01T20:12:40",
  "corrections": [
    {
      "merchant_key": "AMAZON",
      "category": "Groceries",
      "scope": "merchant",
      "transaction_id": null,
      "example_description": "UPI/AMAZON PAY/9928311/PAYMENT"
    }
  ]
}
```

Validation on harvest: `kind` must match exactly; every `category` must exist; every
`merchant_key` must be non-empty. Any violation rejects the **whole** patch with a reason
(a half-applied patch is worse than none).

### 3.7 `output/<month>/transactions.csv` (FR21, AC9)

UTF-8 **with BOM** so Excel on Windows renders `₹` and non-ASCII merchant names correctly.
`RFC 4180` quoting. Column order is a frozen contract.

```csv
date,description,raw_description,merchant_key,amount,currency,direction,category,category_source,is_transfer,needs_review,confidence,source_account,source_file,transaction_id
2026-08-02,RENT TRANSFER,"NEFT DR-HDFC0001234-RENT TRANSFER-AUG",RENT TRANSFER,15000.00,INR,debit,Rent/Housing,rule,false,false,1.0,HDFC-XXXX1234,hdfc_aug2026.pdf,1a4b7c22de90f331
2026-08-14,SWIGGY*ORDER 4471,"POS SWIGGY*ORDER 4471 BANGALORE IN",SWIGGY,487.00,INR,debit,Food & Dining,rule,false,false,1.0,HDFC-XXXX1234,hdfc_aug2026.pdf,8f2c41ad9be07c53
2026-08-19,PAYTM*QR 88213,"UPI/PAYTM*QR 88213/PAYMENT FROM PHONE",PAYTM,240.00,INR,debit,Uncategorized,uncategorized,false,true,1.0,ICICI-XXXX9087,icici_aug2026.csv,c71e0d4498b2a065
2026-08-25,SALARY AUG 2026,"NEFT CR-ACME PAYROLL-SALARY AUG 2026",SALARY,65000.00,INR,credit,Investments/Savings,rule,false,false,1.0,HDFC-XXXX1234,hdfc_aug2026.pdf,fe33a1cc7b0d5e92
```

### 3.8 `output/<month>/summary.json`

Exactly the object in §1.2 above. This file is the **input to next month's FR14** and is
therefore versioned and never overwritten by a later month.

### 3.9 `output/<month>/report.html`

One file, no external references. Structure:

```html
<!doctype html>
<html data-theme="auto">
  <head>
    <meta charset="utf-8">
    <title>Expense Summary — August 2026</title>
    <style>/* design tokens as CSS custom properties + all layout CSS, inlined */</style>
  </head>
  <body>
    <!-- rendered markup: tiles, callout, chart canvas, tables, panels -->
    <script type="application/json" id="expense-data">
      {"summary": { ... }, "transactions": [ ... ], "run_report": { ... }}
    </script>
    <script>/* vendored Chart.js UMD, inlined */</script>
    <script>/* ~200 lines of report behaviour: chart init, drill-down, corrections tray */</script>
  </body>
</html>
```

**Invariant, asserted at write time:** the produced string contains no `src="http`,
`href="http`, `@import url(http`, or `fetch(` — the mechanical proof of NFR1 and AC8.

### 3.10 `output/<month>/run-log.txt`

```
2026-09-01 20:14:01 INFO  config      loaded 12 categories, 3 bank profiles, 3 accounts
2026-09-01 20:14:01 INFO  ingest      3 candidate files, 1 correction patch
2026-09-01 20:14:01 INFO  corrections applied patch corrections-2026-08.json (1 new, 0 rejected)
2026-09-01 20:14:02 INFO  parse       hdfc_aug2026.pdf -> pdf_text, profile=generic_pdf_indian_savings, 168 rows
2026-09-01 20:14:03 INFO  parse       icici_aug2026.csv -> tabular, profile=icici_savings_csv, 47 rows
2026-09-01 20:14:03 ERROR parse       corrupt_statement.pdf excluded: password-protected
2026-09-01 20:14:03 INFO  normalize   215 rows -> 214 transactions (1 duplicate removed, 0 row errors)
2026-09-01 20:14:03 INFO  transfers   4 transactions flagged is_transfer (18000.00 INR excluded from spend)
2026-09-01 20:14:03 INFO  categorize  208 by rule, 6 uncategorized, 2 need review
2026-09-01 20:14:03 INFO  analysis    total_spend=42350.00 INR, top=Rent/Housing 35.4%, prev=2026-07
2026-09-01 20:14:04 INFO  report      wrote report.html (612 KB), transactions.csv, summary.json
2026-09-01 20:14:04 INFO  run         completed in 3.1s
```

Account numbers are already masked at parse time; the logging handler re-applies `redact()`
as a second line of defence (§13).
