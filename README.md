# sisu-market

Faux Finnish based prediction market with fake money (sisu "points").

Users bet a virtual currency called **sisu** on yes/no questions ("predictions") created by other users. Sisu has no monetary value and can't be bought or cashed out. The interface is in Finnish.

## Features

- **Accounts:** register, log in and log out. Passwords are hashed, failed logins are rate limited, and the session is a JWT in an httpOnly cookie.
- **Sisu balance:** every new user starts with 1000 sisu. The balance can't go negative, and every change is recorded in a transaction history.
- **Predictions:** anyone can browse and filter predictions (open, closed, resolved, cancelled). Logged-in users can create them, within length limits (question 200, description 2000, resolution criteria 1000 characters).
- **Wagering:** logged-in users bet on Yes or No. Odds are pari-mutuel, so they move as sisu is wagered. Balances and odds update live over Server-Sent Events.
- **Closing and resolution:** predictions close automatically at their closing time, and a user with the resolver role resolves them and pays out the winners.
- **Leaderboard and profile:** a public leaderboard, plus a profile with open wagers and transaction history.

## Tech stack

| Part     | Technology                                                                       |
|----------|----------------------------------------------------------------------------------|
| Backend  | Java 25, Quarkus 3 (REST, scheduler, SmallRye JWT, OpenAPI), Maven               |
| Database | MongoDB 7 (single-node replica set, needed for transactions)                     |
| Frontend | React, TypeScript, Vite, Tailwind CSS, TanStack Query                            |
| Tooling  | Docker Compose, Checkstyle, Spotless, JaCoCo (80% coverage gate), Vitest, ESLint |

## Running the project

### Prerequisites

- Docker with Docker Compose

### Start everything

```bash
docker compose up --build
```

This starts MongoDB, generates a JWT signing key pair once into a volume, and starts the backend and frontend.

| What       | URL                                |
|------------|------------------------------------|
| Web app    | http://localhost:5173              |
| API        | http://localhost:8080/api          |
| Swagger UI | http://localhost:8080/q/swagger-ui |

Open the web app, register a user and you will get 1000 sisu to wager.

To stop the stack, press `Ctrl+C` or run `docker compose down`. Data stays in the `mongo-data` volume. To wipe it, run `docker compose down -v`.

After code changes, rebuild with `docker compose up --build`.

### The resolver account

Only users with the `RESOLVER` role can resolve a closed prediction. The role can't be obtained through registration. Instead, the backend creates one resolver account at startup from two environment variables:

| Variable                 | Default    | Notes                                                    |
|--------------------------|------------|----------------------------------------------------------|
| `SISU_RESOLVER_USERNAME` | `resolver` | Username of the resolver account                         |
| `SISU_RESOLVER_PASSWORD` | none       | At least 8 characters. No resolver is created without it |

Set them in the shell, or in an untracked `.env` file next to `docker-compose.yml`, before starting:

```bash
SISU_RESOLVER_PASSWORD='choose-a-password' docker compose up --build
```

Then log in as `resolver` with that password. The account gets the normal starting balance, and a closed prediction shows a "Resolve" action.

- If the password is missing or invalid, the app still starts but logs a warning, and no one can resolve predictions.
- Restarting is safe. An existing resolver is left unchanged, and the password and balance are not reset.
- If the configured username already belongs to a regular user, that user is promoted to resolver and keeps their password and balance.
- There is no default password, so no credentials are committed to the repo.

## Development

Run the backend and frontend outside Docker. The backend needs Docker anyway, because in dev mode Quarkus starts a throwaway MongoDB through Dev Services.

### Backend

```bash
cd sisu-backend
./mvnw quarkus:dev      # API on http://localhost:8080, dev UI on /q/dev
./mvnw verify           # tests, Checkstyle, Spotless and the coverage gate
./mvnw spotless:apply   # auto-format
```

On Windows use `mvnw.cmd`, or `sh ./mvnw` if the executable bit is missing. A JDK 25 is required.

### Frontend

```bash
cd sisu-frontend
npm install
npm run dev            # Vite dev server
npm test               # Vitest
npm run lint
npm run typecheck
npm run build
```

### Using the API directly

```bash
curl -i -c cookies.txt -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"correct horse battery staple"}'

curl http://localhost:8080/api/predictions
```

`specs/001-sisu-prediction-market/quickstart.md` has an end-to-end walkthrough of every user story.

## Project layout

```
sisu-backend/    Quarkus API, organised by feature (auth, user, prediction, wager, transaction, realtime)
sisu-frontend/   React single-page app, served by nginx in Docker
specs/           Spec Kit artifacts: spec, plan, data model, API contracts, tasks
.specify/        Spec Kit templates, scripts and the project constitution
docker-compose.yml
```

## Spec-driven development

The project was built with [GitHub Spec Kit](https://github.com/github/spec-kit). The principles are in `.specify/memory/constitution.md`. For each feature, `specs/<feature>/` holds `spec.md` (what and why), `plan.md` and `research.md` (how), `data-model.md`, `contracts/` (OpenAPI and the SSE event contract) and `tasks.md` (the ordered work, tests first).

## License

See [LICENSE](LICENSE).
