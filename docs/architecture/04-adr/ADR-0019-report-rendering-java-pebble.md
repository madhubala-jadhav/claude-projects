# ADR-0019 — Report rendering in Java: Pebble templating, Chart.js unchanged

**Status:** Accepted
**Date:** 2026-08-28
**Deciders:** Project owner, following [ADR-0014](ADR-0014-language-and-runtime-java-override.md)'s Java decision
**Supersedes:** [ADR-0007](ADR-0007-report-and-chart-rendering.md) (template engine only — the chart library, the offline-assertion mechanism, the pie-chart binding rules, and every alternative it weighed are kept, see below)

---

## Context

[ADR-0014](ADR-0014-language-and-runtime-java-override.md) commits the implementation to
Java 17, leaving [ADR-0007](ADR-0007-report-and-chart-rendering.md)'s template engine —
Jinja2 — without a runtime. This ADR re-picks the template engine only. **Every part of
ADR-0007 that is not the template engine still holds**, because none of it lives
server-side or is Python-specific:

- **Chart.js v4 (UMD build), vendored and inlined** — a client-side JS asset, unaffected by
  the server language. Still ~200 KB, still delivers FR17's per-slice `offset` and FR19's
  `onClick` hit-testing exactly as before.
- **Hand-written CSS with design tokens as custom properties** — unchanged; still no
  framework, no build step.
- **System font stack only** — unchanged.
- **Data as a JSON island** (`<script type="application/json" id="expense-data">`) —
  unchanged in shape; only the serializer producing it changes (Jackson instead of `json.dumps`,
  using the custom `BigDecimal`-as-string codec from
  [ADR-0018](ADR-0018-money-java-bigdecimal.md) so amounts stay `"487.00"` inside the island
  too, not just in `summary.json`).
- **The offline assertion** — at write time, asserting the rendered string contains no
  `src="http`, `href="http`, `@import url(http`, or `fetch(` — is a plain string/regex check
  over the final HTML, trivially portable to Java with no design change.
- **All seven pie-chart binding rules** (§"The pie chart, specifically") — colour bound to
  category not rank, slot-ordered slices, geometry-only emphasis, the 8-slice + "Other" cap,
  direct labels ≥ 3%, EC6's zero-spend exclusion, the 2 px slice gap — none of these reference
  the server language and none are revisited here.
- **Alternatives A–F** (Plotly, server-side static SVG, hand-rolled SVG, web fonts, a CSS
  framework, a React/Vue app) were all rejected on size, offline, or maintenance grounds that
  do not depend on which language renders the template, so none are re-litigated.

The only open question is: what renders the `.html` template into the final string, now that
Jinja2 isn't available.

## Decision

**Pebble (`io.pebbletemplates:pebble`) as the template engine. Everything else from
ADR-0007 is unchanged.**

### Why Pebble over the other Java template engines

ADR-0007 picked Jinja2 for three named reasons: *"mature, autoescaping on, testable."* Pebble
is the Java engine that matches all three most closely, because it was deliberately designed
as a Java port of the Twig/Jinja template-language family:

- **Syntax is a near-1:1 match to Jinja2** — `{{ variable }}`, `{% for %}`/`{% if %}` block
  tags, filters (`{{ amount | currency }}`), template inheritance (`{% extends %}`,
  `{% block %}`). This matters concretely: [`design/screens/`](../../../design/screens/)'s
  screen specs and the DOM structure in
  [02-data-model.md](../02-data-model.md) §3.9 were written against a Jinja2-shaped mental
  model, and Pebble's syntax carries that over with the smallest translation gap of any Java
  option.
- **Autoescaping is on by default for HTML output**, matching Jinja2's default exactly — no
  extra configuration needed to get the same safety posture ADR-0007 chose.
- **Mature and actively maintained**, with a small, focused dependency footprint (no
  transitive web-framework baggage), which fits a tool whose whole point is a single
  self-contained artifact.
- **Standalone by design** — Pebble has no assumption of a servlet container or a web
  framework around it, unlike Thymeleaf (below), so wiring it into a plain CLI/report-writer
  component needs no extra scaffolding.

### Composition (updated from ADR-0007)

| Layer | Choice | Why |
|---|---|---|
| Template | **Pebble** | Closest Java match to Jinja2's syntax, autoescaping default, and standalone use — see above |
| Chart | **Chart.js v4 UMD**, vendored under the report's assets, unchanged | Same as ADR-0007 |
| Styling | Hand-written CSS with design tokens as custom properties, inlined, unchanged | Same as ADR-0007 |
| Fonts | System stack only, unchanged | Same as ADR-0007 |
| Data | `<script type="application/json" id="expense-data">`, produced by Jackson with the ADR-0018 `BigDecimal` codec | Same shape as ADR-0007; different serializer |
| Behaviour | ~200 lines of vanilla JS, inlined, unchanged | Same as ADR-0007 |

## Alternatives considered

### A. Thymeleaf — rejected

The most widely adopted Java template engine, and a reasonable second choice. Rejected in
favor of Pebble for three concrete reasons:

- **"Natural templating"** — Thymeleaf's signature feature, where `.html` template files
  remain valid, previewable HTML via `th:*` attributes rather than `{{ }}` delimiters — is a
  real strength for a designer opening the file directly, but it makes the syntax
  attribute-based and noticeably more verbose than Jinja2's, widening the translation gap
  from the design package's Jinja2-shaped screen specs.
- **Heavier dependency footprint** and a setup model (`TemplateEngine` +
  `ClassLoaderTemplateResolver`, dialect configuration) built around Spring MVC integration,
  even though it works standalone — more configuration surface for a tool that has no web
  framework anywhere else in its stack.
- Not a rejection on correctness or maturity — Thymeleaf is fully capable of everything this
  report needs. If Pebble's smaller community ever becomes a real maintenance concern,
  Thymeleaf is the documented fallback.

### B. Apache FreeMarker — rejected

Mature and Apache-licensed (consistent with the rest of the dependency stack), but two
specific mismatches with ADR-0007's original criteria:

- **Autoescaping is off by default** in classic FreeMarker configurations and requires
  explicit enabling (auto-escaping models, or FreeMarker's newer "safe" configuration mode)
  to match the safety posture Jinja2 provided out of the box. Getting this wrong silently
  reopens an XSS-shaped risk in a report that echoes user-editable data (merchant
  descriptions, category names) into HTML.
- Syntax (`${...}`, `<#if>`, `<#list>`) is a larger departure from the Jinja2-shaped mental
  model the design package was written against than Pebble's is.

### C. JMustache / Handlebars.java (logic-less templates) — rejected

Mustache-family engines escape by default, which is a point in their favor, but "logic-less"
is a real constraint against a report with FR14's month-over-month deltas, FR18's per-row
category table, and the corrections tray's conditional states — all of it would move into
Java-side pre-formatting rather than the template, which is more code to own for no offline
or safety benefit over Pebble.

### D. String concatenation / `String.format` with no template engine — rejected

Zero dependencies, but re-implements escaping, conditionals and loops by hand for a
non-trivial multi-section report (stat tiles, chart, table, drill-down panel, corrections
tray, MoM panel) — exactly the class of hand-rolled risk ADR-0007's alternatives C and F
already rejected for the chart specifically. Not worth it for the template layer either.

## Consequences

**Positive**

- The template engine change is isolated to how the `.html` string gets produced; every
  visual, structural and accessibility decision in ADR-0007 and the design package carries
  over with zero redesign.
- Pebble's Jinja2-shaped syntax keeps the design package's screen specs directly usable as
  authoring references for the actual `.peb` templates, minimizing translation risk.
- Autoescaping-on-by-default preserves ADR-0007's safety posture without extra configuration.

**Negative / accepted costs**
- Pebble has a smaller community and ecosystem than Thymeleaf; if that becomes a real
  maintenance concern, Thymeleaf is the documented, fully-capable fallback (Alternative A).
- The JSON-island serializer must reuse ADR-0018's custom `BigDecimal` codec explicitly —
  a naive `ObjectMapper.writeValueAsString()` on a raw `BigDecimal` field would reintroduce
  the "string, not number" contract violation ADR-0018 §2 already flagged for `summary.json`.
  This is a one-line reuse, not new design, but is easy to forget at a second call site.
- Pebble template compilation errors surface differently (Java stack traces through
  Pebble's own exception types) than Jinja2's `TemplateSyntaxError`; any developer-facing
  tooling around template errors needs its own (small) adjustment, not a design change.

## Verification

- A fixture `MonthlySummary` renders through the Pebble template to HTML containing the same
  DOM structure and section order [02-data-model.md](../02-data-model.md) §3.9 specifies,
  independent of which engine produced it.
- The offline assertion (no `http` src/href, no `@import url(http`, no `fetch(`) passes on
  the Pebble-rendered output, ported directly from ADR-0007's check.
- The embedded JSON island's amount fields deserialize back to the exact `BigDecimal` values
  they were rendered from — the same round-trip guarantee ADR-0018 requires for
  `summary.json`, verified here for the report's inline data island too.
- `aria-label` on the chart canvas and the FR18 category table both render exactly as
  [design-system.md](../../../design/design-system/design-system.md) specifies, confirming
  the accessibility guarantees ADR-0007 §Consequences named survive the engine swap.
