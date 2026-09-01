# ADR-0002 — Execution model: a local batch CLI, not a local server

**Status:** Accepted
**Date:** 2026-08-26

---

## Context

The spec wants two things that pull against each other:

- **A rich, interactive surface.** FR19 (hover/click a pie slice to drill down) and FR20
  (change a transaction's category from inside the report) describe app-like behaviour.
- **Absolute locality.** NFR1 and AC8 forbid statement data reaching any network service;
  §13 forbids uploading, syncing or phoning home; NG2 rules out multi-user.

The obvious way to get FR19/FR20 is a small local web app — Flask or FastAPI on
`127.0.0.1`, serving a page that can POST changes back. Meanwhile FR16 asks for a
"self-contained local HTML report" that "opens automatically", FR23 asks for a
single-command/double-click run, and NFR5 targets a non-programmer.

We must pick an execution model before designing the report.

## Decision

**A batch CLI that renders a static, self-contained HTML document and opens it with the
OS's default browser. No server, no daemon, no listening socket — ever.**

- One process, started by the user, that runs the pipeline and exits.
- `report.html` is a complete document: markup, CSS, a vendored chart library, and the
  month's data as an inline JSON island. It works from `file://`, offline, forever.
- FR19's drill-down is client-side JavaScript over the inlined data — no round trip needed.
- FR20's category edits are staged in the page and exported as a **downloaded JSON patch**
  that the next run harvests (ADR-0006).

## Alternatives considered

### A. Local web server on `127.0.0.1` (Flask/FastAPI) — **rejected**

*Attraction:* FR20 becomes a trivial `POST /corrections`; the loop closes instantly with no
patch-file dance, and AC4 is easy.

*Why rejected:*

- **It puts a listening socket in a product whose headline promise is "nothing leaves this
  machine".** Even bound to loopback, it is reachable by every other process and every
  browser tab on the machine; it needs CSRF protection, origin checks and a port-collision
  story. AC8 says "verifiable via no outbound network calls" — a design with *no network
  code at all* is verifiable by a one-line static check. A design with a server is
  verifiable only by reading it carefully, forever.
- **It breaks NFR7's artifact model.** A served report is alive only while the process
  runs. The spec wants each month kept as a durable artifact; a saved-out server page is a
  second rendering path to maintain.
- **It is worse for a non-programmer (NFR5).** "Open your browser and go to
  http://127.0.0.1:8731" is a strictly harder instruction than a file that opens itself,
  and it fails in confusing ways behind corporate proxies, VPN split-tunnelling, and
  host-file overrides.
- **It contradicts FR23's shape.** A server run does not *finish*; the user has to know to
  stop it.

### B. Desktop GUI (Tkinter / PyQt / Electron) — **rejected**

Solves FR20 natively and needs no browser. But it means building and maintaining a real UI
toolkit surface for what is fundamentally a monthly document; it contradicts FR16's explicit
"HTML report... opens automatically in the user's default browser"; PyQt/Electron add
enormous packaging weight (against ADR-0010 and NFR4); and Tkinter cannot render the
chart quality FR17/G4 deserve.

### C. Jupyter notebook — **rejected**

Excellent for exploration, and it would give live editing. But it fails NFR5 outright — the
persona is explicitly "not necessarily a programmer" — and a notebook is not a durable,
shareable artifact in the sense NFR7 wants.

### D. Static HTML + a tiny "companion" watcher process — **rejected**

A background process watching the Downloads folder for correction patches would make AC4
feel instant. But a resident daemon on the user's machine is a large trust and lifecycle
cost (autostart, updates, "why is this running?") for a workflow that happens twelve times
a year.

## Consequences

**Positive**

- **NFR1/AC8 become structural.** There is no network client in the codebase, so the
  privacy claim is provable by a static import check rather than by review
  ([03](../03-interfaces-and-contracts.md) F10).
- FR16 is satisfied literally: one file, opens itself, self-contained.
- NFR7 falls out for free — the artifact *is* the app.
- Reports are portable: archivable, emailable, openable in five years with no runtime.
- FR19 drill-down is instant (data is already in the page) and works with the browser
  disconnected from any network.
- Simplest possible failure mode: if the browser will not open, print the path.

**Negative / accepted costs**

- **FR20 cannot write to disk from `file://`.** This is the one real cost, and it forces
  the patch-file round trip in ADR-0006 — an extra manual step for the user, mitigated by
  the report's own instructions and by making the drop folder the same `input/` folder they
  already use.
- The report carries its data inline, so file size grows with transaction count. At the
  NFR2 ceiling of ~500 transactions this is roughly 250–350 KB of JSON plus a ~200 KB
  vendored chart library — comfortably fine. A future 10,000-transaction "all history" view
  would need reconsidering.
- No cross-month interactive querying inside a single report; each report is one month
  (FR14 comparison is precomputed at render time).

**Revisit if:** the correction round trip proves too clumsy in real monthly use. The
smallest escalation is an *opt-in*, explicitly-off-by-default `--serve` mode on loopback —
which would need its own ADR and its own NFR1 argument.
