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
IV); a user's own balance and the odds they affected update in the UI within 1 second of a
successful wager (Constitution III, SC-002); prediction list/detail pages load within 2 seconds at
≥100 concurrent users (SC-003)

**Constraints**: All sisu amounts stored as integers (no floating point); wager + balance + payout
writes MUST be atomic (MongoDB multi-document transactions); UI text sourced from a Finnish
localization file, WCAG 2.1 AA; passwords hashed (bcrypt/Argon2), login rate-limited to 5 attempts /
15-minute cooldown per username+IP (FR-037); project MUST start locally with a single
`docker compose up`

**Scale/Scope**: MVP scope — 6 user stories, 38 functional requirements, ~100 concurrent users;
excludes multi-outcome predictions, buying sisu with real money, and social features (comments,
following)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How this plan satisfies it |
|---|---|---|
| I. Code Quality | PASS | Layered backend (`resource` → `service` → `repository`/Panache entity) keeps business logic (odds/payout math, rate limiting, resolution) out of HTTP and persistence code; `quarkus-smallrye-openapi` generates the OpenAPI spec from the same annotated resources described in `contracts/openapi.yaml`; secrets (Mongo URI, JWT signing key) come from environment variables, never committed; a single root `docker-compose.yml` starts MongoDB, the backend, and the frontend together. |
| II. Testing Standards (NON-NEGOTIABLE) | PASS | Odds/payout/rate-limit logic lives in plain Java services tested with mocked repositories and an injectable `Clock` (no live DB needed for unit tests); Testcontainers-backed integration tests cover the real MongoDB transaction path separately; both suites run in CI on every PR; JaCoCo enforces the 80% line-coverage floor on business logic. |
| III. User Experience Consistency | PASS | All frontend copy lives in `src/i18n/strings.fi.ts`; Tailwind's responsive utilities cover desktop/mobile; accessible primitives (labeled forms, focus-visible states, semantic landmarks) target WCAG 2.1 AA; the SSE stream pushes balance/odds changes to the placing user's browser, satisfying the 1-second update requirement without polling. |
| IV. Performance Requirements | PASS | Quarkus's low request overhead plus indexed MongoDB queries (`predictions.status`, `wagers.userId`) target the 500ms/100-concurrent-user budget; odds are precomputed and stored on the prediction document at wager time (not recalculated via aggregation on read), keeping list/detail reads cheap; SSE avoids the read amplification of client polling. |

No violations — Complexity Tracking is left empty.

**Post-Phase-1 re-check**: Re-evaluated after `research.md`, `data-model.md`, and `contracts/` were
written. No new violations were introduced — the transaction design in `data-model.md` §"Cross-entity
invariants" reinforces Testing Standards (explicit atomicity/non-negative-balance/single-resolution
invariants map directly to required edge-case tests), and `contracts/openapi.yaml` is the concrete
artifact Code Quality's "REST API MUST be documented with an OpenAPI specification" requires. Status
unchanged: PASS on all four principles.

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
│   ├── user/                          # User entity, UserResource, UserService, UserRepository
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
│   ├── i18n/                          # strings.fi.ts (all UI copy, per Constitution III)
│   ├── App.tsx
│   └── main.tsx
└── tests/                             # Vitest + React Testing Library

docker-compose.yml                     # mongo + sisu-backend + sisu-frontend, single-command startup
```

**Structure Decision**: Web application split into `sisu-backend/` (already scaffolded, currently
empty) and a new `sisu-frontend/`, matching the existing repo naming convention rather than generic
`backend/`/`frontend/` folders. Each feature area (user, auth, prediction, wager, transaction,
realtime) is its own vertical package on the backend so business logic, HTTP resources, and
persistence stay separated per Constitution I.

## Complexity Tracking

*No Constitution Check violations — this section is intentionally empty.*
