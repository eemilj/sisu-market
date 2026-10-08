export type Category = 'POLITICS' | 'SPORTS' | 'ECONOMY' | 'WEATHER' | 'CULTURE';
export type PredictionStatus = 'OPEN' | 'CLOSED' | 'RESOLVED';
export type Outcome = 'YES' | 'NO';

export interface User {
  id: string;
  username: string;
  email: string;
  balance: number;
  createdAt: string;
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
  yesPercent: number;
  volume: number;
  /** Creator's username; null for seeded predictions. */
  createdBy: string | null;
  createdAt: string;
}

export type TransactionType = 'SIGNUP_BONUS' | 'OPENING_BALANCE' | 'WAGER';

export interface Transaction {
  id: string;
  type: TransactionType;
  /** Signed: positive credits, negative debits. */
  amount: number;
  balanceAfter: number;
  description: string;
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
  createdAt: string;
}

export interface WagerResult {
  wager: Wager;
  /** User's balance after the wager. */
  balance: number;
  /** Prediction with updated pools and odds. */
  prediction: Prediction;
}

export interface NewPrediction {
  question: string;
  description: string;
  resolutionCriteria: string;
  category: Category;
  /** ISO-8601 with offset. */
  closesAt: string;
}

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
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
    throw new ApiError(0, 'Cannot reach the server. Is the backend running?');
  }

  if (!response.ok) {
    let message = response.statusText || `Request failed (${response.status})`;
    try {
      const body = await response.json();
      if (body?.message) message = body.message;
    } catch {
      // Non-JSON error body (e.g. 401 from the JWT layer); keep the status text.
    }
    throw new ApiError(response.status, message);
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

  placeWager: (predictionId: string, side: Outcome, amount: number) =>
    request<WagerResult>(`/api/predictions/${encodeURIComponent(predictionId)}/wagers`, {
      method: 'POST',
      body: JSON.stringify({ side, amount }),
    }),

  predictions: (filters: { category?: Category | 'ALL'; status?: PredictionStatus | 'ALL' } = {}) => {
    const params = new URLSearchParams();
    if (filters.category && filters.category !== 'ALL') params.set('category', filters.category);
    if (filters.status && filters.status !== 'ALL') params.set('status', filters.status);
    const query = params.toString();
    return request<Prediction[]>(`/api/predictions${query ? `?${query}` : ''}`);
  },
};
