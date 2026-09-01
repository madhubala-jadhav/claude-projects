# S08 — Setup & Monthly Run Console

**Frame name in Figma:** `08 — Setup & Run Console`
**Page:** `03 Diagnostics & Setup`
**Frame size:** 880 × 1400 (`size.frame.console`) — three stacked terminal frames.

---

## Purpose

The console is a designed surface, not an accident of using a CLI. It is the **only** thing
the user looks at between double-clicking `run` and the browser opening — and on a bad run
it is where they find out why nothing opened.

Spec §5 describes the persona precisely: *"Comfortable running a local script … not
necessarily a programmer."* Every line below is written for that person. No tracebacks, no
logger names, no exception classes.

## Requirements served

| ID | How |
|---|---|
| **US1** | "Drop the files in a folder and run" — this is the run |
| **FR23** | One double-click, no flags, no code changes |
| **FR22** | First-run bootstrap writes the config files and says where they are |
| **NFR5** | A non-programmer completes the workflow without reading source |
| **FR5, AC7** | Skipped files are reported here as well as in the report |
| **FR16** | The console prints the report path, so the report is findable even if the browser fails |
| **NFR2** | Timings are printed, making the 30-second budget observable |
| **§11.1, §11.2** | The two permitted interactive prompts: PDF password, unknown columns |

---

## Type & colour

Everything is `textStyle.mono` (Roboto Mono in Figma / `ui-monospace` in a real terminal) at
12/1.65. The frame background is `surface.canvas`; in Figma each console frame is drawn as a
`Card` with `radius.lg` and a 3-dot title bar so it reads as a terminal without pretending to
be a screenshot of one.

`ConsoleLine` variants and their tokens:

| Variant | Prefix | Token |
|---|---|---|
| info | none | `text.secondary` |
| step | `▸` | `text.primary` |
| success | `✓` | `text.success` |
| warn | `⚠` | `text.warning` |
| error | `✕` | `text.danger` |
| prompt | `?` | `text.accent`, bold |
| path | none | `text.accent`, underlined |

Glyphs are Unicode, not an icon font, and every line is legible with colour stripped —
terminals vary and some users pipe output to a file.

---

## Frame 08a — First run (`setup` then `run`)

```
ExpenseInNutshell — first-time setup

▸ Checking for Python...                     found Python 3.12.4
▸ Creating a private environment...          done (12 s)
▸ Installing what the tool needs...          done (31 s)
▸ Checking for the OCR engine (optional)...  not found

⚠ Tesseract OCR is not installed.
  You only need it if your bank sends scanned (image) PDFs.
  Everything else works without it. Install later from:
  https://github.com/tesseract-ocr/tesseract

▸ Creating your workspace...
  ✓ config\config.yaml           settings
  ✓ config\categories.yaml       12 categories and their keywords - edit this to fit you
  ✓ config\bank_profiles.yaml    how to read HDFC, ICICI, SBI, Axis, Kotak exports
  ✓ config\accounts.yaml         list your own accounts here so transfers are not counted as spend
  ✓ input\                       put your statements here
  ✓ output\                      your reports will appear here

✓ Setup finished.

Next: put this month's statement files in
  C:\Users\Madhubala\ExpenseInNutshell\input\
then double-click run.bat
```

Three deliberate choices:

- **Each config file is described in the same line that creates it.** FR22 is only useful if
  the user knows which file to edit; a bare list of filenames is not documentation.
- **The missing OCR engine is a warning, not an error**, and the message says explicitly that
  everything else still works. A first-run yellow line that looks fatal is how a
  non-programmer decides the tool is broken.
- **`accounts.yaml` gets a full-sentence reason.** It is the file most likely to be ignored
  and the one whose absence causes the worst failure mode (R3 — transfers counted as spend).

---

## Frame 08b — A normal monthly run

```
ExpenseInNutshell v1.0                                   1 Sep 2026, 20:14

▸ Reading your settings...        12 categories, 3 bank profiles, 3 accounts
▸ Looking in input\...            3 files, 1 corrections file
✓ Applied 1 saved correction      AMAZON PAY -> Groceries

▸ Reading hdfc_aug2026.pdf...     168 transactions   (text, HDFC savings)   1.4 s
▸ Reading icici_aug2026.csv...     47 transactions   (ICICI savings)        0.2 s
✕ corrupt_statement.pdf           could not be read: unreadable / password-protected
  -> Re-download it, or run this from a terminal so it can ask for the password.

▸ Tidying up...                   215 -> 214 transactions (1 duplicate removed)
▸ Spotting internal transfers...  4 found, Rs 18,000.00 kept out of your spend
▸ Sorting into categories...      208 by rule, 6 need a category, 3 need a check
▸ Working out the totals...       Rs 42,350.00 spent, biggest: Rent/Housing 35.4%
▸ Comparing with July 2026...     up 6.7%

✓ Done in 3.1 s.

  Your report:  C:\Users\Madhubala\ExpenseInNutshell\output\2026-08\report.html
  Spreadsheet:  C:\Users\Madhubala\ExpenseInNutshell\output\2026-08\transactions.csv

▸ Opening it in your browser...
```

Notes:

- **Stage labels are verbs in plain English** — "Tidying up", "Spotting internal transfers"
  — not `normalize`, `detect_transfers`. The internal names live in `run-log.txt`
  ([02](../../docs/architecture/02-data-model.md) §3.10), which is for debugging; this is
  for the user.
- **The failed file is one `✕` line plus one indented next action**, and the run visibly
  continues afterwards. AC7's "does not stop the tool" is something the user should *watch
  happen*.
- **Amounts print as `Rs` in the console, `₹` in the report.** Windows consoles still
  mis-render `₹` under some code pages, and a mojibake character in the first line of output
  undermines confidence in everything after it. The report, which controls its own encoding,
  uses the real symbol.
- **Both paths are always printed, before the browser is launched** — so FR16 degrades
  cleanly if the launch fails.
- **Per-stage timings are printed** because NFR2 is a promise, and a promise nobody can
  observe is not kept.

Final line variants:

| Condition | Line |
|---|---|
| Browser opened | `▸ Opening it in your browser...` |
| Launch failed (`WARN-506`) | `⚠ Could not open a browser. Open the report path above yourself.` |
| `--no-open` | `▸ Not opening a browser (--no-open).` |
| OCR in progress | `▸ Reading scan_july.pdf (scanned)... page 3 of 12 — this takes a few minutes.` |

That last line is assumption **A4** made visible: OCR is outside the 30-second budget, so the
console explains the wait instead of appearing hung.

---

## Frame 08c — The two interactive prompts

Both are from the spec (§11.1, §11.2) and both are one-time.

### Password prompt (§11.1)

```
? hdfc_aug2026.pdf is password-protected.
  Enter the password (it is used once and never saved): ********

✓ Opened. 168 transactions.
```

Input is not echoed. The password is never written to `run-log.txt`, never passed as an
argument, and never stored (§13). The parenthetical says so at the moment of asking, because
that is when the user is deciding whether to trust the tool.

### Unknown bank columns (§11.2)

```
? I have not seen this file's layout before: kotak_aug2026.csv
  Its columns are:
    1  Date            2  Description     3  Debit
    4  Credit          5  Balance         6  Chq No

  Which column holds the transaction date?  [1] 1
  Which column holds the description?       [2] 2
  Which holds money going OUT?              [3] 3
  Which holds money coming IN?              [4] 4
  Are dates day-first (12/03 = 12 March)?   [Y/n] Y

✓ Saved as profile "kotak_aug2026_csv" in config\bank_profiles.yaml.
  You will not be asked about this bank again.
```

Every question offers a **guessed default in brackets** derived from header-name matching, so
the common case is five presses of Enter. The date-order question has no default and is asked
in words with an example, because `12/03/2026` is genuinely ambiguous and guessing it wrong
silently corrupts every date in the file — the one place in the tool where a wrong guess is
invisible and catastrophic.

---

## States

| State | Treatment |
|---|---|
| **No TTY** (double-click on some systems, cron, CI) | Both prompts are skipped. The affected file becomes a skipped file with an actionable reason, and the console prints: `⚠ Could not ask you about kotak_aug2026.csv — run this from a terminal once to set it up.` The tool never blocks waiting for input that cannot arrive. |
| **Python missing** (R8) | `✕ Python is not installed on this computer.` plus the download URL, the version needed, and *"After installing, run setup again."* No traceback. |
| **Output folder not writable** (`WS-102`, exit 3) | `✕ I cannot write to output\. Check the folder's permissions, or set a different one in config\config.yaml.` |
| **Config invalid** (`CFG-00x`, exit 2) | `✕ config\categories.yaml, line 14: keywords must be a list, but it is text.` — file, line, expectation, actual. Never a YAML library traceback. |
| **Unexpected crash** (exit 4) | `✕ Something went wrong that I did not expect. The details are in output\2026-08\run-log.txt — that file is safe to share, account numbers are masked.` A minimal report is still written. |
| **Empty input** | See [S07](07-empty-and-error-states.md) Variant A; the console prints the same path-and-instruction pair. |

---

## Interaction notes

| Trigger | Behaviour |
|---|---|
| Double-click `run.bat` / `run.command` / `run.sh` | Runs with zero arguments (FR23). The window stays open after the run so the paths remain readable — closing instantly is the single most common way a double-click CLI loses its user. |
| `Ctrl-C` | Exit 130, message `▸ Stopped. Nothing was changed.` Partial output directories are removed. |
| Long-running OCR | Progress line rewrites in place; falls back to one line per page when the output is not a TTY. |
| Screen readers / piped output | Every line is meaningful without its glyph or colour; `--no-color` is honoured, as is `NO_COLOR`. |
