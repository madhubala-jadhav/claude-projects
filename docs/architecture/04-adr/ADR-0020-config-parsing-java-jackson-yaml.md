# ADR-0020 — Config parsing in Java: Jackson YAML (`jackson-dataformat-yaml`)

**Status:** Accepted
**Date:** 2026-08-28
**Deciders:** Project owner, following [ADR-0014](ADR-0014-language-and-runtime-java-override.md)'s Java decision
**Related:** no prior ADR owns this decision — `PyYAML` was only ever named inline, in
[ADR-0010](ADR-0010-packaging-and-distribution.md)'s original Core dependency tier and in
spec.md §9's suggested stack, never given its own rationale. This ADR is the first formal
decision for config parsing, closing the gap [ADR-0017](ADR-0017-packaging-java-fat-jar.md)'s
Core tier table left open ("plus the templating and YAML-config libraries chosen by C8/C2's
own Java follow-up ADRs (not decided here)") — the templating half was resolved in
[ADR-0019](ADR-0019-report-rendering-java-pebble.md); this ADR resolves the YAML half.

---

## Context

FR22 requires every user setting to live in a single human-editable config, not hardcoded.
[02-data-model.md](../02-data-model.md) §3.1–§3.4 names and fully specifies the shape of the
four config files this produces, all YAML: `config.yaml` (paths, currency, dedupe, transfer
detection, OCR, report options, logging), `categories.yaml` (the FR10 category set with
`slot`, `keywords`, `patterns`, `is_transfer`, plus an `overrides` map), `bank_profiles.yaml`
(a list of per-bank column-mapping and `pdf_table` records), and `accounts.yaml` (FR11/OQ3's
declared-own-accounts registry). All four carry a `schema_version` field.

Two structural facts about these files matter for the library choice:

1. **They are read, and one of them is also written.** [ADR-0004](ADR-0004-tabular-parsing-and-bank-profiles.md)'s
   interactive column-mapping flow (§11.2) appends a new profile to `bank_profiles.yaml` on
   first encountering an unrecognized bank — this ADR needs both a deserializer and a
   serializer, not just a reader.
2. **One field is money-shaped.** `config.yaml`'s `transfers.amount_tolerance` is a currency
   amount (`0.00`), so it falls under [ADR-0018](ADR-0018-money-java-bigdecimal.md)'s "every
   monetary value is `BigDecimal`, never `double`" rule — this needs to bind correctly, not
   just parse into *some* numeric type.

## Decision

**Jackson's YAML data-format module (`com.fasterxml.jackson.dataformat:jackson-dataformat-yaml`,
built on SnakeYAML as its low-level parser/emitter) bound to plain Java records, using the
same `ObjectMapper`/Jackson-annotation model already adopted for JSON in
[ADR-0018](ADR-0018-money-java-bigdecimal.md) (`summary.json`) and
[ADR-0019](ADR-0019-report-rendering-java-pebble.md) (the report's JSON island).**

### Why one binding library for both JSON and YAML

`jackson-dataformat-yaml` reuses `ObjectMapper` with a `YAMLFactory` in place of the default
`JsonFactory` — the same `@JsonProperty`, `@JsonAlias`, and record-binding behavior used for
`summary.json` applies unchanged to `config.yaml`, `categories.yaml`, `bank_profiles.yaml`
and `accounts.yaml`. Concretely:

- **One mental model, one dependency family** instead of Jackson for JSON plus a separately
  configured SnakeYAML `Constructor`/`TypeDescription` setup for YAML. Fewer binding
  mechanisms to get subtly wrong across four config files with real structural depth (nested
  maps, lists of records, optional/nullable fields like `categories.yaml`'s `slot: null`).
- **`BigDecimal` fields bind correctly by default, without the custom codec
  [ADR-0018](ADR-0018-money-java-bigdecimal.md) §2 required for JSON.** That codec exists
  because the JSON *contract* requires amounts serialize as a quoted string
  (`"amount": "487.00"`), and Jackson's default number handling would otherwise round-trip
  through a `double`. YAML config fields have no such string-contract requirement —
  `amount_tolerance: 0.00` is a bare scalar — and Jackson's default deserialization of a
  numeric token into a `BigDecimal`-typed field reads the token's text directly via
  `getDecimalValue()`, never through a `double` intermediate, as long as the target field is
  declared `BigDecimal` (which it must be, per ADR-0018). No custom deserializer is needed
  here; declaring the field type correctly is sufficient.
- **Java `record` types map directly onto each config file's documented shape** — e.g.
  `record BankProfile(String name, List<String> appliesTo, Match match,
  Map<String, String> columns, String dateOrder, List<String> dateFormats, ...)` for §3.3's
  profile entries — giving compile-time structure that mirrors
  [02-data-model.md](../02-data-model.md)'s tables directly, with Jackson's built-in
  `-parameters`-based record support (Jackson 2.12+) needing no Lombok or manual builder
  code.
- **Consistent Apache 2.0 licensing** across `jackson-databind`, `jackson-dataformat-yaml`,
  Apache PDFBox, Apache POI and Apache Commons CSV.

### Writing `bank_profiles.yaml` (§11.2)

The same `ObjectMapper` (configured with `YAMLFactory`) serializes a new `BankProfile` record
and appends it to the `profiles` list, then rewrites the file. Consistent with the accepted
behavior of the original Python design (plain `PyYAML`, no comment-preserving round-trip was
ever promised there either — [ADR-0004](ADR-0004-tabular-parsing-and-bank-profiles.md) is
silent on comment preservation), this ADR makes no stronger promise: a rewrite is a
structurally faithful re-serialization of the full document, not a comment-preserving patch.
If comment preservation ever becomes a real user complaint, SnakeYAML's underlying node model
can be walked directly as an escape hatch — Jackson's YAML module does not block that option,
since it delegates to SnakeYAML underneath.

### `schema_version`

Every config file's `schema_version` field is read first, before full deserialization, using
a small preliminary parse (`YAMLMapper` reading just that field into a lightweight probe
type) so an unsupported version fails with a clear message before Jackson attempts to bind
the rest of a shape it doesn't recognize — mirroring the `summary.json` version-refusal
behavior [07-implementation-roadmap.md](../07-implementation-roadmap.md) Slice 6 already
specifies for `HistoryStore`.

## Alternatives considered

### A. SnakeYAML directly, without Jackson — rejected

SnakeYAML is what Jackson's YAML module uses internally, so this alternative asks whether to
skip the Jackson layer and bind YAML directly. SnakeYAML does support typed loading via its
own `Constructor` + `TypeDescription` API, but that is a second, differently-shaped binding
mechanism from the Jackson annotations already needed for `summary.json`/the JSON island —
two ways to say "map this field to that property" in one codebase, for no capability gain.
Rejected in favor of the single Jackson-based model.

### B. A different Java YAML library (`eo-yaml`, `yamlbeans`) — rejected

Neither has SnakeYAML's maturity or Jackson's ecosystem integration; no concrete advantage
identified over Jackson YAML for this project's needs (four moderately-nested config files,
one of which needs write-back).

### C. Switch config format away from YAML (e.g. TOML, HOCON) — rejected, out of scope

[02-data-model.md](../02-data-model.md) §3.1–§3.4 and the frozen file layout in
[03-interfaces-and-contracts.md](../03-interfaces-and-contracts.md) specify `.yaml` files by
name and extension; changing the format is a data-model/interface change, not a Java library
substitution, and is not what this ADR (or ADR-0014's mandate) is for. YAML's comment support
and low punctuation also remain the better fit for FR22's "human-editable" requirement than
either alternative.

### D. A hand-rolled YAML-subset parser — rejected

Reinventing block scalars, anchors, and quoting edge cases for zero benefit over a mature,
widely-used library.

## Consequences

**Positive**

- One binding library (Jackson) handles `summary.json`, the report's JSON data island, and
  all four YAML config files — a single annotation model and a single mental map of "how does
  a field become a Java type" across the whole codebase.
- `BigDecimal` config fields (`amount_tolerance`) bind safely by default, with no custom
  codec required — a simpler case than the JSON contract ADR-0018 had to solve.
- Java records give compile-time-checked config shapes that mirror
  [02-data-model.md](../02-data-model.md)'s documented tables directly.
- Apache-2.0-consistent licensing across the dependency stack.

**Negative / accepted costs**

- **No comment-preserving round-trip on write.** Re-serializing `bank_profiles.yaml` after
  the §11.2 interactive flow rewrites the whole file, not just the new entry — identical,
  unchanged accepted cost from the original Python/`PyYAML` design, not a regression
  introduced here.
- Jackson record support requires the `-parameters` compiler flag (or explicit
  `@JsonCreator`/`@JsonProperty` constructor annotations as a fallback) — a one-line `pom.xml`
  compiler-plugin setting, not a design cost, but worth calling out so it isn't missed.
- `schema_version` pre-parsing adds one small extra read pass per config file at startup;
  negligible at NFR2's scale (a handful of small YAML files, not the transaction data itself).

## Verification

- Each of the four config files parses via `YAMLMapper` into the record types matching
  [02-data-model.md](../02-data-model.md) §3.1–§3.4 exactly, including nullable fields
  (`slot: null`), lists of records (`bank_profiles.yaml`'s `profiles`), and nested maps
  (`categories.yaml`'s `overrides`).
- `config.yaml`'s `transfers.amount_tolerance` deserializes to a `BigDecimal` equal to
  `new BigDecimal("0.00")`, not a value that round-tripped through `double`.
- A fixture unknown-bank CSV triggers the §11.2 mapping flow; the resulting written
  `bank_profiles.yaml` re-parses successfully via the same `YAMLMapper`, and the new profile
  is selected unattended on the next run — the same acceptance check
  [07-implementation-roadmap.md](../07-implementation-roadmap.md) Slice 3 already specifies.
- A `config.yaml` with an unrecognized `schema_version` fails with a clear, actionable
  message before any downstream binding is attempted.
