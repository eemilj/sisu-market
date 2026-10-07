# Sisu-markkinat (vibe-coded version)

A Finnish prediction market played with **sisu**, a fake currency. Sisu has no monetary value and
cannot be bought, sold or exchanged for money.

This is the vibe-coded counterpart to the spec-driven implementation in `../sisu-backend` and
`../sisu-frontend`. It shares no code with them.

## Run

```bash
docker compose up --build
```

- App: http://localhost:3000
- API docs (Swagger UI): http://localhost:8081/q/swagger-ui

The ports are different from the SDD stack's, so both stacks can run at the same time. Registering
the username `admin` gives you an admin account that can resolve any prediction. To choose other
admin usernames, set `SISU_ADMINS=name1,name2`.

### Local development

```bash
# backend: Quarkus dev mode; Dev Services starts MongoDB in Docker
cd backend && mvn quarkus:dev          # http://localhost:8080

# frontend: Vite dev server, proxies /api to :8080
cd frontend && npm install && npm run dev   # http://localhost:5173
```

Tests: `cd backend && mvn test` runs pure unit tests and needs no database.
`cd frontend && npm run build` runs the type check and the production build.

## Features

| Area | What it does |
|---|---|
| Accounts | Register and log in with bcrypt-hashed passwords and a 12 h JWT bearer token. Failed logins are rate-limited per username and per IP. |
| Balance | New users get 1 000 sisu. The header always shows the current balance. Every balance change is written to the transaction log. |
| Predictions | Create yes/no questions with a description, resolution criteria and a closing time at least 5 minutes ahead. The list can be filtered by open, closed, resolved or all. |
| Wagering | Bet a whole number of sisu on YES or NO. The form shows the potential payout live. Balances can never go negative. |
| Live updates | The server pushes every new prediction, wager, close and resolution over SSE (`/api/live`). Odds bars update without a reload. |
| Resolution | The creator or an admin resolves a closed prediction. Payouts are made in the same transaction. A prediction can't be resolved twice. |
| Profile | Shows balance, wagers with potential or actual payout, and created predictions. Your own profile also shows your transaction history. |
| Leaderboard | Top 50 users by balance. |

## Design

**Pricing: parimutuel pool.** Each wager goes into the YES pool or the NO pool. P(YES) is
`yesPool / (yesPool + noPool)`; an empty market shows 50 %. On resolution the whole pool is split
among the winning wagers in proportion to their stakes. Payouts are floored, and the leftover
rounding sisu goes to the largest winning stake, so the pool is always paid out exactly. If nobody
backed the winning side, every wager is refunded. See `backend/.../domain/Parimutuel.java`.

**Consistency.** A wager does four writes in one MongoDB multi-document transaction:
1. A conditional debit (`balance >= amount`).
2. A conditional pool increment (status is OPEN and `closesAt > now`).
3. The wager record.
4. The transaction record.

Concurrent wagers therefore can't overdraw a balance or land after closing. Resolution flips the
status with a conditional update in the same transaction as the payouts, so it is idempotent.

**Closing.** Reads and wagers compare against `closesAt` directly, so a prediction stops taking
wagers exactly on time. A scheduler also persists the CLOSED status every 30 s and broadcasts it.

### Layout

```
backend/   Quarkus 3 · MongoDB sync driver (no Panache) · SmallRye JWT · SSE (Mutiny)
  domain/    pure logic: Parimutuel pricing, LoginThrottle, enums (unit tested)
  store/     collections, indexes, transaction helper, document <-> view mapping
  service/   auth, predictions + resolution, wagers, users, live feed
  api/       REST resources, request/response records, error mappers ({code, message})
frontend/  React 19 · React Router 7 · Vite · TypeScript · plain CSS
  src/i18n/fi.ts   every UI string (Finnish); API error codes map to Finnish messages here
  src/live.ts      one shared EventSource, used through useLivePredictions()
```

## Not done yet

- Account deletion (GDPR).
- HTTPS termination. Locally it's HTTP; put TLS in front of nginx for a real deployment.
- Integration tests against MongoDB, frontend tests, linters and formatters, CI.
- The JWT is stored in `localStorage`, which keeps the app CSRF-free but is readable by any XSS. An
  httpOnly cookie would be safer.
