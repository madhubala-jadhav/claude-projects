# ADR-0009 — History as dated files, not a database

**Status:** Accepted
**Date:** 2026-08-26

---

## Context

- **NFR7:** each month's report and CSV must be kept as a **separate, dated artifact (not
  overwritten)**, so historical months remain available for comparison.
- **FR14:** if prior months' reports exist, show month-over-month change per category.
- **AC6:** running on a second month must show MoM % change per category versus the first.
- **NG2:** single user, single machine — no sync, no concurrency.

FR14 needs *structured* prior-month data. Re-parsing last month's `report.html` to recover
numbers would be absurd, so there must be a machine-readable per-month record. The question
is what holds it.

## Decision

**One directory per month, `output/<YYYY-MM>/`, containing four files, with `summary.json`
as the sole structured input to the next month's comparison. No database.**

```
output/
├─ 2026-07/
│  ├─ report.html          human artifact (NFR7)
│  ├─ transactions.csv     data artifact (FR21, AC9, NFR7)
│  ├─ summary.json         <- machine-readable; the FR14 input
│  └─ run-log.txt          diagnostics
└─ 2026-08/
   └─ (same four)
```

**Rules:**

1. **Directory name is the contract.** `HistoryStore` discovers months by globbing
   `output/[0-9][0-9][0-9][0-9]-[0-9][0-9]/`. The folder name is the index — no separate
   catalogue file to drift out of sync with reality.
2. **`previous_month()` returns the most recent stored month strictly before the target**,
   not necessarily `month - 1`. A user who skips September still gets a meaningful October
   comparison (against August), with `previous_month` recorded in the summary so the report
   can say *which* month it compared against rather than implying "last month".
3. **Months are never overwritten by a different month.** Re-running the *same* month
   replaces that month's files, which is intended (you fixed a category, you want a fresh
   report). With `report.keep_reruns: true` the prior render is preserved as
   `report.<timestamp>.html`.
4. **`summary.json` carries `schema_version`.** A reader must tolerate unknown fields,
   tolerate missing optional fields, and **refuse** a version greater than its own —
   degrading FR14 to `WARN-503` "history unavailable" rather than guessing at a future
   shape.
5. **Writes are atomic.** Render to `.tmp`, fsync, `os.replace`. A crash mid-write must never
   leave a truncated `summary.json` that poisons next month's comparison.
6. **Deletion is the user's business.** The tool never prunes. NFR7 says keep; a tool that
   silently deletes financial history would be a serious breach of trust. Housekeeping is a
   documented manual action.

## Alternatives considered

### A. SQLite — **rejected**

*Attraction:* one file, ACID, real queries, trivial cross-month aggregation, and it is in
the Python standard library so it costs no dependency. It is the strongest alternative here.

*Why rejected:*
- **NFR7 asks for artifacts, not records.** "Keep each month's report and CSV as a separate,
  dated artifact" describes files on disk that a human can open, copy, back up and email. A
  database would sit *alongside* those files as a second source of truth, and the two would
  eventually disagree.
- **v1 needs exactly one query:** "the most recent month before X." That is a directory
  listing. Adopting a schema, a migration story and a query layer to answer it is
  disproportionate.
- **It fails the archive test.** A `summary.json` from 2026 is readable in 2036 with any
  text editor. A SQLite file needs the right library and a schema you have to remember.
- **Backup and inspection get worse.** Copy a folder vs. worry about a locked/partial DB file.
- NG2 removes the concurrency argument that usually justifies SQLite.

**Revisit if** a cross-month trend view (spec §15 "future enhancements") is built. At that
point SQLite becomes a natural *derived cache* rebuilt from the `summary.json` files, which
remain the source of truth — the best of both, and additive rather than migratory.

### B. A single append-only `history.jsonl` at the output root — **rejected**

Compact and easy to scan. But it separates a month's numbers from that month's artifacts,
making a month no longer a self-contained folder, and it becomes a single point of
corruption for all history — the opposite of what NFR7 wants.

### C. Re-parse prior `transactions.csv` files to compute MoM — **rejected**

Avoids a second format, and is tempting because the CSV already exists. But it would
recompute totals with *today's* categorization rules, so last month's numbers would silently
change whenever the user edits `categories.yaml` or adds a correction — and the report would
show a month-over-month delta that reflects a rule change rather than a spending change.
FR14 must compare what was actually reported. `summary.json` freezes the past.

### D. Overwrite a single `latest/` output folder — **rejected**

Directly violates NFR7 and destroys FR14's input.

### E. Store history inside `config/` alongside corrections — **rejected**

Conflates user-authored configuration with tool-generated output, making backup, `.gitignore`
and "can I safely delete this?" all ambiguous.

## Consequences

**Positive**

- NFR7 satisfied literally: dated, separate, never overwritten.
- FR14/AC6 need one directory listing plus one JSON read.
- History is inspectable, diffable, greppable and backup-friendly.
- Zero dependencies, zero schema migrations, zero lock contention.
- Past reports are frozen: a rule change today cannot retroactively alter what August said.
- A month folder is self-contained — copy it anywhere and it still means something.

**Negative / accepted costs**

- **No cross-month querying** without reading N files. Irrelevant at 12–120 files; the
  future trend view would add a derived cache.
- **Storage grows unbounded** — roughly 0.6–1 MB per month, so ~10 MB/year. Acceptable, and
  the user prunes if they choose.
- **A month is identified by its folder name**, so a user renaming folders breaks history.
  Mitigated by `summary.json.month` being authoritative on read, with a mismatch warning.
- Two representations of the same month (HTML for humans, JSON for the tool) must stay
  consistent. Guaranteed by both being rendered from the same `MonthlySummary` in one call.
