import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useParams } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { PredictionDetail } from '../src/api/types';
import { errorMessage, fi } from '../src/i18n/strings.fi';
import CreatePrediction from '../src/pages/CreatePrediction';

const me = { id: 'u1', username: 'matti', balanceSisu: 1000 };

const created: PredictionDetail = {
  id: 'new1',
  question: 'Sataako juhannuksena?',
  status: 'OPEN',
  closingAt: '2099-06-20T09:00:00Z',
  totalSisuWagered: 0,
  oddsYesPercent: 50,
  oddsNoPercent: 50,
  description: '',
  resolutionCriteria: 'Ilmatieteen laitos',
  creatorUsername: 'matti',
  winningOutcome: null,
};

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

function mockApi({ loggedIn = true, createResponse = () => json(created, 201) } = {}) {
  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url === '/api/users/me') {
      return Promise.resolve(
        loggedIn ? json(me) : json({ code: 'UNAUTHENTICATED', message: 'Login required' }, 401),
      );
    }
    if (url === '/api/predictions' && init?.method === 'POST') {
      return Promise.resolve(createResponse());
    }
    return Promise.resolve(json({ code: 'NOT_FOUND', message: url }, 404));
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function DetailStub() {
  return <p>detail:{useParams().id}</p>;
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/create-prediction']}>
        <Routes>
          <Route path="/create-prediction" element={<CreatePrediction />} />
          <Route path="/predictions/:id" element={<DetailStub />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const postCalls = (fetchMock: ReturnType<typeof mockApi>) =>
  fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST');

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('CreatePrediction', () => {
  it('asks logged-out visitors to log in instead of showing the form', async () => {
    mockApi({ loggedIn: false });
    renderPage();

    expect(await screen.findByText(fi.createPrediction.loginRequired)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: fi.createPrediction.submit })).toBeNull();
    expect(screen.getByRole('link', { name: fi.nav.login })).toHaveAttribute('href', '/login');
  });

  it('shows field errors for empty question, empty criteria and a past closing date without posting', async () => {
    const fetchMock = mockApi();
    const user = userEvent.setup();
    renderPage();

    await user.type(
      await screen.findByLabelText(new RegExp(fi.createPrediction.closingAt)),
      '2000-01-01T12:00',
    );
    await user.click(screen.getByRole('button', { name: fi.createPrediction.submit }));

    const question = screen.getByLabelText(new RegExp(fi.createPrediction.question));
    expect(question).toHaveAttribute('aria-invalid', 'true');
    expect(question).toHaveAccessibleDescription(
      new RegExp(errorMessage('QUESTION_REQUIRED').replace('.', '\\.')),
    );
    expect(screen.getByText(errorMessage('RESOLUTION_CRITERIA_REQUIRED'))).toBeInTheDocument();
    expect(screen.getByText(errorMessage('CLOSING_DATE_NOT_IN_FUTURE'))).toBeInTheDocument();
    expect(postCalls(fetchMock)).toHaveLength(0);
  });

  it('posts the trimmed prediction with an ISO closing time and opens the new prediction', async () => {
    const fetchMock = mockApi();
    const user = userEvent.setup();
    renderPage();

    await user.type(
      await screen.findByLabelText(new RegExp(fi.createPrediction.question)),
      '  Sataako juhannuksena?  ',
    );
    await user.type(screen.getByLabelText(new RegExp(fi.createPrediction.description)), 'Kuvaus');
    await user.type(
      screen.getByLabelText(new RegExp(fi.createPrediction.resolutionCriteria)),
      'Ilmatieteen laitos',
    );
    await user.type(
      screen.getByLabelText(new RegExp(fi.createPrediction.closingAt)),
      '2099-06-20T12:00',
    );
    await user.click(screen.getByRole('button', { name: fi.createPrediction.submit }));

    expect(await screen.findByText('detail:new1')).toBeInTheDocument();
    const posts = postCalls(fetchMock);
    expect(posts).toHaveLength(1);
    expect(JSON.parse(String(posts[0]?.[1]?.body))).toEqual({
      question: 'Sataako juhannuksena?',
      description: 'Kuvaus',
      resolutionCriteria: 'Ilmatieteen laitos',
      closingAt: new Date('2099-06-20T12:00').toISOString(),
    });
  });

  it('shows a server-side field error next to its field', async () => {
    mockApi({
      createResponse: () =>
        json(
          { code: 'CLOSING_DATE_NOT_IN_FUTURE', message: 'Closing date must be in the future' },
          422,
        ),
    });
    const user = userEvent.setup();
    renderPage();

    await user.type(await screen.findByLabelText(new RegExp(fi.createPrediction.question)), 'Q?');
    await user.type(
      screen.getByLabelText(new RegExp(fi.createPrediction.resolutionCriteria)),
      'Peruste',
    );
    await user.type(
      screen.getByLabelText(new RegExp(fi.createPrediction.closingAt)),
      '2099-06-20T12:00',
    );
    await user.click(screen.getByRole('button', { name: fi.createPrediction.submit }));

    expect(await screen.findByText(errorMessage('CLOSING_DATE_NOT_IN_FUTURE'))).toBeInTheDocument();
    expect(screen.getByLabelText(new RegExp(fi.createPrediction.closingAt))).toHaveAttribute(
      'aria-invalid',
      'true',
    );
    expect(screen.queryByRole('alert')).toBeNull();
  });
});
