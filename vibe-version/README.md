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

MongoDB runs as a single-node replica set because transactions need one. The healthcheck initialises it automatically. On first start, eight Finnish demo predictions are added. Databases seeded by older versions have them translated in place. Data persists in the `mongo-data` volume. To wipe it, run `docker compose down -v`.

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
- `common`: error mapping. Every error has the same JSON shape:
  - `status`, plus a stable `code` (see [`ErrorCode`](backend/src/main/java/fi/sisu/market/common/ErrorCode.java)) and optional `params`, e.g. `{"code": "INSUFFICIENT_BALANCE", "params": {"balance": 750, "required": 900}}`.
  - Validation failures add `fieldErrors`, a list of `{field, rule, params}` objects, e.g. `{"field": "password", "rule": "Size", "params": {"min": 8, "max": 72}}`.
  - `message` is English, for developers and logs. The UI builds its own Finnish text from `code`, `params` and `fieldErrors`.

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

- `api.ts`: typed fetch wrapper. It attaches the token and turns every failure into an `ApiError` whose `message` is Finnish and tells the user what to do. The message text comes from [`errors.ts`](frontend/src/errors.ts).
- **Language:** the UI is entirely in Finnish. Labels and number/date formatting (`1 000 Sisua`, `42,4 %`, `9.10.2026 klo 14.05`) live in [`format.ts`](frontend/src/format.ts).
- **Forms:** forms use [`forms.tsx`](frontend/src/forms.tsx).
  - Each problem is shown under its own field and linked with `aria-describedby`.
  - Errors appear when the user leaves a field or submits, and focus moves to the first problem.
  - Server field errors are mapped onto the matching inputs.
- `live.tsx`: a single SSE connection over `fetch` (needed to send the Authorization header). It reconnects with backoff, uses a watchdog for silent connections, and lets components subscribe with `useLiveEvents`.
  - **Connection limit:** browsers allow only 6 connections per host over plain HTTP/1.1. A tab hidden for more than 10 s therefore releases its stream, and a page being left closes its stream even if the browser keeps the page in its back/forward cache. The tab reconnects and reloads its data when shown again.
  - **Updates keep cards in place:** live updates change cards already on screen but never remove them. The current filter applies again on the next load. The predictions list, the wallet page and the header balance merge incoming events by version.
- `auth.tsx`: auth context. The token lives in `localStorage` and is checked with `/api/users/me` on page load.
- Pages: `/` (predictions), `/users/:username` (profile; your own adds wager and transaction history), `/leaderboard`, `/wallet` (redirects to your transactions), `/predictions/new` (create; sends you to login first if needed), `/login`, `/register`.

## Local development without Docker

```bash
# backend: Quarkus Dev Services starts a throwaway MongoDB in Docker automatically
cd backend && mvn quarkus:dev            # http://localhost:8080

# frontend: Vite proxies /api to :8080
cd frontend && npm install && npm run dev  # http://localhost:5173
```

Tests: run `cd backend && mvn test`, which runs 146 tests. Docker must be running.

- **Unit tests** need no app or database. They cover:
  - pricing, including edge cases and a solvency check over 2,000 random trades;
  - settling payouts;
  - the password policy and hashing, including the 72-byte regression;
  - the rate limiter, run on a controllable clock;
  - the login and registration limits;
  - closing-date boundaries;
  - wager amount parsing;
  - every request's validation rules.
- **Integration tests** run against a real MongoDB through Dev Services. They cover:
  - the HTTP flows;
  - concurrency (simultaneous wagers and resolutions);
  - transaction rollback, using injected failures;
  - live events;
  - rate limiting end to end;
  - hidden internal errors.

## Security notes

- **Passwords:**
  - Passwords are stored only as salted bcrypt hashes. The cost is `sisu.bcrypt-cost`, 12 by default; tests use 4 for speed.
  - Hashes made with a lower cost are upgraded the next time the user logs in.
  - bcrypt silently ignores everything after 72 *bytes*. Passwords are therefore limited to 72 UTF-8 bytes, and anything longer never matches. `ä` counts as 2 bytes.
  - A password may not equal the username.
- **Login and registration limits:** the limits are in-memory, so they assume a single backend instance. Blocked requests get `429` with a `Retry-After` header, and are refused before any password hashing happens.

  | Limit | Default |
  |---|---|
  | Failed logins per account + client IP | 5 per 15 min |
  | Failed logins per client IP | 20 per 15 min |
  | Failed logins per account, from any IP | 50 per hour |
  | New accounts per client IP | 10 per hour |

  Unknown usernames and wrong passwords give the same response and take the same time.
- **Client IPs:** these come from `X-Forwarded-For`, which is trusted only from private and loopback proxy addresses. nginx overwrites the header, so clients can't fake it.
- **Errors:**
  - Unexpected failures return `500 {"code": "INTERNAL_ERROR", "params": {"errorId": ...}}`. The details are logged on the server under that ID and are never sent to the client.
  - Request bodies over 64 KB are rejected with `413`.
- **Validation:** every rule the forms check is also enforced by the API, and tests cover them.
  - Text is trimmed before length checks.
  - Control characters are rejected; questions must be a single line.
  - Emails need a domain with a TLD.
  - Wager amounts must be whole numbers from 1 to 10¹² Sisu.
  - Extra JSON fields (e.g. `balance`, `admin`) are ignored.
- **JWT signing keys:** the keypair in `backend/src/main/resources` is **for local development only**. For any shared deployment, set `JWT_PUBLIC_KEY_LOCATION` and `JWT_PRIVATE_KEY_LOCATION`.
- **Logout:** tokens are stateless and valid for 24 h, so logging out removes the token from the browser but doesn't revoke it on the server.
