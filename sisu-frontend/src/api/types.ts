// Response/request shapes from specs/001-sisu-prediction-market/contracts/openapi.yaml.
// Sisu amounts are integers (whole sisu); odds percentages are display-only numbers.

export type Role = 'USER' | 'RESOLVER';
export type Outcome = 'YES' | 'NO';
export type PredictionStatus = 'OPEN' | 'CLOSED' | 'RESOLVED' | 'CANCELLED';
export type WagerStatus = 'OPEN' | 'WON' | 'LOST' | 'REFUNDED';
export type TransactionReason = 'STARTING_GRANT' | 'WAGER_PLACED' | 'PAYOUT' | 'REFUND';

export interface ApiErrorBody {
  code: string;
  message: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
}

export type LoginRequest = RegisterRequest;

export interface UserSummary {
  id: string;
  username: string;
  balanceSisu: number;
}

export interface UserProfile extends UserSummary {
  role: Role;
  createdPredictions: PredictionSummary[];
  wagers: Wager[];
}

export interface LeaderboardEntry {
  rank: number;
  username: string;
  balanceSisu: number;
}

export interface PredictionSummary {
  id: string;
  question: string;
  status: PredictionStatus;
  closingAt: string;
  totalSisuWagered: number;
  oddsYesPercent: number;
  oddsNoPercent: number;
}

export interface PredictionDetail extends PredictionSummary {
  description: string;
  resolutionCriteria: string;
  /** `null` once the creator has deleted their account. */
  creatorUsername: string | null;
  winningOutcome: Outcome | null;
}

export interface CreatePredictionRequest {
  question: string;
  description: string;
  resolutionCriteria: string;
  closingAt: string;
}

export interface ResolvePredictionRequest {
  outcome: Outcome;
}

export interface PlaceWagerRequest {
  outcome: Outcome;
  amountSisu: number;
}

export interface Wager {
  id: string;
  predictionId: string;
  outcome: Outcome;
  amountSisu: number;
  status: WagerStatus;
  potentialPayoutSisu: number;
  placedAt: string;
}

export interface Transaction {
  id: string;
  direction: 'CREDIT' | 'DEBIT';
  amountSisu: number;
  reason: TransactionReason;
  createdAt: string;
}

// Server-Sent Event payloads (contracts/realtime.md).

export interface BalanceUpdatedEvent {
  balanceSisu: number;
}

export interface OddsUpdatedEvent {
  predictionId: string;
  oddsYesPercent: number;
  oddsNoPercent: number;
  totalSisuWagered: number;
}

export interface PredictionStatusChangedEvent {
  predictionId: string;
  status: PredictionStatus;
  winningOutcome: Outcome | null;
}
