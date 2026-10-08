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

To make someone an admin who can resolve any prediction, run for example `SISU_ADMIN_USERNAMES=matti_v docker compose up -d`. Without an admin, creators can still resolve their own predictions.

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
- `wager`:
  - `GET /api/predictions/{id}/quote?side=YES&amount=100` is public. It returns what that wager would get right now: shares, `payoutIfWin`, average price, and the odds before and after.
  - `POST /api/predictions/{id}/wagers` requires login. The body is `{ "side", "amount", "minPayout"?, "requestId"? }`. It returns the wager, the user's new balance and the updated prediction.
  - A single MongoDB transaction does all of the following, so any failure leaves none of it behind:
    - prices the wager against the current pool;
    - updates the pool and `totalWagered` with a compare-and-set that also re-checks the prediction is open;
    - debits the user's balance and adds a ledger entry;
    - records the wager.
  - `minPayout` rejects the wager (409) if the odds moved and the payout would be lower than the one the user was shown.
  - `requestId` is an idempotency key backed by a unique index. Resending the same request returns the original wager (200) and never places a second one.
  - Errors: `400` for invalid input, `404` for an unknown prediction, `409` if the prediction is closed, the balance is too low or the odds moved.
- `resolution`:
  - **Closing:** wagering stops exactly at `closesAt`, because the wager transaction itself checks it. A scheduler (`sisu.close-check-interval`, 5 s) also flips expired predictions to `CLOSED` and pushes a live update.
  - **Resolving:** `POST /api/predictions/{id}/resolve` with `{ "outcome": "YES" | "NO" }`. Only closed predictions can be resolved.
  - **Who may resolve:** admins (usernames in `SISU_ADMIN_USERNAMES`) can resolve any prediction. The creator can resolve their own, unless they've wagered on it.
  - **One transaction:** a conditional `CLOSED → RESOLVED` update, then each winning wager is paid its locked `payoutIfWin` (with a `PAYOUT` ledger entry), and every wager is marked `WON` or `LOST`. If any step fails, nobody is paid.
  - **Never twice:** a second resolution, including a simultaneous one, cannot match the status filter, so it returns `409`.
  - **Limit:** settlement runs in a single MongoDB transaction, which suits this app's scale. A market with tens of thousands of wagers would need batched settlement.
- `profile`:
  - `GET /api/profiles/{username}` returns a public profile: balance, rank, join date, number of predictions created and admin flag.
  - `GET /api/profiles/{username}/predictions` lists the predictions that user created.
  - `GET /api/users/me/wagers` is private and paged. It returns your wagers with the prediction question, status and outcome.
  - `GET /api/leaderboard?limit=25` ranks users by balance. Equal balances share a rank. A logged-in caller outside the top list also gets their own row as `me`.
- `live`: `GET /api/events` is a Server-Sent Events stream.
  - Everyone receives `prediction.created` and `prediction.updated`, with the full prediction including new odds and total wagered.
  - A request with a bearer token also receives that user's own `wallet.transaction` events (the ledger entry, the new balance and `balanceVersion`).
  - The first event is always `ready`, and clients reload their data when they get it, so anything missed while disconnected is recovered.
  - A comment heartbeat is sent every 20 s.
  - Events are published only **after the transaction commits**, so rolled-back or retried attempts never reach clients.
  - Predictions carry a `version` and users a `balanceVersion`, both increasing with every change. Clients keep the highest version they've seen, because events can arrive out of order.
  - The hub is in-process. Running several backend instances would need a shared event source such as MongoDB change streams.
- `wallet`: **all balance changes go through `WalletService`**. It updates `users.balance` and appends an immutable entry to the `ledger` collection (type, signed amount, balance after, description) in one MongoDB transaction. Debits that would push a balance below zero are rejected atomically. As a result, every user's balance always equals the sum of their ledger entries. Registration creates the account and its `SIGNUP_BONUS` entry in the same transaction. On startup, accounts created before the ledger existed get a one-time `OPENING_BALANCE` entry.
- `prediction`: `GET /api/predictions?category=&status=` and `GET /api/predictions/{id}`. Both are public. If an `OPEN` prediction's close time has passed, the API reports it as `CLOSED`.
  - `POST /api/predictions` (login required) creates a prediction from `question` (10–200 chars), `description`, `resolutionCriteria` (10–1000 chars), `category` and `closesAt`. `closesAt` must be an ISO-8601 date-time with an offset, e.g. `2027-01-01T12:00:00Z`. It must also be at least 1 hour and at most 5 years in the future. The server enforces these rules, and the form checks the same rules before submitting.
- `common`: error mapping. Every error has the form `{ "status": 409, "message": "..." }`.

Persistence uses MongoDB Panache repositories. Unique indexes on the lower-cased username and on the email enforce uniqueness, so `Matti` and `matti` can't both register. Indexes are created at startup.

### Pricing and payouts

Every prediction uses the same **constant-product market maker** ([`Cpmm`](backend/src/main/java/fi/sisu/market/pricing/Cpmm.java)):

- Each prediction holds a pool of YES and NO shares, with `yes × no = k`. **One share pays exactly 1 Sisu if its side wins.**
- YES probability, which is also the price of a YES share, is `no / (yes + no)`. NO is `1 − that`.
- Wagering M Sisu on YES works in two steps. First, the M Sisu mint M YES and M NO shares into the pool. Then the bettor takes out YES shares until `yes × no = k` again. NO works the same way in reverse. This moves the price toward the side that was bought.
- The **payout is locked at placement**: `floor(shares)` Sisu if the chosen side wins, otherwise 0. The quote shows that exact number.
- All predictions have the same depth, `k = liquidity²` (`sisu.market.liquidity`, default 1000). Seeded predictions differ only in their starting probability. User-created ones start at 50%.
- `totalWagered` counts real wagers only.
- Solvency: each staked Sisu mints exactly one YES/NO pair. So the shares of the winning side, and therefore the payouts, can never exceed the stakes plus the market maker's starting liquidity.
- Arithmetic is decimal (Decimal128), and rounding favours the market maker. A quote and the wager placed from it therefore give identical results.

Pricing was introduced after wagering existed. On startup, [`PricingMigration`](backend/src/main/java/fi/sisu/market/pricing/PricingMigration.java) refunds wagers placed before payouts were defined, each in its own transaction. It then gives old predictions a pool at the probability their seed data implied. It is idempotent.

### Frontend (`frontend/`): React 19, Vite, TypeScript, React Router

- `api.ts`: typed fetch wrapper. It attaches the token and turns error bodies into readable messages.
- `live.tsx`: a single SSE connection over `fetch` (needed to send the Authorization header). It reconnects with backoff, uses a watchdog for silent connections, and lets components subscribe with `useLiveEvents`. The predictions list, the wallet page and the header balance merge incoming events by version.
- `auth.tsx`: auth context. The token lives in `localStorage` and is checked with `/api/users/me` on page load.
- Pages: `/` (predictions), `/users/:username` (profile; your own adds wager and transaction history), `/leaderboard`, `/wallet` (redirects to your transactions), `/predictions/new` (create; sends you to login first if needed), `/login`, `/register`.

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
