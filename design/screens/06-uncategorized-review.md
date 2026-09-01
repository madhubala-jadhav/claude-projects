# S06 — Uncategorized & Needs-Review Queue

**Frame name in Figma:** `06 — Uncategorized & Review Queue`
**Page:** `02 Detail & Corrections`
**Frame size:** 1280 × 980 — the expanded review section of `report.html`.

---

## Purpose

Turn the tool's accuracy gap into a two-minute task with a visible finish line.

NG5 states plainly that manual review "is part of the intended workflow, not a failure
state." This screen is that statement rendered. It must not look like an error log, and it
must not look like something the user can safely ignore — those are the two failure modes.

## Requirements served

| ID | How |
|---|---|
| **FR8** | Everything that matched no rule lands here as `Uncategorized`, never as a guess |
| **NFR6** | The gap is visually obvious: count, amount, badge, hatched slice, dedicated section |
| **AC5** | Uncategorized flagged in both the summary table and as a count/callout |
| **NG5** | Framed as a workflow with progress, not as a defect |
| **FR3** | Low-OCR-confidence rows queued for verification alongside |
| **EC4** | Foreign-currency rows queued as a third review reason |
| **FR20, FR9, US5** | Every row carries an inline `CategorySelect` (see [S03](03-recategorize-and-corrections-tray.md)) |
| **AC4** | Corrections made here persist to future months |

---

## Layout

### 1. Header with progress

`SectionHeader`: **"Needs your attention"**

Beneath it, a horizontal auto-layout strip:

- `textStyle.h2` / `text.primary`: **"9 transactions need your attention"**
- `textStyle.body` / `text.secondary`: **"₹1,420.00 uncategorized — 3.3% of your spend —
  plus 1 amount to verify and 2 in another currency. Two minutes here makes next month's
  report better automatically."**

  The three counts are stated separately and never summed. ₹1,420.00 and $44.98 are not
  addable, and an OCR-flagged row is already inside a category's total — a single combined
  figure would be arithmetically meaningless in three different ways at once.
- A progress bar, 8 px tall, `radius.pill`, `surface.sunken` track: fills in
  `accent.default` as rows are resolved, labelled **"0 of 9 resolved"**.

**The progress bar is the whole design idea of this screen.** A list of nine problems is a
chore; a bar that reaches the end is a task. It is also honest — it fills only as categories
are actually assigned.

### 2. Three grouped queues, in descending order of consequence

Grouping is by *why the row needs attention*, because the three reasons require different
user judgements. A single flat list forces the user to context-switch on every row.

#### 2a. `Uncategorized` — 6 transactions, ₹1,420.00

`surface.warningSubtle` card. Group header: `StatusBadge/uncategorized` +
**"No rule matched these"** + `textStyle.bodySm` / `text.secondary`:
*"Pick a category once and every future transaction from the same merchant follows."*

| Date | Description / raw | Amount | Suggested | Action |
|---|---|---:|---|---|
| 19 Aug | **AMAZON PAY**<br>`UPI/AMAZON PAY/9928311/PAYMENT` · icici_aug2026.csv | ₹1,000.00 | *Shopping?* | `CategorySelect` |
| 19 Aug | **PAYTM\*QR 88213**<br>`UPI/PAYTM*QR 88213/PAYMENT FROM PHONE` · icici_aug2026.csv | ₹240.00 | — | `CategorySelect` |
| 04 Aug | **UPI/9880021/PAYMENT**<br>`UPI/9880021/PAYMENT` · hdfc_aug2026.pdf | ₹95.00 | — | `CategorySelect` |
| 11 Aug | **NEFT DR-ANANYA S**<br>`NEFT DR-ANANYA S-RENT SHARE` · hdfc_aug2026.pdf | ₹45.00 | *Rent/Housing?* | `CategorySelect` |
| 26 Aug | **RAZORPAY\*SPRT**<br>`UPI/RAZORPAY*SPRT/COLLECT` · icici_aug2026.csv | ₹22.00 | — | `CategorySelect` |
| 30 Aug | **POS 4471 BLR**<br>`POS 4471 BLR` · hdfc_aug2026.pdf | ₹18.00 | — | `CategorySelect` |
| | **Group total** | **₹1,420.00** | | |

The **Suggested** column shows a `text.accent` hint **only** when the merchant key already
appears in `corrections.json` history or matches a keyword in a category the user renamed.
It is never a guess from a model — FR8 forbids silent guessing, and a "suggestion" that is
wrong half the time trains the user to click through it. When there is no defensible
suggestion the cell is an em dash.

Each row shows the `merchant_key` on hover in `textStyle.mono` — the same disclosure as
[S03](03-recategorize-and-corrections-tray.md), because it determines what the correction
will match next month.

#### 2b. `Needs review — low OCR confidence` — 1 transaction

`surface.warningSubtle` card, `StatusBadge/needs-review`. Header:
**"Read from a scanned page — please check the amount"** (`FIELD-401`).

| Date | Description | Amount | Confidence | Action |
|---|---|---:|---|---|
| 28 Aug | CAFE COFFEE DAY BLR<br>`hdfc_aug2026.pdf`, page 4, row 61 | ₹280.00 | **68%** — bar in `text.warning` | inline amount field + `CategorySelect` |

The amount is rendered in `textStyle.numericSm` on a `surface.sunken` inset with a dotted
underline, signalling *"this number is not as trustworthy as the others"*. This is R2's
mitigation and the reason
[06-risks](../../docs/architecture/06-risks-and-open-questions.md) insists OCR-derived
numbers never carry the same visual confidence as extracted ones.

Editing the amount here does **not** change the totals in this report — it is recorded in the
correction patch and applied on the next run, because a report is an artifact of a run and
must stay consistent with its own `summary.json` (NFR7).

#### 2c. `Needs review — another currency` — 2 transactions

`surface.warningSubtle` card, `StatusBadge/foreign-currency` (`FIELD-402`). Header:
**"Not included in your totals"**, with the same explanatory footnote as
[S05](05-sources-and-top-transactions.md) §3b.

| Date | Description | Amount | Action |
|---|---|---:|---|
| 07 Aug | AWS EMEA LUXEMBOURG | $14.99 USD | `CategorySelect` (categorizes it for the CSV; it stays out of totals) |
| 22 Aug | STEAM PURCHASE | $29.99 USD | `CategorySelect` |

### 3. Bulk actions

Sticky sub-bar above the queues, `surface.raised`, `border.subtle`:

- Checkbox per row + a header select-all.
- `Button/Secondary` **"Assign selected to…"** → a `CategorySelect` in bulk mode.
- `Button/Secondary` **"Mark all as reviewed"** — resolves the *review* flag without
  changing a category. Available only for group 2b/2c, never for 2a: an uncategorized
  transaction has no category to keep.

### 4. Completion state

When the last row is resolved, the three cards collapse into a single
`surface.successSubtle` panel:

> **✓ All 9 reviewed.**
> Save your corrections to make them stick — **AMAZON PAY**, **PAYTM** and 4 others will be
> categorized automatically from next month.
>
> `[ Save corrections ]`

`Button/Primary` here is the same control as the tray in
[S03](03-recategorize-and-corrections-tray.md), duplicated at the point of completion so the
user is never left having done the work without committing it.

---

## States

| State | Treatment |
|---|---|
| **Empty — nothing to review** | `EmptyState` on `surface.successSubtle`: **"✓ Everything was categorized."** *"All 214 transactions matched a rule or one of your saved corrections."* The S01 uncategorized stat tile simultaneously turns neutral and reads **"0 · All categorized"**. The section is still rendered — its presence-when-clean is what makes its presence-when-dirty meaningful. |
| **Partially resolved** | Resolved rows stay in place, dimmed to `text.muted` with a `✓` and their new `CategoryBadge`, rather than disappearing. A list that shrinks under the cursor loses the user's place and hides mistakes. |
| **Large queue (> 25 rows)** | The first 25 render; a `Button/Secondary` **"Show all 61"** reveals the rest. The header count is always the true total. |
| **Loading** | Not applicable. |
| **Error** | None specific. If a row's category no longer exists (`WARN-501`), it appears in group 2a with a `text.warning` note naming the missing category. |

---

## Interaction notes

| Trigger | Behaviour |
|---|---|
| Assign a category | Row dims and gains `✓`; progress bar advances; the S01 chart, table and tiles recalculate optimistically; the corrections tray increments. |
| Multiple rows, same merchant | A `text.accent` note — *"3 other AMAZON PAY transactions will move too."* — and they resolve together, advancing the progress bar by 4. |
| Hover a row | Reveals the `merchant_key` and the source file/page/row reference. |
| Keyboard | `↓`/`↑` move between rows, `Enter` opens the select, `1`–`9` pick the first nine categories, `Esc` closes. The queue is designed to be cleared without a mouse. |
| Screen reader | Progress announced via `aria-live`: *"3 of 9 resolved."* |
| Jump-in from S01 | Clicking the uncategorized stat tile scrolls here and focuses the first unresolved row. |
