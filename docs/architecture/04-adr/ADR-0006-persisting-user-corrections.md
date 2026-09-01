# ADR-0006 — Persisting user corrections from a `file://` report

**Status:** Accepted
**Date:** 2026-08-26
**Depends on:** [ADR-0002](ADR-0002-execution-model-local-batch-cli.md) (no server)

---

## Context

This is the hardest constraint intersection in the spec.

- **FR20:** the report must provide an in-report control to change a transaction's
  category, *"satisfying US5 without editing files by hand."*
- **FR9:** that reassignment must be persisted by merchant/description pattern and applied
  automatically in future months.
- **AC4:** provably — reassign, then run again on a *new* statement from the same merchant,
  and the new transaction must land in the corrected category.
- **ADR-0002 / FR16 / NFR1:** the report is a static `file://` document with no server.

A page loaded from `file://` **cannot write to arbitrary local paths.** Every browser
forbids it. So the report can capture the user's intent, but it cannot itself deliver that
intent to `config/corrections.json`. Something has to carry it across.

## Decision

**A two-store model plus a browser-download patch hand-off through the folder the user
already uses.**

### The two stores

1. **`config/corrections.json`** — the durable, tool-owned store of learned overrides. The
   only file the tool mutates across runs; written atomically (temp file → fsync →
   `os.replace`) so a crash can never truncate accumulated user effort.
2. **`input/corrections-YYYY-MM.json`** — a transient, browser-produced **patch**: just the
   corrections made in that one report session ([02](../02-data-model.md) §3.6).

### The loop

```mermaid
sequenceDiagram
    actor U as User
    participant B as report.html (file://)
    participant D as Downloads / input folder
    participant T as Tool (next run)
    participant S as config/corrections.json

    U->>B: change a category on 3 rows (FR20)
    B->>B: stage in memory; sticky tray shows "3 unsaved corrections"
    U->>B: click "Save corrections"
    B->>D: Blob download -> corrections-2026-08.json (F5 shape)
    Note over B: the tray then shows the exact one-line instruction:<br/>"Move this file into <input path> — it applies on your next run"
    U->>D: move the file into input/
    U->>T: next monthly run
    T->>T: C3.harvest_correction_patches() finds it first, before parsing
    T->>S: validate whole patch, merge (last-write-wins, prior value -> history[])
    T->>D: move patch to archive/corrections/…applied-<timestamp>.json
    T->>T: C6 applies at ladder layer L1/L2 (ADR-0005)
    Note over T,S: AC4 satisfied - and satisfied for the CURRENT run too
```

### Design details that make it tolerable

- **Harvest happens first**, before file discovery and parsing, so corrections apply to the
  run that harvests them, not the one after. If the user drops the patch and re-runs the
  same month, they see the fix immediately.
- **The drop target is `input/`** — the same folder the workflow already trains them to use
  every month. No new concept, no new path to remember.
- **The report states the literal destination path**, injected at render time, so there is
  nothing to look up.
- **The patch is validated as a unit.** Unknown `kind`, a category that does not exist, or
  an empty `merchant_key` rejects the entire patch with `WARN-504` and leaves the file in
  place. A half-applied patch is worse than none.
- **Applied patches are moved, not deleted**, giving idempotency (never applied twice) plus
  an audit trail.
- **Prior values move to `history[]`** rather than being overwritten, so a mistaken
  correction is recoverable.
- **A manual escape hatch exists.** `corrections.json` is human-readable JSON and can be
  edited directly — FR20 removes the *need* to hand-edit, it does not forbid it.
- **The tray is loud.** Unsaved corrections show a persistent count and block nothing, but
  the browser's `beforeunload` warns if the user closes with unsaved changes — the one
  place where an easy mistake would silently discard their work.

## Alternatives considered

### A. Local HTTP server so the page can `POST` — **rejected** in [ADR-0002](ADR-0002-execution-model-local-batch-cli.md)

It is the ergonomically best answer and the architecturally worst one: it introduces a
listening socket into a product whose entire value proposition is that nothing leaves the
machine, and it makes AC8 a matter of careful reading rather than a one-line static check.

### B. File System Access API (`window.showSaveFilePicker`) — **rejected as primary**

Chromium browsers can let the page write directly to a user-chosen file, and could even
retain the handle. That would be excellent. But it is unavailable in Firefox and Safari
(NFR4/NFR5 want the *default* browser to work, whatever it is), and is restricted or absent
on `file://` origins in several versions. **Adopted as a progressive enhancement instead:**
when `showSaveFilePicker` exists, the Save button offers "write directly to
`config/corrections.json`" and the whole manual step disappears; otherwise it falls back to
the download. Same patch format either way, so nothing else in the system changes.

### C. Copy-to-clipboard JSON, paste into a file — **rejected**

No new capability over a download and strictly more steps and more ways to corrupt the
JSON.

### D. `localStorage` in the browser — **rejected**

`file://` origins share (or isolate) `localStorage` unpredictably across browsers, the data
is invisible to the tool, and it is silently wiped by routine browser cleaning. Storing the
user's accumulated corrections somewhere the tool cannot read and the browser may delete
fails FR9 outright.

### E. A separate "corrections" CLI (`run.bat --fix`) with a terminal prompt — **rejected**

Works and is fully offline, but directly contradicts FR20's "in-report control" and NFR5's
non-programmer persona. Kept as an optional power-user path, not the primary.

### F. Watch the Downloads folder automatically — **rejected**

Would remove the manual move. But it requires a resident process (rejected in ADR-0002) or
guessing the Downloads path, and silently ingesting files from a folder the user did not
designate is a surprising, slightly creepy behaviour in a privacy-first tool.

## Consequences

**Positive**

- FR9, FR20 and AC4 are satisfied with **zero network surface** — the privacy promise stays
  structural.
- Corrections are durable, versioned, human-readable, diffable and recoverable.
- The patch format is frozen ([03](../03-interfaces-and-contracts.md) F5), so a report
  archived in March still feeds corrections in September.
- Idempotent by construction (move-after-apply).
- On Chromium, the manual step vanishes entirely via the progressive enhancement.

**Negative / accepted costs**

- **A manual file-move step on Firefox and Safari.** This is the single largest usability
  cost in the whole design and is honestly acknowledged as risk R4 in
  [06](../06-risks-and-open-questions.md). Mitigations: the drop folder is one the user
  already uses; the exact path is printed in the report; the download filename is
  self-describing; and the next run confirms what it applied.
- Corrections do not take effect until a run happens — the report the user is looking at
  does not re-categorize live. The UI mitigates this by updating the displayed category,
  chart and totals **optimistically in-page**, so the user sees the intended end state
  immediately even though the durable write is deferred.
- Two representations of a correction (patch and store) to keep in sync. Bounded: the patch
  is a strict subset, and only C3 ever converts between them.
