<!--
Sync Impact Report
==================
Version change: 1.1.0 → 1.1.1 (PATCH: records a decision already made in the plan; no principle
added, removed or redefined)

Modified principles (old title → new title):
  - V. Security by Default → V. Security by Default [session bullet now states the decided
    mechanism and expiry instead of an open TODO]

Added sections: none
Removed sections: none

Resolved TODOs:
  - TODO(SESSION_MECHANISM) → resolved. Decision taken in specs/001-sisu-prediction-market/
    research.md §3 and implemented in sisu-backend (sisu.session.ttl=PT24H): a stateless JWT
    delivered only in an httpOnly, Secure, SameSite=Strict cookie, valid for 24 hours.

Deferred TODOs:
  - TODO(TECH_STACK_VERSIONS): Java version and build tool for `sisu-backend/` are still marked
    unset here, although specs/001-sisu-prediction-market/plan.md pins Java 25, Quarkus 3.x and
    MongoDB 7.x (Maven wrapper in the repo). Not changed in this amendment; pin and amend (PATCH).

Dependent templates: not modified by this command (they read the constitution at runtime).
  - .specify/templates/plan-template.md — Constitution Check gate evaluates principles I–VII
  - .specify/templates/tasks-template.md — unchanged
-->

# Sisu Market Constitution

## Core Principles

### I. Play Money Only (NON-NEGOTIABLE)

Sisu Market is a prediction market that uses fictional "sisu" points exclusively.

- Sisu points MUST NOT be purchasable with, redeemable for, or exchangeable into real money,
  cryptocurrency, gift cards, or any other item of monetary value.
- No feature MAY introduce payments, cash-out, point transfers to external systems, or prizes of
  monetary value.
- The system MUST clearly state, in the product itself, that sisu has no monetary value and
  cannot be exchanged for money — this is what keeps the platform outside Finnish gambling
  regulation, not a legal disclaimer alone.
- The system MUST collect only the personal data needed to operate accounts (at minimum:
  username, password hash) and MUST let a user delete their account, in line with GDPR.

**Rationale**: Real-money prediction markets are regulated gambling/financial products in most
jurisdictions. Keeping value strictly fictional and data collection minimal keeps the project
legally simple and focused on the forecasting game itself.

### II. Ledger Integrity & Atomic Transactions

Every point in the system MUST be accounted for and every balance change traceable.

- Every balance change (starting grant, wager, payout, refund, correction) MUST be recorded as
  an entry in an append-only transaction ledger, with timestamp and reason; entries MUST NOT be
  updated or deleted. Corrections are made with new compensating entries.
- A user's balance MUST NOT be allowed to go below zero; a wager exceeding the user's current
  balance MUST be rejected before any ledger entry is written.
- Deducting a wager from a balance and recording that wager MUST happen as a single atomic
  operation (e.g. a MongoDB multi-document transaction) — no partial state may ever exist.
- The system MUST prevent double-spending when a user submits concurrent wagers (e.g. two
  simultaneous wagers that would each individually be affordable but not both together).
- Sisu amounts MUST use integers — never binary floating point — to avoid rounding errors.
- The sum of all sisu in the system MUST be explainable by the transaction log at any time; any
  cached/denormalized balance MUST be reconcilable to it.
- Points are created only by documented mechanisms (starting grant, and any future explicitly
  specified grant) and destroyed only by documented mechanisms; no code path MAY mint or burn
  points implicitly.

**Rationale**: Even fake money loses all meaning if it can be duplicated, lost, or double-spent.
Users trust the leaderboard only if the books balance under concurrency too.

### III. Test-First Core Domain

Core domain logic MUST be developed test-first (Red → Green → Refactor).

- Core domain includes: pricing/odds math, wager placement, the ledger, market closing and
  resolution, and payout/refund calculation.
- Tests for core domain code MUST be written and observed failing before the implementation.
- Pricing and payout calculations MUST have unit tests covering edge cases: no wagers on the
  winning side, a single wager, and cancellation/refund.
- Business logic MUST be separated from controllers and persistence so it can be tested in
  isolation; unit tests MUST run without external services, with the database and time mocked or
  injected.
- All business logic MUST be covered by unit tests with a minimum of 80% line coverage.
- The full test suite MUST run in CI on every pull request and block merging on failure.

**Rationale**: Bugs in pricing or payouts silently corrupt every user's balance and are hard to
unwind after the fact; isolating business logic is what makes fast, external-service-free tests
possible at all.

### IV. Transparent, Auditable Market Resolution

Every market MUST be resolvable unambiguously and every resolution MUST be traceable.

- A prediction MUST NOT open without a question, description, resolution criteria, and closing
  date; question and resolution criteria MUST be validated as non-empty, and the closing date
  MUST NOT be in the past. The creator MUST be recorded.
- The system MUST automatically stop accepting wagers once the closing date passes, and this
  closing job MUST run within 1 minute of that time, even after a server restart.
- Resolving a closed prediction to an outcome MUST be restricted to an authorized user, and MUST
  record who resolved it, when, and the chosen outcome.
- A prediction MUST NOT be resolved more than once; payout and refund processing MUST be
  idempotent, so a retried resolution never pays out twice.
- Winnings MUST be paid to all winning wagers as part of resolution, via ledger entries
  (Principle II); cancelled or invalid markets MUST refund participants the same way.
- All resolutions, cancellations, and failed transactions MUST be logged with enough context to
  trace them after the fact.

**Rationale**: Ambiguous, silently repeated, or unexplained resolutions destroy trust in the
market, fake money or not.

### V. Security by Default

Every request that touches an account, a balance, or a market MUST be handled defensively.

- Passwords MUST be stored only in hashed form, using a modern adaptive algorithm (bcrypt or
  Argon2).
- All client-server communication MUST use HTTPS.
- Authenticated sessions MUST use a single, consistently-applied mechanism with a defined expiry:
  a stateless signed JWT carried only in an `httpOnly`, `Secure`, `SameSite=Strict` cookie, expiring
  after 24 hours. The token MUST NOT be exposed to client-side script (no `localStorage`, no
  query-string or `Authorization`-header transport), and the signing key MUST come from the
  environment (see the secrets rule below). Changing the mechanism or lengthening the expiry is an
  amendment to this constitution.
- All input MUST be validated on the server, regardless of any client-side validation.
- The system MUST protect against NoSQL injection, XSS, and CSRF.
- Login attempts MUST be rate limited per username and per IP.
- Error responses MUST NOT expose stack traces or other internal details; the system MUST fail
  fast with a clear, user-actionable error when a precondition is not met (insufficient balance,
  closed prediction, invalid input).
- Secrets (database credentials, signing keys) MUST NOT be stored in the repository; they come
  from environment variables or an untracked local config file.
- Creating predictions and placing wagers MUST require authentication; browsing MAY remain
  public.

**Rationale**: A points economy is still an attractive target for abuse (fake-balance farming,
account takeover); baseline security hygiene is cheap and non-negotiable regardless of stakes.

### VI. Contract-First, Reproducible Delivery

Build the smallest thing that works, behind a clear, documented contract.

- Features MUST start with the simplest design that satisfies the spec (YAGNI). Additional
  services, queues, caches, or abstraction layers MUST be justified in the plan's Complexity
  Tracking section.
- Each feature MUST have a Spec Kit `spec.md` and `plan.md` before implementation begins (for the
  specification-driven workflow; not required for an explicitly-scoped vibe-code run).
- The backend (`sisu-backend`) exposes functionality through a documented, versioned REST API
  contract (OpenAPI specification) defined before implementation; contract changes MUST be
  reviewed as such.
- Code MUST pass the configured linter and formatter for both frontend and backend.
- The project MUST start locally with a single command (e.g. `docker compose up`).

**Rationale**: A hobby-scale project stays maintainable only if complexity is earned; a stable,
documented contract plus a one-command local start keeps the backend, any client, and future
contributors able to move independently and quickly.

### VII. Localized, Accessible, Performant UX

The product MUST be usable, in Finnish, by real people on real devices, at the promised speed.

- UI text MUST be presented in Finnish, with all strings kept in a localization file rather than
  hardcoded, so translation/copy changes never require touching logic.
- The UI MUST be usable on both desktop and mobile screen widths, and MUST meet WCAG 2.1 AA for
  contrast, keyboard navigation, and form labels.
- Error messages MUST explain what went wrong and how to fix it (not just that something failed).
- The system MUST respond to 95% of API requests within 500 ms under normal load, and MUST
  support at least 100 concurrent users without exceeding that target.
- Updated odds MUST be reflected to the placing user within 1 second of a successful wager, and
  prediction list pages MUST load within 2 seconds on a typical broadband connection.

**Rationale**: A prediction market only works if people can place a wager, see the odds move, and
understand what happened — quickly, in their own language, and regardless of device or ability.

## Technology & Security Constraints

- **Backend**: Java, located in `sisu-backend/`. Exact Java version and build tool are
  TODO(TECH_STACK_VERSIONS) and MUST be fixed in the first implementation plan.
- **Persistence**: MongoDB, using multi-document transactions for any operation that must satisfy
  Principle II's atomicity requirement (e.g. wager placement, payout processing).
- **Session mechanism**: JWT in an `httpOnly`, `Secure`, `SameSite=Strict` cookie with a 24-hour
  expiry, as specified in Principle V (rationale and rejected alternatives: `research.md` §3 of the
  feature that introduced accounts).
- **Personal data**: Collect only what is needed to run accounts (minimal PII per Principle I).

## Development Workflow & Quality Gates

- Features follow the Spec Kit flow: `/speckit-specify` → (`/speckit-clarify`) →
  `/speckit-plan` → `/speckit-tasks` → `/speckit-implement`.
- Every plan MUST pass the Constitution Check against Principles I–VII before design proceeds,
  and again after design. Violations MUST be justified in Complexity Tracking or removed.
- Work happens on feature branches; `main` MUST stay buildable with all tests passing, and CI
  MUST block merging on any test failure (Principle III).
- Reviews (self-review is acceptable for a solo maintainer) MUST explicitly check ledger
  integrity, play-money compliance, and input/auth handling for any change touching points,
  markets, or accounts.

## Governance

- This constitution supersedes other project practices and conventions. Where a spec, plan,
  or task conflicts with it, the constitution wins until amended.
- **Amendments**: Proposed via `/speckit-constitution`, recorded with a Sync Impact Report at
  the top of this file, and committed as a standalone documentation change.
- **Versioning** (semantic):
  - MAJOR — removing or redefining a principle in a backward-incompatible way.
  - MINOR — adding a principle or section, or materially expanding guidance.
  - PATCH — clarifications, wording, typo fixes, non-semantic refinements.
- **Compliance**: Each `/speckit-plan` run performs a Constitution Check; `/speckit-analyze`
  SHOULD be run before implementation to catch cross-artifact violations. Principle I is
  non-negotiable and MAY NOT be waived by Complexity Tracking justification.

**Version**: 1.1.1 | **Ratified**: 2026-09-23 | **Last Amended**: 2026-10-13
