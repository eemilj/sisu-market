import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { PredictionDetail as Detail, PredictionSummary } from '../src/api/types';
import OddsBar from '../src/components/OddsBar';
import { fi } from '../src/i18n/strings.fi';
import PredictionDetail from '../src/pages/PredictionDetail';
import PredictionList from '../src/pages/PredictionList';

const open: PredictionSummary = {
  id: 'p1',
  question: 'Sataako Helsingissä juhannuksena?',
  status: 'OPEN',
  closingAt: '2026-10-01T12:00:00Z',
  totalSisuWagered: 1420,
  oddsYesPercent: 63.2,
  oddsNoPercent: 36.8,
};

const resolved: PredictionSummary = {
  ...open,
  id: 'p2',
  question: 'Voittaako Suomi jääkiekon MM-kultaa?',
  status: 'RESOLVED',
};

const detail: Detail = {
  ...open,
  description: 'Mitataan Kaisaniemen sääasemalla.',
  resolutionCriteria: 'Ilmatieteen laitoksen virallinen sademäärä > 0 mm.',
  creatorUsername: 'matti',
  winningOutcome: null,
};

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

/** Routes each request URL to a canned response. */
function mockApi(routes: Record<string, () => Response>) {
  const fetchMock = vi.fn((input: RequestInfo | URL) => {
    const url = String(input);
    const handler = routes[url];
    return Promise.resolve(handler ? handler() : json({ code: 'NOT_FOUND', message: url }, 404));
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function renderAt(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/predictions" element={<PredictionList />} />
          <Route path="/predictions/:id" element={<PredictionDetail />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const normalizeSpaces = (text: string | null) => text?.replace(/\s/g, ' ');

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('PredictionList', () => {
  it('shows open predictions with question, odds, total wagered and closing date', async () => {
    const fetchMock = mockApi({ '/api/predictions?status=OPEN': () => json([open]) });

    renderAt('/predictions');

    const link = await screen.findByRole('link', { name: open.question });
    expect(link).toHaveAttribute('href', '/predictions/p1');
    const card = link.closest('article')!;
    expect(
      within(card).getByRole('group', {
        name: fi.odds.summary(fi.odds.percent(63.2), fi.odds.percent(36.8)),
      }),
    ).toBeInTheDocument();
    expect(normalizeSpaces(card.textContent)).toContain('1 420 sisua');
    expect(within(card).getByText(fi.date.dateTime(open.closingAt))).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('refetches with the chosen status filter and marks it pressed', async () => {
    const fetchMock = mockApi({
      '/api/predictions?status=OPEN': () => json([open]),
      '/api/predictions?status=RESOLVED': () => json([resolved]),
      '/api/predictions': () => json([open, resolved]),
    });
    const user = userEvent.setup();
    renderAt('/predictions');
    await screen.findByText(open.question);

    const filters = screen.getByRole('group', { name: fi.predictions.filterLabel });
    await user.click(within(filters).getByRole('button', { name: fi.predictionStatus.RESOLVED }));

    expect(await screen.findByText(resolved.question)).toBeInTheDocument();
    expect(screen.queryByText(open.question)).not.toBeInTheDocument();
    expect(
      within(filters).getByRole('button', { name: fi.predictionStatus.RESOLVED }),
    ).toHaveAttribute('aria-pressed', 'true');

    await user.click(within(filters).getByRole('button', { name: fi.predictions.filterAll }));
    expect(await screen.findByText(open.question)).toBeInTheDocument();
    expect(screen.getByText(resolved.question)).toBeInTheDocument();
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      '/api/predictions?status=OPEN',
      '/api/predictions?status=RESOLVED',
      '/api/predictions',
    ]);
  });

  it('shows an empty state when nothing matches', async () => {
    mockApi({ '/api/predictions?status=CANCELLED': () => json([]) });
    renderAt('/predictions?status=CANCELLED');
    expect(await screen.findByText(fi.predictions.empty)).toBeInTheDocument();
  });
});

describe('PredictionDetail', () => {
  it('shows description, resolution criteria, creator and current odds', async () => {
    mockApi({ '/api/predictions/p1': () => json(detail) });

    renderAt('/predictions/p1');

    expect(
      await screen.findByRole('heading', { level: 1, name: open.question }),
    ).toBeInTheDocument();
    expect(screen.getByText(detail.description)).toBeInTheDocument();
    expect(screen.getByText(detail.resolutionCriteria)).toBeInTheDocument();
    expect(screen.getByText('matti')).toBeInTheDocument();
    expect(
      screen.getByRole('group', {
        name: fi.odds.summary(fi.odds.percent(63.2), fi.odds.percent(36.8)),
      }),
    ).toBeInTheDocument();
    expect(screen.queryByText(new RegExp(fi.predictions.winningOutcome))).not.toBeInTheDocument();
  });

  it('shows the winning outcome and a deleted creator', async () => {
    mockApi({
      '/api/predictions/p2': () =>
        json({ ...detail, ...resolved, creatorUsername: null, winningOutcome: 'NO' }),
    });

    renderAt('/predictions/p2');

    expect(
      await screen.findByText(`${fi.predictions.winningOutcome}: ${fi.outcome.NO}`),
    ).toBeInTheDocument();
    expect(screen.getByText(fi.predictions.deletedCreator)).toBeInTheDocument();
  });

  it('shows a not-found message for an unknown prediction', async () => {
    mockApi({
      '/api/predictions/missing': () =>
        json({ code: 'PREDICTION_NOT_FOUND', message: 'No such prediction' }, 404),
    });

    renderAt('/predictions/missing');

    expect(
      await screen.findByRole('heading', { name: fi.predictions.notFoundTitle }),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: fi.predictions.backToList })).toHaveAttribute(
      'href',
      '/predictions',
    );
  });
});

describe('OddsBar', () => {
  it('renders both percentages as text in Finnish number format', () => {
    render(<OddsBar oddsYesPercent={63.2} oddsNoPercent={36.8} />);
    const group = screen.getByRole('group');
    expect(normalizeSpaces(group.getAttribute('aria-label'))).toBe(
      'Kertoimet: kyllä 63,2 %, ei 36,8 %',
    );
    expect(normalizeSpaces(group.textContent)).toContain('Kyllä 63,2 %');
    expect(normalizeSpaces(group.textContent)).toContain('Ei 36,8 %');
  });
});
