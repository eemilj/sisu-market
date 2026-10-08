import { describeError, fieldErrorMessage, type ErrorBody, type FieldError } from './errors';

export type Category = 'POLITICS' | 'SPORTS' | 'ECONOMY' | 'WEATHER' | 'CULTURE';
export type PredictionStatus = 'OPEN' | 'CLOSED' | 'RESOLVED';
export type Outcome = 'YES' | 'NO';

export interface User {
  id: string;
  username: string;
  email: string;
  balance: number;
  /** Increases with every balance change; used to ignore out-of-order updates. */
  balanceVersion: number;
  createdAt: string;
  /** May resolve any prediction. */
  admin: boolean;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface Prediction {
  id: string;
  question: string;
  description: string;
  /** Null for predictions created before this field existed. */
  resolutionCriteria: string | null;
  category: Category;
  status: PredictionStatus;
  outcome: Outcome | null;
  closesAt: string;
  /** Price of a YES share = implied chance of YES, 0-1. */
  yesProbability: number;
  /** yesProbability as a whole percentage. */
  yesPercent: number;
  /** Total Sisu wagered by users. */
  totalWagered: number;
  /** Creator's username; null for seeded predictions. */
  createdBy: string | null;
  createdAt: string;
  resolvedAt: string | null;
  /** Increases with every change; used to ignore out-of-order updates. */
  version: number;
}

export type TransactionType = 'SIGNUP_BONUS' | 'OPENING_BALANCE' | 'WAGER' | 'WAGER_REFUND' | 'PAYOUT';

export interface Transaction {
  id: string;
  type: TransactionType;
  /** Signed: positive credits, negative debits. */
  amount: number;
  balanceAfter: number;
  /** English, for developers; the UI labels entries by `type`. */
  description: string;
  /** Question of the related prediction, if any. */
  predictionQuestion: string | null;
  predictionId: string | null;
  createdAt: string;
}

export interface TransactionPage {
  items: Transaction[];
  /** Pass as `before` to load the next (older) page; null when there is none. */
  nextCursor: string | null;
}

export interface Wager {
  id: string;
  predictionId: string;
  side: Outcome;
  amount: number;
  shares: number;
  /** Sisu paid if this side wins; fixed when the wager was placed. */
  payoutIfWin: number;
  averagePrice: number;
  status: WagerStatus;
  createdAt: string;
  /** Sisu actually paid at settlement; null until resolved. */
  payout: number | null;
  settledAt: string | null;
}

export type WagerStatus = 'OPEN' | 'WON' | 'LOST' | 'REFUNDED';

export interface WagerHistoryItem {
  wager: Wager;
  question: string;
  predictionStatus: PredictionStatus | null;
  outcome: Outcome | null;
}

export interface Page<T> {
  items: T[];
  nextCursor: string | null;
}

export interface Profile {
  username: string;
  balance: number;
  /** Competition rank by balance (ties share a rank). */
  rank: number;
  joinedAt: string;
  predictionsCreated: number;
  admin: boolean;
}

export interface LeaderboardEntry {
  rank: number;
  username: string;
  balance: number;
}

export interface Leaderboard {
  entries: LeaderboardEntry[];
  /** The caller's row if logged in and outside `entries`. */
  me: LeaderboardEntry | null;
}

export interface ResolutionResult {
  prediction: Prediction;
  winningWagers: number;
  losingWagers: number;
  totalPaidOut: number;
}

/** What a wager would get right now. */
export interface Quote {
  side: Outcome;
  amount: number;
  shares: number;
  payoutIfWin: number;
  averagePrice: number;
  yesProbabilityBefore: number;
  yesProbabilityAfter: number;
}

export interface WagerResult {
  wager: Wager;
  /** User's balance after the wager. */
  balance: number;
  balanceVersion: number;
  /** Prediction with updated pools and odds. */
  prediction: Prediction;
}

/** Events pushed by GET /api/events. */
export type LiveEvent =
  | { type: 'ready'; data: { authenticated: boolean } }
  | { type: 'prediction.created'; data: Prediction }
  | { type: 'prediction.updated'; data: Prediction }
  | { type: 'wallet.transaction'; data: { transaction: Transaction; balance: number; balanceVersion: number } };

export interface NewPrediction {
  question: string;
  description: string;
  resolutionCriteria: string;
  category: Category;
  /** ISO-8601 with offset. */
  closesAt: string;
}

/**
 * Any failed API call. `message` is ready to show to the user (Finnish, says what to do); `code` and
 * `fieldErrors` let forms point at the exact field.
 */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly code: string | null = null,
    public readonly fieldErrors: FieldError[] = [],
  ) {
    super(message);
  }

  /** Finnish message per field name, for showing next to form inputs. */
  fieldMessages(): Record<string, string> {
    const out: Record<string, string> = {};
    for (const f of this.fieldErrors) out[f.field] ??= fieldErrorMessage(f);
    return out;
  }
}

const TOKEN_KEY = 'sisu.token';

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body) headers.set('Content-Type', 'application/json');
  const token = tokenStore.get();
  if (token) headers.set('Authorization', `Bearer ${token}`);

  let response: Response;
  try {
    response = await fetch(path, { ...init, headers });
  } catch {
    throw new ApiError(0, describeError(0, null));
  }

  if (!response.ok) {
    let body: ErrorBody | null = null;
    try {
      body = await response.json();
    } catch {
      // Non-JSON error body (e.g. 401 from the JWT layer); fall back to the status.
    }
    throw new ApiError(response.status, describeError(response.status, body), body?.code ?? null, body?.fieldErrors ?? []);
  }
  return response.json() as Promise<T>;
}

export const api = {
  register: (username: string, email: string, password: string) =>
    request<AuthResponse>('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ username, email, password }),
    }),

  login: (username: string, password: string) =>
    request<AuthResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    }),

  me: () => request<User>('/api/users/me'),

  transactions: (before?: string | null) =>
    request<TransactionPage>(`/api/users/me/transactions${before ? `?before=${encodeURIComponent(before)}` : ''}`),

  createPrediction: (prediction: NewPrediction) =>
    request<Prediction>('/api/predictions', { method: 'POST', body: JSON.stringify(prediction) }),

  resolve: (predictionId: string, outcome: Outcome) =>
    request<ResolutionResult>(`/api/predictions/${encodeURIComponent(predictionId)}/resolve`, {
      method: 'POST',
      body: JSON.stringify({ outcome }),
    }),

  profile: (username: string) => request<Profile>(`/api/profiles/${encodeURIComponent(username)}`),

  profilePredictions: (username: string) =>
    request<Prediction[]>(`/api/profiles/${encodeURIComponent(username)}/predictions`),

  myWagers: (before?: string | null) =>
    request<Page<WagerHistoryItem>>(`/api/users/me/wagers${before ? `?before=${encodeURIComponent(before)}` : ''}`),

  leaderboard: (limit = 25) => request<Leaderboard>(`/api/leaderboard?limit=${limit}`),

  quote: (predictionId: string, side: Outcome, amount: number, signal?: AbortSignal) =>
    request<Quote>(
      `/api/predictions/${encodeURIComponent(predictionId)}/quote?side=${side}&amount=${amount}`,
      { signal },
    ),

  /**
   * `minPayout`: reject if the payout would be lower than this (send the quoted payout).
   * `requestId`: idempotency key; resending the same one never places a second wager.
   */
  placeWager: (predictionId: string, wager: { side: Outcome; amount: number; minPayout: number; requestId: string }) =>
    request<WagerResult>(`/api/predictions/${encodeURIComponent(predictionId)}/wagers`, {
      method: 'POST',
      body: JSON.stringify(wager),
    }),

  predictions: (filters: { category?: Category | 'ALL'; status?: PredictionStatus | 'ALL' } = {}) => {
    const params = new URLSearchParams();
    if (filters.category && filters.category !== 'ALL') params.set('category', filters.category);
    if (filters.status && filters.status !== 'ALL') params.set('status', filters.status);
    const query = params.toString();
    return request<Prediction[]>(`/api/predictions${query ? `?${query}` : ''}`);
  },
};
