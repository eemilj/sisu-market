import { fi, type ErrorCode } from './i18n/fi';
import type {
  LeaderboardEntry,
  Prediction,
  PredictionDetail,
  PredictionStatus,
  Profile,
  Session,
  Side,
  Tx,
  User,
  WagerResult,
} from './types';

const TOKEN_KEY = 'sisu.token';

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (t: string) => localStorage.setItem(TOKEN_KEY, t),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

/** An API failure with a stable code and a Finnish, user-facing message. */
export class ApiError extends Error {
  constructor(
    public readonly code: ErrorCode,
    public readonly status: number,
  ) {
    super(fi.errors[code]);
  }
}

let onUnauthorized: () => void = () => {};
export function setUnauthorizedHandler(fn: () => void) {
  onUnauthorized = fn;
}

function codeForStatus(status: number): ErrorCode {
  if (status === 401) return 'UNAUTHORIZED';
  if (status === 403) return 'NOT_ALLOWED';
  if (status === 404) return 'NOT_FOUND';
  if (status === 429) return 'TOO_MANY_ATTEMPTS';
  if (status === 502 || status === 503 || status === 504) return 'NETWORK';
  return 'UNKNOWN';
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  const token = tokenStore.get();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let res: Response;
  try {
    res = await fetch(`/api${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError('NETWORK', 0);
  }
  if (res.ok) {
    return (res.status === 204 ? undefined : await res.json()) as T;
  }
  let code: ErrorCode = codeForStatus(res.status);
  try {
    const data = (await res.json()) as { code?: string };
    if (data.code && data.code in fi.errors) code = data.code as ErrorCode;
  } catch {
    // No JSON error body (e.g. rejected by the proxy or a filter); keep the status-based code.
  }
  if (res.status === 401 && code === 'UNAUTHORIZED' && token) onUnauthorized();
  throw new ApiError(code, res.status);
}

export const api = {
  register: (username: string, password: string) =>
    request<Session>('POST', '/auth/register', { username, password }),
  login: (username: string, password: string) => request<Session>('POST', '/auth/login', { username, password }),
  me: () => request<User>('GET', '/me'),
  transactions: () => request<Tx[]>('GET', '/me/transactions'),
  predictions: (status?: PredictionStatus) =>
    request<Prediction[]>('GET', `/predictions${status ? `?status=${status}` : ''}`),
  prediction: (id: string) => request<PredictionDetail>('GET', `/predictions/${encodeURIComponent(id)}`),
  createPrediction: (p: { question: string; description: string; criteria: string; closesAt: string }) =>
    request<Prediction>('POST', '/predictions', p),
  wager: (id: string, side: Side, amount: number) =>
    request<WagerResult>('POST', `/predictions/${encodeURIComponent(id)}/wagers`, { side, amount }),
  resolve: (id: string, outcome: Side) =>
    request<Prediction>('POST', `/predictions/${encodeURIComponent(id)}/resolve`, { outcome }),
  profile: (username: string) => request<Profile>('GET', `/users/${encodeURIComponent(username)}`),
  leaderboard: () => request<LeaderboardEntry[]>('GET', '/leaderboard'),
};

export function errorMessage(e: unknown): string {
  return e instanceof ApiError ? e.message : fi.errors.UNKNOWN;
}
