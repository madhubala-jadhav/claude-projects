# ADR-0015 — CSV/Excel parsing in Java: Apache POI for Excel, Apache Commons CSV for CSV

**Status:** Accepted
**Date:** 2026-08-28
**Deciders:** Project owner, following [ADR-0014](ADR-0014-language-and-runtime-java-override.md)'s Java decision
**Supersedes:** [ADR-0004](ADR-0004-tabular-parsing-and-bank-profiles.md) (library choice only — the design it decided is kept, see below)

---

## Context

[ADR-0014](ADR-0014-language-and-runtime-java-override.md) commits the implementation to
Java 17, which leaves [ADR-0004](ADR-0004-tabular-parsing-and-bank-profiles.md)'s library
choices (`openpyxl` for Excel, stdlib `csv` for CSV, no `pandas`) without a runtime. This ADR
re-picks the libraries only. **Every part of ADR-0004 that is not a library name still
holds**: the declarative `BankProfile` / `bank_profiles.yaml` design, the sniff → score →
accept-above-0.8 → interactive-mapping resolution flow, the required (never inferred)
`date_order` field, and the reasons `pandas` was rejected (float coercion of money columns,
~50 MB footprint, silent type coercion fighting NFR3/NFR6). Those reasons apply just as much
to a Java equivalent, so the same category of library — a heavy, general-purpose "read
anything into a typed table" framework — is rejected here for the same reasons, not
re-litigated.

The owner has chosen **Apache POI** for the Excel side of this decision.

## Decision

**Apache POI (`org.apache.poi:poi-ooxml`) for `.xlsx`/`.xls`. Apache Commons CSV
(`org.apache.commons:commons-csv`) for `.csv`. No pandas-equivalent, no full "smart" table
framework — same reasoning as ADR-0004.**

### Excel — Apache POI

- **`.xlsx`** via POI's XSSF API (`XSSFWorkbook`), streaming (`SXSSFWorkbook`/event API not
  needed at this scale — NFR2's ≤ 5 files / ~500 transactions budget is small enough for the
  simple in-memory `XSSFWorkbook` reader).
- **`.xls`** (legacy BIFF) via POI's HSSF API (`HSSFWorkbook`) — **the same library**, just a
  different `Workbook` implementation class. This is a genuine improvement over the Python
  path: ADR-0004 flagged `.xls` as needing a separate, fragile `xlrd>=2.0` dependency and
  [05-traceability.md](../05-traceability.md) §8 records FR1's `.xls` support as
  **partial**, degrading to `PARSE-206`. Apache POI supports both formats natively through
  one dependency and one `WorkbookFactory.create(...)` call, so **`.xls` can be promoted from
  partial to full support** in the Java implementation — a follow-up to
  [05-traceability.md](../05-traceability.md) once this is verified against a real legacy
  `.xls` export.
- **Money-column precision (the ADR-0004/ADR-0008 concern, carried over):** POI's
  `Cell.getNumericCellValue()` returns a `double`, because XLSX itself stores numbers as
  IEEE754 doubles at the file-format level — this is not a library choice, it is how Excel
  works. Reading a money cell **must** go through `DataFormatter.formatCellValue(cell)` to
  get the exact displayed string (respecting the cell's number format), then
  `new BigDecimal(that string)` — never `BigDecimal.valueOf(cell.getNumericCellValue())`,
  which round-trips through the same double and can reintroduce the exact class of error
  ADR-0008 exists to prevent. This is the one place the Java port needs to be *more*
  careful than the Python original, since Python's `openpyxl` reading a cell as text sidesteps
  the issue naturally.

### CSV — Apache Commons CSV

- Chosen over hand-rolling (what the Python side did with the stdlib `csv` module, which
  needed no replacement because it's small) because Java has no CSV parser in its standard
  library, and Commons CSV is the same weight class as the stdlib module was: no schema
  inference, no type coercion, just correct quoting/escaping/delimiter handling over
  `String` rows. It plugs into the same encoding-sniff → delimiter-sniff → header-detection
  pipeline ADR-0004 already specified, unchanged.
- All cells arrive as `String`; the `BankProfile`-driven `parse_amount`-equivalent parses
  directly to `BigDecimal` from text, so CSV never has the double round-trip problem Excel
  has.

### Money type — `java.math.BigDecimal`

[ADR-0008](ADR-0008-money-and-currency.md) named Python's `decimal.Decimal` end-to-end, with
an explicit `ROUND_HALF_UP` context (chosen specifically because Python's own default,
`ROUND_HALF_EVEN`, doesn't match bank-statement/Indian financial rounding convention). The
Java successor is `java.math.BigDecimal` with `RoundingMode.HALF_UP` passed explicitly at
every division/quantization site — `BigDecimal` has no implicit default rounding mode the
way a `Decimal` context does, so every rounding site must state its mode explicitly, and it
must be `HALF_UP`, not Java's own `HALF_EVEN`-flavored defaults, to preserve ADR-0008's
rounding behavior. This is flagged here because it directly affects how ADR-0015's parsers
hand off values to the rest of the pipeline; the full language update is formalized in
[ADR-0018](ADR-0018-money-java-bigdecimal.md).

### Everything else from ADR-0004 is unchanged

Profile resolution (sniff → score → accept ≥ 0.8 → interactive mapping → `PARSE-204` on no
TTY), the declared `date_order` field and `ROW-301` on unparseable dates, and the
`bank_profiles.yaml` schema all carry over verbatim — none of it is Python-specific.

## Alternatives considered

### A. Hardcoded per-bank parser classes — rejected, same reasoning as ADR-0004 §B

Still a direct violation of FR22 and §11.1; unchanged by language.

### B. A Java "pandas-equivalent" (e.g. Tablesaw) — rejected

Same objection as ADR-0004 §A: general dataframe libraries infer types and silently coerce,
which is precisely the "quietly wrong" behavior NFR3/NFR6 exist to eliminate, and adds a
dependency for functionality (read rows, map columns, coerce to `BigDecimal`/`LocalDate`)
that Commons CSV + POI + hand-written mapping already covers.

### C. `jxls` or other Excel-templating libraries — rejected

These solve report *generation* from templates, not statement *reading*. Wrong tool.

### D. `xlsx4j` / other smaller XLSX-only readers — rejected

Apache POI is the de facto standard, actively maintained, and — uniquely among the
alternatives — handles legacy `.xls` in the same dependency, which is the deciding factor
given FR1's `.xls` gap in the Python path.

## Consequences

**Positive**

- `.xls` support can be upgraded from ADR-0004's documented partial degradation to full
  support, closing part of the [05-traceability.md](../05-traceability.md) §8 gap. This
  should be re-verified against a real legacy `.xls` export before claiming it in the
  traceability doc.
- One dependency (`poi-ooxml`, which pulls in `poi` for HSSF) covers both Excel formats.
- CSV parsing stays as lightweight as the original stdlib-`csv` approach; Commons CSV adds no
  schema inference to fight.
- The `BankProfile` design, the interactive mapping flow, and the whole resolution algorithm
  from ADR-0004 transfer with zero redesign — confirming ADR-0001/ADR-0014's shared point
  that the architecture is language-neutral.

**Negative / accepted costs**

- Apache POI is a heavier dependency than `openpyxl` (POI's transitive closure, including
  XML libraries, is tens of MB vs. `openpyxl`'s pure-Python footprint) — a cost for
  [ADR-0010](ADR-0010-packaging-and-distribution.md) (packaging) to weigh, tracked as a
  follow-up since ADR-0010 also needs a Java re-decision per ADR-0014.
- The `DataFormatter` / `BigDecimal`-from-string discipline for Excel cells is a new failure
  mode that didn't exist in the Python path (`openpyxl` never round-trips through a Java
  `double`-typed API) — must be enforced by review or a lint rule, the Java equivalent of
  ADR-0008's "no stray `float()` cast" discipline note.
- [ADR-0008](ADR-0008-money-and-currency.md)'s `decimal.Decimal` successor is now formalized
  in [ADR-0018](ADR-0018-money-java-bigdecimal.md) as `BigDecimal` +
  `RoundingMode.HALF_UP`.

## Verification

- A fixture `.xlsx` with a money cell formatted as `1,234.56` round-trips through
  `DataFormatter` → `BigDecimal` to exactly `1234.56`, not `1234.5599999999999`.
- A fixture legacy `.xls` (HSSF) parses through the same `BankProfile` path as `.xlsx`, with
  FR1's `.xls` status updated from partial to full once confirmed against a real export.
- Commons CSV output for a known fixture matches the row/column values the Python
  implementation's stdlib-`csv` path would have produced, for every existing bank profile
  fixture named in ADR-0011.
