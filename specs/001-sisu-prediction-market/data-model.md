# Phase 1 Data Model: Sisu Prediction Market

Source: `spec.md` Key Entities section, refined with the FR-034 (pari-mutuel), FR-035 (resolver
role), FR-036 (cancellation), FR-021 (cancelled predictions reject wagers), and FR-025
(closing-time enforced directly at wager time, not just via cached status) clarifications, plus the
MongoDB persistence decision in `research.md`. All sisu amounts are stored as integers (no floating
point), per REQUIREMENTS.md.

## User (`users` collection)

| Field | Type | Notes |
|---|---|---|
| `_id` | ObjectId | |
| `username` | string | Unique (unique index); case-insensitive uniqueness check at write time |
| `passwordHash` | string | Bcrypt/Argon2 hash; never returned by any API response |
| `role` | enum: `USER`, `RESOLVER` | FR-035 — only `RESOLVER` may resolve predictions; never self-service. Assigned only by startup bootstrap provisioning (FR-039, FR-040; research.md §8); registration always yields `USER` |
| `balanceSisu` | integer | Denormalized current balance; MUST always equal the sum of that user's `transactions`; MUST never go negative (FR-009) |
| `createdAt` | timestamp | |
| `deletedAt` | timestamp \| null | Set on account deletion (FR-033); personal fields (`username`, `passwordHash`) are scrubbed at that point, `_id` and de-identified history rows are retained per the Assumptions in spec.md |

**Validation**: `username` non-empty, unique; `balanceSisu >= 0` enforced at the service layer inside
the same transaction as any balance-changing write (FR-009).

**Relationships**: referenced by `Prediction.creatorId`, `Wager.userId`, `Transaction.userId`.

## Prediction (`predictions` collection)

| Field | Type | Notes |
|---|---|---|
| `_id` | ObjectId | |
| `creatorId` | ObjectId (→ User) | FR-015 |
| `question` | string | Non-empty (FR-013); at most 200 characters (FR-038) |
| `description` | string | May be empty; at most 2000 characters (FR-038) |
| `resolutionCriteria` | string | Non-empty (FR-013); at most 1000 characters (FR-038) |
| `closingAt` | timestamp | Must be in the future at creation time (FR-014) |
| `status` | enum: `OPEN`, `CLOSED`, `RESOLVED`, `CANCELLED` | See state transitions below (Clarifications session 2026-09-23) |
| `yesPoolSisu` | integer | Running total wagered on "yes"; updated atomically with each wager (FR-023, FR-034) |
| `noPoolSisu` | integer | Running total wagered on "no" |
| `winningOutcome` | enum: `YES`, `NO` \| null | Set once, on resolution (FR-026, FR-027) |
| `resolvedAt` | timestamp \| null | |
| `resolvedBy` | ObjectId (→ User) \| null | Must reference a `RESOLVER`-role user (FR-035) |
| `createdAt` | timestamp | |

**Derived (not stored)**: current odds = `yesPoolSisu / (yesPoolSisu + noPoolSisu)` and its
complement; computed on read from the two stored totals, never recomputed by summing `wagers` (keeps
list/detail reads a single-document fetch).

**Validation**: `question` and `resolutionCriteria` non-empty; `question` ≤ 200, `description`
≤ 2000 and `resolutionCriteria` ≤ 1000 characters, all stored trimmed and measured after trimming
(FR-038); `closingAt` in the future at creation (FR-014); `yesPoolSisu`, `noPoolSisu` >= 0.

**State transitions**:

```text
OPEN --(closingAt reached, automatic)--> CLOSED
CLOSED --(resolver settles to YES or NO)--> RESOLVED   [terminal; FR-027 blocks re-resolution]
OPEN or CLOSED --(creator's account deleted, unresolved)--> CANCELLED   [terminal; FR-036]
```

A `RESOLVED` or `CANCELLED` prediction never transitions again. Wagering is rejected once a
prediction's `status` is `CLOSED`, `RESOLVED`, or `CANCELLED` (FR-021), and is independently
rejected the instant `closingAt` has passed even if `status` still reads `OPEN` (FR-025) — see the
closing-time invariant under Cross-entity invariants below.

**Relationships**: has many `Wager`; referenced by `Transaction.relatedPredictionId` for
wager/payout/refund transactions tied to it.

## Wager (`wagers` collection)

| Field | Type | Notes |
|---|---|---|
| `_id` | ObjectId | |
| `userId` | ObjectId (→ User) | |
| `predictionId` | ObjectId (→ Prediction) | |
| `outcome` | enum: `YES`, `NO` | Chosen side |
| `amountSisu` | integer | Positive whole number (FR-019); immutable once placed |
| `status` | enum: `OPEN`, `WON`, `LOST`, `REFUNDED` | `REFUNDED` is set for every wager on a `CANCELLED` prediction, and for a user's own open wagers refunded on account deletion (FR-036) |
| `payoutSisu` | integer \| null | Set when `status` becomes `WON` or `REFUNDED`; stake + pari-mutuel share for `WON` (FR-034), stake only for `REFUNDED` |
| `placedAt` | timestamp | |
| `settledAt` | timestamp \| null | Set when the wager leaves `OPEN` |

**Validation**: `amountSisu > 0` and a whole number (FR-019, User Story 3 scenario 5); rejected at
placement if it exceeds the placing user's current `balanceSisu` (FR-020), the prediction's `status`
is `CLOSED`, `RESOLVED`, or `CANCELLED` (FR-021), or the current time is at or after the
prediction's `closingAt` (FR-025) — the latter is checked directly against the clock, not solely via
the cached `status`.

**Relationships**: belongs to one `User` and one `Prediction`; each settled wager has a matching
`Transaction` row.

## Transaction (`transactions` collection)

| Field | Type | Notes |
|---|---|---|
| `_id` | ObjectId | |
| `userId` | ObjectId (→ User) | |
| `direction` | enum: `CREDIT`, `DEBIT` | |
| `amountSisu` | integer | Always positive; `direction` gives sign |
| `reason` | enum: `STARTING_GRANT`, `WAGER_PLACED`, `PAYOUT`, `REFUND` | FR-010 |
| `relatedWagerId` | ObjectId (→ Wager) \| null | Present for `WAGER_PLACED`, `PAYOUT`, `REFUND` |
| `relatedPredictionId` | ObjectId (→ Prediction) \| null | Convenience denormalization for history queries |
| `createdAt` | timestamp | |

**Validation**: append-only — no update or delete operation is ever exposed; this is the ledger
FR-010/FR-011/SC-006 requires to always explain a user's `balanceSisu`.

**Relationships**: every `User.balanceSisu` change is backed by exactly one `Transaction` row created
in the same MongoDB transaction as the balance write.

## Cross-entity invariants (enforced in the service layer, inside MongoDB transactions)

1. **Atomicity** (FR-022): placing a wager writes the `Wager`, updates `User.balanceSisu`, updates
   `Prediction.{yes,no}PoolSisu`, and inserts a `Transaction` in one MongoDB `ClientSession`
   transaction — all four succeed or all four roll back.
2. **Closing-time enforcement** (FR-025): wager placement compares the current time to the
   prediction's `closingAt` directly, inside the same transaction as the wager write — not merely
   the cached `status` — so a wager cannot succeed in the gap between `closingAt` passing and the
   closing scheduler (`PredictionClosingScheduler`) updating `status` to `CLOSED`. That scheduler
   still flips the visible `status` within 1 minute of `closingAt`, and MUST keep doing so correctly
   across a server restart, so browsing/filtering by status stays accurate even though it isn't
   what wager placement itself relies on.
3. **Non-negative balance** (FR-009): the balance debit and the `balanceSisu >= amount` check happen
   inside the same transaction to prevent the concurrent-wager race described in spec.md's Edge
   Cases.
4. **Single resolution** (FR-027): resolving a `Prediction` is a conditional update
   (`status = CLOSED` → `status = RESOLVED`) so a second concurrent resolution attempt fails the
   condition rather than double-paying.
5. **Ledger completeness** (SC-006): the sum of a user's `Transaction` rows always equals their
   current `balanceSisu`; no code path writes to `balanceSisu` without also inserting a
   `Transaction`.

## Bootstrap provisioning rules (FR-039, FR-040)

No new fields or collections. The designated resolver is identified by the configured username
(deployment configuration, not stored). At startup:

| Existing `users` row for the configured username | Result |
|---|---|
| none | Insert with `role = RESOLVER`, hashed password, `balanceSisu` = starting grant, plus one `STARTING_GRANT` `Transaction` — in one MongoDB transaction (invariant 5 still holds) |
| `role = RESOLVER` | No change |
| `role = USER` | `role` set to `RESOLVER`; `passwordHash`, `balanceSisu`, history unchanged |

Invariants: at most one account is created per configured username (unique index); no account other
than the configured one is ever promoted by bootstrap; `Prediction.resolvedBy` continues to
reference a `RESOLVER`-role user.
