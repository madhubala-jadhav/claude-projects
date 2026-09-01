# ADR-0012 — Credit card statements as linked accounts in the same monthly view (resolves OQ2)

**Status:** Accepted — **architect's assumption, pending owner confirmation**
**Date:** 2026-08-26

---

## Context

Spec §16, open question 2:

> Should credit card statements be treated as a separate input type with their own
> due-date/billing-cycle logic, or merged into the same monthly view as a linked account?

The question matters because of a specific double-counting trap:

- The **card statement** shows `SWIGGY ₹487` on 14 Aug — the real expense.
- The **bank statement** shows `CREDIT CARD PAYMENT ₹18,400` on 5 Sep — paying off the
  card.

Counting both inflates spend, mixes two months, and makes the FR13 top-category callout —
the product's entire point — wrong. Whatever we choose must handle this.

Constraints that bear on it:

- **G3/FR12** want *one* monthly summary of total spend.
- **US6** wants statements from more than one bank/account combined *"so that I see my
  total spend, not just one account's."* A credit card is, for a normal person, exactly
  such an account.
- **FR11** already requires internal transfers — and explicitly names *"credit card bill
  payments"* — to be excluded or separately labelled.
- **NG4** excludes investment/brokerage statements, but says nothing about cards.
- **§15** lists *"Investment and credit-card-only statement support as first-class input
  types"* as a **future enhancement** — which tells us cards are not meant to be a
  first-class *separate* type in v1.

## Decision

**Merge. A credit card is a linked account (`kind: credit_card`) whose transactions join
the same monthly dataset, keyed on the transaction date. Billing-cycle and due-date logic
is explicitly out of scope for v1.**

Concretely:

1. **`accounts.yaml` gains `kind: bank | credit_card`.** Cards are declared with `own: true`
   like any other account of the user's.
2. **Card transactions are grouped by transaction date, not statement/billing period.** A
   purchase on 14 Aug belongs to August, even if it appears on a statement dated 2 Sep and
   is paid in September. This is what the user actually means by "what did I spend in
   August" (G3, US4).
3. **The card bill payment is a transfer (FR11).** A debit from a bank account whose
   description matches the card's aliases, or whose counterparty is a known `own` account,
   is `is_transfer = true` and excluded from `total_spend` — with the amount surfaced in
   `transfers_total` and the transfers panel, so ₹18,400 does not appear to vanish.
4. **Sign conventions are normalized at parse time.** Card statements typically show
   purchases as positive and payments/refunds as negative — the opposite of a bank
   statement. The `amount_sign` profile field ([02](../02-data-model.md) §1.5) handles this
   declaratively; the resulting `direction` is what everything downstream sees
   ([ADR-0008](ADR-0008-money-and-currency.md)).
5. **The report shows the account split.** `MonthlySummary.accounts` and the Sources panel
   list each account's transaction count and spend, so the user can verify the merge (US6,
   AC1) and spot a card whose statement they forgot to add.
6. **Not deferred, not silently unsupported.** Cards work in v1; what is deferred is
   *billing-cycle semantics* (statement period, due date, minimum due, interest,
   revolving balance) — none of which any FR or AC requires.

## Alternatives considered

### A. Separate input type with billing-cycle logic — **rejected for v1**

*What it means:* group card transactions by statement period, track due dates, possibly warn
about upcoming payments.

*Why rejected:*
- **No requirement asks for it.** No FR, no AC, no user story mentions due dates or billing
  cycles. §15 explicitly parks it as a future enhancement, and NG1 rules out alerts.
- **It fragments the answer.** G3, US4 and FR13 want one number and one top category. A
  view split into "bank month" and "card cycle" cannot produce a single honest
  `total_spend`.
- **Billing cycles are not derivable** from the transaction table alone — you need the
  statement header, which varies by issuer and is exactly the fragile parsing surface
  ADR-0003 is already fighting.

### B. Treat a credit card as an ordinary unlinked account, no special handling — **rejected**

Zero code, but it walks straight into the double-counting trap: the card's ₹18,400 of
purchases *and* the bank's ₹18,400 payment both count, roughly doubling apparent spend in
the worst case. FR11's explicit mention of "credit card bill payments" exists precisely to
prevent this.

### C. Exclude credit cards from v1 entirely — **rejected**

Defensible on a strict reading of §15, and simplest. But for the spec's persona a large
share of discretionary spend — Food & Dining, Shopping, Entertainment, the categories the
product exists to surface — lives on a card. A report that omits them would give a
confidently wrong answer to "where did most of my money go", which is worse than not
shipping the feature.

### D. Merge, but group by billing period instead of transaction date — **rejected**

Matches the card statement's own presentation, so reconciliation is easier. But it makes the
monthly total incomparable with the bank side, and "August" would silently mean different
date ranges for different accounts — invisibly corrupting FR14's month-over-month comparison.

## Consequences

**Positive**

- US6 is satisfied for the accounts that matter most to the answer.
- Double counting is structurally prevented by FR11's transfer detection, not by user
  vigilance.
- One coherent `total_spend` for the month (G3, FR12, FR13).
- No new input type, no new pipeline branch — a card is a `BankProfile` plus an
  `AccountRef`.
- §15's future "first-class credit-card support" remains open and additive.

**Negative / accepted costs**

- **Transfer detection must be right, or double counting returns.** This is risk R3 in
  [06](../06-risks-and-open-questions.md). Mitigations: pair matching across own accounts,
  card aliases in `accounts.yaml`, the `Transfers/Self` category's `is_transfer: true`
  keywords (`"credit card payment"`, `"cc bill"`), and a visible transfers panel so an
  undetected transfer is *noticeable* rather than silent.
- **A card bill paid from an account whose statement was not supplied cannot be detected as
  a transfer** by pair matching; it falls back to alias/keyword matching. The user is told
  which accounts were seen, so the gap is visible.
- **Timing differences are visible to the user.** A card purchase on 31 Aug appears in
  August's report even though the money leaves the bank in September. This is the right
  semantic for "what did I spend", but it will occasionally surprise; the report's
  methodology footnote states it explicitly.
- **The user must declare their cards** in `accounts.yaml` for detection to work well. Part
  of one-time setup (NFR5), and the report warns when a large recurring debit looks like an
  undeclared card payment.

## Confirmation requested

Recorded in [06-risks-and-open-questions.md](../06-risks-and-open-questions.md) as **A2**.
If the owner in fact wants due-date tracking, that is a new requirement beyond v1's FR set
and should be specified rather than assumed — it would pull NG1's "no alerts" boundary as
well.
