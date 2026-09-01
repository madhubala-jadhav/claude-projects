# ADR-0007 — Report rendering: Jinja2 + a vendored Chart.js, inlined into one file

**Status:** Superseded (template engine only) by
[ADR-0019](ADR-0019-report-rendering-java-pebble.md), which re-picks Jinja2 as Pebble for
Java. Chart.js, the offline assertion, and all seven pie-chart binding rules below are
unchanged and still apply.
**Date:** 2026-08-26
**Superseded by:** [ADR-0019](ADR-0019-report-rendering-java-pebble.md) (2026-08-28)

---

## Context

FR16 requires a **self-contained** local HTML report that opens automatically. FR17 requires
a pie chart of spend-by-category with the top category visually distinguished. FR18 requires
the summary table, the FR13 callout and the FR14 comparison alongside the chart. FR19
requires hover/click drill-down into a slice's transactions. NFR1/AC8 forbid any network
fetch. The spec suggests "Plotly (embedded interactive chart in HTML) **or** Chart.js via a
Jinja2-rendered HTML template" — an explicit either/or that this ADR has to settle.

"Self-contained" plus "offline" together mean **every asset must be inlined at render
time**: no CDN `<script src>`, no Google Fonts `@import`, no external stylesheet. A CDN
reference would not merely be a broken chart offline — it would be an outbound request
carrying the report's existence to a third party, breaking AC8's promise on the very
artifact that is supposed to embody it.

## Decision

**Jinja2 templating + Chart.js (UMD build) vendored into the package and inlined into every
report. System font stack only. Design tokens as CSS custom properties. Data as a JSON
island.**

### Composition

| Layer | Choice | Why |
|---|---|---|
| Template | **Jinja2** | As the spec suggests; mature, autoescaping on, testable |
| Chart | **Chart.js v4 UMD**, vendored under `expense_nutshell/report/assets/` | ~200 KB minified; native pie/doughnut with per-slice `offset` (FR17), built-in hover tooltips (FR19), `onClick` element hit-testing (FR19) |
| Styling | Hand-written CSS with design tokens as custom properties, inlined | No framework, no build step; `[data-theme]` swaps light/dark in one place |
| Fonts | **System stack only** — `system-ui, -apple-system, "Segoe UI", Roboto, sans-serif` | Zero bytes, zero network, renders natively everywhere (NFR1, NFR4) |
| Data | `<script type="application/json" id="expense-data">` | Parsed once by the page's JS; keeps data out of the markup and makes the report machine-readable |
| Behaviour | ~200 lines of vanilla JS, inlined | Drill-down, category `<select>`s, corrections tray, theme toggle |

### The offline assertion

At write time, `render_report()` asserts the produced string contains no `src="http`,
`href="http`, `@import url(http`, or `fetch(`. If the assertion fails, the write fails.
This is the mechanical proof of NFR1/AC8 at the artifact level, complementing the
import-lint at the code level ([03](../03-interfaces-and-contracts.md) F10).

### The pie chart, specifically (FR17, G4, US3)

The chart is the product's centrepiece, so its rules are part of this decision, not left to
the implementer. Full derivation and the measured colour numbers live in
[design-system.md](../../../design/design-system/design-system.md); the binding rules are:

1. **Colour follows the category, never its rank.** Each category owns a fixed palette slot
   (`slot: 1..8` in `categories.yaml`), so Food is the same colour every month. Rank-based
   colouring would repaint the whole chart whenever spending shifted, destroying
   month-over-month recognition.
2. **Slices are ordered by slot, not by value.** Fixed ordering keeps adjacency stable and
   makes two months' charts directly comparable at a glance. Rank is communicated by the
   pulled slice, the callout and the table — three places, none of which is slice position.
3. **Top-category emphasis is not colour-based** (FR17). It is a **12 px pulled slice** +
   a 2 px surface ring + a bold direct label + the FR13 text callout above the chart. A
   user with any colour-vision deficiency, or reading a greyscale print, still identifies
   the top category immediately.
4. **Maximum 8 coloured slices + "Other".** The validated categorical palette carries 8
   distinguishable hues; a 9th generated hue is not colour-blind-safe. Categories beyond
   `report.top_n_slices`, below `report.min_slice_percent`, or with `slot: null` fold into a
   neutral "Other" slice — which remains fully itemised in the table (FR18) and
   drillable (FR19).
5. **Every slice ≥ 3% carries a direct label** (name + %); smaller ones use leader lines.
   Identity is never carried by colour alone. This is also the mandated relief channel for
   the three light-mode palette slots that sit below 3:1 against white.
6. **Zero-spend categories are excluded from the chart** (EC6) but retained in
   `by_category` for FR14 history.
7. **2 px surface-coloured gap between slices**, so adjacent fills never touch.

## Alternatives considered

### A. Plotly (the spec's first suggestion) — **rejected**

*Attraction:* the richest interactivity out of the box, and `pull=` gives FR17's emphasis
directly.

*Why rejected:* `plotly.js` inlined is **~3.5 MB minified**, which would be ~90% of a report
whose data is ~300 KB. Twelve reports a year is ~42 MB of duplicated library across an
archive NFR7 requires us to keep forever. The `include_plotlyjs="cdn"` option that avoids
this is exactly what NFR1/AC8 forbid. Chart.js delivers everything FR17/FR19 need at ~6% of
the size.

### B. Server-side static SVG (matplotlib) — **rejected**

Smallest possible output and beautiful control. But it cannot satisfy FR19's hover/click
drill-down without hand-writing an interaction layer over the SVG, and it adds matplotlib
(~40 MB with its dependencies) to the distribution. Retained as a **fallback**: if the
vendored Chart.js asset is missing, the report renders a static SVG pie and emits
`WARN-507`, so FR17's chart still appears even in a degraded install.

### C. Hand-rolled SVG pie generated in Python, interactive via a little JS — **rejected as primary**

Zero dependencies and full control, and honestly attractive. But re-implementing tooltips,
hit-testing, legend toggling, responsive resize and accessibility is a meaningful amount of
code to own and test, for a solved problem. It *is* the mechanism behind alternative B's
fallback path.

### D. Web fonts (Inter, Roboto via `@font-face`) — **rejected**

Better typographic control, but a font file is 100–300 KB inlined **per report**, or a
network fetch. The system stack renders natively on every target OS and costs nothing
(NFR1, NFR4).

### E. A CSS framework (Tailwind/Bootstrap) — **rejected**

Either a build step (against a tool whose whole point is one command) or a large inlined
stylesheet mostly unused. The report is one page with about a dozen component types;
hand-written CSS against design tokens is smaller, faster and directly traceable to
`tokens.json`.

### F. React/Vue app inside the report — **rejected**

A build toolchain and a framework runtime inlined into an artifact meant to still open in
2036. Vanilla JS over a JSON island has no such decay.

## Consequences

**Positive**

- One file, ~500–650 KB, that works offline forever, from `file://`, in any modern browser
  (FR16).
- FR17 and FR19 are satisfied with a mature charting library rather than bespoke code.
- Design tokens flow from `tokens.json` → CSS custom properties → both the report **and**
  the Figma plugin, so the report and the design file cannot drift.
- Light/dark and a print stylesheet come nearly free.
- The offline assertion makes AC8 testable in one line.

**Negative / accepted costs**

- **We deviate from the spec's first-listed chart suggestion.** Justified on NFR7 (archive
  size) and NFR1 (no CDN escape hatch); the spec offered Chart.js as an equal alternative,
  so this is a choice within the spec, not against it.
- Chart.js must be vendored into the repository and its licence (MIT) shipped alongside.
  Version bumps are a deliberate, reviewed act — which is appropriate for something inlined
  into permanent artifacts.
- Report size grows linearly with transaction count. Fine at NFR2's ~500-transaction
  ceiling; would need revisiting for a multi-year view.
- Hand-written CSS means responsive behaviour is ours to get right. Bounded by the screen
  specs in [`design/screens/`](../../../design/screens/).
- Chart.js's canvas rendering is not natively screen-reader accessible; the mandated
  category table (FR18) is the accessible equivalent, and the canvas carries an
  `aria-label` summarising the top category.
