# S02 — Category Drill-down Panel

**Frame name in Figma:** `02 — Category Drill-down`
**Page:** `02 Detail & Corrections`
**Frame size:** 1280 × 900 — the dashboard dimmed, with a 420 px panel (`size.frame.panel`)
docked to the right edge.

---

## Purpose

Answer the question the pie chart provokes: *"₹14,400 on Food & Dining — on what?"*
Without this, the chart is a number the user has to trust. With it, every slice is
auditable in one click.

## Requirements served

| ID | How |
|---|---|
| **FR19** | Hover/click a pie slice → the list of transactions contributing to that category |
| **US3** | Completes "see the top expense visually" by making the visual clickable |
| **FR15** | Large transactions inside a category surface here too |
| **NFR6** | `needs_review` and `Uncategorized` rows are badged inside the drill-down, not only in the summary |
| **EC3** | Refunds appear as credits **inside** the category, not netted away |

---

## Layout

### Backdrop
The S01 dashboard remains fully rendered at 100% opacity except for a
`#12161D` @ 24% scrim over the main column (`shadow.overlay` colour and alpha).
The chart itself is **not** dimmed — the selected slice keeps its emphasis so the user
never loses the connection between panel and slice.

### Panel — 420 px, full height, `surface.overlay`, `shadow.overlay`, `radius.lg` on the left corners

Vertical auto layout, `padding` `spacing.xl`, `itemSpacing` `spacing.lg`.

**Header block** (`padding-bottom` `spacing.md`, `border.subtle` rule below):

- Row 1: `CategoryBadge/slot2` swatch + **"Food & Dining"** in `textStyle.h2`, and a
  32 × 32 close button (`✕`, `text.secondary`) at the far right.
- Row 2: `textStyle.numericLg` / `text.primary` — **"₹14,400.00"**, then
  `textStyle.body` / `text.secondary` — **"34.0% of spend · 63 transactions"**.
- Row 3: `DeltaChip/up` — **▲ 12.5% vs July (₹12,800.00)**.

**Filter/sort row** — horizontal auto layout, `itemSpacing` `spacing.sm`:
`Button/Secondary` segmented: **Largest first** (default) · **By date** ·
a checkbox **"Only flagged (1)"**.

**Transaction list** — `TransactionRow` instances, `size.control.rowHeight` 44,
1 px `border.subtle` between rows, scrollable.

| Date | Description | Amount | Row variant |
|---|---|---:|---|
| 23 Aug | SWIGGY*ORDER 8812 | ₹1,180.00 | default |
| 09 Aug | ZOMATO ORDER 55210 | ₹940.00 | default |
| 17 Aug | STARBUCKS INDIA BLR | ₹710.00 | default |
| 21 Aug | SWIGGY*ORDER 7734 | ₹655.00 | default |
| 14 Aug | SWIGGY*ORDER 4471 | ₹487.00 | default |
| 12 Aug | DOMINOS PIZZA 4471 | ₹449.00 | default |
| 06 Aug | ZOMATO ORDER 51188 | **−₹312.00** | **credit** — `text.success`, `StatusBadge` reading **REFUND** |
| 28 Aug | CAFE COFFEE DAY BLR | ₹280.00 | `needs-review` — `surface.warningSubtle`, `StatusBadge/needs-review`, tooltip *"read from a scanned page at 68% confidence — please check the amount"* (`FIELD-401`) |
| … | *58 more* | | |

Each row shows, under the description in `textStyle.caption` / `text.muted`, the
`raw_description` truncated to one line — e.g.
**"POS SWIGGY\*ORDER 4471 BANGALORE IN · hdfc_aug2026.pdf"**. This is what lets a user
judge whether a categorization is right, and it is why `raw_description` is a frozen field.

Every row has a trailing `CategorySelect` (collapsed) so a miscategorization can be fixed
without leaving the panel — see [S03](03-recategorize-and-corrections-tray.md).

**Panel footer** — sticky, `surface.raised`, `border.subtle` rule above:
`textStyle.bodySm` **"63 transactions · ₹14,400.00 total · 1 refund of ₹312.00 included as income, not netted"** (EC3),
plus `Button/Secondary` **"Export this category to CSV"**.

---

## The refund line is not a detail

EC3 says a refund must be visible on both sides rather than silently reduced from the
original category. The panel therefore lists the credit **in the category**, styled as a
credit, and states in the footer that it was counted as income rather than subtracted. A
user who sees ₹14,400 and a −₹312 refund in the same list can reconcile the number
themselves; one who sees ₹14,088 with no explanation cannot.

---

## States

### Loading
None. All transactions are already in the page's `#expense-data` island; the panel is a
filter over an in-memory array. Opening it is synchronous. If a category somehow has more
than 500 rows the list virtualizes after row 200 with a **"Show all 63"**-style control —
this is the only place in the design where content is deferred.

### Empty
Two cases:
- **Zero-spend category** (EC6): reached only from the table, since the slice does not
  exist. `EmptyState` — *"No spending in Entertainment this month. You spent ₹649.00 here in
  July."* with a `Button/Secondary` **"Compare with July"** → [S04](04-month-over-month.md).
- **Filter yields nothing** ("Only flagged" on a clean category): `EmptyState` —
  *"Nothing flagged in Groceries. All 11 transactions were matched by a rule."*

### Error
None specific. A category whose transactions failed to parse never appears — the failure is
reported at file level in [S07](07-empty-and-error-states.md).

---

## Interaction notes

| Trigger | Behaviour |
|---|---|
| Click slice / legend entry / table row | Panel slides in from the right over 120 ms, `ease-out`. Under `prefers-reduced-motion` it appears instantly. |
| Panel open | Focus moves to the panel heading; focus is **trapped** inside the panel; `Esc` closes and returns focus to the element that opened it. |
| Hover a pie slice (no click) | Tooltip only — the panel does **not** open on hover. FR19 says "hover/click"; hover gives the tooltip, click gives the list. A panel that opened on hover would be unusable with a trackpad. |
| Click another slice while open | Panel content cross-fades; it does not close and reopen. |
| Click the scrim | Closes the panel. |
| Change a `CategorySelect` inside the panel | The row moves out of this category on the next recalculation; a `text.secondary` inline note says *"Moved to Groceries — pending save"*, and the corrections tray count increments. The row is not removed from view until the panel is reopened, so the user can undo. |
| Keyboard | The panel is reachable without the chart: `Enter` on any category table row opens it. This is the accessible path for FR19. |
