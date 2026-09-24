import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError, NETWORK_ERROR } from '../src/api/client';

function mockFetch(response: Response | Error) {
  const fetchMock = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
    response instanceof Error ? Promise.reject(response) : Promise.resolve(response),
  );
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

const json = (body: unknown, init: ResponseInit = {}) =>
  new Response(JSON.stringify(body), {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init.headers },
  });

describe('api client', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('sends JSON with the session cookie and returns the parsed body', async () => {
    const fetchMock = mockFetch(
      json({ id: 'u1', username: 'matti', balanceSisu: 1000 }, { status: 201 }),
    );

    const user = await api.register({ username: 'matti', password: 'salasana123' });

    expect(user.balanceSisu).toBe(1000);
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/register', {
      method: 'POST',
      credentials: 'include',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'matti', password: 'salasana123' }),
    });
  });

  it('adds the status filter and encodes path ids', async () => {
    const fetchMock = mockFetch(json([]));
    await api.predictions('OPEN');
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/predictions?status=OPEN');

    const detail = mockFetch(json({}));
    await api.prediction('a/b');
    expect(detail.mock.calls[0]?.[0]).toBe('/api/predictions/a%2Fb');
  });

  it('returns undefined for 204 responses', async () => {
    mockFetch(new Response(null, { status: 204 }));
    await expect(api.logout()).resolves.toBeUndefined();
  });

  it('decodes the contract Error body and Retry-After', async () => {
    mockFetch(
      json(
        { code: 'TOO_MANY_ATTEMPTS', message: 'Slow down' },
        { status: 429, headers: { 'Retry-After': '900' } },
      ),
    );

    const error = await api.login({ username: 'matti', password: 'x' }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 429, code: 'TOO_MANY_ATTEMPTS', retryAfterSeconds: 900 });
  });

  it('falls back to an HTTP_ code when the error body is not JSON', async () => {
    mockFetch(new Response('<html>Bad gateway</html>', { status: 502, statusText: 'Bad Gateway' }));
    await expect(api.leaderboard()).rejects.toMatchObject({ status: 502, code: 'HTTP_502' });
  });

  it('reports unreachable servers as NETWORK_ERROR', async () => {
    mockFetch(new TypeError('Failed to fetch'));
    await expect(api.me()).rejects.toMatchObject({ status: 0, code: NETWORK_ERROR });
  });
});
