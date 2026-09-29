---

description: "Task list template for feature implementation"
---

# Tasks: Sisu Prediction Market

**Input**: Design documents from `/specs/001-sisu-prediction-market/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md (all present)

**Tests**: Included. The project constitution's Testing Standards principle is explicitly
NON-NEGOTIABLE (80% line coverage on business logic, explicit pricing/payout edge-case tests, full
suite in CI blocking merge), so test tasks are generated per user story rather than treated as
optional.

**Organization**: Tasks are grouped by user story (from spec.md, priority order P1 → P1 → P1 → P2 →
P2 → P3) to enable independent implementation and testing of each story, plus a small
non-story phase for account deletion (FR-033/FR-036), which depends on wagering and prediction
creation already existing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1–US6); Setup, Foundational, the account
  deletion phase, and Polish carry no story label
- Every task names its exact file path(s)

## Path Conventions (from plan.md)

- Backend: `sisu-backend/src/main/java/market/sisu/...`, tests under
  `sisu-backend/src/test/java/market/sisu/{unit,integration,contract}/...`
- Frontend: `sisu-frontend/src/...`
- Root: `docker-compose.yml`, `.github/workflows/ci.yml`

---

## Phase 1: Setup

**Purpose**: Project initialization and tooling

- [X] T001 Create the `sisu-backend` Quarkus 3.x + Java 25 Maven project skeleton with
  `quarkus-resteasy-reactive`, `quarkus-resteasy-reactive-jackson`, `quarkus-mongodb-panache`,
  `quarkus-smallrye-jwt`, `quarkus-smallrye-openapi`, and `quarkus-hibernate-validator` in
  `sisu-backend/pom.xml`
- [X] T002 [P] Create the `sisu-frontend` Vite + React 18 + TypeScript project skeleton with
  Tailwind CSS configured in `sisu-frontend/`
- [X] T003 [P] Configure backend Checkstyle + Spotless in `sisu-backend/pom.xml`
- [X] T004 [P] Configure frontend ESLint + Prettier in `sisu-frontend/.eslintrc.cjs` and
  `sisu-frontend/.prettierrc`
- [X] T005 [P] Configure the backend JaCoCo plugin with an 80% line-coverage gate on business-logic
  packages in `sisu-backend/pom.xml`
- [X] T006 Create the root `docker-compose.yml` starting a single-node MongoDB replica set,
  `sisu-backend`, and `sisu-frontend` with one command
- [X] T007 [P] Add a GitHub Actions CI workflow running the backend (`mvn test`) and frontend
  (`npm test`) suites on every pull request and blocking merge on failure, in
  `.github/workflows/ci.yml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared infrastructure and the four core entities every user story builds on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T008 Configure the MongoDB client and single-node replica set connection in
  `sisu-backend/src/main/resources/application.properties`
- [X] T009 [P] Implement the `Sisu` integer-money value type, the `Error` response schema, and
  global exception mappers in `sisu-backend/src/main/java/market/sisu/common/`
- [X] T010 [P] Create the `User` Panache entity and repository with a unique index on `username` in
  `sisu-backend/src/main/java/market/sisu/user/User.java` and `UserRepository.java`
- [X] T011 [P] Create the `Prediction` Panache entity and repository with an index on `status` in
  `sisu-backend/src/main/java/market/sisu/prediction/Prediction.java` and
  `PredictionRepository.java`
- [X] T012 [P] Create the `Wager` Panache entity and repository with an index on `userId` in
  `sisu-backend/src/main/java/market/sisu/wager/Wager.java` and `WagerRepository.java`
- [X] T013 [P] Create the append-only `Transaction` Panache entity and repository in
  `sisu-backend/src/main/java/market/sisu/transaction/Transaction.java` and
  `TransactionRepository.java`
- [X] T014 Implement a MongoDB `ClientSession` transaction helper enforcing the cross-entity
  invariants from `data-model.md` (atomic balance/wager/pool/ledger writes) in
  `sisu-backend/src/main/java/market/sisu/common/MongoTransactions.java` (depends on T008)
- [X] T015 [P] Configure JWT issuance/validation (SmallRye JWT) and the httpOnly/Secure/
  SameSite=Strict session-cookie filter in `sisu-backend/src/main/java/market/sisu/auth/JwtService.java`
  and `SessionCookieFilter.java`
- [X] T016 [P] Create a `login_attempts` collection with a 15-minute TTL index for rate limiting in
  `sisu-backend/src/main/java/market/sisu/auth/LoginAttempt.java` and `LoginAttemptRepository.java`
- [X] T017 Implement the SSE connection registry and event broadcaster
  (`balance-updated`, `odds-updated`, `prediction-status-changed`) per `contracts/realtime.md` in
  `sisu-backend/src/main/java/market/sisu/realtime/`
- [X] T018 [P] Configure `quarkus-smallrye-openapi` generation to match `contracts/openapi.yaml` in
  `sisu-backend/src/main/resources/application.properties`
- [X] T019 [P] Create a typed REST API client wrapper (fetch with credentials, error decoding) in
  `sisu-frontend/src/api/client.ts`
- [X] T020 [P] Create the Finnish string dictionary module in `sisu-frontend/src/i18n/strings.fi.ts`
- [X] T021 [P] Set up the React Router shell, TanStack Query provider, and base app layout in
  `sisu-frontend/src/App.tsx` and `sisu-frontend/src/main.tsx`
- [X] T022 [P] Implement the `useLiveUpdates` SSE hook (EventSource connection dispatching into the
  TanStack Query cache) per `contracts/realtime.md` in `sisu-frontend/src/hooks/useLiveUpdates.ts`

**Checkpoint**: Foundation ready — user story implementation can now begin.

---

## Phase 3: User Story 1 - Create an account and see your sisu balance (Priority: P1) 🎯 MVP

**Goal**: A visitor can register, is logged in with a starting sisu balance, can log out/back in,
and repeated failed logins are rate-limited.

**Independent Test**: Register a new account and confirm the starting balance is visible
immediately, independent of any other feature.

### Tests for User Story 1

- [X] T023 [P] [US1] Contract test for `POST /api/auth/register` (success, duplicate username,
  empty-field validation) in `sisu-backend/src/test/java/market/sisu/contract/AuthRegisterContractTest.java`
- [X] T024 [P] [US1] Contract test for `POST /api/auth/login` (success, single generic 401, 429
  after 5 attempts) in `sisu-backend/src/test/java/market/sisu/contract/AuthLoginContractTest.java`
- [X] T025 [P] [US1] Unit test for the starting-balance grant and username-uniqueness check in
  `sisu-backend/src/test/java/market/sisu/unit/UserServiceTest.java`
- [X] T026 [P] [US1] Unit test for the 5-attempts/15-minute login rate limit using an injected
  `Clock` in `sisu-backend/src/test/java/market/sisu/unit/LoginRateLimiterTest.java`

### Implementation for User Story 1

- [X] T027 [US1] Implement `UserService.register` (uniqueness check, password hashing, starting-grant
  transaction inserting `User` + `Transaction`) in
  `sisu-backend/src/main/java/market/sisu/user/UserService.java` (depends on T010, T013, T014)
- [X] T028 [US1] Implement `AuthService.login`/`logout` (credential check, JWT cookie issuance) in
  `sisu-backend/src/main/java/market/sisu/auth/AuthService.java` (depends on T010, T015)
- [X] T029 [US1] Implement `LoginRateLimiter` enforcing FR-037 in
  `sisu-backend/src/main/java/market/sisu/auth/LoginRateLimiter.java` (depends on T016)
- [X] T030 [US1] Implement `AuthResource` (`/api/auth/register`, `/api/auth/login`,
  `/api/auth/logout`) in `sisu-backend/src/main/java/market/sisu/auth/AuthResource.java` (depends
  on T027, T028, T029)
- [X] T031 [P] [US1] Build the Register page in `sisu-frontend/src/pages/Register.tsx`
- [X] T032 [P] [US1] Build the Login page in `sisu-frontend/src/pages/Login.tsx`
- [X] T033 [US1] Implement the `useAuth` hook (session state, current balance) in
  `sisu-frontend/src/hooks/useAuth.ts` (depends on T019, T030)
- [X] T034 [US1] Build the `BalanceBadge` component subscribed to live balance updates in
  `sisu-frontend/src/components/BalanceBadge.tsx` (depends on T022, T033)

**Checkpoint**: User Story 1 is fully functional and independently testable.

---

## Phase 4: User Story 2 - Browse open predictions (Priority: P1)

**Goal**: Anyone, logged in or not, can list predictions (filterable by status) and open one for
full detail and current odds.

**Independent Test**: View the prediction list and a prediction's detail page as a signed-out
visitor, independent of wagering or account creation.

### Tests for User Story 2

- [X] T035 [P] [US2] Contract test for `GET /api/predictions` including the `status` filter in
  `sisu-backend/src/test/java/market/sisu/contract/PredictionListContractTest.java`
- [X] T036 [P] [US2] Contract test for `GET /api/predictions/{id}` in
  `sisu-backend/src/test/java/market/sisu/contract/PredictionDetailContractTest.java`

### Implementation for User Story 2

- [X] T037 [US2] Implement `PredictionQueryService` (list with status filter, detail, odds derived
  from stored pool totals) in
  `sisu-backend/src/main/java/market/sisu/prediction/PredictionQueryService.java` (depends on T011)
- [X] T038 [US2] Implement the `PredictionResource` GET endpoints in
  `sisu-backend/src/main/java/market/sisu/prediction/PredictionResource.java` (depends on T037)
- [X] T039 [P] [US2] Build the `PredictionList` page with a status filter in
  `sisu-frontend/src/pages/PredictionList.tsx` (depends on T019)
- [X] T040 [P] [US2] Build the `PredictionDetail` page in
  `sisu-frontend/src/pages/PredictionDetail.tsx` (depends on T019)
- [X] T041 [P] [US2] Build the `OddsBar` component in `sisu-frontend/src/components/OddsBar.tsx`

**Checkpoint**: User Stories 1 AND 2 both work independently.

---

## Phase 5: User Story 3 - Wager sisu on a prediction (Priority: P1)

**Goal**: A logged-in user wagers sisu on an open prediction; balance and odds update atomically and
live; the user can see their own open wagers and potential payout.

**Independent Test**: A logged-in user with a balance places a wager on an existing open prediction
and the balance/odds update, independent of prediction creation or resolution.

### Tests for User Story 3

- [ ] T042 [P] [US3] Contract test for `POST /api/predictions/{id}/wagers` (success, over-balance,
  closed/resolved/cancelled prediction (FR-021), invalid amount) in
  `sisu-backend/src/test/java/market/sisu/contract/WagerContractTest.java`
- [ ] T043 [P] [US3] Contract test for `GET /api/users/me/wagers` in
  `sisu-backend/src/test/java/market/sisu/contract/MyWagersContractTest.java`
- [ ] T044 [P] [US3] Unit test for the pari-mutuel pool/odds update after a wager in
  `sisu-backend/src/test/java/market/sisu/unit/OddsCalculatorTest.java`
- [ ] T045 [P] [US3] Unit test for atomic balance-debit/wager/ledger writes under concurrent wagers
  (balance never negative, no double spend), plus a case asserting a wager is rejected when
  `closingAt` has passed even while the prediction's cached `status` is still `OPEN` (FR-025, using
  an injected `Clock`), in `sisu-backend/src/test/java/market/sisu/unit/WagerServiceTest.java`

### Implementation for User Story 3

- [ ] T046 [US3] Implement `WagerService.placeWager` (validation — including rejecting the wager if
  `now >= prediction.closingAt`, checked directly against the clock rather than only the cached
  `status`, per FR-025 — one MongoDB transaction across balance/wager/pool/ledger, SSE broadcast) in
  `sisu-backend/src/main/java/market/sisu/wager/WagerService.java` (depends on T011, T012, T014,
  T017)
- [ ] T047 [US3] Implement `WagerResource` (`POST /api/predictions/{id}/wagers`,
  `GET /api/users/me/wagers`) in `sisu-backend/src/main/java/market/sisu/wager/WagerResource.java`
  (depends on T046)
- [ ] T048 [P] [US3] Build the `WagerForm` component with balance-aware client-side validation in
  `sisu-frontend/src/components/WagerForm.tsx` (depends on T019)
- [ ] T049 [US3] Wire `WagerForm` into the `PredictionDetail` page and subscribe to
  `odds-updated`/`balance-updated` SSE events in `sisu-frontend/src/pages/PredictionDetail.tsx`
  (depends on T040, T048, T022)
- [ ] T050 [P] [US3] Create the Profile page with a "my open wagers and potential payout" section
  (balance/created-predictions/transaction-history sections are added by User Story 6) in
  `sisu-frontend/src/pages/Profile.tsx` (depends on T019, T033)

**Checkpoint**: User Story 3 is independently functional — the core wager loop is complete and
live-updating.

---

## Phase 6: User Story 4 - Create a prediction (Priority: P2)

**Goal**: A logged-in user creates a new yes/no prediction for others to wager on.

**Independent Test**: A logged-in user submits a new prediction and it appears in the open
predictions list, independent of whether wagering exists yet.

### Tests for User Story 4

- [X] T051 [P] [US4] Contract test for `POST /api/predictions` (success, empty fields, past closing
  date) in `sisu-backend/src/test/java/market/sisu/contract/CreatePredictionContractTest.java`

### Implementation for User Story 4

- [X] T052 [US4] Implement `PredictionService.create` (validation, creator attribution) in
  `sisu-backend/src/main/java/market/sisu/prediction/PredictionService.java` (depends on T011)
- [X] T053 [US4] Implement the `PredictionResource` POST endpoint in
  `sisu-backend/src/main/java/market/sisu/prediction/PredictionResource.java` (depends on T038,
  T052)
- [X] T054 [P] [US4] Build the `CreatePrediction` page/form in
  `sisu-frontend/src/pages/CreatePrediction.tsx` (depends on T019)

### Text length limits for User Story 4 (FR-038, added 2026-09-29)

- [X] T079 [P] [US4] Tests for the FR-038 length limits: boundary cases (at the limit, one over,
  measured after trimming) in `sisu-backend/src/test/java/market/sisu/unit/PredictionServiceTest.java`,
  the `QUESTION_TOO_LONG` / `DESCRIPTION_TOO_LONG` / `RESOLUTION_CRITERIA_TOO_LONG` codes in
  `sisu-backend/src/test/java/market/sisu/contract/CreatePredictionContractTest.java`, and the
  character counters in `sisu-frontend/tests/createPrediction.test.tsx`
- [X] T080 [US4] Enforce the FR-038 length limits (question 200, description 2000, resolution
  criteria 1000) in `PredictionService.create`, declare them as `maxLength` on
  `CreatePredictionRequest` and in `contracts/openapi.yaml`, and add per-field character counters
  and pre-submit checks to `sisu-frontend/src/pages/CreatePrediction.tsx` (depends on T052, T054)

**Checkpoint**: User Stories 1–4 all work independently.

---

## Phase 7: User Story 5 - Close and resolve predictions (Priority: P2)

**Goal**: A prediction automatically stops accepting wagers at its closing date; a resolver settles
it to one outcome exactly once, and winning wagers are paid out automatically.

**Independent Test**: Advance a prediction past its closing date, resolve it, and confirm winning
wagers are paid out and the balance changes are recorded.

### Tests for User Story 5

- [ ] T055 [P] [US5] Unit test for the automatic OPEN→CLOSED transition at `closingAt`, asserting
  the ≤1-minute bound and that it still holds after a simulated restart (FR-025), in
  `sisu-backend/src/test/java/market/sisu/unit/PredictionClosingSchedulerTest.java`
- [ ] T056 [P] [US5] Unit test for the pari-mutuel payout calculation (no winners, a single wager,
  multiple wagers) in `sisu-backend/src/test/java/market/sisu/unit/PayoutCalculatorTest.java`
- [ ] T057 [P] [US5] Contract test for `POST /api/predictions/{id}/resolve` (resolver-only,
  single-resolution guard) in
  `sisu-backend/src/test/java/market/sisu/contract/ResolvePredictionContractTest.java`

### Implementation for User Story 5

- [ ] T058 [US5] Implement a DB-driven `@Scheduled` job (interval ≤1 minute) flipping `OPEN`
  predictions to `CLOSED` once `closingAt` has passed, self-healing after a restart so the bound
  still holds (FR-025) — this is for the prediction's *visible* status only; wager placement itself
  already rejects late wagers directly via T046, so this job is not the correctness-critical path —
  in `sisu-backend/src/main/java/market/sisu/prediction/PredictionClosingScheduler.java` (depends on
  T011)
- [ ] T059 [US5] Implement `ResolutionService.resolve` (RESOLVER-role check, conditional
  CLOSED→RESOLVED update, pari-mutuel payout transaction, SSE broadcast) in
  `sisu-backend/src/main/java/market/sisu/prediction/ResolutionService.java` (depends on T014,
  T017, T046)
- [ ] T060 [US5] Implement the `PredictionResource` POST `/resolve` endpoint in
  `sisu-backend/src/main/java/market/sisu/prediction/PredictionResource.java` (depends on T053,
  T059)
- [ ] T061 [P] [US5] Add the resolver-only "Resolve" action to the `PredictionDetail` page in
  `sisu-frontend/src/pages/PredictionDetail.tsx` (depends on T049)

**Checkpoint**: User Stories 1–5 all work independently — the full wager-to-payout loop is complete.

---

## Phase 8: User Story 6 - View leaderboard, profile, and transaction history (Priority: P3)

**Goal**: Anyone sees the leaderboard; a logged-in user sees their own private balance, wager
history, created predictions, and full transaction history.

**Independent Test**: View the leaderboard and a user's own profile/transaction history after some
wagers and balance changes already exist.

### Tests for User Story 6

- [ ] T062 [P] [US6] Contract test for `GET /api/leaderboard` in
  `sisu-backend/src/test/java/market/sisu/contract/LeaderboardContractTest.java`
- [ ] T063 [P] [US6] Contract test for `GET /api/users/me` and `GET /api/users/me/transactions`,
  including the 401-when-logged-out case and confirming no endpoint exposes another user's profile,
  in `sisu-backend/src/test/java/market/sisu/contract/UserProfileContractTest.java`
- [ ] T064 [P] [US6] Unit test asserting a user's transaction-log sum always equals `balanceSisu`
  (SC-006) in `sisu-backend/src/test/java/market/sisu/unit/TransactionLedgerTest.java`

### Implementation for User Story 6

- [ ] T065 [US6] Implement the `LeaderboardResource` (ranked by balance) in
  `sisu-backend/src/main/java/market/sisu/user/LeaderboardResource.java` (depends on T010)
- [ ] T066 [US6] Implement `UserResource` `GET /api/users/me` and
  `GET /api/users/me/transactions` in `sisu-backend/src/main/java/market/sisu/user/UserResource.java`
  (depends on T010, T013)
- [ ] T067 [P] [US6] Build the `Leaderboard` page in `sisu-frontend/src/pages/Leaderboard.tsx`
  (depends on T019)
- [ ] T068 [US6] Extend the Profile page with balance, created-predictions, and transaction-history
  sections in `sisu-frontend/src/pages/Profile.tsx` (depends on T050, T066)

**Checkpoint**: All six user stories are independently functional.

---

## Phase 9: Account Deletion (FR-033, FR-036)

**Purpose**: Lets a user delete their account; depends on wagering (US3) and prediction creation
(US4) already existing, since deletion must refund open wagers and cancel unresolved created
predictions. No single spec.md user story covers this end-to-end, so it carries no `[Story]` label.

- [ ] T069 [P] Unit test for the account-deletion cascade (own open wagers refunded; own unresolved
  predictions cancelled and every wager on them refunded) in
  `sisu-backend/src/test/java/market/sisu/unit/AccountDeletionServiceTest.java`
- [ ] T070 Implement `AccountDeletionService` (refund cascade, personal-data scrub, de-identified
  history retained per spec.md Assumptions) in
  `sisu-backend/src/main/java/market/sisu/user/AccountDeletionService.java` (depends on T014, T046,
  T052)
- [ ] T071 Implement the `UserResource` `DELETE /api/users/me` endpoint in
  `sisu-backend/src/main/java/market/sisu/user/UserResource.java` (depends on T066, T070)
- [ ] T072 [P] Add a confirm-and-delete account action to the Profile page in
  `sisu-frontend/src/pages/Profile.tsx` (depends on T068)

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that span multiple user stories

- [ ] T073 [P] Audit all pages for WCAG 2.1 AA (labels, contrast, keyboard/focus order) per
  Constitution III
- [ ] T074 [P] Audit the frontend for hardcoded copy; confirm every string comes from
  `sisu-frontend/src/i18n/strings.fi.ts` per FR-031
- [ ] T075 [P] Add the "sisu has no monetary value" disclaimer component per FR-032/SC-008 in
  `sisu-frontend/src/components/SisuDisclaimer.tsx`
- [ ] T076 Run the JaCoCo report and confirm ≥80% line coverage on business-logic packages per
  Constitution II
- [ ] T077 Load-test prediction listing and wager placement at 100 concurrent users; confirm the
  500ms/2s targets from Constitution IV and SC-002/SC-003
- [ ] T078 Execute the full `quickstart.md` validation script end-to-end and record the results

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories
- **User Stories (Phases 3–8)**: All depend on Foundational; in priority order P1 → P1 → P1 → P2 →
  P2 → P3. US1/US2/US3 are mutually independent; US4 only needs Foundational; US5 depends on US3
  (`WagerService`, reused by `ResolutionService`); US6's `Profile` page extends the file US3 created
  (T050 → T068)
- **Account Deletion (Phase 9)**: Depends on US1 (`UserResource`), US3 (`WagerService` refund path),
  US4 (`PredictionService`)
- **Polish (Phase 10)**: Depends on all preceding phases

### Within Each User Story

- Tests are written first and MUST fail before implementation begins
- Entities (Foundational) before services; services before resources/endpoints; backend endpoint
  before the frontend page that calls it
- Story complete and checkpointed before moving to the next priority

### Parallel Opportunities

- Setup: T002, T003, T004, T005, T007 in parallel
- Foundational: T009–T013, T015, T016, T018–T022 in parallel (T008, T014, T017 are sequential
  blockers)
- Once Foundational completes, US1, US2, US4 can be staffed in parallel; US3 can start alongside
  them but its service layer (T046) is what US5 later depends on, so US5 should not start until
  US3's implementation tasks (not just tests) are done
- Within each story, all `[P]` tasks (mostly tests and independent frontend files) run in parallel

---

## Parallel Example: User Story 1

```bash
# Tests together:
Task: "Contract test for POST /api/auth/register in sisu-backend/src/test/java/market/sisu/contract/AuthRegisterContractTest.java"
Task: "Contract test for POST /api/auth/login in sisu-backend/src/test/java/market/sisu/contract/AuthLoginContractTest.java"
Task: "Unit test for starting-balance grant in sisu-backend/src/test/java/market/sisu/unit/UserServiceTest.java"
Task: "Unit test for login rate limiting in sisu-backend/src/test/java/market/sisu/unit/LoginRateLimiterTest.java"

# Frontend pages together:
Task: "Build the Register page in sisu-frontend/src/pages/Register.tsx"
Task: "Build the Login page in sisu-frontend/src/pages/Login.tsx"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (blocks everything)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: register/login/logout/starting-balance/rate-limit all work
5. Demo if ready — this alone proves the account/balance foundation the rest of the market needs

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. + US1 (account/balance) → demo (MVP)
3. + US2 (browse) → demo
4. + US3 (wager, live updates) → demo — this is the first point the product's core loop is visible
5. + US4 (create predictions) → demo
6. + US5 (close/resolve/payout) → demo — the loop is now fully closed
7. + US6 (leaderboard/profile/history) → demo
8. + Account Deletion → demo
9. Polish → ship

### Parallel Team Strategy

After Foundational: one developer on US1, one on US2, one on US4 (all Foundational-only
dependencies) simultaneously; US3 starts once US1/US2's entities are stable; US5 starts once US3's
`WagerService` lands; US6 starts once US3's Profile page stub (T050) exists.
