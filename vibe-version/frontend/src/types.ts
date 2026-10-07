export type Side = 'YES' | 'NO';
export type PredictionStatus = 'OPEN' | 'CLOSED' | 'RESOLVED';
export type WagerStatus = 'OPEN' | 'WON' | 'LOST' | 'REFUNDED';
export type TxReason = 'STARTING_GRANT' | 'WAGER' | 'PAYOUT' | 'REFUND';

export interface User {
  id: string;
  username: string;
  balance: number;
  role: 'USER' | 'ADMIN';
}

export interface Session {
  token: string;
  user: User;
}

export interface Prediction {
  id: string;
  question: string;
  description: string;
  criteria: string;
  closesAt: string;
  createdAt: string;
  creatorId: string;
  creatorName: string;
  status: PredictionStatus;
  yesPool: number;
  noPool: number;
  totalPool: number;
  yesProbability: number;
  outcome: Side | null;
  wagerCount: number;
}

export interface Wager {
  id: string;
  predictionId: string;
  question: string;
  username: string;
  side: Side;
  amount: number;
  status: WagerStatus;
  payout: number | null;
  potentialPayout: number | null;
  createdAt: string;
}

export interface PredictionDetail {
  prediction: Prediction;
  recentWagers: Wager[];
}

export interface WagerResult {
  wager: Wager;
  prediction: Prediction;
  balance: number;
}

export interface Tx {
  id: string;
  amount: number;
  balanceAfter: number;
  reason: TxReason;
  predictionId: string | null;
  createdAt: string;
}

export interface LeaderboardEntry {
  rank: number;
  username: string;
  balance: number;
}

export interface Profile {
  user: User;
  wagers: Wager[];
  predictions: Prediction[];
}
