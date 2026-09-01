# ADR-0005 — Categorization: a deterministic layered rule ladder, not a classifier

**Status:** Accepted
**Date:** 2026-08-26

---

## Context

FR7 requires rule-based matching against a user-editable keyword/merchant mapping file.
FR8 requires an explicit `Uncategorized` outcome *"rather than guessing silently"*. FR9
requires user reassignments to persist by merchant/description pattern and apply
automatically in future months. FR10 requires 12 default categories, user-extensible. FR11
requires internal transfers to be excluded or separately labelled. NG5 states that perfect
automatic accuracy is explicitly not promised, and manual correction is part of the intended
workflow. NFR6 requires accuracy gaps to be *visible*.

The tension: the rules, the `overrides:` map in `categories.yaml`, and the learned
`corrections.json` can all match the same transaction. Precedence must be defined, total,
and stable — otherwise AC4 ("reassigning a category persists and applies next time") cannot
be relied upon, and the user's trust in the tool collapses the first time a correction
appears not to stick.

## Decision

**A five-layer precedence ladder, first match wins, with the winning layer recorded on the
transaction.**

```
L1  corrections.json, scope="transaction", exact Transaction.id
      -> category_source = "user_override"
L2  corrections.json, scope="merchant", exact merchant_key
      -> category_source = "user_override"
L3  categories.yaml  overrides:  exact merchant_key match
      -> category_source = "rule"
L4  categories.yaml  category keywords (substring, case-insensitive),
    then category patterns (regex)
      -> category_source = "rule"
L5  no match
      -> category = "Uncategorized",
         category_source = "uncategorized",
         needs_review = True                      (FR8)
```

**Rationale for the ordering.** User intent beats author intent beats generic heuristic.
A correction the user made last month (L1/L2) must survive an edit to `categories.yaml`, or
FR9 and AC4 are false. Within user corrections, a transaction-scoped pin (L1) beats a
merchant-wide rule (L2), because it is the more specific statement.

**Determinism inside L4.** Two rules can both match `"AMAZON FRESH"` (`amazon` under
Shopping, `fresh` under Groceries). Resolution, in order:
1. **Longest matching keyword wins** — the more specific rule is the better evidence.
2. **Tie ⇒ the category's declared `order` in `categories.yaml` wins** — a stable,
   user-visible, user-editable tie-break.
3. Keywords are always tried before regex patterns, so the cheap and predictable mechanism
   dominates.

Never dictionary-iteration order, never file order, never random. Categorization must be a
pure function of `(transaction, categories, corrections)` — no clock, no randomness, no
input-order dependence — because AC4's test is literally "run it again and get the same
answer".

**`merchant_key` is the unit of learning (FR9, AC4).** Correcting `"POS SWIGGY*ORDER 4471
BANGALORE IN"` must also fix `"POS SWIGGY*ORDER 9982 BANGALORE IN"` next month. So the
correction is stored against a normalized key, not the raw string:
uppercase → strip payment-rail prefixes (`POS`, `UPI/`, `NEFT`, `IMPS`, `ACH`, `ATM WDL`,
`MPS/`) → strip trailing reference numbers, dates and location tails → collapse whitespace →
keep the first 3 significant tokens.

```
"POS SWIGGY*ORDER 4471 BANGALORE IN"   -> "SWIGGY"
"UPI/AMAZON PAY/9928311/PAYMENT"       -> "AMAZON PAY"
"NEFT DR-HDFC0001234-RENT TRANSFER-AUG"-> "RENT TRANSFER"
```

The derivation is a **frozen contract** ([03](../03-interfaces-and-contracts.md) F6) —
changing it orphans every saved correction, which is why each correction also stores an
`example_description` to permit re-derivation on migration.

**Transfers (FR11).** Transfer status is decided in C5 *before* categorization, using
account-pair matching, and can additionally be asserted by a category carrying
`is_transfer: true`. Transfers keep their category and remain visible in a dedicated panel,
but are excluded from `total_spend` and `total_income`. The spec says "exclude **or
separately label**"; doing both — excluded from the number, still shown — is the honest
reading, and prevents the "where did ₹18,000 go?" confusion that silent exclusion causes.

**Explainability.** `category_source` travels to the report and CSV, so every transaction
can answer "why am I here?" — a rule, your own correction, or nothing matched. This is what
makes NFR6 real rather than decorative.

## Alternatives considered

### A. A machine-learned classifier (Naive Bayes / logistic regression on descriptions) — **rejected**

*Attraction:* would improve raw accuracy over time and reduce the Uncategorized bucket.

*Why rejected:*
- **No training data on day one.** A cold-start classifier on a user's first statement is
  worse than keywords, and the first impression is the whole product.
- **It cannot satisfy FR8's "rather than guessing silently."** A classifier's natural output
  is a confident-looking guess. Suppressing low-confidence predictions into `Uncategorized`
  recreates the rule engine's behaviour with far more machinery.
- **It breaks AC4's guarantee.** A correction becomes one more training example that
  *influences* future predictions, not a rule that *determines* them. The user's mental
  model — "I told it Amazon is Groceries, so Amazon is Groceries" — would be violated,
  and that is the single most trust-destroying thing this tool could do.
- **It is unexplainable**, against NFR6 and NG5's framing of correction as a normal
  workflow step.
- Non-determinism makes the acceptance tests in [03](../03-interfaces-and-contracts.md) §7
  flaky.

**Not forever, though.** A defensible future addition is a *suggestion* layer: for
`Uncategorized` transactions only, propose a category in the UI as a one-click accept, which
then writes a normal L2 correction. Suggestion never becomes assignment. Out of scope for
v1.

### B. Fuzzy matching (Levenshtein / token-set ratio) on merchant names — **rejected for v1**

Would catch `"SWIGY"` vs `"SWIGGY"` OCR noise. But a similarity threshold is a magic number
that produces confident wrong answers just past its edge, and it makes categorization
order-dependent and hard to explain. Normalized keys plus regex `patterns:` cover the real
cases. Revisit if OCR-noise misses show up in practice.

### C. Flat single-list rules, no override/correction layering — **rejected**

Simplest possible model, but FR9 requires learned overrides to beat authored rules, which
*is* a layer. Making it implicit would leave the precedence undefined — the exact bug this
ADR exists to prevent.

### D. An online merchant-category API (e.g. a merchant database lookup) — **rejected outright**

Sending merchant names off the machine violates NFR1, AC8 and §13. Not available at any
accuracy.

## Consequences

**Positive**

- **AC4 is guaranteed, not probable.** A correction is a rule with the highest precedence;
  it cannot be outvoted.
- Fully explainable: every category traces to a named layer and a specific keyword.
- Deterministic ⇒ testable ⇒ the acceptance criteria are real tests.
- Zero training, zero model, zero network (NFR1).
- Users can read and edit `categories.yaml` and immediately predict the effect (FR22, NFR5).

**Negative / accepted costs**

- **Accuracy is bounded by the keyword list**, so the `Uncategorized` bucket will be
  non-trivial in month 1. This is explicitly acceptable — NG5 says so, and NFR6 requires it
  to be visible rather than hidden. The design turns it into a workflow (the Uncategorized
  Review screen) instead of a failure.
- The shipped keyword list is India/English-centric (ADR-0011); users elsewhere will do more
  first-month curation.
- `merchant_key` normalization is a frozen contract and therefore expensive to improve
  later. Mitigated by storing `example_description` for re-derivation.
- Rules cannot express context ("Uber at 9am on a weekday is Transport, at 11pm is
  Entertainment"). Out of scope, and probably always should be.
