# Implementation Plan: Sisu Prediction Market

**Branch**: `001-sisu-prediction-market` | **Date**: 2026-09-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-sisu-prediction-market/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Sisu Market is a Finnish-language, play-money prediction market: users spend a virtual currency
("sisu") wagering on yes/no questions other users create, odds move pari-mutuel style as sisu is
wagered, and a dedicated resolver role settles closed predictions and triggers automatic payouts.
Technical approach: a Java 25 / Quarkus REST + Server-Sent-Events backend backed by MongoDB
(multi-document transactions for atomic balance/wager/payout writes), and a React + TypeScript +
Tailwind CSS single-page frontend that subscribes to an SSE stream so balances and odds update live
without polling.

## Technical Context

**Language/Version**: Java 25 (backend), TypeScript 5.x (frontend)

**Primary Dependencies**: Quarkus 3.x (RESTEasy Reactive / Jakarta REST, `quarkus-mongodb-panache`,
`quarkus-smallrye-jwt`, `quarkus-smallrye-openapi`, `quarkus-hibernate-validator`) on the backend;
React 18, Vite, Tailwind CSS, TanStack Query, React Router on the frontend

**Storage**: MongoDB 7.x, run as a single-node replica set (required for multi-document ACID
transactions) — collections `users`, `predictions`, `wagers`, `transactions`

**Testing**: JUnit 5 + `@QuarkusTest` + REST-assured + Mockito (backend unit/contract), Testcontainers
MongoDB module (backend integration), Vitest + React Testing Library (frontend)

**Target Platform**: Backend as a Linux container; frontend as a static SPA served to evergreen
desktop and mobile browsers

**Project Type**: Web application (separate backend + frontend)

**Performance Goals**: 95% of API requests respond within 500ms at ≥100 concurrent users (Constitution
VII); a user's own balance and the odds they affected update in the UI within 1 second of a
successful wager (Constitution VII, SC-002); prediction list/detail pages load within 2 seconds at
≥100 concurrent users (SC-003)

**Constraints**: All sisu amounts stored as integers (no floating point); wager + balance + payout
writes MUST be atomic (MongoDB multi-document transactions); UI text sourced from a Finnish
localization file, WCAG 2.1 AA; passwords hashed (bcrypt/Argon2), login rate-limited to 5 attempts /
15-minute cooldown per username+IP (FR-037); a resolver account is provisioned idempotently at
startup from environment-supplied credentials, never from a committed default (FR-039/FR-040,
research.md §8); project MUST start locally with a single `docker compose up`

**Scale/Scope**: MVP scope — 6 user stories, 40 functional requirements, ~100 concurrent users;
excludes multi-outcome predictions, buying sisu with real money, and social features (comments,
following)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluated against the constitution at v1.1.0 (Principles I–VII). An earlier revision of this table
used a four-principle draft (Code Quality / Testing / UX / Performance) that no longer exists;
that is why principles I, II, IV, V and VI were previously unchecked.

| Principle | Status | How this plan satisfies it |
|---|---|---|
| I. Play Money Only (NON-NEGOTIABLE) | PASS | No payment, cash-out or transfer feature exists; FR-032 puts the "no monetary value" statement in the product itself (footer on every page, SC-008); personal data is limited to username and password hash, and FR-033/FR-036 delete the account and scrub both. |
| II. Ledger Integrity & Atomic Transactions | PASS | `transactions` is append-only (no update/delete operation exposed); wager + balance + pool + ledger writes share one MongoDB transaction; the non-negative check is a conditional write (no double-spend); all amounts are integers (`Sisu`); `data-model.md` invariant 5 keeps balance reconcilable to the ledger. The only mint is the documented starting grant, which the bootstrap resolver also receives (FR-039) as a recorded `STARTING_GRANT` entry. |
| III. Test-First Core Domain | PASS, one tracked gap | Odds/payout/closing/rate-limit logic is plain Java with an injected `Clock` and mocked repositories; Testcontainers covers the real transaction path separately; JaCoCo enforces 80% and CI blocks merge. Gap: the account-deletion cascade (refund/cancel math) is currently covered only by a real-MongoDB integration test. Design response: keep the refund/cancel decision logic separable from the Mongo calls so it can be unit-tested without a database (research.md §9). |
| IV. Transparent, Auditable Market Resolution | PASS once research.md §9 is implemented | Creation validation, closing within 1 minute (surviving restart), single resolution via a conditional status update, and `resolvedBy`/`resolvedAt`/`winningOutcome` are designed. Gap found: no design previously required logging of resolutions, cancellations and failed transactions. research.md §9 now specifies structured log events for each; implementation tasks are still to be added. |
| V. Security by Default | PASS | Hashed passwords, per-username+IP login rate limiting (FR-037), httpOnly/Secure/SameSite=Strict session cookie with 24h expiry (research.md §3), server-side validation, errors without internals, secrets only from environment variables (the bootstrap resolver password has no committed default, research.md §8). HTTPS is now covered by research.md §10 (TLS at the deployment boundary, Secure cookie, HSTS). |
| VI. Contract-First, Reproducible Delivery | PASS | `contracts/openapi.yaml` is the contract and is checked against the generated document by a test; Checkstyle/Spotless and ESLint/Prettier gate the build; `docker compose up` starts the whole stack; no extra services, queues or caches were added, so Complexity Tracking stays empty. |
| VII. Localized, Accessible, Performant UX | PASS (verification pending) | All copy lives in `strings.fi.ts`; responsive layout; labelled forms and focus-visible styles for WCAG 2.1 AA; errors name the field and the fix; SSE delivers odds/balance within 1 second; stored pool totals keep list/detail reads to single-document fetches. The 500 ms / 100-user and 2 s targets are designed for but not yet measured (load test T077 open). |

No unjustified violations — Complexity Tracking is left empty. Principle IV carries one open
implementation gap and Principle III one tracked test-structure gap; both are recorded above and in
research.md §9, not waived.

**Post-Phase-1 re-check**: Re-evaluated after `research.md`, `data-model.md`, and `contracts/` were
written, and again after the FR-039/FR-040 (bootstrap resolver) update. The transaction design in
`data-model.md` §"Cross-entity invariants" reinforces Principles II and III, `contracts/openapi.yaml`
satisfies Principle VI, and the bootstrap reuses the registration/hashing/ledger path rather than a
parallel one (Principles II and V). Status unchanged apart from the gaps named above.

## Project Structure

### Documentation (this feature)

```text
specs/001-sisu-prediction-market/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
├── contracts/            # Phase 1 output (/speckit-plan command)
│   ├── openapi.yaml
│   └── realtime.md
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
sisu-backend/                          # Quarkus REST + SSE API (existing empty dir, filled by this feature)
├── src/main/java/market/sisu/
│   ├── user/                          # User entity, UserResource, UserService, UserRepository,
│   │                                  # ResolverBootstrap (startup resolver provisioning, FR-039/040)
│   ├── auth/                          # Login/register, JWT issuance, rate limiting (FR-037)
│   ├── prediction/                    # Prediction entity, resource, service, repository, odds math (FR-034)
│   ├── wager/                         # Wager entity, resource, service, repository
│   ├── transaction/                   # Transaction entity, service, repository (balance ledger)
│   ├── realtime/                      # SSE endpoint broadcasting odds/balance updates
│   └── common/                        # Sisu (integer money) value type, error handling, validation
├── src/main/resources/
│   └── application.properties
└── src/test/java/market/sisu/
    ├── unit/                          # Mocked-repository service tests (no external services)
    ├── integration/                   # Testcontainers MongoDB tests (transactions, repositories)
    └── contract/                      # REST-assured tests against the OpenAPI contract

sisu-frontend/                         # React + Tailwind SPA (new)
├── src/
│   ├── pages/                         # PredictionList, PredictionDetail, CreatePrediction, Login,
│   │                                  # Register, Profile, Leaderboard
│   ├── components/                    # Shared UI (OddsBar, BalanceBadge, WagerForm, ErrorBanner, ...)
│   ├── hooks/                         # useAuth, useLiveUpdates (SSE), useWager, usePredictions
│   ├── api/                           # Typed REST client (generated from contracts/openapi.yaml)
│   ├── i18n/                          # strings.fi.ts (all UI copy, per Constitution VII)
│   ├── App.tsx
│   └── main.tsx
└── tests/                             # Vitest + React Testing Library

docker-compose.yml                     # mongo + sisu-backend + sisu-frontend, single-command startup
```

**Structure Decision**: Web application split into `sisu-backend/` (already scaffolded, currently
empty) and a new `sisu-frontend/`, matching the existing repo naming convention rather than generic
`backend/`/`frontend/` folders. Each feature area (user, auth, prediction, wager, transaction,
realtime) is its own vertical package on the backend so business logic, HTTP resources, and
persistence stay separated per Constitution III.

## Complexity Tracking

*No Constitution Check violations — this section is intentionally empty.*
