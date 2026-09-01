# Figma plugin — ExpenseInNutshell Design Generator

This plugin builds the whole design package as **native, editable Figma layers**: paint and
text styles generated from [`../design-system/tokens.json`](../design-system/tokens.json),
17 reusable components, and the 8 screen frames specified in [`../screens/`](../screens/).

It is the offline route. If you have the Figma MCP server wired into your agent, you can
build the same thing directly into a hosted file instead — see
[`../README.md`](../README.md) §"Two routes into Figma". Either way, `tokens.json`, the
`design-system.md` and the `screens/*.md` specs remain the source of truth; the Figma file
is a *rendering* of them.

---

## 1. Run it — the literal click path

You need the **Figma desktop app**. The plugin will not work in a browser tab: Figma only
loads plugins from local disk in the desktop client.

1. Download and install the desktop app if you don't have it: <https://www.figma.com/downloads/>
2. Open the desktop app and **open or create the file you want the design to land in**.
   The plugin adds pages to *whatever file is currently open* — see §3.
3. Menu bar → **Plugins** → **Development** → **Import plugin from manifest…**
4. In the file picker, select:

   ```
   C:\path\to\ExpenseInNutshell\design\figma-plugin\manifest.json
   ```

   (On macOS/Linux, the equivalent path to `design/figma-plugin/manifest.json`.)
5. Menu bar → **Plugins** → **Development** → **ExpenseInNutshell — Design Generator**
6. Wait. It takes roughly 10–30 seconds; the first few seconds are font loading with no
   visible progress. Do not click away.
7. A toast appears at the bottom when it finishes. On success it reads something like:

   ```
   ExpenseInNutshell design generated — 6 fonts loaded · 68 paint styles · 14 text styles
   · 41 variables · 17 components · 8 screen frames on 3 pages
   ```

   On failure it reads `FAILED: <message>`. The whole run is wrapped in try/catch precisely
   so a partial failure tells you *what* broke instead of dying silently.

To re-run after editing `code.js`: **Plugins → Development → Hot reload plugin**, then run
it again. You do not need to re-import the manifest.

---

## 2. Fonts

The plugin loads every font up front, before any text is written:

| Family | Styles used |
|---|---|
| **Inter** | Regular, Medium, **Semi Bold**, Bold |
| **Roboto Mono** | Regular, Medium |

Both are Figma defaults, so nothing needs installing. Note the style name is
`Semi Bold` with a space — `SemiBold` is not a real Figma style name and will throw.

If you see `FAILED: Cannot write to node with unloaded font`, a text style in
`tokens.json` is asking for a weight that is not in the list above. Add it to
`typography.fontWeight` and the loader picks it up automatically.

---

## 3. What it creates, and where

**It creates new pages in whatever file is already open.** It does not create a file, and
it does not touch your existing pages. Run it in a scratch file first if you are unsure.

Pages created (or reused, if a page of that name already exists):

| Page | Frames |
|---|---|
| `00 — Design System` | 17 components + component sets, plus the chart palette board |
| `01 Report` | `01 — Monthly Dashboard`, `04 — Month over Month`, `05 — Sources, Transfers & Top Transactions` |
| `02 Detail & Corrections` | `02 — Category Drill-down`, `03 — Recategorize & Corrections Tray`, `06 — Uncategorized & Review Queue` |
| `03 Diagnostics & Setup` | `07 — Empty & Degraded States`, `08 — Setup & Run Console` |

Frames are laid out left to right with a `size.gutter.frame` (160 px) gutter.

**The plugin is re-runnable.** Styles and components are looked up by name before being
created, so a second run updates them in place rather than making
`light/text/primary 2`. Screen frames, however, are *appended* — delete the old frames
before re-running if you don't want duplicates.

---

## 4. How to tell it worked

Full checklist is the self-check comment block at the bottom of `code.js`. The four things
worth spot-checking first:

1. **Styles, not hexes.** Select any layer on `01 — Monthly Dashboard`. The Fill row in the
   right panel should show a **style name** (`light/surface/raised`), not a loose colour
   swatch. Change `light/accent/default` in the Local styles panel and every primary button
   in the file should repaint at once. If a layer shows a raw hex, that is a bug.
2. **The pie is real geometry.** Click a wedge on `01 — Monthly Dashboard`. Figma should
   select a **Vector** node and show editable vector points on an arc — not a rectangle,
   not an image. The Rent/Housing wedge should sit 12 px out from the centre along its
   bisector, and the Uncategorized wedge should be hatched through a masked group.
3. **Auto layout everywhere.** Select any screen frame and drag its right edge. Sections
   should reflow, not clip. Every container was created through the `autoFrame()` /
   `asAuto()` helpers, so a pixel-frozen frame means something bypassed them.
4. **Components, not copies.** On `00 — Design System`, edit the `DeltaChip` component's
   padding. Every delta chip in the category table and the MoM table should follow.

---

## 5. Editing the design after generation

**Change a colour, size, or type ramp:** edit
[`../design-system/tokens.json`](../design-system/tokens.json), then mirror the change into
the `TOKENS` object at the top of `code.js` (a Figma plugin cannot read a local file, so the
token file is embedded), then re-run. Re-run
`node design/design-system/contrast-check.js design/design-system/tokens.json`
afterwards — it must still print `0 WCAG pair(s) below target`.

**Change a screen:** edit the matching `../screens/NN-*.md` first, then the corresponding
`buildSNN()` function in `code.js`. The markdown is the reviewable artifact; the Figma
frame is the rendering. Changing the frame alone leaves the package inconsistent.

**Add a component:** add it to the table in `../design-system/design-system.md` §5, then to
`buildComponents()`. The names in that table and the names in `code.js` are expected to
match exactly.

---

## 6. Network access

`manifest.json` declares `"networkAccess": { "allowedDomains": ["none"] }`. The plugin makes
no requests of any kind. The product it designs is offline-only by requirement (NFR1, AC8),
and the generator holds itself to the same rule.

---

## 7. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `Import plugin from manifest…` is missing | You are in a browser tab | Use the desktop app |
| `FAILED: Missing paint style: light/...` | The style phase did not complete, or a style was deleted by hand | Re-run; styles are recreated by name |
| `FAILED: Unknown design token: x.y.z` | `code.js` references a token that is not in its embedded `TOKENS` | Add it to `tokens.json` **and** to the `TOKENS` mirror at the top of `code.js` |
| `FAILED: Cannot write to node with unloaded font` | A weight not in §2 | Add it to `typography.fontWeight` in both files |
| Duplicate frames after a re-run | Screen frames are appended, not replaced | Delete the previous frames first |
| Plugin appears to hang for ~20 s | Font loading and ~700 node creations | Normal. Wait for the toast. |
