# S03 — Recategorize & Corrections Tray

**Frame name in Figma:** `03 — Recategorize & Corrections Tray`
**Page:** `02 Detail & Corrections`
**Frame size:** 1280 × 1100 — a transaction table region with one `CategorySelect` open, the
sticky tray docked at the bottom, and the post-save instruction sheet shown as an inset.

---

## Purpose

Close the learning loop. The user sees a wrong category, fixes it in the report, and the fix
is remembered next month. This screen is the entire visible surface of G6 / US5 / FR9 /
FR20 / AC4.

## Requirements served

| ID | How |
|---|---|
| **FR20** | An in-report `<select>` per transaction row — no hand-editing of files |
| **FR9** | The exported patch is keyed on `merchant_key`, so it generalises to next month |
| **US5** | "Amazon from Shopping to Groceries", exactly the spec's own example |
| **G6** | The correction persists across runs |
| **AC4** | The save → drop → re-run path, spelled out on screen |
| **FR10** | The dropdown lists the user's own categories, including added/renamed ones |

---

## Layout

### 1. Transaction row with the select open

A `TransactionRow` in the `Uncategorized` variant, expanded to show the control:

```
┌────────────────────────────────────────────────────────────────────────────────┐
│ 19 Aug   AMAZON PAY                                    ₹1,000.00   [ Shopping ▾]│
│          UPI/AMAZON PAY/9928311/PAYMENT · icici_aug2026.csv                     │
│          ⬤ merchant key: AMAZON PAY                                             │
└────────────────────────────────────────────────────────────────────────────────┘
```

- Description: `textStyle.bodyMedium` / `text.primary`.
- Raw description + source file: `textStyle.caption` / `text.muted`.
- **Merchant key line**: `textStyle.mono` / `text.secondary`, shown only while the select is
  open. This is the single most important piece of honesty in the screen — it tells the user
  *what the correction will be remembered against*, so "Amazon" vs "Amazon Pay" vs this one
  transaction is a visible choice, not a hidden one (F6 in
  [03-interfaces-and-contracts.md](../../docs/architecture/03-interfaces-and-contracts.md)).
- Amount: `textStyle.numericSm`, right-aligned.

### 2. `CategorySelect` — open state

Width 220, `size.control.inputHeight` 36, `border.control`, `radius.sm`.
Open list on `surface.overlay`, `shadow.overlay`, `radius.md`, max-height 320, scrollable.

Each option is a row: `CategoryBadge` swatch · name (`textStyle.body`) · current-count in
`text.muted`. The current value carries a `✓` in `text.accent`.

```
  Food & Dining        63
  Groceries            11      ← hovered, surface.sunken
  Rent/Housing          1
  Transport            28
  Shopping              4  ✓
  Utilities             3
  Entertainment         0
  Healthcare            0
  EMI/Loan Payments     0
  Investments/Savings   0
  Transfers/Self        4
  Uncategorized         6
  ───────────────────────
  Apply to: (•) every future "AMAZON PAY"   ( ) only this transaction
```

**The scope radio is part of the control, not a separate dialog.** It maps directly to
`Correction.scope` (`merchant` | `transaction`). Default is `merchant`, because that is what
AC4 requires and what the user almost always means. `transaction` exists as the escape hatch
for a genuinely one-off charge, and is the only thing immune to a future `merchant_key`
change (risk R7).

The list contains **every category from `categories.yaml`**, including ones the user added
or renamed (FR10) — it is never a hardcoded list of twelve.

### 3. Immediate feedback after choosing

On selection, and with no page reload:

- The row's `CategoryBadge` changes to Groceries' slot colour, and the row gains a
  `surface.accentSubtle` background with a 2 px `accent.default` left edge, meaning
  *"changed, not yet saved"*.
- An inline `textStyle.bodySm` / `text.accent` note appears:
  **"Moved to Groceries. Undo"** — "Undo" is a link.
- **The pie chart, the category table and the stat tiles all recalculate optimistically.**
  Groceries goes ₹5,820.00 → ₹6,820.00; Uncategorized goes 6 → 5 and ₹1,420.00 → ₹420.00.
  Percentages re-apportion. This matters: the user is making a decision about their money,
  and they should see the corrected picture before they commit to it.
- The corrections tray appears/increments.

### 4. Corrections tray — sticky, bottom of the viewport

Full width minus `pageMargin`, `surface.raised`, `shadow.raised`, `radius.lg`,
`padding` `spacing.lg`, horizontal auto layout, `space-between`.

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  3 unsaved corrections                                    [ Discard ]  [ Save… ] │
│  AMAZON PAY → Groceries · PAYTM → Food & Dining · CROMA ELECTRONICS → Shopping   │
└──────────────────────────────────────────────────────────────────────────────────┘
```

- Count: `textStyle.h3` / `text.primary`.
- Summary line: `textStyle.bodySm` / `text.secondary`, truncated with "+2 more" past three.
- `Button/Secondary` **"Discard"** (danger-on-hover: `text.danger`).
- `Button/Primary` **"Save corrections"** — disabled at zero pending changes.

Variants in Figma: `CorrectionsTray/0` (hidden) and `CorrectionsTray/N` (shown).

### 5. Post-save instruction sheet

Clicking **Save corrections** triggers a `Blob` download of
`corrections-2026-08.json` and replaces the tray with a `surface.successSubtle` sheet:

> **✓ Saved corrections-2026-08.json to your Downloads folder.**
>
> To apply them, move that file into your input folder and run the tool again:
>
> `C:\Users\Madhubala\ExpenseInNutshell\input\`   [ Copy path ]
>
> Your next report — and every one after it — will put **AMAZON PAY** in **Groceries**
> automatically.

On Chromium, where the File System Access API is available, this becomes a single line
instead: **"✓ Applied directly to your corrections file. Nothing else to do."** The
four-step version is the honest fallback for Firefox and Safari, where a `file://` page
cannot write to disk ([ADR-0006](../../docs/architecture/04-adr/ADR-0006-persisting-user-corrections.md)).

**This is risk R4 made visible rather than hidden.** The design's job here is to make four
steps feel like a completed task, which is why the sheet states the end benefit
("every one after it") and not just the mechanics.

---

## States

| State | Treatment |
|---|---|
| **Zero pending** | Tray absent. Save button never rendered as an enabled control the user can click to no effect. |
| **Pending (N)** | Tray visible, `beforeunload` guard armed: closing the tab warns *"You have 3 unsaved corrections."* |
| **Saving** | Button shows a 16 px spinner for the duration of the Blob/File-System-Access call. Sub-second in practice; present so a permission prompt does not look like a dead button. |
| **Saved** | Success sheet (above). Row highlights fade to a `border.subtle` left edge — still marked as changed, no longer marked as pending. |
| **Discarded** | Confirmation inline in the tray: *"Discard 3 corrections?"* with **Discard** / **Keep**. All rows revert, chart and totals recalculate back. |
| **Error** | File System Access denied or unavailable → falls back to the Blob download automatically and shows the four-step sheet. A failure here **never** loses the pending changes; they stay in the tray. |
| **Loading** | Not applicable. |
| **Empty** | Not applicable — the screen only exists once a row is present. |

---

## The next run — how the loop closes visually

On the following run, the corrected transaction renders with a
`StatusBadge` reading **YOUR RULE** (`surface.accentSubtle`, `text.accent`), and hovering it
shows *"You moved AMAZON PAY to Groceries on 1 Sep 2026."* This is `category_source ==
"user_override"` made visible, and it is the on-screen proof that AC4 held.

If a saved correction points at a category that no longer exists (`WARN-501`), the report
shows a `surface.warningSubtle` note above the table: *"1 saved correction refers to
'Groceries (old)', which is no longer in your categories file. It was not applied."* — kept,
not deleted, so the user can fix the name.

---

## Interaction notes

| Trigger | Behaviour |
|---|---|
| Click / `Enter` on `CategorySelect` | Opens the list; focus moves to the current value. |
| Type-ahead | Typing filters the list ("gro" → Groceries). |
| `Esc` | Closes without changing. |
| `Undo` link | Reverts that one change and decrements the tray. |
| Multiple rows of the same merchant | Changing one shows *"3 other transactions from AMAZON PAY will also move."* and moves them together — one correction, one merchant, consistently applied. |
| Tab order | Row → select → scope radios → next row. The tray is reachable from anywhere via a skip link **"Go to unsaved corrections"**. |
| Screen reader | The optimistic recalculation announces via `aria-live="polite"`: *"Moved to Groceries. Groceries is now 6,820 rupees, 16.1 percent."* |
