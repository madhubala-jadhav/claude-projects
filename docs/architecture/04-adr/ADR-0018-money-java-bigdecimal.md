# ADR-0018 — Money in Java: `BigDecimal` end to end, `RoundingMode.HALF_UP`

**Status:** Accepted
**Date:** 2026-08-28
**Deciders:** Project owner, following [ADR-0014](ADR-0014-language-and-runtime-java-override.md)'s Java decision
**Supersedes:** [ADR-0008](ADR-0008-money-and-currency.md) (numeric type only — the exactness requirement, the multi-currency segregation rule, the sign/direction model and every alternative it weighed are kept, see below)

---

## Context

[ADR-0014](ADR-0014-language-and-runtime-java-override.md) commits the implementation to
Java 17, leaving [ADR-0008](ADR-0008-money-and-currency.md)'s numeric type — Python's
`decimal.Decimal`, with a module-local `decimal.Context` of `prec=28,
rounding=ROUND_HALF_UP` — without a runtime. This ADR re-picks the type only. **Every part of
ADR-0008 that is not a type name still holds**: `float` is banned from every money path;
amounts are positive magnitudes with a separate `direction` enum; multi-currency rows are
segregated, never converted, per EC4; refunds are credits, never netted against the original
debit (EC3); `MonthlySummary.currency` records which currency totals are in; and the tool
never makes a network call for an exchange rate. None of that is Python-specific, and none of
it is revisited here.

Two things about the Java standard library change how the successor type behaves, both
favorably:

1. **`java.math.BigDecimal` is arbitrary-precision by default.** Python's `Decimal` needed an
   explicit `prec=28` context because its precision is configurable and has a global default;
   `BigDecimal` has no such ceiling to configure — every value carries exactly the digits it
   was constructed with, unless a computation explicitly requests rounding via a
   `MathContext`. There is no Java equivalent of "set the context once" to get right.
2. **`BigDecimal.divide()` refuses to silently produce a non-terminating result.** Called
   with just a divisor and no `RoundingMode`, it throws `ArithmeticException` the moment the
   division doesn't terminate exactly (which most percentage calculations won't). Every
   divide site in a money path is therefore forced, by the compiler-adjacent API contract, to
   state its rounding intent explicitly — a stronger structural guard than ADR-0008's Python
   answer had, which relied entirely on a lint rule banning stray `float()` casts. This does
   not replace that lint rule (a `double` cast is still silently legal), but it closes the
   "unrounded division" half of the risk without any extra tooling.

## Decision

**`java.math.BigDecimal` end to end, constructed only from `String` (never `double`), with
`RoundingMode.HALF_UP` passed explicitly at every quantization and division site — matching
ADR-0008's `ROUND_HALF_UP` choice and its reasoning (bank-statement and Indian financial
convention rounds `₹2.50` up to `₹3`, not to even; Python's `ROUND_HALF_EVEN` default was
rejected for exactly this reason, and `RoundingMode.HALF_EVEN` — Java's own default-flavored
mode — would repeat the same mistake if picked out of habit).**

### 1. `BigDecimal` construction and quantization

- Every monetary value enters the system via `new BigDecimal(String)` — from a CSV cell, a
  PDFBox-extracted text cell, or a POI cell read through `DataFormatter` per
  [ADR-0015](ADR-0015-tabular-parsing-java-apache-poi.md). **Never** `BigDecimal.valueOf(double)`
  and never `new BigDecimal(double)` in a money path — both originate from a `double` and can
  reintroduce binary-float error before the value ever reaches `BigDecimal`'s exactness.
- Amounts are quantized to the currency's minor-unit precision at parse time via
  `amount.setScale(minorUnits(currency), RoundingMode.HALF_UP)` — 2 for INR/USD/EUR, 0 for
  JPY, from the same `minor_units(currency)` table ADR-0008 specified. Derived values
  (totals, percentages) are quantized only for display, never mid-computation — unchanged.
- Percentages: `BigDecimal.divide(total, MathContext.DECIMAL128)` (or an explicit scale +
  `RoundingMode.HALF_UP`) to avoid `ArithmeticException` on non-terminating results, then
  quantized to 1 decimal place for display using the same largest-remainder apportionment
  FR12 requires so the displayed column sums to exactly 100.0%.
- `float`/`double` appear nowhere in a money path, same prohibition as ADR-0008, now checked
  by a Checkstyle/PMD rule (the Java successor to ADR-0008's `float(` lint ban, part of
  [ADR-0017](ADR-0017-packaging-java-fat-jar.md)'s Dev tier) forbidding `double`,
  `Double`, `float` and `Float` types in the money-handling package, plus a property test
  asserting `sum(by_category) == total_spend` exactly — identical verification strategy to
  ADR-0008's.

### 2. Serialization as JSON strings — unchanged contract, new mechanism

`"amount": "487.00"`, never a bare JSON number — identical requirement to ADR-0008 §2, for
the identical reason (`summary.json` is read back by next month's run per FR14; a JSON
number round-trips through a `double` in most parsers, including a naive Jackson
configuration). The Java mechanism:

- A custom Jackson `JsonSerializer<BigDecimal>`/`JsonDeserializer<BigDecimal>` pair, registered
  on the money fields (or a `Money` wrapper type), serializing via `BigDecimal.toPlainString()`
  — **not** the default `toString()`, which can emit scientific notation for some scales
  (e.g. `new BigDecimal("1E+2")` prints `1E+2`, not `100`) — and deserializing via
  `new BigDecimal(jsonNode.asText())`.
- Do **not** rely on Jackson's `SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN` alone: it
  fixes the scientific-notation problem but still writes a JSON *number*, not a *string*,
  which is the actual contract F1/F2 in
  [03-interfaces-and-contracts.md](../03-interfaces-and-contracts.md) require. The custom
  serializer is what enforces "string, not number."
- `transactions.csv` amounts remain plain unquoted decimal strings (`.` separator, no
  thousands grouping) via `BigDecimal.toPlainString()` directly — unchanged from ADR-0008.
  Human formatting (`₹42,350.00`, Indian lakh grouping) stays confined to the HTML report,
  via `java.text.NumberFormat`/`DecimalFormat` with an `en-IN` locale rather than Python's
  formatting, applied only at render time.

### 3. Multi-currency, sign/direction, refunds — unchanged

ADR-0008 §3 and §4 carry over verbatim: segregate foreign-currency rows rather than convert
(EC4), no FX network call ever, `direction` as a separate `"debit"`/`"credit"` enum with
`amount` always positive, refunds as un-netted credits (EC3). None of this is
`Decimal`-specific and none of it changes here.

## Alternatives considered

### A. `double`/`float` — rejected, same reasoning as ADR-0008 §A

Unchanged by language: Java's `double` has the identical IEEE754 representation problem
Python's `float` does (`0.1 + 0.2 != 0.3` in Java too), producing the same cent-drift and
FR18 percentage-sum defect.

### B. Integer minor units (`long` paise/cents) — rejected, same reasoning as ADR-0008 §B, slightly weaker in Java's favor

Exact and fast, and Java's `long` arithmetic is simpler to reason about than Python's
arbitrary-precision `int` was as an alternative. Still rejected for the same legibility
reason: `"amount": "487.00"` stays human-inspectable in JSON/CSV/logs, whereas
`"amountMinor": 48700` requires a scale lookup to read. At 500 rows, `BigDecimal`'s
performance cost over `long` is irrelevant, same conclusion ADR-0008 reached for `Decimal`
over Python `int`.

### C. A Java money library (JSR 354 / `javax.money`, Joda-Money) — rejected, same reasoning as ADR-0008 §C

`javax.money`'s `MonetaryAmount` gives currency-aware arithmetic and formatting for free, but
most implementations default toward FX-conversion-oriented APIs — a feature this product
forbids — and pulling in a spec implementation (e.g. Moneta) is another dependency for what
`BigDecimal` plus a small `minorUnits(currency)` table already covers. Joda-Money is smaller
and closer to what's needed, but still adds a dependency to wrap functionality `BigDecimal`
already provides directly; not worth it at this scale.

### D. A bundled static FX rate table — rejected, same reasoning as ADR-0008 §D

Unchanged: a specific wrong number beats no number for trust, but a visible, honest gap beats
both. Still rejected.

### E. Per-currency totals shown separately — deferred, same reasoning as ADR-0008 §E

Still deferred; `currency` remains a field on every `Transaction` in
[02-data-model.md](../02-data-model.md), so it stays additive later regardless of language.

## Consequences

**Positive**

- Totals are exact; category percentages sum to exactly 100.0% — identical guarantee to
  ADR-0008, now backed by `BigDecimal`'s arbitrary precision rather than a configured
  28-digit context.
- `summary.json` round-trips losslessly across runs (FR14/AC6), via the custom
  string-serializing Jackson codec.
- `BigDecimal.divide()`'s refusal to silently truncate non-terminating results closes the
  "forgot to round" failure mode at the API level, not just via a lint rule — a genuine
  improvement over the Python path's reliance on `Decimal`'s ambient context precision.
- EC3/EC4 satisfied literally, unchanged. No FX network call, so NFR1/AC8 remain structural.

**Negative / accepted costs**

- **`BigDecimal` still requires discipline** the same way `Decimal` did: a stray
  `.doubleValue()` call or a `BigDecimal.valueOf(double)` on a value that started life as a
  `double` reintroduces the bug `BigDecimal` exists to prevent. Mitigated by the Checkstyle/
  PMD rule banning `double`/`float` types in the money package, plus the same
  `sum(by_category) == total_spend` property test ADR-0008 specified.
- **The JSON-as-string contract needs a hand-written Jackson codec**, not just a
  configuration flag — `WRITE_BIGDECIMAL_AS_PLAIN` alone is insufficient (see §2 above). This
  is new implementation surface that didn't exist in the Python path, where every `Decimal`
  naturally serialized through an explicit `str(...)` call site.
- Foreign-currency transactions are still excluded from totals, visibly — unchanged accepted
  cost from ADR-0008, not affected by the type change.
- A `minorUnits(currency)` table must still be maintained for any currency actually used;
  unknown currencies default to 2 with a warning — unchanged from ADR-0008.

## Verification

- A property test asserting `sum(by_category.amount) == total_spend` exactly, over generated
  `BigDecimal` fixtures, ported directly from ADR-0008's Python property test.
- A round-trip test: write `summary.json` for a month, read it back, assert every amount
  `BigDecimal.equals()`/`compareTo() == 0` the original — including a value like
  `"100.00"` that would print as `"1E+2"` under `BigDecimal.toString()` if the custom codec
  were bypassed.
- The Checkstyle/PMD rule fails the build on any `double`/`float`/`Double`/`Float` reference
  inside the money-handling package.
- The FR18 category-percentage column, summed over a fixture month with a non-round total,
  equals exactly 100.0% after largest-remainder apportionment.
