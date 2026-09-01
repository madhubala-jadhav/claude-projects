# ADR-0008 — Money as exact `Decimal`; multi-currency without FX conversion

**Status:** Superseded (numeric type only) by
[ADR-0018](ADR-0018-money-java-bigdecimal.md), which re-picks `decimal.Decimal` as
`java.math.BigDecimal` with `RoundingMode.HALF_UP` for Java. The exactness requirement, the
`ROUND_HALF_UP` rounding choice, the multi-currency segregation rule (EC4), the sign/
direction model and every alternative weighed below are unchanged and still apply.
**Date:** 2026-08-26
**Superseded by:** [ADR-0018](ADR-0018-money-java-bigdecimal.md) (2026-08-28)

---

## Context

Every number this tool shows is money, and the whole product is a decision aid — if
`total_spend` is wrong by ₹0.03 the user loses trust in the ₹42,350 too.

Two specific pressures:

1. **Binary floats cannot represent decimal currency.** `0.1 + 0.2 == 0.30000000000000004`.
   Summing 500 transactions in `float64` produces visible cent-level drift, and percentages
   computed from drifted totals will not sum to 100.0% in the FR18 table — a defect the user
   can see instantly and cannot un-see.
2. **EC4 requires multi-currency handling.** *"Multi-currency transactions (foreign
   transaction on an INR account) → captured with original currency noted; converted amount
   used for totals if available in the statement, otherwise flagged `needs_review`."*
   Note what the spec does **not** say: it never asks the tool to *perform* a conversion.

The spec's suggested stack includes `pandas`, whose default numeric dtype is `float64` —
which is one of the reasons [ADR-0004](ADR-0004-tabular-parsing-and-bank-profiles.md) drops
it.

## Decision

### 1. `decimal.Decimal` end to end

From `parse_amount()` to the rendered string, every monetary value is a `Decimal`. `float`
appears nowhere in a money path. Percentages are computed as `Decimal`, and converted to
`float` only at the final formatting step, where 1-dp display rounding is harmless.

- **Context:** a module-local `decimal.Context` with `prec=28`, `rounding=ROUND_HALF_UP`.
  `ROUND_HALF_UP` matches how bank statements and Indian financial convention round, and
  matches user expectation; Python's default `ROUND_HALF_EVEN` would produce
  "₹2.5 → ₹2" and generate support questions.
- **Quantization:** amounts are quantized to the currency's minor-unit precision at parse
  time (2 for INR/USD/EUR, 0 for JPY) via a small `minor_units(currency)` table. Derived
  values (totals, percentages) are quantized only for display, never mid-computation.

### 2. Serialization as JSON **strings**

`"amount": "487.00"` — never `487.00`. A JSON *number* round-trips through a float in almost
every parser, so `487.00` can return as `486.99999999999994`. This matters because
`summary.json` is read back by **next month's run** (FR14) — a lossy round trip would
silently corrupt month-over-month comparison. Strings feed `Decimal` directly and losslessly.

In `transactions.csv` amounts are written as plain unquoted decimal strings with `.` as
separator and no thousands grouping, so Excel and every other consumer parse them
unambiguously. Formatting for humans (`₹42,350.00`, Indian lakh grouping) happens **only**
in the HTML report.

### 3. Multi-currency: segregate, never convert (EC4)

- Each `Transaction` carries `currency`. The run has a single **run currency**
  (`config.currency.default`, default `INR`).
- If a foreign transaction's statement row **includes** a home-currency amount, that becomes
  `amount`/`currency` and the foreign values are preserved in
  `original_amount`/`original_currency`.
- If it does **not**, the transaction keeps its foreign currency, is marked
  `needs_review = True` with `FIELD-402`, and is **excluded from `total_spend`,
  `total_income` and every category total**. It remains fully visible in the transaction
  table, the drill-down and the CSV, badged with its currency.
- `MonthlySummary.currency` records which currency the totals are in — without it,
  `"total_spend": "42350.00"` is meaningless.

**The tool never fetches an exchange rate.** Doing so would require a network call, which
NFR1, AC8 and §13 forbid outright. Nor does it use a hardcoded rate, which would be wrong by
an unknown amount on an unknown date and would be worse than an honest gap. This is exactly
what EC4 prescribes.

### 4. Direction carries the sign; amounts are positive magnitudes

`amount` is always `> 0`; `direction` is `"debit"` or `"credit"`. A `-487.00` in a signed
amount column becomes `amount=487.00, direction="debit"`.

*Why:* sign conventions are inconsistent across banks (some sign debits negative, some sign
credits negative, some use separate columns, some use `Dr`/`Cr` suffixes). Normalizing the
ambiguity into one explicit enum at parse time means no downstream component ever has to ask
"is negative a debit here?" — the class of bug that produces a report where income and spend
are swapped.

Refunds follow directly: a refund is a `credit`, counted as income, **never netted** against
the original debit's category — both stay visible, exactly as EC3 requires.

## Alternatives considered

### A. `float` — **rejected.** Cent-drift on 500-row sums; percentages that do not total 100.0%; lossy `summary.json` round trips that corrupt FR14.

### B. Integer minor units (paise/cents) — **rejected, but respectable**

Exact, fast, and immune to context configuration — the classic financial-systems answer. Two
reasons against it here: it needs a currency-aware scale factor at every boundary (JPY has 0
minor units, and some currencies have 3), and every parse and render site becomes a
multiply/divide that is easy to get wrong once. `Decimal` gives the same exactness while
keeping `"487.00"` legible in JSON, CSV, logs and tests — legibility matters for a tool
whose artifacts are meant to be human-inspectable. The performance difference is irrelevant
at 500 rows.

### C. A money library (`py-moneyed`, `money`) — **rejected**

Would give currency-aware arithmetic and formatting for free. But it is another dependency
for what amounts to `Decimal` plus a small minor-units table, and most such libraries want to
do FX conversion — a feature actively forbidden here.

### D. Convert foreign currency using a bundled static rate table — **rejected**

Requires no network, but produces a specific wrong number for any date the table does not
cover, and creates an obligation to keep the table current. A visible gap the user can
resolve (they know what the card was charged) beats an invisible inaccuracy.

### E. Compute totals per-currency and show several totals — **deferred**

Genuinely correct for a heavy multi-currency user, but the spec's persona and G3's "one
screen answer" want a single headline number. Deferred; the data model already supports it
(`currency` is on every transaction), so it is additive later.

## Consequences

**Positive**

- Totals are exact; category percentages sum to 100.0%.
- `summary.json` round-trips losslessly, so FR14/AC6 stay correct across years.
- Sign ambiguity is eliminated at the earliest possible point.
- EC3 and EC4 are satisfied literally, with no hidden assumptions.
- No FX network call ⇒ NFR1/AC8 remain structural.

**Negative / accepted costs**

- **Foreign-currency transactions are excluded from totals**, so a user with heavy
  international spend sees a `total_spend` that under-counts. This is deliberate and
  *visible*: the count appears in `needs_review_count`, the rows are badged in the table,
  and the report explains it. An invisible wrong number would be worse.
- `Decimal` requires discipline: one stray `float()` cast reintroduces the bug. Mitigated by
  a lint rule banning `float(` in money paths and a property test asserting
  `sum(by_category) == total_spend` exactly.
- Amounts as JSON strings look unusual and require a deliberate `Decimal(...)` on read.
  Documented as frozen contract F1/F2.
- A minor-units table must be maintained for any currency the user actually uses; unknown
  currencies default to 2 with a warning.
