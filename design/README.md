# ExpenseInNutshell — Design Package

The visual half of the solution-architecture deliverable for the **Monthly Expense Summary
Tool**. Its architecture counterpart is [`../docs/architecture/`](../docs/architecture/).

**Source spec:** [`../src/main/resources/specs/spec.md`](../src/main/resources/specs/spec.md)
— *Monthly Expense Summary Tool — Specification*, Status **Draft v1.0**, owner Madhubala
Jadhav, last updated 2026-08-26.

**What this package is for.** The product's entire value is one screen that answers *"where
did most of my money go this month, and should I cut it back?"* (G4, G5, US4, FR13, FR17).
That screen is a `file://` HTML document generated locally every month. This package
specifies what it looks like, in enough detail that a developer can build it and a designer
can extend it without asking follow-up questions.

---

## 1. Read it in this order

| # | File | What it gives you |
|---|---|---|
| 1 | [`design-system/design-system.md`](design-system/design-system.md) | Why every token has the value it has, the **measured** accessibility numbers, and the component inventory. Start here. |
| 2 | [`design-system/tokens.json`](design-system/tokens.json) | The source of truth. Colour, type, spacing, radius, shadow, sizes, and the chart palette, in both themes. |
| 3 | [`screens/01-monthly-dashboard.md`](screens/01-monthly-dashboard.md) | The product. Everything else supports this one page. |
| 4 | [`screens/`](screens/) 02 → 08 | The remaining seven surfaces, in dependency order. |
| 5 | [`figma-plugin/README.md`](figma-plugin/README.md) | How to turn all of the above into real Figma layers. |

If you only have ten minutes: read `design-system.md` §3 (the chart palette, which is the
hardest constraint in the package) and `screens/01-monthly-dashboard.md`.

---

## 2. What's in here

```
design/
├─ README.md                                  this file
├─ design-system/
│  ├─ tokens.json                             W3C-style design tokens — THE SOURCE OF TRUTH
│  ├─ design-system.md                        token rationale + measured WCAG/CVD results
│  └─ contrast-check.js                        the validator that produced those numbers
├─ screens/
│  ├─ 01-monthly-dashboard.md                 S01 · the report — chart, callout, table
│  ├─ 02-category-drilldown.md                S02 · click a slice, see the transactions
│  ├─ 03-recategorize-and-corrections-tray.md S03 · fix a category, make it stick
│  ├─ 04-month-over-month.md                  S04 · what changed vs last month
│  ├─ 05-sources-and-top-transactions.md      S05 · provenance, transfers, top 5, skipped files
│  ├─ 06-uncategorized-review.md              S06 · the accuracy-gap queue
│  ├─ 07-empty-and-error-states.md            S07 · four degraded variants of the report
│  └─ 08-setup-and-run-console.md             S08 · the CLI as a designed surface
└─ figma-plugin/
   ├─ manifest.json                           Figma plugin manifest (api 1.0.0)
   ├─ code.js                                 the generator: styles, components, screens
   └─ README.md                               the literal click path to run it
```

---

## 3. Screens, and the requirements they serve

Screens are derived from the spec's user stories and output requirements, not from a
generic dashboard template. Every screen names the IDs it serves in its own header table.

| ID | Frame name in Figma | Page | Size | User stories | Key FR/NFR/AC |
|---|---|---|---|---|---|
| **S01** | `01 — Monthly Dashboard` | `01 Report` | 1280 × auto | US2, US3, US4, US6 | FR12, FR13, FR16, FR17, FR18, FR21, NFR6, AC2, AC3, AC5 |
| **S02** | `02 — Category Drill-down` | `02 Detail & Corrections` | 1280 × 900 | US3 | FR19, FR15, EC3, NFR6 |
| **S03** | `03 — Recategorize & Corrections Tray` | `02 Detail & Corrections` | 1280 × 1100 | US5 | FR9, FR10, FR20, G6, **AC4** |
| **S04** | `04 — Month over Month` | `01 Report` | 1280 × 1000 | US7 | FR14, NFR7, **AC6** |
| **S05** | `05 — Sources, Transfers & Top Transactions` | `01 Report` | 1280 × 1240 | US6 | FR5, FR6, FR11, FR15, EC4, **AC1**, **AC7** |
| **S06** | `06 — Uncategorized & Review Queue` | `02 Detail & Corrections` | 1280 × 980 | US5 | FR3, FR8, FR20, NG5, NFR6, **AC5** |
| **S07** | `07 — Empty & Degraded States` | `03 Diagnostics & Setup` | 1280 × 1600 | — | FR5, EC5, NFR3, NFR5, **AC7** |
| **S08** | `08 — Setup & Run Console` | `03 Diagnostics & Setup` | 880 × 1400 | US1 | FR16, FR22, FR23, NFR2, NFR5 |

**Coverage:** every FR with a visible surface is on at least one screen. FR1, FR2, FR4 and
FR7 have no UI of their own — they are parsing and categorization internals — but their
*user-visible consequences* do appear: the unknown-column mapping prompt (§11.2) in S08, and
the per-account extraction method and bank profile in S05.

Full requirement traceability, including the IDs with no visual surface, is in
[`../docs/architecture/05-traceability.md`](../docs/architecture/05-traceability.md).

---

## 4. The three constraints that shaped everything

Read these before changing anything, because most "obvious" improvements violate one.

### 4.1 No web fonts, no CDN, no remote images — ever

NFR1 and AC8 say no statement data leaves the machine, and FR16 says the report is
self-contained. The report is also an **archive**: NFR7 keeps every month forever, so a
report opened in three years must still render with no network and no runtime. Consequences:

- Type is a system stack in the report (`system-ui, -apple-system, "Segoe UI", Roboto,
  sans-serif`); Inter is only the Figma proxy for it.
- Icons are inline vector or Unicode glyphs (▲ ▼ ✓ ✕ ⚠). There is deliberately no icon set.
- The chart library is vendored and inlined, not fetched.

### 4.2 Emphasis is geometry, never hue

FR17 wants the top category "visually distinguished". The design uses a **pulled slice**
(12 px along the bisector), a **bolder direct label**, and the **FR13 callout in words** —
three cues, none of them colour. Colour cannot carry it, because:

- Chart slots are bound to **categories, not rank** (assumption A6), so a category is the
  same colour every month and US7/FR14's month-to-month comparison survives.
- The 8-hue palette **fails the all-pairs CIEDE2000 test under protanopia** (worst ΔE 1.9
  light, 1.8 dark). Measured, not assumed — see `design-system.md` §6.4 and risk R6.
- Three light-theme fills measure **under** the WCAG 1.4.11 3:1 target against white. The
  mandatory relief channel is the 2 px `chart.sliceGap` stroke at 3.07:1, so every slice
  *boundary* passes even where a *fill* does not.

Direct labels, the legend, and the FR18 table are therefore **mandatory, not decorative**.
Delete them and the chart becomes inaccessible.

### 4.3 The accuracy gap is designed, not hidden

NG5 says manual review "is part of the intended workflow, not a failure state", and NFR6
says uncategorized transactions must be visually obvious. So `Uncategorized` gets a warning
surface, a pill badge, a hatched chart wedge that never receives a slot colour, its own stat
tile on S01, and its own screen (S06) with a progress bar. It is never styled as "fine".

---

## 5. Two routes into Figma

The markdown specs and `tokens.json` are the reviewable artifact and the source of truth.
A Figma file is a *rendering* of them, and there are two ways to produce it.

### Route A — Figma MCP (build directly into a hosted file)

If your agent has the Figma MCP server's write tools available and authorised, it can build
the design straight into a real Figma file: create variables from `tokens.json`, then
components, then screens from those variables and component instances.

A working file has been created for this project and is the intended target:

| | |
|---|---|
| **File** | ExpenseInNutshell — Design Package |
| **File key** | not committed - see `design/.figma-file-key` (gitignored) |
| **URL** | `https://www.figma.com/design/<file key>` |

> **Current status: the file exists but is empty.** It was created successfully, but the
> MCP *write* call (`use_figma`) was refused by the permission layer in the session that
> generated this package, so no layers were written. Run Route B against this file — or
> re-run Route A from an interactive session — to populate it.

Rules if you take Route A: create the design system **first** (variables, then components),
build every screen from variables and component instances, put auto layout on every
container, name frames `<NN> — <Screen name>`, and **read back** what you created to confirm
it exists. A write call returning without error is not proof the design is there.

### Route B — the plugin script (offline, no account needed)

[`figma-plugin/code.js`](figma-plugin/code.js) generates the identical design as native
Figma layers, offline. See [`figma-plugin/README.md`](figma-plugin/README.md) for the exact
click path. Summary: Figma **desktop** app → Plugins → Development → Import plugin from
manifest… → pick `manifest.json` → Plugins → Development → run it. It adds pages to
**whatever file is currently open**, so open the design-package file first if you want both
routes to land in the same place.

Both routes produce the same page structure, the same style names, and the same component
names, so they are interchangeable.

---

## 6. Verifying the package

### Accessibility — reproducible, not asserted

Every ratio and ΔE in `design-system.md` §6 is copied from a real run of:

```
node design/design-system/contrast-check.js design/design-system/tokens.json
```

It must end with `================ RESULT: 0 WCAG pair(s) below target ================`.
Re-run it after **any** change to `tokens.json`. The three sub-3:1 chart fills are reported
as `BELOW` in the chart-series section by design — they are the documented R6 finding, and
the `RESULT` line counts only the WCAG text/non-text pairs.

### The plugin script

```
node --check design/figma-plugin/code.js
```

### Consistency rules that must hold across the package

1. Component names in `design-system.md` §5 == component names in `code.js`.
2. Every token referenced in `code.js` exists in `tokens.json`. (`code.js` embeds a mirror
   of `tokens.json` because a Figma plugin cannot read local files — keep the two in sync.)
3. Every screen spec names at least one requirement ID in its header table.
4. Screen specs reference tokens **by name only**. A hex typed in a screen spec is a bug.

---

## 7. What this package deliberately does not include

| Not included | Why |
|---|---|
| An icon set | NFR1 forbids fetched assets. Unicode glyphs and inline vector only. |
| Animation tokens | The report is a document. The only motion is S02's 120 ms panel slide, which respects `prefers-reduced-motion`. |
| Budget / goal / alert components | NG1. There is deliberately no visual language for "over budget", so nobody can build it by accident. |
| A mobile breakpoint set | Spec §15 future enhancement. The single-column collapse under 900 px is the only responsive behaviour v1 promises. |
| Brand colour | There is no brand. `accent` is functional (interactive), not decorative. |
| Dark-theme screen frames | Every dark token and paint style exists, but the generated screens apply the light theme only. Duplicate a page and re-bind to build the dark set. |

---

## 8. Open assumptions that affect the design

Two of the architect's assumptions are visible in these screens and are **pending owner
confirmation** — full detail in
[`../docs/architecture/06-risks-and-open-questions.md`](../docs/architecture/06-risks-and-open-questions.md).

- **A6 — chart slices are ordered by palette slot, not by size.** Rank is carried by the
  pulled slice, the callout and the sorted table instead. If you prefer a conventional
  descending pie, say so; it reads rank faster on a single month at the cost of
  month-to-month recognition, and it is a one-line change in the renderer.
- **A1 — INR, en-IN, day-first dates, Indian merchant examples.** Every string in the screen
  specs and in `code.js` uses ₹ and Indian merchants because the spec's own §10 examples do.
  Banking outside India changes currency, locale, date order and the seed keyword list —
  all config, no code.
