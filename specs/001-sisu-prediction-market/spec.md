# Feature Specification: Sisu Prediction Market

**Feature Branch**: `001-sisu-prediction-market`

**Created**: 2026-09-23

**Status**: Draft

**Input**: User description: "Build Sisu Market, a play-money prediction market where users bet a
virtual currency called \"sisu\" on the outcome of yes/no questions (\"predictions\") created by
other users. The entire interface is in Finnish. Actors: Visitor (browse predictions and the
leaderboard without an account), Registered user (create predictions, place wagers, manage their
own profile once logged in), an authorized resolver (resolve a closed prediction to its final
outcome). Accounts: register with a unique username and password, log in, log out; invalid login
attempts show a clear error without revealing which field was wrong; passwords stored securely;
creating a prediction or placing a wager requires login, browsing stays public. Sisu balance: every
new user starts with a fixed amount of sisu; balance always visible while logged in; balance can
never go negative; every balance change (wager, payout, refund, initial grant) is recorded with a
timestamp and reason and reviewable as history. Creating predictions: a logged-in user creates a
yes/no prediction with a question, description, resolution criteria, and closing date; question and
resolution criteria can't be empty; closing date must be in the future; each prediction shows its
creator. Browsing predictions: anyone sees a list of open predictions with question, current odds,
total sisu wagered, and closing date; anyone can open a prediction for its full description,
resolution criteria, and current odds; predictions can be filtered by status (open, closed,
resolved). Wagering: a logged-in user wagers a positive whole number of sisu on one side of an open
prediction; a wager is rejected if it exceeds balance or the prediction is closed/resolved; placing
a wager reliably and atomically updates balance and odds together; a user sees their own open wagers
and potential payout. Closing and resolution: a prediction automatically stops accepting wagers once
its closing date passes; an authorized resolver settles a closed prediction to exactly one final
outcome, only once; on resolution every winning wager is paid out automatically. Leaderboard and
profile: a leaderboard ranks users by sisu balance; every user has a profile showing balance, wager
history, and predictions they've created. What 'good' looks like: odds and balances feel current,
with a user's own wager reflected within about a second; prediction lists and pages stay fast with
at least 100 people browsing and wagering at once; the product works on phone and desktop and is
usable via keyboard and screen reader; error messages explain what went wrong and how to fix it; it
is always clear sisu has no real-world monetary value and cannot be cashed out; a user can delete
their account and have their personal data removed. Out of scope: predictions with more than two
outcomes, buying additional sisu with real money, and social features like comments or following
other users."

**Note**: The text pasted into the triggering command was corrupted in transit (several lines were
cut off mid-word). This spec was written from the complete, uncorrupted version of the same
description that the user and assistant had already agreed on earlier in this conversation.

## Clarifications

### Session 2026-09-23

- Q: When a prediction gets cancelled because its creator deleted their account (FR-036), what
  status should it show as? → A: Add "cancelled" as a fourth, filterable prediction status,
  distinct from open/closed/resolved.
- Q: Can any logged-in user view another user's profile (balance, wager history, created
  predictions), or is a profile visible only to its owner? → A: A profile is private — only its
  owner can see their own balance, wager history, and created predictions there. (The leaderboard
  remains public and still shows every user's rank and balance, per FR-029.)
- Q: After how many failed login attempts should the system start blocking further tries for that
  username/IP, and for how long? → A: 5 failed attempts, then a 15-minute cooldown for that
  username/IP pair.
- Q: What's the concrete, measurable target for SC-003 ("prediction list and detail pages remain
  fully responsive" under load)? → A: Prediction list/detail pages load in ≤2 seconds with at least
  100 concurrent users browsing and wagering (matches the constitution's Performance Requirements
  principle).
- Q: When a wager is submitted right as a prediction's closing time arrives, should the system check
  the closing time directly at that moment, or is a periodic background check acceptable? → A:
  Check the closing time directly at the moment each wager is placed (zero gap); a separate
  background job still flips the prediction's visible status to closed within 1 minute (surviving a
  restart) so browsing/filtering stays accurate.
- Q: Should wagering also be rejected on a "cancelled" prediction, the same way it already is for
  closed or resolved ones? → A: Yes — FR-021 now reads "closed, resolved, or cancelled"; a
  cancelled prediction is terminal and already refunded, so a new wager on one would contradict
  FR-036.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create an account and see your sisu balance (Priority: P1)

A visitor registers with a username and password, is logged in, and immediately sees the starting
sisu balance granted to every new user. They can log out and log back in later and still see their
current balance.

**Why this priority**: Every other capability in the market — wagering, creating predictions,
appearing on the leaderboard — depends on having an account and a balance. Without this, there is no
product.

**Independent Test**: Can be fully tested by registering a new account and confirming a starting
balance is visible immediately, without any other feature existing yet.

**Acceptance Scenarios**:

1. **Given** a visitor on the registration screen, **When** they submit a unique username and a
   password, **Then** an account is created, they are logged in, and their starting sisu balance is
   shown.
2. **Given** a registered user, **When** they log in with correct credentials, **Then** they reach a
   logged-in view showing their current balance.
3. **Given** a registered user, **When** they log in with an incorrect username or password,
   **Then** they see a single clear error message that does not reveal which of the two was wrong.
4. **Given** a logged-in user, **When** they log out, **Then** they can browse but can no longer
   create predictions or place wagers until they log back in.
5. **Given** someone attempting to register, **When** they choose a username that is already taken,
   **Then** registration is rejected with a clear explanation.
6. **Given** a username/IP pair with 5 failed login attempts in a row, **When** a 6th attempt is
   made within the next 15 minutes, **Then** it is rejected with a message explaining the temporary
   cooldown, even if the credentials are correct.

---

### User Story 2 - Browse open predictions (Priority: P1)

Anyone, logged in or not, can see the list of open predictions with enough information to decide
whether to look closer, and can open one to read its full details and current odds.

**Why this priority**: Browsing is the entry point for every user, including visitors who haven't
registered yet, and is a prerequisite for wagering. It also must work without an account to let
prospective users evaluate the market before signing up.

**Independent Test**: Can be fully tested by viewing the prediction list and a prediction's detail
page as a signed-out visitor, independent of whether wagering or account creation works yet.

**Acceptance Scenarios**:

1. **Given** open predictions exist, **When** a visitor views the prediction list, **Then** each
   entry shows its question, current odds, total sisu wagered, and closing date, with no login
   required.
2. **Given** a prediction, **When** a user opens its detail view, **Then** they see its full
   description, resolution criteria, and current odds.
3. **Given** predictions in different states, **When** a user filters the list by open, closed,
   resolved, or cancelled, **Then** only matching predictions are shown.

---

### User Story 3 - Wager sisu on a prediction (Priority: P1)

A logged-in user places a wager of sisu on one side of an open prediction, sees their balance and
the prediction's odds update accordingly, and can review their own open wagers and potential payout
at any time.

**Why this priority**: This is the core interaction the entire product exists for — everything else
supports or follows from placing a wager.

**Independent Test**: Can be fully tested by having a logged-in user with a balance place a wager on
an existing open prediction and confirming the balance and odds update, independent of prediction
creation or resolution.

**Acceptance Scenarios**:

1. **Given** a logged-in user with a sufficient balance, **When** they wager a positive whole number
   of sisu on an outcome of an open prediction, **Then** their balance decreases by that amount, the
   wager is recorded, and the prediction's odds update to reflect it.
2. **Given** a logged-in user, **When** they try to wager more sisu than their current balance,
   **Then** the wager is rejected with a clear reason and their balance is unchanged.
3. **Given** a prediction that is closed or already resolved, **When** a user tries to wager on it,
   **Then** the wager is rejected.
4. **Given** a logged-in user with open wagers, **When** they view their wagers, **Then** they see
   each one's amount, chosen outcome, and potential payout.
5. **Given** a user submitting a wager of zero, a negative amount, or a non-whole amount, **When**
   they submit it, **Then** it is rejected with a clear reason.

---

### User Story 4 - Create a prediction (Priority: P2)

A logged-in user creates a new yes/no prediction by supplying a question, a description, the
criteria that will decide the outcome, and a closing date, so that others can wager on it.

**Why this priority**: New predictions are what keeps the market alive over time, but the market can
launch and be demonstrated with an initial set of predictions before any user-generated ones exist,
so this ranks below the core browse/wager loop.

**Independent Test**: Can be fully tested by having a logged-in user submit a new prediction and
confirming it appears in the open predictions list, independent of whether wagering exists yet.

**Acceptance Scenarios**:

1. **Given** a logged-in user, **When** they submit a question, description, resolution criteria,
   and a closing date in the future, **Then** a new open prediction is created, attributed to them,
   and appears in the prediction list.
2. **Given** a logged-in user, **When** they submit a prediction with an empty question or empty
   resolution criteria, **Then** creation is rejected with a clear error identifying the missing
   field.
3. **Given** a logged-in user, **When** they submit a closing date that is in the past, **Then**
   creation is rejected with a clear error.

---

### User Story 5 - Close and resolve predictions (Priority: P2)

A prediction stops accepting wagers automatically once its closing date passes. An authorized
resolver later settles it to exactly one outcome, and everyone who wagered on the winning side is
paid out automatically.

**Why this priority**: Resolution is what turns a wager into a win or a loss and completes the
core loop, but it naturally happens after predictions and wagers already exist, so it depends on
User Stories 3 and 4 being in place first.

**Independent Test**: Can be fully tested by advancing a prediction past its closing date, resolving
it to an outcome, and confirming winning wagers are paid out and the balance changes are recorded.

**Acceptance Scenarios**:

1. **Given** a prediction whose closing date has passed, **When** a user tries to place a wager on
   it, **Then** the wager is rejected because the prediction is no longer open.
2. **Given** a closed prediction, **When** an authorized resolver settles it to one outcome,
   **Then** every wager on the winning outcome is paid out automatically and the payouts are
   recorded as transactions.
3. **Given** a prediction that has already been resolved, **When** anyone attempts to resolve it
   again, **Then** the attempt is rejected and the original outcome is unchanged.
4. **Given** a resolved prediction where no one wagered on the winning outcome, **When** resolution
   completes, **Then** no payouts occur and the prediction is still marked resolved without error.

---

### User Story 6 - View leaderboard, profile, and transaction history (Priority: P3)

Anyone can see a leaderboard ranking users by sisu balance. A logged-in user can see their own
profile with balance, wager history, and created predictions, and can review every balance change
they've ever had.

**Why this priority**: These views add transparency and a sense of standing, but no one needs them
to place their first wager, so they can ship after the core loop.

**Independent Test**: Can be fully tested by viewing the leaderboard and a user's own profile and
transaction history after some wagers and balance changes already exist.

**Acceptance Scenarios**:

1. **Given** users with different balances, **When** anyone views the leaderboard, **Then** users
   are ranked from highest to lowest sisu balance.
2. **Given** a logged-in user, **When** they view their profile, **Then** they see their current
   balance, their wager history, and the predictions they've created.
3. **Given** a logged-in user, **When** they try to view another user's profile, **Then** they
   cannot see that user's wager history or created-predictions list, only the balance and rank
   already visible on the leaderboard.
4. **Given** a logged-in user with past balance changes, **When** they view their transaction
   history, **Then** every change (wager, payout, refund, starting grant) is listed with its
   timestamp and reason.

---

### Edge Cases

- What happens when a wager is submitted in the same instant a prediction's closing date passes? The
  wager MUST be rejected: the system checks the closing time directly at the moment the wager is
  placed (not just the prediction's cached status), so there is no gap in which a late wager can
  slip through. See FR-025.
- How does the system handle two wagers from the same user submitted at nearly the same time whose
  combined amount would exceed their balance? At most one of them may succeed; the balance must
  never go negative.
- What happens if someone attempts to resolve a prediction before its closing date has passed?
- What happens when a user with open wagers or an unresolved created prediction requests account
  deletion? See FR-036.
- How does the leaderboard handle two users with exactly equal balances?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow a visitor to register an account with a unique username and a
  password.
- **FR-002**: System MUST allow a registered user to log in with their username and password.
- **FR-003**: System MUST reject a login attempt with invalid credentials and show a single error
  message that does not reveal whether the username or the password was incorrect.
- **FR-004**: System MUST allow a logged-in user to log out.
- **FR-005**: System MUST store passwords only in a securely hashed form, never in plain text.
- **FR-006**: System MUST require a user to be logged in to create a prediction or place a wager,
  while keeping browsing of predictions and the leaderboard open to visitors.
- **FR-007**: System MUST grant each newly registered user a starting sisu balance.
- **FR-008**: System MUST show a logged-in user their current sisu balance at all times while they
  are using the product.
- **FR-009**: System MUST prevent any action from taking a user's balance below zero.
- **FR-010**: System MUST record every balance change (wager placed, payout received, refund,
  starting grant) as a transaction with a timestamp and a reason.
- **FR-011**: System MUST let a logged-in user view their own transaction history.
- **FR-012**: System MUST allow a logged-in user to create a yes/no prediction consisting of a
  question, a description, resolution criteria, and a closing date.
- **FR-013**: System MUST reject creating a prediction whose question or resolution criteria is
  empty.
- **FR-014**: System MUST reject creating a prediction whose closing date is not in the future.
- **FR-015**: System MUST record and display the creator of each prediction.
- **FR-016**: System MUST list open predictions, each showing its question, current odds, total
  sisu wagered, and closing date, visible to visitors and logged-in users alike.
- **FR-017**: System MUST provide a detail view for each prediction showing its full description,
  resolution criteria, and current odds.
- **FR-018**: System MUST let users filter predictions by status: open, closed, resolved, or
  cancelled.
- **FR-019**: System MUST allow a logged-in user to wager a positive whole number of sisu on one
  outcome of an open prediction.
- **FR-020**: System MUST reject a wager that exceeds the user's current balance.
- **FR-021**: System MUST reject a wager on a prediction that is closed, resolved, or cancelled.
- **FR-022**: System MUST update the user's balance and record the wager together as a single
  reliable operation, so a wager is never deducted without being recorded or recorded without being
  deducted.
- **FR-023**: System MUST recompute a prediction's displayed odds after each wager placed on it.
- **FR-024**: System MUST let a logged-in user see their own open wagers, including the amount
  wagered, the chosen outcome, and the potential payout for each.
- **FR-025**: System MUST reject any wager whose prediction's closing date/time has already passed,
  checked directly against the current time at the moment the wager is placed — not solely against
  the prediction's cached status — so there is no gap in which a late wager can succeed. System MUST
  also flip a prediction's visible status to closed within 1 minute of its closing time, and this
  MUST continue to happen correctly even after a server restart.
- **FR-026**: System MUST allow only an authorized resolver to settle a closed prediction to exactly
  one final outcome.
- **FR-027**: System MUST prevent a prediction from being resolved more than once.
- **FR-028**: System MUST automatically pay out every wager on the winning outcome when a
  prediction is resolved, recording each payout as a transaction.
- **FR-029**: System MUST show a leaderboard ranking all users by their sisu balance.
- **FR-030**: System MUST show a logged-in user their own profile, including balance, wager
  history, and the predictions they've created, and MUST NOT expose another user's wager history or
  created-predictions list to anyone but that user (only that user's balance and leaderboard rank
  remain publicly visible, per FR-029).
- **FR-031**: System MUST present all interface text in Finnish.
- **FR-032**: System MUST state, in the product itself, that sisu has no real-world monetary value
  and cannot be exchanged for money.
- **FR-033**: System MUST let a user request deletion of their account and removal of their
  personal data.
- **FR-034**: System MUST calculate a prediction's current odds as the proportional split of total
  sisu wagered on each outcome (pool-proportional / pari-mutuel), recalculated after every wager,
  and MUST calculate each winning wager's payout as its proportional share of the losing side's
  pool, in addition to returning its own stake.
- **FR-035**: System MUST restrict resolving a prediction to users holding a dedicated
  resolver/moderator role, distinct from being that prediction's creator or an ordinary logged-in
  user.
- **FR-036**: System MUST, when a user requests account deletion while holding open wagers or an
  unresolved prediction they created: (a) refund each of the user's own open wagers back to their
  balance as a recorded transaction before the account is removed, and (b) mark any prediction they
  created that is not yet resolved as "cancelled" and refund every wager placed on that prediction —
  by any user — back to that wager's owner.
- **FR-037**: System MUST block further login attempts for a username/IP pair for 15 minutes after
  5 consecutive failed attempts, even if a subsequent attempt uses the correct credentials.

### Key Entities

- **User**: A registered account — unique username, hashed password, current sisu balance, role
  (regular user or resolver), created predictions, and transaction history.
- **Prediction**: A yes/no question a user created — question text, description, resolution
  criteria, closing date, status (open, closed, resolved, or cancelled), the winning outcome once
  resolved, and the creator.
- **Wager**: A single bet a user places on a prediction — the amount of sisu, the chosen outcome,
  the placing user, the prediction it belongs to, and its resulting payout once resolved.
- **Transaction**: A single recorded balance change for a user — amount, direction (credit/debit),
  reason (starting grant, wager, payout, refund), and timestamp.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A new visitor can register, land on a logged-in view, and see their starting sisu
  balance in under 2 minutes.
- **SC-002**: 95% of users placing a wager see their updated balance and the prediction's updated
  odds within 1 second of submitting it.
- **SC-003**: The prediction list and detail pages load in 2 seconds or less with at least 100
  people browsing and wagering at the same time.
- **SC-004**: 100% of resolved predictions correctly pay out every winning wager exactly once, with
  zero predictions resolved more than once.
- **SC-005**: Every core action (register, browse, wager, create a prediction, view profile) can be
  completed on both a phone-sized screen and a desktop screen, and entirely via keyboard.
- **SC-006**: 100% of a user's displayed balance changes are matched by a corresponding entry in
  their transaction history, with zero discrepancies.
- **SC-007**: In usability testing, users who trigger a rejected action (over-balance wager, empty
  prediction field, past closing date, duplicate username) can state what went wrong and what to do
  differently without outside help, 100% of the time.
- **SC-008**: In usability testing, 100% of new users can correctly state, after browsing the
  product, that sisu cannot be exchanged for real money.

## Assumptions

- The starting sisu balance granted to new users is a single fixed amount defined by the product
  (exact number is a configuration detail, not a scope decision).
- "Normal load" for responsiveness (SC-002, SC-003) means up to 100 concurrent users browsing and
  wagering, matching the stated concurrency target.
- Deleting an account removes the user's personal data (username, password) but a de-identified
  record of their past *resolved* wagers and predictions may be retained where needed so other
  users' odds, payouts, and resolved-prediction history stay accurate; any *open* wagers or
  *unresolved* created predictions are refunded and cancelled per FR-036 before deletion completes.
- "Authorized resolver" is a dedicated role held by designated moderators/admins, separate from and
  not automatically granted to a prediction's creator (FR-035).
- No mobile app is assumed — "works on phone and desktop" refers to a responsive web experience.
