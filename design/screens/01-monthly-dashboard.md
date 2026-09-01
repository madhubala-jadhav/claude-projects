# S01 — Monthly Dashboard (`report.html`, above and below the fold)

**Frame name in Figma:** `01 — Monthly Dashboard`
**Page:** `01 Report`
**Frame size:** 1280 × 2280 (auto-height; 1280 is `size.frame.report`)
**Artifact this describes:** `output/2026-08/report.html` — the whole scrolling document.

---

## Purpose

This is the product. Everything else in the tool exists to render this one page. Its job is
to let the user answer, in under ten seconds and without interpreting a chart:

> "Where did most of my money go in August, and should I cut it back?"

## Requirements served

| ID | How this screen serves it |
|---|---|
| **US2** | Category table with amount and % of total |
| **US3** | Pie chart with the top slice pulled out and directly labelled |
| **US4** | The top-category callout, stated as a sentence |
| **US6** | The accounts strip showing both statements merged |
| **G3, G4, G5** | The summary, the chart, the auto-opened local report |
| **FR12** | Stat tiles: total spend, total income, net |
| **FR13** | Top-category callout — name, amount, percentage as literal text |
| **FR14** | `DeltaChip` in the table's MoM column (full treatment in [S04](04-month-over-month.md)) |
| **FR15** | Top-5 transactions panel (detail in [S05](05-sources-and-top-transactions.md)) |
| **FR16** | The frame *is* the self-contained HTML report |
| **FR17** | Pie chart, top slice distinguished by geometry + label |
| **FR18** | Chart, table, callout and MoM all on one screen |
| **FR19** | Slice hover/click → drill-down ([S02](02-category-drilldown.md)) |
| **FR20** | Row-level `CategorySelect` ([S03](03-recategorize-and-corrections-tray.md)) |
| **FR21** | Footer link to `transactions.csv` |
| **NFR6** | Uncategorized stat tile, hatched slice, badged row |
| **AC2, AC3, AC5** | Chart present; top category stated in words; uncategorized flagged twice |

---

## Layout, top to bottom

All measurements are tokens. `pageMargin` = 40, `columnGap` = 24, section gap =
`size.gutter.section` (24), inner card padding = `spacing.xl` (24).

### 1. Header — `spacing.xxl` (32) top padding

Horizontal auto layout, `space-between`, `align: baseline`.

- **Left:** `textStyle.h1` / `text.primary` — **"Expense Summary — August 2026"**
  Below it, `textStyle.caption` / `text.muted` — **"Generated 1 Sep 2026, 20:14 · 214 transactions · 2 accounts · INR"**
- **Right:** `Button/Secondary` — **"Download CSV"** (FR21) and a theme toggle
  (`Auto / Light / Dark`) as a 3-segment control.

### 2. Stat tiles — 4 columns, `columnGap` 24, `size.control.tileHeight` 104

`StatTile` instances. Label in `textStyle.label` / `text.secondary`; value in
`textStyle.numericLg` / `text.primary`.

| Tile | Label | Value | Variant |
|---|---|---|---|
| 1 | TOTAL SPEND | **₹42,350.00** | neutral |
| 2 | TOTAL INCOME | **₹65,000.00** | neutral |
| 3 | NET | **+₹22,650.00** | neutral, value in `text.success` when positive |
| 4 | UNCATEGORIZED | **6 · ₹1,420.00** | **warning** — `surface.warningSubtle` fill, `text.warning` value, `StatusBadge/uncategorized` |

Tile 4 is deliberately a *stat*, not a footnote. NFR6 and AC5 require the accuracy gap to be
as prominent as the answer. Clicking it jumps to [S06](06-uncategorized-review.md).

A fifth line under the tiles, `textStyle.bodySm` / `text.secondary`:
**"₹18,000.00 in transfers between your own accounts was excluded from spend."** (FR11) —
with "transfers" as a link to the transfers panel in [S05](05-sources-and-top-transactions.md).

### 3. Top-category callout — full width, `surface.accentSubtle`, `radius.lg`

The single most important element on the page. Vertical auto layout, padding `spacing.xl`.

- Eyebrow, `textStyle.label` / `text.accent`: **"YOUR LARGEST CATEGORY"**
- Headline, `textStyle.h2` / `text.primary`:
  **"Rent/Housing: ₹15,000.00 — 35.4% of your spend"**
- Supporting line, `textStyle.bodyLg` / `text.secondary`:
  **"Unchanged from July. Your second largest, Food & Dining (₹14,400.00, 34.0%), is up 12.5% — that is where this month actually moved."**
- Right-aligned: `Button/Secondary` **"See the 1 transaction"** → drill-down.

**The supporting line is generated, not decorative.** Rule: when the top category is flat or
`is_transfer`-adjacent and the *second* category has the largest positive MoM delta, name
the second one — because "Rent is your biggest expense" is true every month and therefore
not a decision. AC3 is satisfied by the headline alone; the supporting line is the part that
makes US4's "clear signal to act on" real.

### 4. Chart + legend — 2 columns: 7 cols chart, 5 cols legend

Card, `surface.raised`, `radius.lg`, `shadow.card`.
`SectionHeader`: **"Where your money went"**, with a right-aligned
`Button/Secondary` toggle **"Patterns"** (pattern-fill mode, for CVD/print).

**Chart (left, 7 columns):** pie, `size.chart.diameter` 340, `innerRadius` 0 (FR17 says pie,
not doughnut), centred with 32 px around it.

Slices, in **slot order** — not size order (see design-system §3.1):

| Order | Category | Amount | % | Fill | Notes |
|---|---|---|---|---|---|
| 1 | Rent/Housing | ₹15,000.00 | 35.4 | `chart.series.slot1` | **Pulled out 12 px** along its bisector (`size.chart.explodeOffset`) |
| 2 | Food & Dining | ₹14,400.00 | 34.0 | `chart.series.slot2` | |
| 3 | Groceries | ₹5,820.00 | 13.7 | `chart.series.slot3` | |
| 4 | Transport | ₹1,760.00 | 4.2 | `chart.series.slot4` | |
| 5 | Shopping | ₹2,100.00 | 5.0 | `chart.series.slot5` | |
| 6 | Utilities | ₹1,850.00 | 4.4 | `chart.series.slot6` | |
| 7 | Uncategorized | ₹1,420.00 | 3.3 | `chart.uncategorizedFill` + 45° hatch in `chart.uncategorizedHatch` | Never a slot colour |
| — | Entertainment | ₹0.00 | 0.0 | — | **Absent from the chart** (EC6); still in the table |

Every slice carries a 2 px `chart.sliceGap` stroke. Direct labels for every slice ≥ 3%:
`textStyle.bodySm`, category name + %, `text.primary`, leader line in `chart.axis` where the
slice is too thin. The top slice's label is `textStyle.bodyMedium` (bolder) — the second
non-colour emphasis cue.

**Legend (right, 5 columns):** vertical auto layout, `itemSpacing` `spacing.sm`.
`ChartLegendEntry` per slice: 12 × 12 swatch (`radius.sm`) · name (`textStyle.body`) ·
amount (`textStyle.numericMd`, right-aligned) · % (`text.secondary`). The Uncategorized entry
uses the hatched swatch and appends `StatusBadge/uncategorized`.

### 5. Category table — full width card

`SectionHeader`: **"Spend by category"** with `textStyle.caption` note
*"Sorted by amount. Percentages are apportioned to sum to exactly 100.0%."*

Header row on `surface.sunken`, `textStyle.label` / `text.secondary`:
`CATEGORY · TXNS · AMOUNT · % OF SPEND · VS JULY`
Rows are `CategoryRow`, height `size.control.rowHeight` 44.

| Category | Txns | Amount | % of spend | vs July |
|---|---:|---:|---:|---|
| **Rent/Housing** `top` variant | 1 | **₹15,000.00** | 35.4% | `DeltaChip/flat` — 0.0% |
| Food & Dining | 63 | ₹14,400.00 | 34.0% | `DeltaChip/up` — ▲ 12.5% |
| Groceries | 11 | ₹5,820.00 | 13.7% | `DeltaChip/down` — ▼ 8.2% |
| Shopping | 4 | ₹2,100.00 | 5.0% | `DeltaChip/new` — NEW |
| Utilities | 3 | ₹1,850.00 | 4.4% | `DeltaChip/up` — ▲ 2.1% |
| Transport | 28 | ₹1,760.00 | 4.2% | `DeltaChip/up` — ▲ 41.0% |
| **Uncategorized** `uncategorized` variant | 6 | ₹1,420.00 | 3.3% | `DeltaChip/down` — ▼ 22.4% |
| Entertainment `zero` variant | 0 | ₹0.00 | 0.0% | `DeltaChip/gone` — GONE |
| **Total** (footer row, `surface.sunken`, `textStyle.bodyMedium`) | **214** | **₹42,350.00** | **100.0%** | ▲ 6.7% |

Row variants: `top` gets a 3 px `chart.series.slot1` left edge and bold name;
`uncategorized` sits on `surface.warningSubtle` with the badge; `zero` uses `text.muted`
for the amount so a ₹0 row reads as history, not as spend.

### 6. Month-over-month strip

Condensed; the full treatment is [S04](04-month-over-month.md). One horizontal card:
**"August ₹42,350.00 vs July ₹39,679.00 — ▲ 6.7%"** plus the three largest movers as
`DeltaChip`s: `Transport ▲41.0%`, `Food & Dining ▲12.5%`, `Groceries ▼8.2%`.
Right-aligned `Button/Secondary` **"Compare all categories"**.

### 7. Top 5 transactions

See [S05](05-sources-and-top-transactions.md) §2. Rendered here as a compact card.

### 8. Uncategorized review

See [S06](06-uncategorized-review.md). Rendered here as a collapsed card showing the count
and the first three rows, expandable in place.

### 9. Sources & skipped files

See [S05](05-sources-and-top-transactions.md) §1 and §3. On this screen the skipped-file
panel is **always rendered when non-empty**, on `surface.dangerSubtle`, above the footer —
AC7 requires the user to see it without scrolling past the interesting part.

### 10. Footer

`textStyle.caption` / `text.muted`, `border.subtle` rule above:
**"Generated locally by ExpenseInNutshell v1.0 on 1 Sep 2026. No data left this machine."**
· link **"transactions.csv"** (FR21) · link **"run-log.txt"** ·
**"Report saved at C:\Users\...\output\2026-08\report.html"**

The privacy line is not marketing — NFR1/AC8 is a promise the user cannot verify themselves,
so the report states it where they will see it every month.

---

## States

### Loading
Not applicable in the browser — the report is a static document, fully rendered before it
opens. The equivalent "loading" surface is the CLI console, [S08](08-setup-and-run-console.md).
The one exception: if the vendored chart asset is missing (`WARN-507`), the chart region
renders a static SVG fallback pie and a `text.warning` note; the page never shows a spinner
that could hang forever.

### Empty
Zero parseable transactions (EC5) → the whole screen is replaced by
[S07](07-empty-and-error-states.md). Partial emptiness is handled locally:
no prior month → the MoM column shows `DeltaChip/unavailable` ("—") and section 6 collapses
to a one-line note; no uncategorized → tile 4 turns neutral and reads **"0 · All categorized"**
in `text.success`, and section 8 disappears.

### Error
There is no full-page error state. Errors are per-unit and inline: skipped files in §9,
row errors in the run log, `needs_review` rows badged in §7 and §8. This is NFR3 expressed
as a layout rule — **a failure never takes the report away from the user.**

---

## Interaction notes (static frames cannot show these)

| Trigger | Behaviour |
|---|---|
| Hover a pie slice | Slice lightens 6%; tooltip on `surface.overlay` with name, amount, %, txn count. The matching legend entry and table row both highlight to `surface.sunken`. |
| Click a pie slice | Opens the drill-down panel — [S02](02-category-drilldown.md). |
| Hover a table row | `surface.sunken`; the matching slice gets a 2 px `border.focus` ring. |
| Click a table row | Same as clicking its slice. **This is the keyboard-accessible equivalent of FR19** — the canvas is not focusable, the rows are. |
| Tab order | Header controls → tiles (tile 4 focusable) → callout button → Patterns toggle → legend entries → table rows → MoM → top-5 → uncategorized → footer links. |
| `Patterns` toggle | Replaces every slice fill with a distinct SVG pattern at the same hue. Persisted in `localStorage`, and forced on under `prefers-contrast: more` and `forced-colors: active`. |
| Theme segmented control | Sets `data-theme` on `<html>`; default follows `prefers-color-scheme`. |
| Print | Chart switches to patterns, drill-down panel hidden, table repeats headers across pages, links print as text. |
| `prefers-reduced-motion` | Drill-down slide is replaced by an instant show. |
