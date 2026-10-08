# Sisu Market

A Finnish prediction market in the spirit of Polymarket/Kalshi, played with **Sisu**, a virtual currency with no real-world value.

**Current scope:** user registration and login, a Sisu balance (1 000 Sisu on sign-up) with full transaction history, a public page listing predictions with category and status filters, creating new yes/no predictions, and wagering Sisu on YES or NO (logged-in users). Settling predictions and paying out winners isn't implemented yet.

## Run it

```bash
docker compose up --build
```

| What        | URL                                   |
|-------------|---------------------------------------|
| Web app     | http://localhost:3100                 |
| API direct  | http://localhost:8180/api/predictions |
| Health      | http://localhost:8180/q/health        |

MongoDB runs as a single-node replica set because transactions need one. The healthcheck initialises it automatically. On first start, eight Finnish-themed demo predictions are added. Data persists in the `mongo-data` volume. To wipe it, run `docker compose down -v`.

## Architecture

```
browser ──► nginx (frontend container, :3100)
              ├─ /        → React SPA (static build)
              └─ /api/*   → Quarkus backend (:8080) ──► MongoDB
```

nginx serves the SPA and proxies `/api`, so the browser only ever talks to one origin. That means no CORS in normal use.

### Backend (`backend/`): Quarkus 3, Java 21

Packages are organised by feature:

- `auth`: `POST /api/auth/register` and `POST /api/auth/login`. Both return `{ token, user }`. Passwords are hashed with bcrypt. Tokens are RS256 JWTs (SmallRye JWT) and are valid for 24 h.
- `user`: `GET /api/users/me` and `GET /api/users/me/transactions?limit=20&before=<cursor>` (both require `Authorization: Bearer <token>`). History comes newest first. Pass the response's `nextCursor` as `before` to page back.
- `wager`: `POST /api/predictions/{id}/wagers` with body `{ "side": "YES" | "NO", "amount": <positive whole number> }` (login required). It returns the wager, the user's new balance and the prediction with updated odds. A single transaction adds the stake to the chosen pool and debits the user's balance. The pool update only matches a prediction that is `OPEN` and not past `closesAt`, and the debit fails if it would overdraw the balance. If either check fails, nothing changes. Errors: `400` for an invalid amount or side, `404` for an unknown prediction, `409` if the prediction is closed or the balance is too low.
- `wallet`: **all balance changes go through `WalletService`**. It updates `users.balance` and appends an immutable entry to the `ledger` collection (type, signed amount, balance after, description) in one MongoDB transaction. Debits that would push a balance below zero are rejected atomically. As a result, every user's balance always equals the sum of their ledger entries. Registration creates the account and its `SIGNUP_BONUS` entry in the same transaction. On startup, accounts created before the ledger existed get a one-time `OPENING_BALANCE` entry.
- `prediction`: `GET /api/predictions?category=&status=` and `GET /api/predictions/{id}`. Both are public. If an `OPEN` prediction's close time has passed, the API reports it as `CLOSED`.
  - `POST /api/predictions` (login required) creates a prediction from `question` (10–200 chars), `description`, `resolutionCriteria` (10–1000 chars), `category` and `closesAt`. `closesAt` must be an ISO-8601 date-time with an offset, e.g. `2027-01-01T12:00:00Z`. It must also be at least 1 hour and at most 5 years in the future. The server enforces these rules, and the form checks the same rules before submitting.
- `common`: error mapping. Every error has the form `{ "status": 409, "message": "..." }`.

Persistence uses MongoDB Panache repositories. Unique indexes on the lower-cased username and on the email enforce uniqueness, so `Matti` and `matti` can't both register. Indexes are created at startup.

The displayed **Yes %** comes from the ratio of Sisu staked on YES vs NO (`yesPool / (yesPool + noPool)`). For now, the pools come from the seed data.

### Frontend (`frontend/`): React 19, Vite, TypeScript, React Router

- `api.ts`: typed fetch wrapper. It attaches the token and turns error bodies into readable messages.
- `auth.tsx`: auth context. The token lives in `localStorage` and is checked with `/api/users/me` on page load.
- Pages: `/` (predictions), `/wallet` (balance and transaction history), `/predictions/new` (create; sends you to login first if needed), `/login`, `/register`.

## Local development without Docker

```bash
# backend: Quarkus Dev Services starts a throwaway MongoDB in Docker automatically
cd backend && mvn quarkus:dev            # http://localhost:8080

# frontend: Vite proxies /api to :8080
cd frontend && npm install && npm run dev  # http://localhost:5173
```

Tests: `cd backend && mvn test`. They cover the auth flow and the prediction listing against a real MongoDB through Dev Services, so Docker must be running.

## Security notes

- The JWT keypair in `backend/src/main/resources` is **for local development only**. For any shared deployment, set `JWT_PUBLIC_KEY_LOCATION` and `JWT_PRIVATE_KEY_LOCATION` to point at your own keys.
- Failed logins return the same message and take about the same time whether or not the username exists.
