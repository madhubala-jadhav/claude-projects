# ADR-0013 — Income vs internal transfer: pair-matching over a declared account registry (resolves OQ3)

**Status:** Accepted — **architect's assumption, pending owner confirmation**
**Date:** 2026-08-26

---

## Context

Spec §16, open question 3:

> What should count as "income" vs. an excluded internal transfer when a salary account and
> a savings account both receive transfers from each other?

The concrete failure this prevents: the user moves ₹20,000 from salary to savings. If both
statements are supplied and nothing special happens, the tool sees a ₹20,000 debit (counted
as **spend**) and a ₹20,000 credit (counted as **income**). Total spend is inflated by
₹20,000, total income by ₹20,000, and the top-category callout — the entire point of the
product (FR13, US4) — may name "Transfers" as the biggest expense.

Governing requirements:

- **FR11:** exclude or separately label internal transfers *"so they don't inflate the
  'spend' total."*
- **FR12:** compute total spend, total income (if present), and net.
- **US6:** combine multiple accounts into one summary.
- **NFR6:** accuracy gaps must be visible, not hidden.

## Decision

**A three-signal ladder over a user-declared account registry, with pair matching as the
primary mechanism. Movements between the user's own accounts are transfers — never income,
never spend — and the excluded total is always shown.**

### The ladder (C5 `detect_transfers`, first match wins)

```
T1  PAIR MATCH  (strongest)
    A debit in account A and a credit in account B, where both A and B are
    own: true, with equal absolute amount and dates within
    transfers.window_days (default 3).
    -> BOTH sides is_transfer = true, linked by transfer_pair_id.

T2  COUNTERPARTY MATCH
    The transaction's description contains an alias of a known own: true
    account (an alias, a masked number, the user's own name).
    -> is_transfer = true, even when only one side's statement was supplied.

T3  CATEGORY FLAG
    The transaction categorizes into a category with is_transfer: true
    (e.g. Transfers/Self, whose seed keywords include "self transfer",
    "own account", "credit card payment", "cc bill").
    -> is_transfer = true.

otherwise -> a normal debit (spend) or credit (income).
```

### Income classification (FR12)

A **credit** that is not a transfer counts as income. Additionally:

- Credits matching `income_rules.salary_keywords` (`"salary"`, `"sal cr"`, `"payroll"`) are
  income even if some other heuristic would have been ambiguous — salary is the one credit
  we should never get wrong.
- `income_rules.treat_unmatched_credit_from_own_account_as: transfer` is the **default
  answer to OQ3's literal question**: an unmatched credit that appears to come from one of
  the user's own accounts is treated as a transfer, not income. The alternative default
  (income) would inflate income every month by the amount the user shuffles between their
  own accounts — a number that means nothing. The key is set in `accounts.yaml`, so a user
  who disagrees flips one line.
- **Refunds are income, not negative spend** (EC3). A refund credit is never netted against
  the original debit's category; both remain visible.

### Visibility is mandatory (FR11's "or separately label", NFR6)

The spec permits exclusion *or* labelling. Doing only the first would make ₹18,000 silently
disappear from a report the user is trying to reconcile against their own bank balance. So:

- `MonthlySummary.transfers_total` records the excluded amount.
- The report's Sources & Transfers panel lists every transfer with both legs when paired.
- Transfers keep their category and appear in the CSV with `is_transfer=true`, so the
  underlying data is complete (FR21).
- A **single-leg transfer** (T2/T3 matched, no pair found) is badged as such, because it is
  the case most likely to be a misclassification.

### Why the account registry has to be declared

There is no reliable way to infer "this is also my account" from a statement. Account
numbers are masked, counterparty names are inconsistent, and NEFT/IMPS narrations are
free-text. `accounts.yaml` is a one-time setup step (NFR5) whose value is immediate and
explicable. Without it, only T3 (keyword) works, and the report says so.

## Alternatives considered

### A. Treat every credit as income — **rejected**

Simplest possible rule, and it is what a naive tool does. It inflates income by every
internal shuffle, makes `net` meaningless, and directly violates FR11's purpose.

### B. Treat every credit as a transfer unless explicitly whitelisted as income — **rejected**

Safe for `total_spend`, but it erases genuine income (salary, freelance payments, refunds)
unless the user pre-declares every payer. FR12 asks for total income; defaulting it to zero
is not "computing" it.

### C. Amount-and-date pair matching **only**, with no account registry — **rejected as sole mechanism**

Elegant, needs no configuration, and is genuinely the strongest signal — which is why it is
T1. But it fails whenever only one side's statement was supplied, which is common (the user
may not export their savings account every month). It also produces false positives: two
unrelated ₹500 payments on the same day would pair. The registry constrains T1 to
own-account pairs, which removes that class of false positive.

### D. Ask the user interactively about every ambiguous credit — **rejected**

Most accurate, and unbearable: a month with 200 transactions could generate dozens of
prompts, against NFR5 and FR23's unattended-run promise. The **report** is the right place
to resolve ambiguity, and it already has the mechanism — the user recategorizes to
`Transfers/Self` and the correction sticks (FR9, FR20, AC4).

### E. Infer own-accounts automatically from recurring paired amounts — **rejected**

Clever, and would remove the setup step. But it is a heuristic that silently reclassifies
money, and when it is wrong the user has no idea why their income dropped. Against NFR6's
transparency requirement.

## Consequences

**Positive**

- OQ3 has a concrete, defensible default that does not inflate either total.
- Transfers are excluded from spend **and** visible — both halves of FR11.
- Works with partial statement coverage (T2/T3 cover the one-legged case).
- Every classification is explainable: which signal fired, and why.
- The user can override any single transaction through the normal FR20 correction flow —
  no special mechanism.

**Negative / accepted costs**

- **Requires a one-time `accounts.yaml` setup.** Without it, only keyword matching works.
  Mitigated by the setup script prompting for it and by the report warning when large
  recurring debits look like undetected transfers.
- **Pair matching can produce false positives** where two genuinely unrelated same-amount
  transactions occur between two own accounts within the window. Rare, visible in the
  transfers panel, and correctable.
- **The `window_days: 3` default is a judgement call.** NEFT/IMPS is usually same-day; the
  window exists for weekend and cheque delays. Configurable.
- **A user with only one account gets no benefit from T1**, and relies on T2/T3. Acceptable
  — the double-counting problem largely does not exist for them.
- Exact-amount matching misses transfers reduced by a fee. `amount_tolerance` exists in
  `config.yaml` (default `0.00`) for users whose bank charges one.

## Confirmation requested

Recorded in [06-risks-and-open-questions.md](../06-risks-and-open-questions.md) as **A3**.
Two things from the owner would settle it completely: the list of accounts they actually
hold (for `accounts.yaml`), and confirmation that **self-transfers should count as neither
income nor spend** — which is the default adopted here.
