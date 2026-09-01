# 06 — Risks & Open Questions

Two sections: **technical risks** the architecture has to survive, and **assumptions** I
adopted for questions the spec left open. Everything in §2 is an architect's decision made
so work can proceed — each is flagged for owner confirmation, and each is cheap to reverse
if the owner disagrees.

---

## 1. Technical risks

Likelihood and impact are Low / Medium / High. "Exposure" is the product, used only for
ordering.

### R1 — Bank statement format diversity defeats the parsers

| | |
|---|---|
| **Likelihood** | **High** — this is the single most probable source of real-world failure |
| **Impact** | **High** — a bank that cannot be parsed makes the tool useless *for that user* |
| **Exposure** | **Critical** |

Bank PDF layouts vary in column positions, ruling, header repetition and page furniture;
CSV exports vary in header names, preamble rows and sign conventions. Banks also change
their export format without notice. §11 acknowledges this; OQ1 asks about it directly.

**Mitigations**
- Everything format-specific is declarative config, never code
  ([ADR-0004](04-adr/ADR-0004-tabular-parsing-and-bank-profiles.md), FR22).
- A generic Indian-savings PDF template plus generic tabular profile as the default
  ([ADR-0011](04-adr/ADR-0011-day-one-bank-format-support.md)).
- The interactive one-time column-mapping flow (§11.2) means *no* bank is unsupported —
  the worst case is one guided minute.
- Failure is per-file and named, never a crash (FR5, NFR3), so one bad bank does not
  destroy the month.
- Slice 3 exit criteria require at least two *real* bank exports to validate the seeds.

**Residual risk.** PDF table extraction has no interactive escape hatch equivalent to the
CSV column mapper. A truly hostile PDF layout still needs `pdf_table` tuning by someone who
understands the config. **Owner action that would retire most of this risk: supply one real
(redacted) export per bank actually used.**

---

### R2 — OCR accuracy on scanned statements

| | |
|---|---|
| **Likelihood** | **Medium** — only affects users whose banks issue scanned PDFs |
| **Impact** | **High** — a misread digit in an amount is silently wrong money |
| **Exposure** | **High** |

Tesseract on a 300 DPI scan misreads digits, especially `1`/`7`, `5`/`6`, `0`/`8`, and
struggles with the thin fonts and tight columns typical of bank tables.

**Mitigations**
- Word-level confidence is captured and carried to the report; rows under threshold are
  `needs_review` and visually badged (FR3, NFR6).
- OCR is the fallback, never the default — most statements never touch it (ADR-0003).
- [S06](../../design/screens/06-uncategorized-review.md) makes reviewing flagged rows an
  explicit workflow.
- A per-page balance-column sanity check is a cheap future addition: if a row's balance
  delta does not equal its amount, flag it regardless of OCR confidence.

**Residual risk.** A confidently-misread digit with high OCR confidence will pass through.
The review screen is the only defence. **Do not present OCR-derived numbers with the same
visual confidence as extracted ones** — the design enforces this with a distinct badge.

---

### R3 — Transfer detection misfires, corrupting the headline number

| | |
|---|---|
| **Likelihood** | **Medium** |
| **Impact** | **High** — a missed transfer can put "Transfers" at the top of the pie, making the product's core answer wrong |
| **Exposure** | **High** |

FR11 and OQ3 depend on knowing which accounts are the user's own. A missed transfer inflates
spend; a false positive hides real spend.

**Mitigations**
- Three-signal ladder with pair matching as primary
  ([ADR-0013](04-adr/ADR-0013-income-versus-internal-transfer.md)).
- Declared `accounts.yaml` registry, prompted for during setup.
- **Transfers are excluded *and* shown** (`transfers_total`, the transfers panel), so an
  error is visible rather than silent — the single most important mitigation here.
- The user can override any transaction via the normal FR20 correction flow.
- The report warns when a large recurring debit looks like an undeclared card payment.

**Residual risk.** A first-run user who has not filled in `accounts.yaml` gets keyword
matching only. The setup script's prompt is the mitigation, and the report says which
accounts it knew about.

---

### R4 — The correction round trip is too clumsy, so users stop correcting

| | |
|---|---|
| **Likelihood** | **Medium** |
| **Impact** | **Medium** — the tool still works, but accuracy stops improving and G6/US5 quietly fail in practice |
| **Exposure** | **Medium** |

On Firefox and Safari, FR20 requires: click Save → find the download → move it to `input/`
→ re-run. Four steps where one was implied.

**Mitigations**
- Chromium's File System Access API removes the manual step entirely
  ([ADR-0006](04-adr/ADR-0006-persisting-user-corrections.md) alt B).
- The drop folder is `input/` — a folder the workflow already trains the user to use.
- The report prints the literal absolute destination path.
- Harvest happens *before* parsing, so dropping the patch and re-running shows the fix
  immediately.
- The in-page UI updates the category, chart and totals optimistically, so the user sees
  the intended end state at once.
- `beforeunload` warns on unsaved corrections.

**Residual risk.** Real. This is the most likely thing to prove annoying in month 2. The
documented escalation is an opt-in loopback `--serve` mode, which would need its own ADR
and its own NFR1 argument.

---

### R5 — Report file size grows with the archive

| | |
|---|---|
| **Likelihood** | **Low** at the spec's scale |
| **Impact** | **Low** |
| **Exposure** | **Low** |

Each report inlines ~200 KB of Chart.js plus the month's data. At 12 reports/year that is
roughly 8–10 MB/year, all duplicated library bytes.

**Mitigations.** Chart.js was chosen over Plotly specifically for this (~200 KB vs ~3.5 MB —
[ADR-0007](04-adr/ADR-0007-report-and-chart-rendering.md)). If it ever matters, a shared
`assets/` folder next to `output/` with relative references stays offline and self-contained
*enough*, at the cost of a month folder no longer being individually portable — a trade
worth making only if the archive becomes a real problem.

---

### R6 — Chart palette cannot make 8+ categories pairwise distinguishable

| | |
|---|---|
| **Likelihood** | **High** — this is measured, not speculative |
| **Impact** | **Medium** — a colour-vision-deficient user could confuse two slices |
| **Exposure** | **Medium-High** |

Validated with the palette checker
([`design/design-system/contrast-check.js`](../../design/design-system/contrast-check.js),
output recorded in [design-system.md](../../design/design-system/design-system.md) §6). The
8-hue categorical palette passes every gate on the *adjacent* pairlist in both themes
(worst adjacent CVD ΔE 8.4 light / 10.2 dark against a floor of 6), but **fails the
all-pairs test under protanopia**: worst all-pairs ΔE **1.9 light** (`slot5`/`other`) and
**1.8 dark** (`slot1`/`slot7`), with 2 light and 3 dark pairs under the floor. A pie chart
can place any two categories next to each other once zero-spend categories drop out (EC6),
so all-pairs is the honest test.

Separately, three light-theme slot fills measure **under** the WCAG 1.4.11 3:1 non-text
target against white: `slot3` 2.82:1, `slot4` 2.17:1, `slot5` 2.69:1. Darkening them far
enough to pass would collapse the ΔE separation above — the two goals pull against each
other at 8 hues. The gap is closed with a relief channel instead: `chart.sliceGap` is a
2 px **#8A94A3** stroke (3.07:1 on white, 5.71:1 on the dark surface) drawn between every
pair of adjacent slices, so the *boundary* is always perceivable even where a *fill* is not.

**Mitigations** (all mandatory, not optional — see
[design-system.md](../../design/design-system/design-system.md)):
- Every slice ≥ 3% carries a **direct label** with the category name; smaller ones get
  leader lines. Identity never depends on colour.
- A legend with swatch + name + amount + %.
- The FR18 category table is the full relief channel and is required by the spec anyway.
- 2 px `chart.sliceGap` stroke between slices, at 3:1 against the chart surface, so adjacent
  fills never touch and every slice boundary satisfies WCAG 1.4.11 on its own.
- Maximum 8 coloured slices; everything else folds into a neutral "Other".
- Top-category emphasis is a **pulled slice + ring + bold label**, never hue alone (FR17).
- A pattern-fill toggle for CVD users, print, and `forced-colors` mode.

**Residual risk.** Two similar hues can still sit adjacent. Accepted, because the mitigation
stack means no information is carried by colour alone — colour is redundant encoding here,
not primary.

---

### R7 — `merchant_key` normalization is a frozen contract that will want to change

| | |
|---|---|
| **Likelihood** | **Medium** |
| **Impact** | **Medium** — a change orphans every saved correction |
| **Exposure** | **Medium** |

The normalization heuristic (strip rails, strip references, keep 3 tokens) will meet
descriptions it handles badly, and improving it breaks existing keys
([03](03-interfaces-and-contracts.md) F6).

**Mitigations.** Every correction stores `example_description`, so a migration can re-derive
keys under the new algorithm. `history[]` preserves prior values. The `scope: "transaction"`
option gives an exact-pin escape hatch that no key change can break.

---

### R8 — Python not present, or blocked, on the user's machine

| | |
|---|---|
| **Likelihood** | **Low-Medium** |
| **Impact** | **High** — nothing runs at all |
| **Exposure** | **Medium** |

[ADR-0010](04-adr/ADR-0010-packaging-and-distribution.md) deliberately defers PyInstaller,
so an interpreter is required. Corporate machines may also block script execution.

**Mitigations.** `setup.*` detects the absence and prints a direct download link and the
exact clicks — never a traceback. The README documents the one-time PowerShell
execution-policy and macOS quarantine unblocks. PyInstaller remains a purely additive
escalation that changes no contract.

---

### R9 — Deleting the Java skeleton is rejected by the owner

| | |
|---|---|
| **Likelihood** | **Low-Medium** |
| **Impact** | **Medium** |
| **Exposure** | **Medium** |

[ADR-0001](04-adr/ADR-0001-language-and-runtime.md) removes `pom.xml` and
`src/main/java/org/example/Main.java`. The owner created that project and may want Java.

**Mitigation.** The architecture was deliberately written to be language-neutral. The
pipeline shape, all file formats, the error taxonomy, the categorization ladder, the whole
traceability matrix and the entire design package survive a switch to Java unchanged; only
ADR-0003, ADR-0004 and ADR-0010 would need their library choices re-picked. That is exactly
what spec §9's "the interfaces should stay stable regardless of implementation choice"
asked for. Flagged as **A5**.

---

### Risk ordering

| Rank | Risk | Exposure |
|---|---|---|
| 1 | R1 Bank format diversity | Critical |
| 2 | R2 OCR accuracy | High |
| 3 | R3 Transfer detection | High |
| 4 | R6 Chart palette / CVD | Medium-High |
| 5 | R4 Correction round trip | Medium |
| 6 | R7 `merchant_key` frozen | Medium |
| 7 | R8 Python availability | Medium |
| 8 | R9 Java skeleton removal | Medium |
| 9 | R5 Report size | Low |

---

## 2. Open questions and the assumptions adopted

Per the architect's brief, none of these blocked the design. Each was resolved in an ADR
and is recorded here as **an architect's assumption pending confirmation**.

### A1 — Which banks need day-one support? *(spec OQ1)*

> **Assumption adopted:** no fixed bank list. Ship a generic Indian-savings template
> (§11.1's own default) plus seed profiles for HDFC, ICICI, SBI, Axis and Kotak, and make
> the unknown-bank interactive mapping flow (§11.2) a first-class one-minute experience.
> Default currency INR, locale en-IN, date order DMY.

**Basis:** §11.1 names the Indian layout as the default; §10.1's own example uses INR, HDFC
and Bangalore; §10.3's keywords are all Indian merchants.
**Decided in:** [ADR-0011](04-adr/ADR-0011-day-one-bank-format-support.md)
**Confirm:** Which banks and account types do you actually hold? **One real redacted export
per bank would retire most of R1.** If you bank outside India, say so — currency, locale,
date order and the seed keyword list all change (all YAML, no code).
**Cost if wrong:** Low. Profiles are data; a wrong seed is one YAML edit.

---

### A2 — Credit cards: separate input type, or linked account? *(spec OQ2)*

> **Assumption adopted:** linked account, merged into the same monthly view, grouped by
> **transaction date** rather than billing cycle. The card bill payment from the bank
> account is detected as an internal transfer (FR11) so nothing is double-counted.
> Due-date, billing-period and interest logic are out of scope for v1.

**Basis:** §15 lists first-class credit-card statement support as a *future enhancement*;
FR11 explicitly names "credit card bill payments" as a transfer to exclude; G3/US4/FR13 want
one number and one top category.
**Decided in:** [ADR-0012](04-adr/ADR-0012-credit-card-statements.md)
**Confirm:** Do you want August's report to include a card purchase made on 14 Aug but paid
in September? (The assumption says yes — it is what "what did I spend in August" means.)
And do you want any due-date tracking? (The assumption says no; that would be a new
requirement, and would push against NG1's no-alerts boundary.)
**Cost if wrong:** Medium. Billing-cycle grouping would change C7's month assignment and
add a statement-period parse to C4.

---

### A3 — What counts as income vs an excluded internal transfer? *(spec OQ3)*

> **Assumption adopted:** movements between the user's own declared accounts are
> **transfers — neither income nor spend**, detected by debit/credit pair matching across
> `own: true` accounts within a 3-day window, backed by counterparty-alias matching and an
> `is_transfer` category flag. Non-transfer credits are income; salary keywords are
> always income. The excluded total is always displayed.

**Basis:** FR11's stated purpose is that transfers must not "inflate the 'spend' total";
counting a self-transfer as income would equally inflate income and make `net` meaningless.
**Decided in:** [ADR-0013](04-adr/ADR-0013-income-versus-internal-transfer.md)
**Confirm:** Two things settle this — (1) the list of accounts you hold, for
`accounts.yaml`; (2) that a salary→savings transfer should count as **neither** income nor
spend. Also: should a transfer *into* an account you did not supply a statement for count as
spend? (The assumption says no — it is flagged as a single-leg transfer.)
**Cost if wrong:** Low. `income_rules.treat_unmatched_credit_from_own_account_as` is a
single config key.

---

### A4 — Does NFR2's 30-second budget include OCR runs? *(architect-raised)*

> **Assumption adopted:** no. The 30 s budget applies to text extraction at the stated scale
> (~500 transactions, ≤ 5 files). OCR of a scanned multi-page PDF at 300 DPI takes minutes
> in any language and on any stack; treating that as an NFR2 violation would make the
> requirement unmeetable rather than useful.

**Basis:** NFR2 says "a typical monthly statement"; §11.1 frames scanned PDFs as the
exception requiring a fallback, i.e. not typical.
**Decided in:** [ADR-0003](04-adr/ADR-0003-pdf-parsing-strategy.md), recorded in
[05](05-traceability.md) §8.
**Confirm:** acceptable? The mitigation is a visible per-page OCR progress line so the wait
is explained rather than looking hung.
**Cost if wrong:** Medium — would force pre-emptive image downscaling and parallel OCR,
trading accuracy for speed on exactly the input where accuracy is already weakest (R2).

---

### A5 — Should the existing Java/Maven skeleton be discarded? *(architect-raised, from the repo state)*

> **Assumption adopted:** yes. Build in Python 3.11+ per NFR4's explicit "(Python +
> standard cross-platform libraries)", and delete `pom.xml` and
> `src/main/java/org/example/Main.java` in slice 1. Move `spec.md` out of the Maven
> directory convention to `docs/spec.md`, and rewrite `CLAUDE.md` in the same slice.

**Basis:** the skeleton is unmodified IDE template code with no logic, no tests and no
dependencies, so the sunk cost is genuinely zero; NFR4 names Python inside a numbered
requirement, not merely as a §9 suggestion; and the PDF-table and OCR ecosystems are
materially stronger in Python — which matters because R1 is the top risk.
**Decided in:** [ADR-0001](04-adr/ADR-0001-language-and-runtime.md)
**Confirm:** This is the one assumption that **discards existing repository content**, so it
deserves an explicit yes/no. If the answer is Java, say so before slice 1 — everything else
in this package still holds.
**Cost if wrong:** Medium, and bounded. Only ADR-0003, ADR-0004 and ADR-0010 would be
re-decided; all frozen contracts, the data model, the error taxonomy, the roadmap and the
entire design package are language-neutral.

---

### A6 — Chart slice colours are bound to categories, not to rank *(architect-raised)*

> **Assumption adopted:** each category owns a fixed palette slot, so the same category is
> the same colour every month and slices are ordered by slot rather than by value. Rank is
> communicated by the pulled slice, the FR13 callout and the sorted table — three
> independent channels, none of them slice position.

**Basis:** FR14/US7 are about comparing months; a chart that repaints itself whenever
spending shifts destroys that comparison. FR17's emphasis requirement is satisfied by
geometry, which is also what makes it work in greyscale and for CVD users (R6).
**Decided in:** [ADR-0007](04-adr/ADR-0007-report-and-chart-rendering.md),
[design-system.md](../../design/design-system/design-system.md)
**Confirm:** Do you prefer the conventional descending-by-size pie instead? It reads rank
faster on a single month at the cost of month-to-month recognition. This is a one-line
change in the renderer.
**Cost if wrong:** Very low.

---

## 3. What I would want before implementation starts

In priority order, each tied to the risk it retires:

1. **One real, redacted statement export per bank you use — PDF and CSV/Excel.** Retires
   most of R1 and converts A1's seed profiles from plausible to verified. By far the highest
   value item on this list.
2. **A yes/no on A5** (deleting the Java skeleton), because slice 1 begins with it.
3. **Your account list for `accounts.yaml`**, including credit cards — retires most of R3
   and settles A3.
4. **Confirmation of A2** (transaction-date grouping for cards, no due-date logic).
5. **One scanned statement**, if any of your banks issue them — sizes R2 and validates A4.
