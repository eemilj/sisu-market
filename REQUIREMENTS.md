# Requirements

## Functional Requirements

### Accounts and authentication

- System MUST allow a visitor to register with a unique username and a password.
- System MUST allow a registered user to log in with username and password.
- System MUST reject login attempts with invalid credentials and show an error message.
- System MUST allow a logged-in user to log out.
- System MUST store passwords only in hashed form.
- System MUST require authentication for creating predictions and placing wagers; browsing MAY be public.

### Sisu balance

- System MUST grant each new user a starting balance of sisu.
- System MUST show the user's current sisu balance at all times while logged in.
- System MUST prevent a user's balance from going below zero.
- System MUST record every balance change as a transaction (wager, payout, refund, starting grant) with timestamp and reason.
- System MUST let a user view their transaction history.

### Creating predictions

- System MUST allow a logged-in user to create a prediction with a question, description, resolution criteria, and closing date.
- System MUST support yes/no predictions.
- System MUST reject predictions whose closing date is in the past.
- System MUST validate that question and resolution criteria are non-empty.
- System MUST record the creator of each prediction.

### Browsing predictions

- System MUST list open predictions with question, current odds, total sisu wagered, and closing date.
- System MUST show a detail view for each prediction including its full description, resolution criteria, and current odds.
- System MUST let users filter predictions by status (open, closed, resolved).

### Wagering

- System MUST allow a logged-in user to wager a positive whole amount of sisu on an outcome of an open prediction.
- System MUST reject wagers exceeding the user's balance.
- System MUST reject wagers on predictions that are closed or resolved.
- System MUST deduct the wager from the user's balance atomically with recording the wager.
- System MUST update the prediction's odds after each wager.
- System MUST calculate odds and payouts using a single defined pricing model.
- System MUST let a user see their open wagers and potential payout.

### Closing and resolution

- System MUST automatically stop accepting wagers once the closing date passes.
- System MUST allow an authorized user to resolve a closed prediction to one outcome.
- System MUST pay out winnings to all winning wagers when a prediction resolves.
- System MUST prevent a prediction from being resolved more than once.

### Leaderboard and profile

- System MUST show a leaderboard ranking users by sisu balance.
- System MUST show a user profile with balance, wager history, and created predictions.

### Localization

- System MUST present the UI in Finnish.

---

## Non-Functional Requirements

### Performance

- System MUST respond to 95% of API requests within 500 ms under normal load.
- System MUST support at least 100 concurrent users without exceeding the response time target.
- System MUST reflect updated odds to the placing user within 1 second of a successful wager.
- Prediction list pages MUST load within 2 seconds on a typical broadband connection.

### Data integrity and consistency

- Balance updates and wager records MUST be written in a single atomic operation (MongoDB multi-document transaction), so no partial state can exist.
- System MUST prevent double-spending when a user submits concurrent wagers.
- Payout and refund processing MUST be idempotent, so a retried resolution never pays twice.
- The sum of all sisu in the system MUST be explainable by the transaction log at any time.
- Sisu amounts MUST be stored as integers to avoid floating point errors.

### Security

- Passwords MUST be hashed with a modern adaptive algorithm (bcrypt or Argon2).
- All client-server communication MUST use HTTPS.
- Authenticated sessions MUST use a single mechanism (JWT or server session) with a defined expiry. **[NEEDS CLARIFICATION: JWT or session, and expiry length]**
- System MUST validate all input on the server, regardless of client-side validation.
- System MUST protect against NoSQL injection, XSS, and CSRF.
- System MUST rate limit login attempts per username and IP.
- Error responses MUST NOT expose stack traces or internal details.
- Secrets (database credentials, signing keys) MUST NOT be stored in the repository.

### Reliability and availability

- Closing predictions at their closing date MUST happen within 1 minute of that time, even after a server restart.
- System MUST fail fast with a clear error when a precondition is not met (insufficient balance, closed prediction, invalid input).
- System MUST log all resolutions, cancellations, and failed transactions with enough context to trace them.

### Testability and quality

- All business logic MUST be covered by unit tests with minimum line coverage of 80%.
- Pricing and payout calculations MUST have unit tests covering edge cases (no wagers on winning side, single wager, cancellation).
- Unit tests MUST run without external services (database and time mocked or injected).
- The full test suite MUST run in CI on every pull request and block merging on failure.
- Business logic MUST be separated from controllers and persistence so it can be tested in isolation.

### Maintainability

- Each feature MUST have a spec-kit `spec.md` and `plan.md` before implementation begins. *(For the Specification Driven Development run, version. Not in the vibe-code run.)*
- Code MUST pass linter and formatter configuration for both frontend and backend.
- The REST API MUST be documented with an OpenAPI specification.
- The project MUST start locally with a single command (e.g. `docker compose up`).

### Usability and accessibility

- UI text MUST be in Finnish, with all strings kept in a localization file rather than hardcoded.
- UI MUST be usable on both desktop and mobile screen widths.
- UI MUST meet WCAG 2.1 AA for contrast, keyboard navigation, and form labels.
- Error messages MUST explain what went wrong and how to fix it.

### Compliance

- System MUST clearly state that sisu has no monetary value and cannot be exchanged for money, keeping the platform outside Finnish gambling regulation.
- System MUST collect only the personal data needed (username, password hash) and allow a user to delete their account, in line with GDPR.
