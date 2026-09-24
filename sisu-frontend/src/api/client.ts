import type {
  ApiErrorBody,
  CreatePredictionRequest,
  LeaderboardEntry,
  LoginRequest,
  PlaceWagerRequest,
  PredictionDetail,
  PredictionStatus,
  PredictionSummary,
  RegisterRequest,
  ResolvePredictionRequest,
  Transaction,
  UserProfile,
  UserSummary,
  Wager,
} from './types';

export const API_BASE = '/api';

/** Any failed API call. `code` is the contract's stable error code, mapped to Finnish copy in the UI. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  /** From the Retry-After header (login rate limit, FR-037). */
  readonly retryAfterSeconds: number | null;

  constructor(
    status: number,
    code: string,
    message: string,
    retryAfterSeconds: number | null = null,
  ) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

/** Code used when the server could not be reached at all. */
export const NETWORK_ERROR = 'NETWORK_ERROR';

type Method = 'GET' | 'POST' | 'DELETE';

async function request<T>(method: Method, path: string, body?: unknown): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      // The session lives in an httpOnly cookie; the SPA never sees the token itself.
      credentials: 'include',
      headers:
        body === undefined
          ? { Accept: 'application/json' }
          : { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (cause) {
    throw new ApiError(0, NETWORK_ERROR, cause instanceof Error ? cause.message : 'Network error');
  }

  if (!response.ok) {
    throw await toApiError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

async function toApiError(response: Response): Promise<ApiError> {
  const retryAfter = response.headers.get('Retry-After');
  const retryAfterSeconds =
    retryAfter !== null && /^\d+$/.test(retryAfter) ? Number(retryAfter) : null;
  try {
    const body = (await response.json()) as Partial<ApiErrorBody>;
    if (typeof body.code === 'string') {
      return new ApiError(response.status, body.code, body.message ?? '', retryAfterSeconds);
    }
  } catch {
    // Not a JSON Error body (e.g. a proxy error page); fall through to a generic code.
  }
  return new ApiError(
    response.status,
    `HTTP_${response.status}`,
    response.statusText,
    retryAfterSeconds,
  );
}

const id = (value: string) => encodeURIComponent(value);

/** One typed function per endpoint in contracts/openapi.yaml. */
export const api = {
  register: (body: RegisterRequest) => request<UserSummary>('POST', '/auth/register', body),
  login: (body: LoginRequest) => request<UserSummary>('POST', '/auth/login', body),
  logout: () => request<void>('POST', '/auth/logout'),

  me: () => request<UserProfile>('GET', '/users/me'),
  deleteMe: () => request<void>('DELETE', '/users/me'),
  myTransactions: () => request<Transaction[]>('GET', '/users/me/transactions'),
  myWagers: () => request<Wager[]>('GET', '/users/me/wagers'),

  leaderboard: () => request<LeaderboardEntry[]>('GET', '/leaderboard'),

  predictions: (status?: PredictionStatus) =>
    request<PredictionSummary[]>(
      'GET',
      status ? `/predictions?status=${encodeURIComponent(status)}` : '/predictions',
    ),
  prediction: (predictionId: string) =>
    request<PredictionDetail>('GET', `/predictions/${id(predictionId)}`),
  createPrediction: (body: CreatePredictionRequest) =>
    request<PredictionDetail>('POST', '/predictions', body),
  placeWager: (predictionId: string, body: PlaceWagerRequest) =>
    request<Wager>('POST', `/predictions/${id(predictionId)}/wagers`, body),
  resolvePrediction: (predictionId: string, body: ResolvePredictionRequest) =>
    request<PredictionDetail>('POST', `/predictions/${id(predictionId)}/resolve`, body),
};

/** TanStack Query cache keys, shared by data hooks and the live-update stream. */
export const queryKeys = {
  me: ['me'] as const,
  myWagers: ['me', 'wagers'] as const,
  myTransactions: ['me', 'transactions'] as const,
  leaderboard: ['leaderboard'] as const,
  predictionLists: ['predictions', 'list'] as const,
  predictionList: (status?: PredictionStatus) => ['predictions', 'list', status ?? 'ALL'] as const,
  prediction: (predictionId: string) => ['predictions', 'detail', predictionId] as const,
};
