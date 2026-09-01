# Monthly Expense Summary Tool — Specification

**Type:** Spec-Driven Development (SDD) document
**Feature:** Read bank statements → summarize monthly expenses → visualize spending by category (pie chart) → support a spend decision
**Status:** Draft v1.0
**Owner:** Madhubala Jadhav
**Last updated:** 2026-08-28

---

## 1. Overview

This tool ingests a person's bank statements (PDF and/or CSV/Excel exports), extracts transactions, categorizes each expense, and produces a monthly summary with a pie chart showing which category consumes the largest share of spending. The output is a local, auto-generated HTML report opened in the browser — no server, no account, no data leaving the user's machine.

The purpose is decision support: by the end of each month, the user should be able to look at one screen and answer "where did most of my money go, and should I cut it back?"

## 2. Problem Statement

Bank statements list raw transactions with no categorization or visualization. Manually reviewing a PDF or CSV each month to understand spending patterns is slow and error-prone, and it's hard to spot which category is dominating spend without doing the math by hand. There is no existing personal tool that:

- Accepts statements in the formats banks actually export (PDF, CSV, Excel)
- Automatically categorizes transactions
- Visually surfaces the single largest expense category
- Runs locally, privately, and repeatably every month

## 3. Goals

- G1: Parse bank statements in PDF and CSV/Excel format into a normalized transaction list.
- G2: Categorize each transaction (e.g., Food, Rent, Shopping, Utilities, Transport, EMI/Loan, Entertainment, Transfers, Uncategorized).
- G3: Produce a monthly summary: total spend, total income (optional), spend by category, top category, month-over-month change.
- G4: Render a pie chart of spend-by-category, with the largest slice visually emphasized.
- G5: Present the summary and chart in a simple local HTML report/dashboard that opens automatically after each run — no CLI-only output.
- G6: Let the user correct miscategorized transactions and have that correction remembered for future runs.
- G7: Run entirely offline/locally; no statement data is uploaded anywhere.

## 4. Non-Goals

- NG1: Not a budgeting app with goals, alerts, or forecasting (may be a future enhancement).
- NG2: Not a multi-user or cloud-synced product in v1 — single user, single machine.
- NG3: Not connecting to bank APIs / screen-scraping bank websites — input is a manually downloaded statement file.
- NG4: Not handling investment/portfolio statements (brokerage, mutual funds) — bank account statements only.
- NG5: Not guaranteeing 100% automatic categorization accuracy — a manual review/correction step is part of the intended workflow, not a failure state.

## 5. Users & Personas

**Primary persona:** An individual who downloads their own bank statement(s) once a month and wants a fast, visual answer to "what did I overspend on?" without building a spreadsheet by hand. Comfortable running a local script (e.g., double-clicking a `.bat`/`.command` file or running one command); not necessarily a programmer.

## 6. User Stories

**US1 — Load statements**
As a user, I want to drop my bank statement file(s) for the month into a folder and run the tool, so that I don't have to manually type transactions.

**US2 — See a categorized summary**
As a user, I want to see total spend broken down by category, so that I understand where my money went this month.

**US3 — See the top expense visually**
As a user, I want a pie chart showing spend by category with the largest category clearly highlighted, so that I can immediately identify my biggest expense driver.

**US4 — Make a decision**
As a user, I want the report to explicitly call out the top category and its share of total spend (e.g., "Food & Dining: 34% of your spend — your largest category"), so that I have a clear signal to act on, not just a chart to interpret myself.

**US5 — Fix miscategorized transactions**
As a user, I want to reassign a transaction's category (e.g., "Amazon" from "Shopping" to "Groceries"), so that future reports are more accurate.

**US6 — Handle multiple accounts**
As a user, I want to combine statements from more than one bank/account into a single monthly summary, so that I see my total spend, not just one account's.

**US7 — Compare months**
As a user, I want to see this month's spend compared to last month's (by category), so that I can tell if a category is trending up.

## 7. Functional Requirements

Requirements use EARS-style phrasing (WHEN / IF / THE SYSTEM SHALL) so each is independently testable.

### 7.1 Input & Parsing

- FR1: WHEN the user places one or more files (`.pdf`, `.csv`, `.xlsx`, `.xls`) into the configured input folder and runs the tool, THE SYSTEM SHALL detect the file type and select the matching parser.
- FR2: WHEN a PDF statement contains a text-based transaction table, THE SYSTEM SHALL extract transaction rows (date, description, amount, debit/credit indicator) without manual retyping.
- FR3: IF a PDF is a scanned image with no extractable text, THEN THE SYSTEM SHALL fall back to OCR extraction and flag any row with low OCR confidence for manual review.
- FR4: WHEN a CSV or Excel file is provided, THE SYSTEM SHALL map its columns to the normalized schema (date, description, amount, type) using a configurable column-mapping profile per bank, since export formats differ by bank.
- FR5: IF a statement file cannot be parsed at all, THEN THE SYSTEM SHALL skip that file, continue processing the rest, and list the failed file by name in the report with the reason.
- FR6: WHEN multiple statement files covering the same month are provided (e.g., two accounts), THE SYSTEM SHALL merge their transactions into one monthly dataset and de-duplicate exact repeated entries (same date, amount, and description) that indicate the same file was accidentally added twice.

### 7.2 Categorization

- FR7: WHEN a transaction is parsed, THE SYSTEM SHALL assign it a category using rule-based matching against a user-editable keyword/merchant mapping file.
- FR8: IF no rule matches a transaction description, THEN THE SYSTEM SHALL assign it the category "Uncategorized" rather than guessing silently.
- FR9: WHEN the user reassigns a transaction's category through the report or a correction file, THE SYSTEM SHALL persist that mapping (by merchant/description pattern) so it applies automatically in future months.
- FR10: THE SYSTEM SHALL support at minimum these default categories: Food & Dining, Groceries, Rent/Housing, Utilities, Transport, Shopping, Entertainment, Healthcare, EMI/Loan Payments, Transfers/Self, Investments/Savings, Uncategorized — and SHALL allow the user to add/rename/remove categories.
- FR11: THE SYSTEM SHALL exclude or separately label internal transfers (e.g., between the user's own accounts, credit card bill payments) so they don't inflate the "spend" total.

### 7.3 Summary & Analysis

- FR12: WHEN all transactions for a month are categorized, THE SYSTEM SHALL compute: total spend, total income/credits (if present), net, and spend-per-category (amount and % of total).
- FR13: THE SYSTEM SHALL identify the single category with the highest spend as the "top expense category" and state its amount and percentage of total spend explicitly in the report.
- FR14: IF prior months' reports exist, THEN THE SYSTEM SHALL show month-over-month change per category (▲/▼ and %).
- FR15: THE SYSTEM SHALL list the top 5 individual largest transactions of the month regardless of category, so unusually large one-off expenses are visible even if their category isn't the overall top category.

### 7.4 Output / UI

- FR16: WHEN processing completes, THE SYSTEM SHALL generate a self-contained local HTML report and open it automatically in the user's default browser.
- FR17: THE SYSTEM SHALL render a pie chart of spend-by-category in the report, with the top category visually distinguished (e.g., pulled slice, distinct color, or callout label).
- FR18: THE SYSTEM SHALL display, alongside the chart: a summary table (category, amount, % of total), the top-category callout from FR13, and the month-over-month comparison from FR14 when available.
- FR19: THE SYSTEM SHALL allow the user to hover/click a pie slice to see the list of transactions contributing to that category (drill-down).
- FR20: THE SYSTEM SHALL provide an in-report control (e.g., a dropdown or inline edit) for the user to change a transaction's category, satisfying US5 without editing files by hand.
- FR21: THE SYSTEM SHALL also write the categorized transaction data to a CSV file alongside the HTML report, so the underlying data can be opened in a spreadsheet if desired.

### 7.5 Configuration & Repeatability

- FR22: THE SYSTEM SHALL read all user-specific settings (input folder, output folder, category rules, bank column-mapping profiles) from a single human-editable config file (e.g., YAML/JSON), not hardcoded values.
- FR23: THE SYSTEM SHALL be runnable with a single command/double-click action per month, requiring no code changes for routine use.

## 8. Non-Functional Requirements

- NFR1 (Privacy): All statement data and generated reports SHALL stay on the local machine. THE SYSTEM SHALL NOT transmit statement contents to any external network service.
- NFR2 (Performance): THE SYSTEM SHALL process a typical monthly statement (up to ~500 transactions across up to 5 files) in under 30 seconds on a standard laptop.
- NFR3 (Reliability): A single malformed row or unparseable file SHALL NOT crash the whole run (see FR5); the tool degrades gracefully and reports what it couldn't process.
- NFR4 (Portability): THE SYSTEM SHALL run on Windows, macOS, and Linux with the same codebase (Java 17 + standard cross-platform libraries; see [ADR-0014](../../../../docs/architecture/04-adr/ADR-0014-language-and-runtime-java-override.md)).
- NFR5 (Usability): A non-programmer user SHALL be able to run the monthly workflow (place files → run → view report) without reading source code, after a one-time setup.
- NFR6 (Accuracy transparency): THE SYSTEM SHALL make it visually obvious in the report which transactions are "Uncategorized," so accuracy gaps are surfaced, not hidden.
- NFR7 (Data retention): THE SYSTEM SHALL keep each month's report and CSV as a separate, dated artifact (not overwritten), so historical months remain available for comparison (FR14).

## 9. System Architecture

```
 ┌─────────────────────┐
 │  input/ folder       │  user drops statement files here (PDF/CSV/XLSX)
 └──────────┬───────────┘
            │
            ▼
 ┌─────────────────────┐
 │  Parser Layer        │  PDF parser (table + OCR fallback)
 │                       │  CSV/Excel parser (per-bank column mapping)
 └──────────┬───────────┘
            │  normalized transactions
            ▼
 ┌─────────────────────┐
 │  Normalization &      │  de-dupe, currency/date normalization,
 │  Transfer Detection   │  internal-transfer flagging
 └──────────┬───────────┘
            │
            ▼
 ┌─────────────────────┐
 │  Categorization Engine│  rule/keyword matching + learned overrides
 │                       │  (categories.yaml + corrections.json)
 └──────────┬───────────┘
            │
            ▼
 ┌─────────────────────┐
 │  Analysis Layer       │  totals, % by category, top category,
 │                       │  month-over-month diff, top transactions
 └──────────┬───────────┘
            │
            ▼
 ┌─────────────────────┐
 │  Report Generator     │  renders HTML (chart + tables) + CSV export
 └──────────┬───────────┘
            │
            ▼
 ┌─────────────────────┐
 │  output/YYYY-MM/      │  report.html (auto-opens in browser),
 │                       │  transactions.csv
 └─────────────────────┘
```

**Suggested stack (local, no server):**

| Layer | Technology |
|---|---|
| Language | Java 17 ([ADR-0014](../../../../docs/architecture/04-adr/ADR-0014-language-and-runtime-java-override.md)) |
| PDF table extraction | Apache PDFBox (text-based) with tess4j (Tesseract) as OCR fallback ([ADR-0016](../../../../docs/architecture/04-adr/ADR-0016-pdf-parsing-java-pdfbox-tess4j.md)) |
| CSV/Excel parsing | Apache Commons CSV, Apache POI ([ADR-0015](../../../../docs/architecture/04-adr/ADR-0015-tabular-parsing-java-apache-poi.md)) |
| Categorization | Rule engine over parsed rows, keyword/regex rules in `categories.yaml` |
| Money | `java.math.BigDecimal` with `RoundingMode.HALF_UP` ([ADR-0018](../../../../docs/architecture/04-adr/ADR-0018-money-java-bigdecimal.md)) |
| Chart rendering | `Chart.js` (vendored, embedded interactive chart in HTML) |
| Report templating | Pebble → single self-contained `report.html` ([ADR-0019](../../../../docs/architecture/04-adr/ADR-0019-report-rendering-java-pebble.md)) |
| Config | Jackson YAML (`jackson-dataformat-yaml`) ([ADR-0020](../../../../docs/architecture/04-adr/ADR-0020-config-parsing-java-jackson-yaml.md)) |
| Packaging / CLI entry point | A single fat JAR (`java -jar ExpenseInNutshell.jar`) launched via `run.bat`/`run.command`/`run.sh`, no required flags for the default monthly workflow ([ADR-0017](../../../../docs/architecture/04-adr/ADR-0017-packaging-java-fat-jar.md)) |

This is a suggestion, not a mandate — any stack meeting the functional/non-functional requirements is acceptable, but the interfaces below (data model, file layout) should stay stable regardless of implementation choice.

## 10. Data Model

### 10.1 Transaction (normalized, post-parsing)

```json
{
  "date": "2026-08-14",
  "description": "SWIGGY*ORDER 4471",
  "raw_description": "POS SWIGGY*ORDER 4471 BANGALORE IN",
  "amount": 487.00,
  "currency": "INR",
  "direction": "debit",
  "source_account": "HDFC-XXXX1234",
  "source_file": "hdfc_aug2026.pdf",
  "category": "Food & Dining",
  "category_source": "rule",
  "is_transfer": false,
  "needs_review": false
}
```

- `direction`: `debit` | `credit`
- `category_source`: `rule` | `user_override` | `uncategorized`
- `needs_review`: true when OCR confidence was low (FR3) or no rule matched (FR8)

### 10.2 Monthly Summary (derived)

```json
{
  "month": "2026-08",
  "total_spend": 42350.00,
  "total_income": 65000.00,
  "net": 22650.00,
  "top_category": {
    "name": "Food & Dining",
    "amount": 14400.00,
    "percent_of_spend": 34.0
  },
  "by_category": [
    {"name": "Food & Dining", "amount": 14400.00, "percent": 34.0, "mom_change_percent": 12.5},
    {"name": "Rent/Housing", "amount": 15000.00, "percent": 35.4, "mom_change_percent": 0.0}
  ],
  "top_transactions": [
    {"date": "2026-08-02", "description": "RENT TRANSFER", "amount": 15000.00, "category": "Rent/Housing"}
  ],
  "uncategorized_count": 6,
  "excluded_files": [
    {"file": "corrupt_statement.pdf", "reason": "unreadable / password-protected"}
  ]
}
```

### 10.3 Category Rules (`categories.yaml`, user-editable)

```yaml
categories:
  Food & Dining:
    keywords: ["swiggy", "zomato", "restaurant", "cafe"]
  Groceries:
    keywords: ["bigbasket", "dmart", "grocery", "blinkit"]
  Rent/Housing:
    keywords: ["rent", "landlord"]
  Transport:
    keywords: ["uber", "ola", "irctc", "fuel", "petrol"]
  Transfers/Self:
    keywords: ["self transfer", "own account"]
    is_transfer: true
overrides:
  "amazon pay": "Shopping"
```

## 11. Input Handling Detail

### 11.1 PDF statements
- Attempt direct text/table extraction first (fast, exact).
- If the page has no extractable text layer (i.e., it's a scanned image), fall back to OCR.
- Support common Indian bank statement table layouts (Date, Narration/Description, Withdrawal, Deposit, Balance columns) as the default template; allow additional per-bank templates to be added via config without code changes.
- If a PDF is password-protected, prompt once for a password (not stored) rather than failing silently.

### 11.2 CSV / Excel statements
- Auto-detect header row and delimiter for CSV.
- Match columns to the normalized schema using a per-bank mapping profile (banks label columns differently, e.g., "Txn Date" vs "Date", "Withdrawal Amt." vs "Debit").
- If a file's columns don't match any known profile, prompt the user to map columns once and save that mapping as a new profile for future runs.

## 12. Edge Cases

- Duplicate transactions from re-adding the same file twice → de-duplicated (FR6).
- A transaction split across two lines in a PDF table (wrapped description) → parser must reassemble before categorization.
- Refunds/reversals (credit that offsets an earlier debit) → shown as income/credit, not netted silently against the original category, so the user can see both.
- Multi-currency transactions (foreign transaction on an INR account) → captured with original currency noted; converted amount used for totals if available in the statement, otherwise flagged `needs_review`.
- A month with zero parseable transactions → report still generates, states "no transactions found," and lists why each input file failed.
- A category with ₹0 spend in a given month → excluded from the pie chart (no empty slices) but still available for month-over-month history.

## 13. Security & Privacy

- No network calls involving statement content; the OCR/parsing/categorization pipeline runs fully offline.
- Statement files and generated reports remain in user-chosen local folders; the tool does not upload, sync, or phone home.
- If password-protected PDFs are supported, the password is used in-memory only for that run and is never written to disk or logs.

## 14. Acceptance Criteria (Definition of Done for v1)

- [ ] AC1: Given a folder with one PDF and one CSV bank statement for the same month, running the tool produces a single merged monthly report.
- [ ] AC2: The generated HTML report opens automatically and displays a pie chart of spend by category.
- [ ] AC3: The report explicitly states the top spending category, its amount, and its % of total spend (not just visually implied by chart size).
- [ ] AC4: Reassigning a transaction's category in the report persists and applies automatically the next time the tool runs on a new statement from the same merchant.
- [ ] AC5: Uncategorized transactions are visibly flagged in both the summary table and a count/callout.
- [ ] AC6: Running the tool on a second month produces a report showing month-over-month % change per category versus the first month.
- [ ] AC7: A corrupted/unreadable file in the input folder does not stop the tool from processing the other valid files, and is listed by name with a reason in the report.
- [ ] AC8: No statement data leaves the local machine during processing (verifiable via no outbound network calls in the parsing/categorization code path).
- [ ] AC9: The categorized transaction list is also available as a CSV in the output folder.

## 15. Future Enhancements (out of scope for v1)

- Budget targets per category with over/under-budget alerts.
- Automatic bank-statement download via secure bank API integrations (would require explicit user consent and credential handling design).
- Multi-user household view (shared expenses, splitting).
- Trend forecasting ("at this rate, you'll spend X by month-end").
- Mobile-friendly report view / PWA.
- Investment and credit-card-only statement support as first-class input types.

## 16. Open Questions

- Which specific banks' statement formats need day-one support (column headers/layouts differ significantly by bank)?
- Should credit card statements be treated as a separate input type with their own due-date/billing-cycle logic, or merged into the same monthly view as a linked account?
- What should count as "income" vs. an excluded internal transfer when a salary account and a savings account both receive transfers from each other?