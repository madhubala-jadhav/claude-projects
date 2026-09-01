# S07 — Empty & Degraded Report States

**Frame name in Figma:** `07 — Empty & Degraded States`
**Page:** `03 Diagnostics & Setup`
**Frame size:** 1280 × 1600 — four stacked variants of `report.html`, each labelled.

---

## Purpose

Prove NFR3 visually: **the report always renders.** Even when every input file failed, the
user gets a real document that names what went wrong and what to do next — never a blank
page, never a stack trace, never a missing file.

This screen exists because EC5 and AC7 are the two requirements most likely to be treated as
"handle later" during implementation, and the way to prevent that is to make the empty state
a designed artifact with the same care as the dashboard.

## Requirements served

| ID | How |
|---|---|
| **EC5** | A month with zero parseable transactions still produces a report saying so, listing why each file failed |
| **FR5** | Every skipped file is listed by name with a reason |
| **AC7** | A corrupt file does not stop the run; it is named with a reason |
| **NFR3** | Degrades, never crashes; exit code 0 on a degraded run |
| **NFR5** | Every message names a next action in plain language |
| **NFR6** | Failures are surfaced, never hidden |

---

## Variant A — No input files at all

Frame `07a`. Reached when `input/` exists but contains no statements.

Full-width card, `surface.raised`, centred, max-width 640, `padding` `spacing.huge`:

- 64 px folder glyph drawn as vector (no icon font — NFR1), `border.strong` stroke.
- `textStyle.h2` / `text.primary`: **"No statement files found."**
- `textStyle.bodyLg` / `text.secondary`:
  *"Put your bank statements in this folder and run the tool again."*
- `textStyle.mono` on `surface.sunken`, `radius.md`, selectable:
  `C:\Users\Madhubala\ExpenseInNutshell\input\`
  with a `Button/Secondary` **"Copy path"**.
- `textStyle.bodySm` / `text.muted`:
  *"Accepted: .pdf, .csv, .xlsx, .xls — download them from your bank's website."*

No chart region, no empty tiles, no zeroed table. Rendering a ₹0.00 dashboard would be a
lie dressed as data.

---

## Variant B — Files present, none parseable (EC5 + AC7)

Frame `07b`. **The most important variant on this screen.** The tool ran, tried everything,
and got nothing — and it still owes the user an explanation.

### Header
Unchanged from [S01](01-monthly-dashboard.md): **"Expense Summary — August 2026"** with the
run stamp reading **"Generated 1 Sep 2026, 20:14 · 0 transactions · 3 files could not be read"**.

### Callout — replaces the top-category callout, `surface.dangerSubtle`

- Eyebrow, `text.danger`: **"NOTHING COULD BE READ"**
- `textStyle.h2`: **"No transactions found in August 2026."**
- `textStyle.bodyLg` / `text.secondary`: *"All 3 files in your input folder failed. Each one
  is listed below with the reason and what to try."*

### Skipped-files table — full width, `SkippedFileRow` instances

| File | Reason | Code | What to do |
|---|---|---|---|
| `corrupt_statement.pdf` | unreadable / password-protected | `PARSE-201` | *Re-download it, or run from a terminal so the tool can ask for the password.* |
| `scan_july.pdf` | scanned PDF and OCR is not installed | `PARSE-202` | *Install Tesseract to read scanned statements — see the README — or ask your bank for a text PDF.* |
| `budget.docx` | unsupported file type | `PARSE-206` | *Only .pdf, .csv, .xlsx and .xls are read. Remove this file from the input folder.* |

Reason strings come verbatim from the error taxonomy
([03](../../docs/architecture/03-interfaces-and-contracts.md) §6.2). The code is shown
because it is the string the user will search for in the docs. The "what to do" column is
the design's own addition and is what turns AC7 from a compliance checkbox into something
useful.

### Footer
Unchanged, including the CSV link — which points at an empty-but-valid
`transactions.csv` with only its 15 header columns, because F3 promises the file exists
(AC9) and a consumer's spreadsheet should not break on a bad month.

**Exit code is 0.** The run completed; it just had nothing to summarize. This is stated in
`textStyle.caption` under the table: *"The tool finished normally — nothing crashed."*

---

## Variant C — Partial success (the realistic case)

Frame `07c`. One of three files failed. This is the normal degraded run and it is **the
full [S01](01-monthly-dashboard.md) dashboard**, with two additions:

1. A `surface.dangerSubtle` strip directly beneath the stat tiles, above the callout:
   **"⚠ 1 of 3 files could not be read, so ₹— from `corrupt_statement.pdf` is missing from
   these totals."** with a link **"See why"** scrolling to the skipped-files panel in
   [S05](05-sources-and-top-transactions.md) §3c.
2. The run stamp includes **"· 1 file skipped"**.

**The strip sits above the callout, not below the fold.** The user is about to make a
decision from a number that is incomplete; they have to know that before they read it, not
after.

---

## Variant D — Row-level and field-level degradation

Frame `07d`. The report is complete and correct, but individual units were demoted. Shown as
a `surface.warningSubtle` card between the category table and the top-5 panel:

> **3 rows were not fully readable.**
>
> | Where | What | Code |
> |---|---|---|
> | `hdfc_aug2026.pdf` page 4, row 61 | date could not be parsed — row skipped | `ROW-301` |
> | `icici_aug2026.csv` row 22 | row has neither a debit nor a credit amount — row skipped | `ROW-303` |
> | `hdfc_aug2026.pdf` page 7, row 12 | amount read at 61% confidence — kept, please verify | `FIELD-401` |
>
> *Two rows were skipped and are not in your totals. One was kept and flagged for review.*

The distinction between **ROW** (dropped, affects totals) and **FIELD** (kept, degraded,
flagged) is stated in the summary line, because they have different consequences for whether
the user should trust the headline number. Field-level rows link to
[S06](06-uncategorized-review.md).

---

## What is deliberately absent

| Not designed | Why |
|---|---|
| A full-page crash screen | An unhandled exception (exit 4) still writes a minimal report via C8's fallback template: the summary numbers it managed to compute, plus the diagnostics. There is no design for "the report failed to render" because the architecture does not permit that outcome to be silent. |
| A retry button | The report is a static `file://` document; it cannot re-run the tool. A button that looked like it could would be a lie. The instruction is always "run the tool again", with the command shown. |
| Progress indicators | Everything is already computed before the page opens. Progress lives in the console, [S08](08-setup-and-run-console.md). |
| An error toast or modal | Errors are not transient here; they are content. They belong in the document, in a place the user can scroll back to next month. |

---

## Interaction notes

| Trigger | Behaviour |
|---|---|
| **Copy path** | Copies to clipboard, button label swaps to **"Copied ✓"** in `text.success` for 2 s. |
| Click an error code | Anchors to that code's entry in the bundled error reference at the bottom of the report — the docs travel with the artifact, since a `file://` report cannot link out to a website (NFR1). |
| Click a `SkippedFileRow` | Copies the filename. Deliberately not "open folder" — impossible from `file://`. |
| Keyboard | Every row and control focusable; the danger strip in Variant C receives focus first on load and is announced via `role="status"`. |
| Print | All variants print in full. The empty report is a valid record that a month was processed and found nothing — worth keeping (NFR7). |
