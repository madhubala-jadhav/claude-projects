# ADR-0011 — Day-one bank format support (resolves OQ1)

**Status:** Accepted — **architect's assumption, pending owner confirmation**
**Date:** 2026-08-26

---

## Context

Spec §16, open question 1:

> Which specific banks' statement formats need day-one support (column headers/layouts
> differ significantly by bank)?

This is the highest-leverage unanswered question in the spec, because bank-format diversity
is the tool's dominant long-tail cost (risk R1). The spec gives three strong hints:

- §11.1 names *"common Indian bank statement table layouts (Date, Narration/Description,
  Withdrawal, Deposit, Balance columns)"* as **the default template**.
- §10.1's own example uses `"currency": "INR"`, `HDFC-XXXX1234`, and `SWIGGY*ORDER 4471
  BANGALORE IN`.
- §10.3's example keywords are `swiggy`, `zomato`, `bigbasket`, `dmart`, `blinkit`, `ola`,
  `irctc` — all Indian.

The owner is a single named individual (§5, spec header). The realistic day-one requirement
is *"the banks Madhubala actually has accounts with"* — which the spec does not name, and I
should not pretend to know.

## Decision

**Do not enumerate a bank list. Ship a generic engine plus a small set of seed profiles, and
make the unknown-bank path a first-class, 60-second experience rather than a defect.**

Three parts:

### 1. The generic default is the contract (§11.1)

`generic_pdf_indian_savings` and `generic_tabular` profiles implement the
`Date | Narration | Withdrawal | Deposit | Balance` shape the spec names. Any bank whose
export resembles it works with no configuration at all. This — not a bank list — is what
§11.1 actually asked for.

### 2. Seed profiles for the five largest Indian retail banks

Shipped in `bank_profiles.yaml`, chosen purely by retail account share so the odds that a
given Indian user's first run "just works" are as high as possible:

| Profile | Bank | Formats |
|---|---|---|
| `hdfc_savings_csv` | HDFC Bank | CSV/XLSX |
| `icici_savings_csv` | ICICI Bank | CSV/XLSX |
| `sbi_savings_csv` | State Bank of India | CSV/XLSX |
| `axis_savings_csv` | Axis Bank | CSV/XLSX |
| `kotak_savings_csv` | Kotak Mahindra | CSV/XLSX |
| `generic_pdf_indian_savings` | any | PDF |
| `generic_tabular` | any | CSV/XLSX |

These are **seeds, not guarantees**. They are written from the documented export column
names; each must be validated against a real export before slice 3 exits, and any that
cannot be validated is shipped commented-out rather than shipped wrong. A profile that
*looks* right and silently maps `Balance` as the amount is far worse than no profile at all.

### 3. The unknown-bank path is a designed feature, not an error

Per §11.2 and [ADR-0004](ADR-0004-tabular-parsing-and-bank-profiles.md): detect the header
row, show the user the headers plus three real data rows, pre-fill the mapping with
heuristics, ask them to confirm six fields and the date order, then **write the answer into
`bank_profiles.yaml` as a named profile**. Asked once per bank, ever. This is the actual
answer to OQ1 — the tool supports *every* bank, at the cost of one guided minute the first
time.

### Follow-on defaults adopted with it

- **Default currency `INR`, locale `en-IN`** (lakh/crore digit grouping in the report), both
  overridable in `config.yaml`.
- **Default `date_order: DMY`** for the generic profiles, matching Indian convention. Never
  inferred per-file — see ADR-0004.
- **Seed `categories.yaml` keywords are the spec's own** (§10.3) plus obvious siblings
  (Zepto, Rapido, BESCOM, Myntra, 1mg…). Localisation is a YAML edit (FR22).

## Alternatives considered

### A. Pick 3–5 banks and hardcode parsers for them — **rejected**

Contradicts FR22 and §11.1's "without code changes", and guarantees the tool is useless to
anyone outside the chosen list until a release ships. It also concentrates effort on the
wrong thing: the generic engine plus the mapping flow serves 100% of banks; a hand-written
parser serves one.

### B. Block on the owner's answer before building — **rejected**

The architecture does not change based on the answer. Whichever banks are named become
`bank_profiles.yaml` entries — data, produced during slice 3 from a real sample export.
Stalling the whole design on a data question would be a poor trade.

### C. Ship no seed profiles at all, rely entirely on the interactive flow — **rejected**

Architecturally purest, and tempting. But the very first run is where trust is won or lost;
being asked to map columns before seeing a single result is a worse first impression than
one that works immediately. The seeds are cheap insurance.

### D. Auto-detect columns with no confirmation — **rejected** in
[ADR-0004](ADR-0004-tabular-parsing-and-bank-profiles.md): confidently wrong beats visibly
unknown, and there is no way for the user to notice.

### E. Support "all Indian banks" via an account-aggregator/statement-parsing service — **rejected outright**: sends statement contents off the machine (NFR1, AC8, §13).

## Consequences

**Positive**

- No bank is architecturally unsupported; the answer to OQ1 stops being a blocker.
- Most Indian users get a zero-config first run; everyone else pays one minute, once.
- Adding a bank is data, forever (FR22).
- Profiles are inspectable and fixable by the user when a bank changes its export format —
  which they periodically do.

**Negative / accepted costs**

- **The seed profiles are unverified until a real export is tested.** Explicitly tracked as
  slice-3 exit criteria; unverified profiles ship commented out.
- **PDF layouts vary more than CSV headers do**, so the generic PDF template will need
  per-bank `pdf_table` tuning more often than the CSV profiles need column tuning. This is
  risk R1 and the reason the template is config-driven.
- Non-Indian users face more first-run curation (keywords, date order, currency). Acceptable
  given the spec's clear India-centric framing, and it is all YAML.

## Confirmation requested

Recorded in [06-risks-and-open-questions.md](../06-risks-and-open-questions.md) as **A1**.
The single most useful thing the owner can provide is **one real statement export per bank
they actually use** (redacted). That converts the seed profiles from plausible to verified
and would retire risk R1 almost entirely.
