# Quickstart: Validating Sisu Prediction Market

Proves the feature works end-to-end against the contracts in `contracts/` and the data model in
`data-model.md`. Run after implementation, or incrementally per user story as it's built.

## Prerequisites

- Docker (for `docker-compose.yml`: MongoDB single-node replica set + backend + frontend)
- A way to issue HTTP requests (`curl` examples below) and a browser for the SPA/SSE checks

## 1. Start the stack

```bash
docker compose up
```

Expected: MongoDB, the `sisu-backend` API (default `http://localhost:8080`), and the
`sisu-frontend` SPA (default `http://localhost:5173`) all start from this one command
(Constitution I — single-command local start).

## 2. Seed a resolver

FR-035 requires a dedicated resolver role that isn't self-service. For local validation, seed one
directly (exact mechanism — a startup script or an admin CLI — is a task-level decision; any
approach that leaves one `RESOLVER`-role user in MongoDB satisfies this step).

## 3. User Story 1 — register, see starting balance

```bash
curl -i -c cookies.txt -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"correct horse battery staple"}'
```

Expected: `201`, JSON body includes `balanceSisu` equal to the configured starting grant (> 0), and
`cookies.txt` now holds a session cookie. Repeating the same username returns `409` (FR-001).

```bash
curl -i -b cookies.txt http://localhost:8080/api/auth/login \
  -X POST -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"wrong password"}'
```

Expected: `401` with a single generic error (FR-003) — not "wrong password" specifically. Repeat 5
times and a 6th attempt within 15 minutes returns `429` (FR-037).

## 4. User Story 2 — browse without logging in

```bash
curl -i http://localhost:8080/api/predictions
```

Expected: `200` with an array, no cookie required (FR-016). Seed at least one prediction first if
the list is empty (see §5).

## 5. User Story 4 — create a prediction

```bash
curl -i -b cookies.txt -X POST http://localhost:8080/api/predictions \
  -H 'Content-Type: application/json' \
  -d '{
    "question": "Sataako Helsingissä huomenna?",
    "description": "Kysymys huomisen säästä.",
    "resolutionCriteria": "Ilmatieteen laitoksen virallinen havainto klo 23:59.",
    "closingAt": "2026-09-24T20:00:00Z"
  }'
```

Expected: `201` with `status: "OPEN"`. Submitting an empty `question` or a past `closingAt` returns
`422` (FR-013, FR-014).

## 6. User Story 3 — wager, and watch it update live

1. Open `http://localhost:5173` in a browser, log in as `alice`, open the prediction created above.
2. In a second terminal, place a wager as `alice`:

   ```bash
   curl -i -b cookies.txt -X POST \
     http://localhost:8080/api/predictions/<id>/wagers \
     -H 'Content-Type: application/json' \
     -d '{"outcome":"YES","amountSisu":100}'
   ```

3. Expected in the browser tab (no manual refresh): the balance badge and the prediction's odds bar
   update within ~1 second (SC-002), via the `balance-updated` / `odds-updated` SSE events in
   `contracts/realtime.md`.
4. Repeat the wager with an amount greater than the current balance → expect `422` and the balance
   unchanged (FR-020).

## 7. User Story 5 — close and resolve

1. Create a prediction with `closingAt` a few seconds in the future; wait for it to pass.
2. Confirm wagering is now rejected: `POST .../wagers` → `409` (FR-025).
3. Log in as the seeded resolver and resolve it:

   ```bash
   curl -i -b resolver_cookies.txt -X POST \
     http://localhost:8080/api/predictions/<id>/resolve \
     -H 'Content-Type: application/json' \
     -d '{"outcome":"YES"}'
   ```

4. Expected: `200`, `status: "RESOLVED"`, `winningOutcome: "YES"`; `alice`'s balance reflects the
   payout, and `GET /api/users/me/transactions` shows a `PAYOUT` entry (FR-028, SC-006).
5. Repeating the resolve call → `409` (FR-027, "cannot resolve twice").

## 8. User Story 6 — leaderboard, private profile, history

```bash
curl -i http://localhost:8080/api/leaderboard          # public, no cookie needed
curl -i -b cookies.txt http://localhost:8080/api/users/me   # alice's own profile
```

Expected: leaderboard works without a cookie; `/users/me` requires one. There is no endpoint to
fetch another user's profile/wager-history — only the leaderboard's rank + balance are public for
other users (Clarifications session 2026-09-23).

## 9. Account deletion with open positions (FR-036)

1. As a user with an open wager and an unresolved created prediction, call
   `DELETE /api/users/me`.
2. Expected: `204`; the wager's status becomes `REFUNDED`; the created prediction's status becomes
   `CANCELLED` and every wager placed on it (by any user) is refunded.

## Done

All eight steps passing is the exit criterion for this feature's `/speckit-tasks` → implementation
cycle to be considered functionally complete against `spec.md`.
