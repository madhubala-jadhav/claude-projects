# ExpenseInNutshell — Design System

**Source of truth:** [`tokens.json`](tokens.json). Everything in this document explains
*why* a token has the value it has, and records the **measured** accessibility numbers.
Nothing here is asserted from memory — every ratio and every ΔE below is copied from a real
run of [`contrast-check.js`](contrast-check.js) against `tokens.json` (§6).

**Consumers of `tokens.json`, in order of authority:**

1. `design/figma-plugin/code.js` — turns the tokens into Figma paint styles and text styles.
2. The CSS custom properties inlined into `output/<month>/report.html` (C8, [ADR-0007](../../docs/architecture/04-adr/ADR-0007-report-and-chart-rendering.md)).
3. The screen specs in [`../screens/`](../screens/), which reference tokens by name only.

If a value needs to change, change it in `tokens.json` and re-run `contrast-check.js`.
A hex typed anywhere else is a bug.

---

## 1. What the design has to do

The spec is unusually specific about what the visual layer is *for*. Three requirements
drive nearly every decision here:

| Driver | Requirement | Design consequence |
|---|---|---|
| One screen must answer "where did my money go?" | US4, FR13, G4 | The top-category callout, not the chart, is the primary answer. The chart is corroboration. |
| Accuracy gaps must be **visible**, not hidden | NFR6, FR8, AC5, NG5 | `Uncategorized` gets its own warning surface, badge and hatched chart fill. It is never styled as "fine". |
| The report is a `file://` document, offline, forever | NFR1, FR16, AC8 | **No web fonts, no CDN, no icon font, no remote images.** Type is a system stack; icons are inline SVG or text glyphs. |

A fourth, quieter driver: the report is an *archive*. NFR7 keeps every month forever, so a
report opened in three years must still render correctly with no network and no runtime.
That rules out anything that degrades when an external dependency disappears.

---

## 2. Colour

### 2.1 The semantic layer is the only layer screens may use

Screens and `code.js` reference `text.primary`, `surface.raised`, `delta.up`,
`chart.series.slot2`. They never reference a hex. The raw values exist in exactly one place.

| Group | Purpose | Notes |
|---|---|---|
| `surface.*` | Every background | `canvas` (page) → `raised` (card) → `sunken` (table header, wells). `accentSubtle` / `warningSubtle` / `dangerSubtle` / `successSubtle` are **status backgrounds** that all carry `text.primary` at 15:1+, so status is never conveyed by background hue alone. |
| `border.*` | Rules and outlines | `subtle` is decorative and deliberately exempt from 3:1. `control` (#767F8C) exists purely to meet WCAG 1.4.11 on input boundaries. `focus` is the focus ring. |
| `text.*` | Every foreground | `muted` is the floor: 4.68:1 on `canvas`. There is deliberately no lighter grey — a fifth tier would fail AA and get used anyway. |
| `accent.*` | Primary action | One accent, two states. |
| `delta.*` | Month-over-month direction (FR14) | Red = spend went **up**. This inverts the finance convention on purpose: this is a spending tool, and up is bad. Always paired with a ▲/▼ glyph and a signed percentage — see §5. |
| `chart.*` | Chart geometry and fills | Separate namespace so the chart palette can be tuned for CVD without disturbing UI colour. |

### 2.2 Why red means "up"

In a P&L, red means loss. Here, `delta.up` (spend increased) is `#B3261E` and `delta.down`
is `#1A6E3D`. A user scanning the MoM column wants "what got worse", and in a spending
report "worse" is "more". Because this is a convention flip, the colour is **never the only
signal**: every delta chip carries a caret glyph, a sign, and a percentage
(`▲ 12.5%` / `▼ 8.2%`), and the chip is legible with colour removed entirely.

### 2.3 Dark theme

Dark values are hand-picked steps against `#171B22`, not an algorithmic inversion. Two
things change beyond luminance:

- Text colours desaturate (`#B3261E` → `#FF9A92`) because saturated red on a dark surface
  vibrates and reads as an error state even when it is a data point.
- Chart slots shift **lighter and less saturated**, because the same hue that reads as
  "filled" on white reads as "hole" on near-black.

`config.report.theme: auto` follows `prefers-color-scheme`; `light` and `dark` pin it.

---

## 3. The chart palette — the hardest constraint in the package

FR17 requires the top category to be *visually distinguished*. The spec's own suggestion
("pulled slice, distinct color, or callout label") offers colour as one option; the design
rejects colour-as-emphasis and uses all three of the non-colour options instead. Here is
the reasoning, with measurements.

### 3.1 Slots are bound to categories, not to rank (assumption A6)

Each category owns a fixed slot in `categories.yaml` ([02](../../docs/architecture/02-data-model.md) §1.3),
so **Food & Dining is orange every month**. If colours were assigned by rank, a category
that moved from 2nd to 3rd would change colour, and US7/FR14's whole purpose — comparing
this month to last — would be undermined by the chart repainting itself.

Consequence: slice order in the pie is **slot order**, not descending size. Rank is carried
by the pulled slice, the FR13 callout, and the table sorted by amount — three independent
channels, none of which is slice position.

### 3.2 Eight slots, then "Other"

Slots 1–8 are a validated 8-hue categorical set. A 9th generated hue is not separable under
protanopia at any lightness that also keeps the first eight separable — measured, not
assumed. So:

- Categories with `slot: null` render in `chart.other` (neutral grey).
- `report.top_n_slices: 8` and `report.min_slice_percent: 1.0` fold the long tail into a
  single **Other** slice.
- Every folded category still gets its own row in the FR18 table, where the **name** carries
  identity. Nothing disappears because it ran out of colour.

### 3.3 Top-category emphasis (FR17) — three cues, none of them hue

| Cue | Token / value | Why |
|---|---|---|
| **Pulled slice** | `size.chart.explodeOffset` = 12 px along the bisector | Geometry survives greyscale, CVD, print and `forced-colors`. This is the primary cue. |
| **Direct label in bold** | `textStyle.bodyMedium` on `text.primary` | Reading "Rent/Housing 35.4%" needs no colour at all. |
| **Callout above the chart** | `surface.accentSubtle` panel, `textStyle.h2` | FR13/AC3 require the fact as *literal text*. The callout is the actual answer; the chart illustrates it. |

Colour is deliberately **not** in that list. The top slice keeps its own category slot
colour, because changing it would break §3.1.

### 3.4 Uncategorized is styled as a gap, not a category (NFR6)

`Uncategorized` never receives a slot. It renders as `chart.uncategorizedFill` (#FFF3D6)
overlaid with a 45° hatch in `chart.uncategorizedHatch` (#7A5200) at **6.28:1** on that
fill. In the table its row sits on `surface.warningSubtle` with a pill badge. The intent is
that a user seeing hatching immediately reads "this is unfinished", which is exactly what
NG5 says the review step is for.

`needs_review` (low OCR confidence, FR3; foreign currency, EC4) is a **different** badge in
the same warning family, because it is a different problem: the category may be right but
the *number* may be wrong.

---

## 4. Type

| Decision | Value | Why |
|---|---|---|
| UI family | `Inter` in Figma; `system-ui, -apple-system, "Segoe UI", Roboto, sans-serif` in the report | The report may not load a web font (NFR1). Inter is Figma-default and metrically close enough that the Figma frames predict the rendered report. |
| Numeric family | `Roboto Mono` in Figma; `ui-monospace, "SF Mono", Consolas, monospace` in the report | Amount columns must align on the decimal. Proportional digits in a ₹ column make a 5-digit rent and a 3-digit coffee look the same width. |
| Ramp | 11 / 12 / 14 / 16 / 20 / 24 / 32 / 44 | Eight sizes, one per role. `hero` (44) is reserved for a single number and is **not used by any v1 screen** — see the note below. |
| Weights | Regular / Medium / Semi Bold / Bold | Four, all present in both Figma defaults. No Light: it fails legibility at 11–12 px. |

The 14 composite `textStyle.*` entries are the complete inventory. A screen spec that needs
a style not in that list is a spec bug, not a licence to invent one.

**On `hero` (44) — the one unused style, and why it stays.** The obvious home for it is the
total-spend figure, but [S01](../screens/01-monthly-dashboard.md) §2 puts total spend in a
282 px-wide stat tile in a four-across row, and `₹42,350.00` set in 44 px `Roboto Mono`
overflows that tile. S01's `numericLg` (24) is therefore authoritative for the stat tiles,
and `hero` is kept in the ramp — unused in v1 — for a future full-width total treatment.
Nothing in v1 should reach for it; if a screen does, resize the container first rather than
shrinking the type.

**Tabular figures matter more than they look.** `numericSm` is the table amount style; the
category table, the top-5 list and the drill-down all right-align amounts using it, so the
eye can compare magnitudes by column width without reading the digits.

---

## 5. Component inventory

Every component below exists as a Figma component in the generated file (page **00 — Design
System**) and as an instance in at least one screen. Names match `code.js` exactly.

| Component | Variants | Tokens | Used by | Serves |
|---|---|---|---|---|
| `Button/Primary` | default, hover | `accent.default` / `accent.hover`, `text.inverse`, `radius.md`, `size.control.buttonHeight` | S01, S03, S07, S08 | FR20, FR21 |
| `Button/Secondary` | default | `surface.raised`, `border.control`, `text.primary` | S01, S02, S05 | FR19, FR21 |
| `StatTile` | neutral, warning | `surface.raised`, `radius.md`, `textStyle.label` + `textStyle.numericLg`, `size.control.tileHeight` | S01, S04 | FR12, NFR6 |
| `TopCategoryCallout` | — | `surface.accentSubtle`, `textStyle.h2` + `bodyLg` | S01 | **FR13, US4, AC3** |
| `ChartLegendEntry` | slot1–8, other, uncategorized | `chart.series.*`, `chart.other`, `chart.uncategorizedFill` | S01, S02 | FR17, FR18 |
| `CategoryRow` | default, top, zero, uncategorized | `surface.raised` / `sunken` / `warningSubtle`, `size.control.rowHeight` | S01, S04 | FR18, EC6, NFR6 |
| `TransactionRow` | default, uncategorized, needs-review, transfer | `textStyle.numericSm`, badge slot | S02, S03, S05, S06 | FR15, FR19, FR20 |
| `CategoryBadge` | slot1–8, other | `radius.pill`, `size.control.badgeHeight` | S02, S03, S05, S06 | FR18 |
| `StatusBadge` | uncategorized, needs-review, foreign-currency, transfer | `surface.warningSubtle` + `text.warning` | S01, S05, S06 | NFR6, FR3, EC4, FR11 |
| `DeltaChip` | up, down, flat, new, gone, unavailable | `delta.*` + caret glyph + signed % | S01, S04 | **FR14, US7, AC6** |
| `CategorySelect` | closed, open, changed | `border.control`, `radius.sm`, `size.control.inputHeight` | S03, S06 | **FR20, US5** |
| `Card` | default, danger | `surface.raised`, `radius.lg`, `shadow.card` | all | — |
| `SectionHeader` | — | `textStyle.h2`, `border.strong` rule | all | — |
| `SkippedFileRow` | — | `surface.dangerSubtle`, `text.danger`, error code + reason | S05, S07 | **FR5, AC7** |
| `CorrectionsTray` | 0, N unsaved | `shadow.raised`, sticky | S03 | FR20 → FR9 → AC4 |
| `ConsoleLine` | info, warn, error, success | `textStyle.mono` | S08 | NFR5, FR5 |
| `EmptyState` | no-transactions, no-prior-month, no-uncategorized | `text.secondary`, centred | S04, S06, S07 | EC5, WARN-503 |

**Interaction states, defined once here rather than repeated per screen:**

- **Hover** on a row: background steps to `surface.sunken`, no movement, no shadow.
- **Focus**: 2 px `border.focus` outline at 2 px offset. Never removed, never replaced by a
  background change alone. Every interactive element is reachable by keyboard, because the
  chart's click-to-drill-down (FR19) must have a keyboard equivalent — activating the
  corresponding table row.
- **Disabled**: `surface.sunken` fill, `text.muted` label, `border.subtle`. Used only for
  the Save-corrections button at zero pending changes.
- **Active/selected** row: `surface.accentSubtle` with a 2 px `accent.default` left edge.

---

## 6. Measured accessibility results

Reproduce with:

```
node design/design-system/contrast-check.js design/design-system/tokens.json
```

The numbers below are that command's actual output, run on 2026-08-26 against
`tokens.json` v1.0.0. **Result line: `0 WCAG pair(s) below target`.**

### 6.1 Text contrast — light theme (target 4.5:1 body, 3:1 non-text)

| Pair | Ratio | Target |
|---|---|---|
| `text.primary` on `surface.raised` | **18.13:1** | 4.5 |
| `text.primary` on `surface.canvas` | **16.75:1** | 4.5 |
| `text.primary` on `surface.sunken` | **15.45:1** | 4.5 |
| `text.primary` on `surface.accentSubtle` (callout) | **15.82:1** | 4.5 |
| `text.primary` on `surface.warningSubtle` (uncategorized row) | **16.44:1** | 4.5 |
| `text.primary` on `surface.dangerSubtle` (skipped file) | **15.42:1** | 4.5 |
| `text.primary` on `surface.successSubtle` | **15.88:1** | 4.5 |
| `text.secondary` on `surface.raised` | **7.53:1** | 4.5 |
| `text.secondary` on `surface.canvas` | **6.95:1** | 4.5 |
| `text.secondary` on `surface.sunken` | **6.41:1** | 4.5 |
| `text.muted` on `surface.raised` | **5.07:1** | 4.5 |
| `text.muted` on `surface.canvas` | **4.68:1** | 4.5 ← the floor of the whole system |
| `text.accent` on `surface.raised` | **5.70:1** | 4.5 |
| `text.accent` on `surface.canvas` | **5.26:1** | 4.5 |
| `text.accent` on `surface.accentSubtle` | **4.97:1** | 4.5 |
| `text.danger` on `surface.raised` | **6.54:1** | 4.5 |
| `text.danger` on `surface.dangerSubtle` | **5.56:1** | 4.5 |
| `text.warning` on `surface.raised` | **6.92:1** | 4.5 |
| `text.warning` on `surface.warningSubtle` | **6.28:1** | 4.5 |
| `text.success` on `surface.raised` | **6.28:1** | 4.5 |
| `text.success` on `surface.successSubtle` | **5.50:1** | 4.5 |
| `text.inverse` on `accent.default` (button label) | **5.70:1** | 4.5 |
| `text.inverse` on `accent.hover` | **8.43:1** | 4.5 |
| `delta.up` on `surface.raised` | **6.54:1** | 4.5 |
| `delta.down` on `surface.raised` | **6.28:1** | 4.5 |
| `delta.flat` on `surface.raised` | **5.07:1** | 4.5 |
| `border.control` on `surface.raised` | **4.05:1** | 3 |
| `border.control` on `surface.canvas` | **3.74:1** | 3 |
| `border.focus` on `surface.raised` | **5.70:1** | 3 |
| `border.focus` on `surface.canvas` | **5.26:1** | 3 |
| `chart.axis` on `chart.surface` | **3.07:1** | 3 |
| `chart.other` on `chart.surface` | **3.07:1** | 3 |
| `chart.sliceGap` on `chart.surface` | **3.07:1** | 3 |
| `chart.uncategorizedHatch` on `chart.uncategorizedFill` | **6.28:1** | 3 |

Every value is AA for body text at any size. `text.muted` at 4.68:1 on `canvas` is the
tightest pair in the system; it is used for captions and timestamps only, and nothing
load-bearing depends on it.

### 6.2 Text contrast — dark theme

All 34 pairs pass, with more headroom than light. Tightest: `border.control` on
`surface.raised` at **3.76:1** (target 3), and `text.muted` on `surface.raised` at
**6.37:1** (target 4.5). `text.primary` on `surface.raised` is **15.82:1**.

### 6.3 Chart fills against the chart surface (WCAG 1.4.11, target 3:1)

| Slot | Light | Dark |
|---|---|---|
| slot1 blue | 4.42:1 ✅ | 4.74:1 ✅ |
| slot2 orange | 3.20:1 ✅ | 4.45:1 ✅ |
| slot3 aqua | **2.82:1 ⚠️** | 5.07:1 ✅ |
| slot4 yellow | **2.17:1 ⚠️** | 5.62:1 ✅ |
| slot5 magenta | **2.69:1 ⚠️** | 4.38:1 ✅ |
| slot6 green | 4.95:1 ✅ | 3.49:1 ✅ |
| slot7 violet | 8.56:1 ✅ | 5.52:1 ✅ |
| slot8 red | 3.95:1 ✅ | 5.34:1 ✅ |
| other grey | 3.07:1 ✅ | 4.20:1 ✅ |

**Three light-theme fills are under 3:1, and this is a real finding, not a rounding
issue.** Darkening aqua, yellow and magenta enough to clear 3:1 on white collapses their
CIEDE2000 separation from the neighbouring hues (§6.4) — at eight categorical hues the two
goals are in direct conflict. The resolution, and it is mandatory rather than optional:

> **`chart.sliceGap` is a 2 px `#8A94A3` stroke drawn around every slice — 3.07:1 on white,
> 5.71:1 on the dark surface.** The *boundary* of every slice therefore satisfies 1.4.11
> even where the *fill* does not, and no slice ever bleeds into the surface or its
> neighbour. Combined with mandatory direct labels (§6.5), no information is lost.

This is recorded as risk **R6** in
[06-risks-and-open-questions.md](../../docs/architecture/06-risks-and-open-questions.md).

### 6.4 Colour-vision-deficiency separation (CIEDE2000, floor ΔE 6.0)

Simulated with the Machado/Oliveira/Fernandes severity-1.0 matrices, all 9 fills
(8 slots + Other), all pairs and adjacent-only:

| Theme | Vision | Worst all-pairs ΔE | Pairs below floor | Worst **adjacent** ΔE |
|---|---|---|---|---|
| light | normal | 13.3 (slot2/slot8) | 0 | 34.0 |
| light | protan | **1.9** (slot5/other) | 2 — `slot2/slot6`=4.8, `slot5/other`=1.9 | 14.6 |
| light | deutan | 6.9 (slot2/slot8) | 0 | 16.6 |
| light | tritan | 5.5 (slot2/slot8) | 1 — `slot2/slot8`=5.5 | 8.4 |
| dark | normal | 14.1 (slot5/slot8) | 0 | 32.2 |
| dark | protan | **1.8** (slot1/slot7) | 3 — `slot1/slot7`=1.8, `slot2/slot6`=4.1, `slot5/other`=6.0 | 14.6 |
| dark | deutan | 3.9 (slot3/slot5) | 2 — `slot2/slot4`=5.4, `slot3/slot5`=3.9 | 17.6 |
| dark | tritan | 5.6 (slot2/slot5) | 3 | 10.2 |

**Read this honestly: the palette passes adjacency everywhere and fails all-pairs under
protanopia.** A protanope can distinguish any two *neighbouring* slices in slot order, but
could confuse magenta with the neutral Other, or blue with violet in dark theme, if
zero-spend categories drop out (EC6) and those two end up adjacent.

Because that failure is unavoidable at eight hues, the design makes colour a **redundant**
channel rather than a primary one. All of the following are mandatory, not stylistic:

1. Every slice ≥ 3% carries a **direct label** (name + %); smaller ones get leader lines.
2. A legend with swatch **+ name + amount + %** sits beside the chart.
3. The FR18 category table repeats all of it in text, sorted by amount.
4. `chart.sliceGap` separates adjacent fills (§6.3).
5. Never more than 8 coloured slices; the rest fold into Other.
6. Top-category emphasis is geometry + label, never hue (§3.3).
7. A pattern-fill toggle is available for CVD users, print, and `forced-colors` mode.

If you delete the labels, the chart becomes inaccessible. That is why they are in every
screen spec rather than in a "nice to have" list.

### 6.5 Greyscale / print separation

Relative luminance ×100 for the light palette:
`slot1=19 slot2=28 slot3=32 slot4=43 slot5=34 slot6=16 slot7=7 slot8=22 other=29`

Spread 7→43 across nine fills means several pairs are within 3 luminance points
(slot2/other = 28/29). Printed in greyscale, the chart is a **shape** diagram, not a colour
one — which is exactly why the pulled top slice and direct labels are the emphasis
mechanism. The print stylesheet additionally switches slices to pattern fills.

---

## 7. Layout

- **4 px base grid.** Every spacing token is a multiple. `md` (12) is the default gap inside
  a card, `xl` (24) between cards, `xxl` (32) between sections.
- **12 columns, 24 px gutter, 40 px page margin**, on a 1280 px frame — 40 + 12×72 + 11×24
  + 40 = 1280 exactly.
- **Row height 44 px** everywhere a row is clickable. This is also the minimum touch target,
  which matters because §15 lists a mobile view as a future enhancement and a 32 px row
  would have to be redesigned then.
- **Auto layout on every container** in the Figma file. A frame that is pixel-frozen cannot
  be reflowed by a developer checking a longer category name, and Indian merchant strings
  in bank narrations are long.
- **The report is a single scrolling column**, not a dashboard grid, because it is printed
  and archived. Two-column regions (chart + legend, table + drill-down) collapse to one
  column under 900 px.

---

## 8. What the design system deliberately does not include

| Not included | Why |
|---|---|
| An icon set | Icon fonts and SVG sprite sheets are external assets; NFR1 forbids anything fetched. The few glyphs needed (▲ ▼ ✓ ✕ ⚠) are Unicode text, styled by the type tokens. |
| Animation tokens | The report is a document. The only motion is the drill-down panel's 120 ms slide, defined in the screen spec that uses it, and it respects `prefers-reduced-motion`. |
| A component for budgets, goals or alerts | NG1. There is deliberately no visual language for "over budget", so nobody can build it by accident. |
| A mobile breakpoint set | §15 future enhancement. The single-column collapse at 900 px is the only responsive behaviour v1 promises. |
| Brand colour | There is no brand. `accent` is functional (interactive), not decorative. |
