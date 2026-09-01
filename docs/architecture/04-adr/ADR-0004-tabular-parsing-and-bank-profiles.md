# ADR-0004 — CSV/Excel parsing via declarative per-bank profiles

**Status:** Superseded (library choice only) by
[ADR-0015](ADR-0015-tabular-parsing-java-apache-poi.md), which re-picks `openpyxl`/stdlib
`csv` as Apache POI/Apache Commons CSV for Java. The profile design, resolution algorithm and
`pandas` rejection below are unchanged and still apply.
**Date:** 2026-08-26
**Superseded by:** [ADR-0015](ADR-0015-tabular-parsing-java-apache-poi.md) (2026-08-28)

---

## Context

FR4 requires mapping CSV/Excel columns to the normalized schema *"using a configurable
column-mapping profile per bank, since export formats differ by bank."* §11.2 adds:
auto-detect header row and delimiter, match columns per bank, and — when nothing matches —
prompt the user to map columns **once** and save that mapping as a new profile.

The variability is not small. Real Indian bank exports differ in:

- **Column names:** `Date` / `Txn Date` / `Transaction Date` / `Value Date`; `Narration` /
  `Transaction Remarks` / `Description` / `Particulars`; `Withdrawal Amt.` /
  `Withdrawal Amount (INR )` (note the space before the paren) / `Debit` / `Dr`.
- **Direction encoding:** separate debit/credit columns, or one signed amount column, or
  one unsigned amount plus a `Cr`/`Dr` suffix.
- **Preamble junk:** 6–15 rows of account holder name, address, account number and
  statement period before the real header row.
- **Trailing junk:** opening/closing balance rows, page totals, legal disclaimers.
- **Date formats:** `14/08/26`, `14/08/2026`, `14-Aug-2026`, `2026-08-14` — and the
  genuinely dangerous ambiguity between `DD/MM/YYYY` and `MM/DD/YYYY`.
- **Number formats:** `1,234.56`, `1 234.56`, `(1234.56)` for negatives, `1,234.56 Cr`.

Also relevant: `pandas` is listed in the spec's suggested stack. It is a heavyweight
dependency (~50 MB with NumPy) whose actual use here would be reading a file and iterating
rows.

## Decision

**A single generic tabular engine driven entirely by declarative `BankProfile` records in
`bank_profiles.yaml`, with an interactive one-time mapping flow for unknown layouts.
`openpyxl` for Excel and the stdlib `csv` module for CSV — no `pandas`.**

### Profile resolution (FR4)

1. **Sniff the layout** (§11.2): detect encoding (`utf-8-sig` → `cp1252` → `latin-1`),
   delimiter (`csv.Sniffer` over the first 8 KB with a `,;\t|` fallback set), and header row
   — the first row where ≥ 3 cells are non-empty, non-numeric strings **and** the following
   row parses as data.
2. **Score every profile** by normalized overlap between the file's headers and
   `match.headers_any` (lowercased, whitespace- and punctuation-stripped), with a bonus for
   `match.filename_contains` hits against the filename.
3. **Accept the best profile above 0.8**, else fall through to (4).
4. **Interactive mapping** (§11.2): show the detected headers and the first three data rows,
   ask which column is date / description / debit / credit / amount / balance, ask the date
   order, then write the answer into `bank_profiles.yaml` as a new named profile. Asked
   once per bank, ever.
5. **No TTY** ⇒ `PARSE-204`, file excluded with an actionable reason, run continues (FR5).

### Why `pandas` is dropped

The spec suggests it, but the actual workload is: read rows, map column names, coerce
strings to `Decimal`/`date`, filter junk rows. All of that is stdlib work.

- `pandas` reads currency columns into **float64** by default — directly against
  [ADR-0008](ADR-0008-money-and-currency.md)'s exactness requirement. Keeping them as
  `object`/`str` to preserve precision means not using the part of `pandas` that is
  valuable.
- It adds ~50 MB to a distribution NFR5/ADR-0010 want to keep small and installable by a
  non-programmer.
- Its silent type coercion (`NaN` for empty cells, dtype inference per column) is precisely
  the class of "quietly wrong" behaviour that NFR3/NFR6 are trying to eliminate. A cell that
  fails to parse should produce a `ROW-302` diagnostic pointing at file and row, not a
  `NaN` that flows into a total.

`openpyxl` is kept, because reading `.xlsx` from scratch is not sensible.
**`.xls` (legacy BIFF)** needs `xlrd>=2.0` in its Excel-only mode, or conversion; if that
proves fragile, `.xls` degrades to `PARSE-206` with a "re-save as .xlsx or .csv" hint —
a documented partial degradation of FR1 tracked in [06](../06-risks-and-open-questions.md).

### Date-order is declared, never inferred

`date_order` (`DMY`/`MDY`/`YMD`) is a **required** profile field. `03/04/2026` is a real,
undetectable ambiguity that silently misfiles a transaction into the wrong month and
corrupts FR12 totals and FR14 comparison. Guessing from a sample is unreliable (a file
where every day ≤ 12 gives no signal). If a date fails all of the profile's declared
`date_formats`, the row is dropped with `ROW-301` — visible, not guessed.

## Alternatives considered

### A. `pandas` as the spec suggests — **rejected**; see above. The decisive point is float coercion of money columns.

### B. Hardcoded per-bank parser classes — **rejected**

Direct violation of FR22 and §11.1's "without code changes". Every new bank would be a code
change, a test, and a release. The whole long-tail risk of this product is bank diversity;
making that a code path guarantees the tool decays.

### C. Fully automatic column detection by heuristics (no profiles) — **rejected**

Tempting — match `date`-ish headers by regex, pick the numeric columns as amounts. It works
often enough to be dangerous: it will confidently map `Balance` as the amount column on some
banks, producing a plausible-looking report that is entirely wrong. FR4 explicitly asks for
profiles, and a one-time question beats a silent misread. Heuristics *are* used, but only to
**pre-fill** the interactive prompt — the user confirms.

### D. Machine-learned column classification — **rejected**

No training data, non-deterministic, unexplainable, and solves a problem a 10-line YAML
block solves exactly.

## Consequences

**Positive**

- A new bank costs one YAML block, or one interactive session, and zero code (FR22, NFR5).
- Money never passes through a float (ADR-0008).
- Small dependency footprint helps ADR-0010's packaging and NFR4's portability.
- Every parse failure names a file, a row and a reason (NFR3, NFR6).
- Profiles are inspectable and diffable — the user can see and fix why a bank was misread.

**Negative / accepted costs**

- **We deviate from the spec's suggested stack** by dropping `pandas`. Justified above
  against NFR3/NFR6 (silent coercion) and ADR-0008 (exact money), not against taste. Spec §9
  explicitly permits this.
- The first encounter with a new bank requires one interactive session, so the very first
  run on an unknown bank is not fully unattended. Mitigated: it happens once per bank, and
  the shipped profiles (ADR-0011) cover the common cases day one.
- We write our own header/junk-row detection instead of inheriting `pandas`'s. This is
  ~150 lines and is exactly where the tool needs to be opinionated anyway.
- `.xls` support carries residual risk; degradation path is documented.
