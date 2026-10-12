import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { PredictionDetail as Detail, UserSummary, Wager } from '../src/api/types';
import { applyBalance, applyOdds } from '../src/hooks/useLiveUpdates';
import { fi } from '../src/i18n/strings.fi';
import PredictionDetail from '../src/pages/PredictionDetail';
import Profile from '../src/pages/Profile';

const me: UserSummary = { id: 'u1', username: 'matti', balanceSisu: 1000 };

const open: Detail = {
  id: 'p1',
  question: 'Sataako Helsingissä juhannuksena?',
  status: 'OPEN',
  closingAt: '2099-06-20T12:00:00Z',
  totalSisuWagered: 1000,
  oddsYesPercent: 60,
  oddsNoPercent: 40,
  description: 'Mitataan Kaisaniemen sääasemalla.',
  resolutionCriteria: 'Ilmatieteen laitoksen virallinen sademäärä > 0 mm.',
  creatorUsername: 'liisa',
  winningOutcome: null,
};

const placed: Wager = {
  id: 'w1',
  predictionId: 'p1',
  predictionQuestion: open.question,
  outcome: 'NO',
  amountSisu: 400,
  status: 'OPEN',
  potentialPayoutSisu: 700,
  placedAt: '2026-10-07T12:00:00Z',
};

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const unauthenticated = () => json({ code: 'UNAUTHENTICATED', message: 'Login required' }, 401);

type Handler = (init?: RequestInit) => Response;

/** Routes "METHOD url" to a handler; anything unrouted is a 404. */
function mockApi(routes: Record<string, Handler>) {
  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${String(input)}`;
    const handler = routes[key];
    return Promise.resolve(
      handler ? handler(init) : json({ code: 'NOT_FOUND', message: key }, 404),
    );
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

const calls = (fetchMock: ReturnType<typeof mockApi>, key: string) =>
  fetchMock.mock.calls.filter(([url, init]) => `${init?.method ?? 'GET'} ${String(url)}` === key);

function renderAt(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/predictions/:id" element={<PredictionDetail />} />
          <Route path="/profile" element={<Profile />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return queryClient;
}

const normalizeSpaces = (text: string | null | undefined) => text?.replace(/\s/g, ' ');

async function wagerSection() {
  return within((await screen.findByRole('heading', { name: fi.wager.title })).closest('section')!);
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('WagerForm on the prediction detail page', () => {
  it('places a wager and confirms it with the potential payout', async () => {
    let balance = 1000;
    const fetchMock = mockApi({
      'GET /api/users/me': () => json({ ...me, balanceSisu: balance }),
      'GET /api/predictions/p1': () => json(open),
      'POST /api/predictions/p1/wagers': () => {
        balance = 600;
        return json(placed, 201);
      },
    });
    const user = userEvent.setup();
    renderAt('/predictions/p1');
    const section = await wagerSection();

    await user.click(await section.findByRole('radio', { name: fi.outcome.NO }));
    await user.type(section.getByRole('textbox', { name: /Panos/ }), '400');
    await user.click(section.getByRole('button', { name: fi.wager.submit }));

    const status = await section.findByRole('status');
    await vi.waitFor(() =>
      expect(normalizeSpaces(status.textContent)).toBe(
        'Panos asetettu: 400 sisua, Ei. Mahdollinen voitto nyt: 700 sisua.',
      ),
    );
    const [, init] = calls(fetchMock, 'POST /api/predictions/p1/wagers')[0]!;
    expect(JSON.parse(String(init?.body))).toEqual({ outcome: 'NO', amountSisu: 400 });
    // Balance and odds are refetched even without the live stream.
    await vi.waitFor(() =>
      expect(normalizeSpaces(section.getByText(/Saldosi/).textContent)).toContain('600 sisua'),
    );
    expect(calls(fetchMock, 'GET /api/predictions/p1').length).toBeGreaterThan(1);
  });

  it('rejects a missing outcome, non-whole or non-positive amounts and over-balance wagers before sending', async () => {
    const fetchMock = mockApi({
      'GET /api/users/me': () => json(me),
      'GET /api/predictions/p1': () => json(open),
    });
    const user = userEvent.setup();
    renderAt('/predictions/p1');
    const section = await wagerSection();
    const amount = await section.findByRole('textbox', { name: /Panos/ });
    const submit = section.getByRole('button', { name: fi.wager.submit });

    await user.type(amount, '10');
    await user.click(submit);
    expect(section.getByText(fi.wager.outcomeRequired)).toBeInTheDocument();

    await user.click(section.getByRole('radio', { name: fi.outcome.YES }));
    for (const value of ['1.5', '0', '-5', 'abc', '']) {
      await user.clear(amount);
      if (value) {
        await user.type(amount, value);
      }
      await user.click(submit);
      expect(section.getByText(fi.errors.INVALID_WAGER_AMOUNT)).toBeInTheDocument();
      expect(amount).toHaveAttribute('aria-invalid', 'true');
    }

    await user.clear(amount);
    await user.type(amount, '1001');
    await user.click(submit);
    expect(section.getByText(fi.errors.INSUFFICIENT_BALANCE)).toBeInTheDocument();

    expect(calls(fetchMock, 'POST /api/predictions/p1/wagers')).toHaveLength(0);
  });

  it('shows server rejections next to the amount or in a banner', async () => {
    let response = () =>
      json({ code: 'INSUFFICIENT_BALANCE', message: 'Amount exceeds the current balance' }, 422);
    mockApi({
      'GET /api/users/me': () => json(me),
      'GET /api/predictions/p1': () => json(open),
      'POST /api/predictions/p1/wagers': () => response(),
    });
    const user = userEvent.setup();
    renderAt('/predictions/p1');
    const section = await wagerSection();

    await user.click(await section.findByRole('radio', { name: fi.outcome.YES }));
    await user.type(section.getByRole('textbox', { name: /Panos/ }), '900');
    await user.click(section.getByRole('button', { name: fi.wager.submit }));
    expect(await section.findByText(fi.errors.INSUFFICIENT_BALANCE)).toBeInTheDocument();

    response = () => json({ code: 'PREDICTION_NOT_OPEN', message: 'Prediction is not open' }, 409);
    await user.click(section.getByRole('button', { name: fi.wager.submit }));
    expect(await section.findByRole('alert')).toHaveTextContent(fi.errors.PREDICTION_NOT_OPEN);
  });

  it('asks a logged-out visitor to log in instead of showing the form', async () => {
    mockApi({
      'GET /api/users/me': unauthenticated,
      'GET /api/predictions/p1': () => json(open),
    });
    renderAt('/predictions/p1');
    const section = await wagerSection();

    expect(await section.findByText(fi.wager.loginRequired)).toBeInTheDocument();
    expect(section.getByRole('link', { name: fi.nav.login })).toHaveAttribute('href', '/login');
    expect(section.queryByRole('button', { name: fi.wager.submit })).not.toBeInTheDocument();
  });

  it('hides the form once the prediction is closed or its closing time has passed', async () => {
    for (const prediction of [
      { ...open, status: 'CLOSED' as const },
      { ...open, closingAt: '2020-01-01T00:00:00Z' },
    ]) {
      mockApi({
        'GET /api/users/me': () => json(me),
        'GET /api/predictions/p1': () => json(prediction),
      });
      renderAt('/predictions/p1');
      const section = await wagerSection();

      expect(section.getByText(fi.wager.notOpen)).toBeInTheDocument();
      expect(section.queryByRole('button', { name: fi.wager.submit })).not.toBeInTheDocument();
      cleanup();
    }
  });

  it('follows live odds and balance updates from the SSE stream', async () => {
    mockApi({
      'GET /api/users/me': () => json(me),
      'GET /api/predictions/p1': () => json(open),
    });
    const queryClient = renderAt('/predictions/p1');
    const section = await wagerSection();
    await section.findByRole('button', { name: fi.wager.submit });

    act(() => {
      applyOdds(queryClient, {
        predictionId: 'p1',
        oddsYesPercent: 42.9,
        oddsNoPercent: 57.1,
        totalSisuWagered: 1400,
      });
      applyBalance(queryClient, { balanceSisu: 600 });
    });

    // Query observers are notified asynchronously, so wait for the re-render.
    expect(
      await screen.findByRole('group', {
        name: fi.odds.summary(fi.odds.percent(42.9), fi.odds.percent(57.1)),
      }),
    ).toBeInTheDocument();
    expect(normalizeSpaces(section.getByText(/Saldosi/).textContent)).toContain('600 sisua');
  });
});

describe('Profile', () => {
  it("lists the user's open wagers with outcome, stake and potential payout", async () => {
    mockApi({
      'GET /api/users/me': () => json(me),
      'GET /api/users/me/wagers': () =>
        json([placed, { ...placed, id: 'w2', predictionQuestion: null, outcome: 'YES' }]),
    });
    renderAt('/profile');

    const card = await screen.findByRole('article', { name: open.question });
    expect(within(card).getByRole('link', { name: open.question })).toHaveAttribute(
      'href',
      '/predictions/p1',
    );
    expect(within(card).getByText(fi.outcome.NO)).toBeInTheDocument();
    expect(normalizeSpaces(within(card).getByText(/^400/).textContent)).toBe('400 sisua');
    expect(normalizeSpaces(within(card).getByText(/^700/).textContent)).toBe('700 sisua');
    expect(screen.getByRole('article', { name: fi.profile.unknownPrediction })).toBeInTheDocument();
  });

  it('shows an empty state without open wagers', async () => {
    mockApi({
      'GET /api/users/me': () => json(me),
      'GET /api/users/me/wagers': () => json([]),
    });
    renderAt('/profile');

    expect(await screen.findByText(fi.profile.noOpenWagers)).toBeInTheDocument();
  });

  it('asks a logged-out visitor to log in', async () => {
    const fetchMock = mockApi({ 'GET /api/users/me': unauthenticated });
    renderAt('/profile');

    expect(await screen.findByText(fi.profile.loginRequired)).toBeInTheDocument();
    expect(calls(fetchMock, 'GET /api/users/me/wagers')).toHaveLength(0);
  });
});
