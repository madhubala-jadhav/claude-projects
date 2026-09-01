# S05 — Sources, Transfers & Top Transactions

**Frame name in Figma:** `05 — Sources, Transfers & Top Transactions`
**Page:** `01 Report`
**Frame size:** 1280 × 1240 — three stacked cards from the lower half of `report.html`.

---

## Purpose

Make the *provenance* of the headline number inspectable. Three questions this screen
answers, all of which a user will ask the first time a total looks wrong:

1. Which files and accounts produced this? (US6, AC1)
2. What single transactions dominated it? (FR15)
3. What was left out, and why? (FR11 transfers, FR5 skipped files, AC7)

## Requirements served

| ID | How |
|---|---|
| **US6** | The accounts strip shows both banks merged into one summary |
| **FR6** | De-duplication result stated, not silent |
| **FR15** | Top 5 largest individual transactions |
| **FR11** | Transfers panel: what was excluded from spend, and both legs of each pair |
| **FR5, AC7** | Skipped-files panel: filename + reason + error code |
| **AC1** | Two files, two accounts, one report — visible as evidence |
| **EC4** | Foreign-currency rows are listed here as excluded from totals |
| **NFR6** | `needs_review` rows are badged wherever they appear |

---

## Layout

### 1. Sources card — "Where this report came from"

`SectionHeader`: **"Sources"** · right-aligned `textStyle.caption` / `text.muted`:
**"214 transactions from 2 files · 1 duplicate removed · 1 file skipped"**

Two-column grid of account tiles, `surface.raised`, `border.subtle`, `radius.md`,
`padding` `spacing.lg`:

```
┌───────────────────────────────────┐  ┌───────────────────────────────────┐
│ HDFC Savings                      │  │ ICICI Savings                     │
│ HDFC-XXXX1234                     │  │ ICICI-XXXX9087                    │
│ hdfc_aug2026.pdf · PDF (text)     │  │ icici_aug2026.csv · CSV           │
│ profile: generic_pdf_indian_savings│  │ profile: icici_savings_csv        │
│ 168 transactions · ₹31,200.00     │  │ 46 transactions · ₹11,150.00      │
└───────────────────────────────────┘  └───────────────────────────────────┘
```

- Label: `textStyle.h3`; account id and profile: `textStyle.mono` / `text.secondary`;
  totals: `textStyle.numericMd`.
- ₹31,200.00 + ₹11,150.00 = ₹42,350.00, which is the total spend tile on
  [S01](01-monthly-dashboard.md). **The arithmetic being checkable on screen is the point** —
  this is how a user confirms AC1 for themselves.
- The extraction method (`PDF (text)` vs `PDF (OCR)` vs `CSV`) is stated because an
  OCR-derived account's numbers deserve less trust (R2).

Below the tiles, `textStyle.bodySm` / `text.secondary`:
**"1 duplicate transaction was removed: 14 Aug, SWIGGY\*ORDER 4471, ₹487.00 — it appeared in
both files."** (FR6, EC1). If none: *"No duplicates found."*

### 2. Top 5 transactions card

`SectionHeader`: **"Your 5 largest transactions"** with `textStyle.caption`:
*"Regardless of category. Internal transfers excluded."*

`TransactionRow` instances, 44 px:

| # | Date | Description | Category | Amount |
|---|---|---|---|---:|
| 1 | 02 Aug | RENT TRANSFER | `CategoryBadge/slot1` Rent/Housing | **₹15,000.00** |
| 2 | 11 Aug | CROMA ELECTRONICS | `CategoryBadge/slot5` Shopping | ₹2,100.00 |
| 3 | 05 Aug | BIGBASKET | `CategoryBadge/slot3` Groceries | ₹1,980.00 |
| 4 | 18 Aug | BESCOM ELECTRICITY | `CategoryBadge/slot6` Utilities | ₹1,415.00 |
| 5 | 23 Aug | SWIGGY*ORDER 8812 | `CategoryBadge/slot2` Food & Dining | ₹1,180.00 |

Each row carries an amount bar behind the description at 5% opacity of the category's slot
colour, scaled to row 1 — a sparkline that shows at a glance that rent dwarfs everything
else.

**Why this card exists at all (FR15's stated rationale):** the top *category* can hide a
single unusual charge. Croma at ₹2,100.00 is the whole of Shopping; without this list the
user would see "Shopping 5.0%" and never learn it was one purchase.

Rows link to [S02](02-category-drilldown.md) for their category.

### 3. Excluded from spend — transfers, foreign currency, skipped files

One card with three sub-sections, each rendered only when non-empty. The card heading is
deliberately **"Excluded from your ₹42,350.00"** rather than "Other", because the entire
purpose is that nothing vanishes silently.

#### 3a. Internal transfers (FR11) — `surface.sunken`

**"₹18,000.00 moved between your own accounts. Not counted as spend or income."**

| Date | From → To | Description | Amount | |
|---|---|---|---:|---|
| 03 Aug | HDFC Savings → ICICI Savings | SELF TRANSFER TO ICICI | ₹12,000.00 | `StatusBadge/transfer` **PAIRED** |
| 03 Aug | ICICI Savings ← HDFC Savings | NEFT CR-MADHUBALA J | ₹12,000.00 | second leg, indented |
| 15 Aug | HDFC Savings → HDFC Credit Card | CC BILL PAYMENT XXXX4412 | ₹6,000.00 | `StatusBadge/transfer` **PAIRED** |

Both legs are shown. A single-leg transfer (the counterpart account has no statement) is
listed with `StatusBadge` **UNPAIRED** and a `text.warning` note: *"Only one side of this
transfer was found. If HDFC-XXXX9087 is not your account, mark this as spend."* with an
inline `CategorySelect`.

This section is R3's mitigation made visible: transfer detection can be wrong, so it is
always shown rather than assumed correct.

#### 3b. Foreign currency (EC4) — `surface.warningSubtle`

**"2 transactions in another currency were not included in the totals."**

| Date | Description | Amount | Note |
|---|---|---:|---|
| 07 Aug | AWS EMEA LUXEMBOURG | **$14.99 USD** | `StatusBadge/foreign-currency` · *no converted INR amount in the statement* (`FIELD-402`) |
| 22 Aug | STEAM PURCHASE | **$29.99 USD** | same |

`text.secondary` footnote: *"This tool never fetches an exchange rate — that would require a
network call. Both rows are in `transactions.csv` with their original currency."* That is
NFR1 explaining an apparent shortcoming rather than hiding it.

#### 3c. Skipped files (FR5, AC7) — `surface.dangerSubtle`

**"1 file could not be read."**

`SkippedFileRow` instances: filename in `textStyle.mono` / `text.primary`, reason in
`text.danger`, code in `text.muted`.

| File | Reason | Code |
|---|---|---|
| `corrupt_statement.pdf` | unreadable / password-protected | `PARSE-201` |

Each row carries a one-line next action in `textStyle.bodySm`:
*"Re-download this statement, or run the tool from a terminal so it can ask for the
password."* Reason strings are the user-facing prose from the error taxonomy
([03](../../docs/architecture/03-interfaces-and-contracts.md) §6.2) — never an exception
class name, never an absolute path.

---

## States

| Section | Empty state |
|---|---|
| Sources | Never empty — if there were no files, the whole report is [S07](07-empty-and-error-states.md). Single account: one tile, full width, and the duplicate line reads *"Only one file — nothing to de-duplicate."* |
| Top 5 | Fewer than 5 transactions → shows however many exist (`min(5, n)`), with the heading adapting: **"Your 3 largest transactions"**. Zero → the card is not rendered. |
| Transfers | Not rendered when `transfers_total == 0`. Suppressible via `config.report.show_transfers_panel: false`. |
| Foreign currency | Not rendered when there are none. |
| Skipped files | **Not rendered when empty — and that absence is itself the signal.** Never render an empty "0 files skipped" panel; it trains the user to ignore the region. |

**Loading:** not applicable. **Error:** this screen *is* the error surface; it has no error
state of its own.

---

## Interaction notes

| Trigger | Behaviour |
|---|---|
| Click an account tile | Filters the drill-down to that account: opens [S02](02-category-drilldown.md) in "all categories, one account" mode. |
| Click a top-5 row | Opens the drill-down for that row's category, scrolled to that transaction and highlighted with `surface.accentSubtle`. |
| Hover a transfer pair | Both legs highlight together, and a connector line is drawn between them in `border.strong`. |
| `CategorySelect` on an unpaired transfer | Reclassifies it as real spend; totals and chart recalculate optimistically, and the correction enters the tray ([S03](03-recategorize-and-corrections-tray.md)). |
| Click a skipped filename | Copies the filename to the clipboard, with a `surface.successSubtle` toast. The report cannot open a local folder from `file://`, and pretending otherwise would produce a dead control. |
| Keyboard | Every row is focusable; the transfers table announces pairs via `aria-describedby` linking the two legs. |
