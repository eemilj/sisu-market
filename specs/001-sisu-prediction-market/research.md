# Phase 0 Research: Sisu Prediction Market

All Technical Context items were resolvable from the user's explicit tech directive (Java 25 +
Quarkus + MongoDB backend, React + Tailwind CSS SPA frontend with real-time updates) plus the
project constitution and feature spec. No items remain marked `NEEDS CLARIFICATION`.

## 1. Real-time update transport

**Decision**: Server-Sent Events (SSE), exposed as a single authenticated
`GET /api/realtime/updates` stream per connected client, implemented with Quarkus RESTEasy
Reactive's `Multi<OutboundSseEvent>` support.

**Rationale**: Every real-time requirement in the spec (SC-002, Constitution III) is one-directional
— server pushes odds/balance changes to the browser; the browser never needs to push data back over
the same channel (wagers, predictions, and resolutions are ordinary REST POSTs). SSE runs over plain
HTTP, auto-reconnects natively via the browser's `EventSource`, and needs no extra client library or
sticky-connection handshake, keeping the frontend's `useLiveUpdates` hook a thin wrapper around
`EventSource`.

**Alternatives considered**:
- *WebSockets* (`quarkus-websockets-next`) — rejected as unnecessary complexity; full duplex isn't
  needed since client→server traffic is already REST, and WebSocket reconnection/backpressure
  handling would add code with no corresponding requirement.
- *Client polling* — rejected outright: REQUIREMENTS.md and Constitution III set a 1-second update
  budget, and polling at that frequency at 100 concurrent users wastes request budget the 500ms API
  performance target (Constitution IV) is meant to protect.

## 2. Persistence & atomic balance/wager writes

**Decision**: MongoDB accessed via `quarkus-mongodb-panache` (imperative, not reactive), run as a
single-node replica set even in local/dev `docker-compose` so MongoDB's multi-document ACID
transactions are available. A wager is written inside one `ClientSession` transaction that (a)
decrements the user's balance, (b) inserts the wager, (c) updates the prediction's pool totals, and
(d) inserts the transaction-log entry; resolution and refund/cancellation flows use the same
pattern.

**Rationale**: FR-022 and the constitution's data-integrity expectations require the balance change
and the wager record to never diverge. MongoDB transactions across the `users`, `wagers`,
`predictions`, and `transactions` collections are the direct, single-database way to guarantee that
without introducing a second datastore or a distributed-transaction framework. Imperative Panache
(vs. reactive) was chosen because Quarkus's reactive MongoDB Panache does not yet have first-class
`ClientSession` transaction ergonomics as mature as the imperative driver's, and the team's stated
stack (plain Java, no reactive requirement mentioned) doesn't need reactive streams end-to-end.

**Alternatives considered**:
- *Reactive MongoDB Panache* — rejected for now due to weaker transaction API support; revisit if
  throughput profiling shows the imperative driver's blocking I/O is the bottleneck.
- *Application-level compensation (no DB transaction)* — rejected: retries/partial failures could
  leave a wager recorded without a balance deduction or vice versa, which FR-022 explicitly forbids.

## 3. Authentication & session handling

**Decision**: `quarkus-smallrye-jwt`-issued JWT, stored by the browser in an `httpOnly`, `Secure`,
`SameSite=Strict` cookie; 24-hour expiry; the SSE `EventSource` request (which cannot set custom
headers) authenticates via the same cookie since it's sent automatically on same-site requests.

**Rationale**: A stateless bearer token fits a horizontally-scalable Quarkus API without server-side
session storage. `httpOnly` prevents XSS-based token theft (Constitution/REQUIREMENTS.md security
requirement), and `SameSite=Strict` plus requiring `Content-Type: application/json` on all
state-changing endpoints blocks the classic CSRF vectors without needing a separate CSRF-token
handshake. 24 hours is long enough that a play-money market user isn't repeatedly logged out
mid-session, short enough to bound the blast radius of a leaked cookie.

**Alternatives considered**:
- *JWT in `localStorage`, sent via `Authorization` header* — rejected: exposes the token to any XSS,
  and doesn't work for the SSE `EventSource` connection without a query-string token (which then
  leaks into server logs).
- *Server-side sessions* — rejected: adds a stateful component (sticky sessions or a shared session
  store) the constitution's Code Quality principle doesn't otherwise require, for no benefit here.

## 4. Odds & payout calculation (FR-034)

**Decision**: Pari-mutuel, pool-proportional odds. Each `Prediction` document stores running
`yesPoolSisu`/`noPoolSisu` totals, updated atomically in the same transaction as each wager. Current
odds shown to users = each side's share of the combined pool. On resolution, each winning wager's
payout = its stake + (its share of the winning pool × the losing pool).

**Rationale**: Already decided with the user during `/speckit-clarify` (Q1: A); storing running pool
totals on the prediction document (rather than summing `wagers` on every read) keeps list/detail
reads a single-document fetch, which is what the 500ms/2s performance targets (Constitution IV,
SC-003) depend on.

**Alternatives considered**: Fixed odds at creation time — already rejected during clarification.

## 5. Login rate limiting (FR-037)

**Decision**: A `login_attempts` MongoDB collection keyed by `(username, ip)` with a TTL index that
auto-expires entries after 15 minutes; the auth service increments a counter on each failed attempt
and rejects with a 429-equivalent error once the counter reaches 5 within the window.

**Rationale**: Reuses the same MongoDB the rest of the system depends on rather than adding Redis or
an in-memory cache that wouldn't survive a backend restart or work across multiple backend
instances. A TTL index gives automatic cleanup with no scheduled job.

**Alternatives considered**: In-memory rate limiter (e.g., a Caffeine cache) — rejected: doesn't
survive a restart or work if the backend is ever scaled to more than one instance, and the
constitution's Reliability expectations favor durability here.

## 6. Frontend localization approach (FR-031)

**Decision**: A single `src/i18n/strings.fi.ts` module exporting a flat, typed dictionary of Finnish
strings, imported directly by components — no i18n framework.

**Rationale**: The product has exactly one locale (Finnish) with no requirement to support switching
languages; a full i18n library (react-i18next, FormatJS) would add bundle size, configuration, and
indirection for a feature (runtime locale switching) nothing in the spec asks for. A typed constant
module still satisfies Constitution III's "sourced from a localization file rather than hardcoded
strings" and gives compile-time safety that a full framework's string-key lookups would not.

**Alternatives considered**: `react-i18next` — rejected as unnecessary until a second locale is
actually required; revisit if that changes.

## 7. Frontend data fetching & SPA structure

**Decision**: Vite + React Router for the SPA shell; TanStack Query for REST data fetching/caching
(predictions list, profile, leaderboard) with cache invalidation driven by SSE events; Tailwind CSS
for styling with no separate component library — small, accessible primitives built directly.

**Rationale**: This is the conventional, low-friction React SPA stack for 2026 and needs no
justification beyond "matches the user's explicit direction (React + Tailwind, SPA, real-time)."
TanStack Query's cache + SSE-triggered invalidation avoids hand-rolling request de-duplication or
loading-state bookkeeping.

**Alternatives considered**: Next.js — rejected: the user asked for single-page-application
characteristics specifically, and Quarkus already serves as the backend, so a React-only SPA build
(no server-side rendering framework) is the closer fit.

## 8. Bootstrap resolver provisioning (FR-039, FR-040)

**Decision**: A `ResolverBootstrap` bean observes Quarkus's `StartupEvent` and, when
`sisu.bootstrap.resolver.username` and `sisu.bootstrap.resolver.password` are both set (mapped from
`SISU_RESOLVER_USERNAME` / `SISU_RESOLVER_PASSWORD`), provisions one `RESOLVER` account:

- **Absent** → create it through the same service path registration uses (password hashing,
  starting grant, `STARTING_GRANT` transaction, all in one MongoDB transaction), with `role =
  RESOLVER`.
- **Present with role `RESOLVER`** → no-op; password and balance are never touched.
- **Present with role `USER`** → promote the role only; password and balance are untouched
  (spec edge case).
- **Not configured, or password fails the registration password rules** → log a clear warning
  (never the password), skip, and continue starting.

Idempotence under concurrent starts relies on the existing unique index on `username`: a
duplicate-key failure on insert is treated as "already present" and falls through to the role
check. `docker-compose.yml` passes the two variables through from the host environment with no
default password; the username defaults to `resolver`.

**Rationale**: Startup provisioning satisfies "no manual database or role change" (SC-009) while
keeping the role non-self-service (FR-035/FR-040). Reusing the registration path guarantees the
ledger invariant (SC-006) holds for the resolver too. Supplying the secret via environment matches
how the JWT keys and Mongo URI are handled (Constitution Code Quality), and refusing a default
password avoids a well-known credential on every deployment.

**Alternatives considered**:
- *First registered user becomes resolver* — rejected: whoever registers first on a public
  deployment gains the role.
- *Hardcoded/default resolver password in compose* — rejected: a committed credential.
- *Admin CLI or HTTP endpoint to promote users* — rejected for this feature: it is the separate
  role-management capability the spec leaves out of scope; an HTTP endpoint would also widen the
  attack surface and the OpenAPI contract.
- *Seed script run by hand (previous quickstart approach)* — rejected: easy to forget, not
  idempotent by construction, and untestable as part of the app.
