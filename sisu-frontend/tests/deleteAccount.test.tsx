import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fi } from '../src/i18n/strings.fi';
import SisuDisclaimer from '../src/components/SisuDisclaimer';
import Profile from '../src/pages/Profile';

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const me = { id: 'u1', username: 'matti', balanceSisu: 1000, createdPredictions: [], wagers: [] };

function mockApi(deleteResponse: () => Response) {
  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${String(input)}`;
    if (key === 'GET /api/users/me') return Promise.resolve(json(me));
    if (key === 'GET /api/users/me/wagers') return Promise.resolve(json([]));
    if (key === 'GET /api/users/me/transactions') return Promise.resolve(json([]));
    if (key === 'DELETE /api/users/me') return Promise.resolve(deleteResponse());
    return Promise.resolve(json({ code: 'NOT_FOUND', message: key }, 404));
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function renderProfile() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/profile']}>
        <Routes>
          <Route path="/profile" element={<Profile />} />
          <Route path="/" element={<p>etusivu</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('account deletion on the profile page (FR-033, FR-036)', () => {
  it('requires a second confirmation before deleting, then leaves the profile', async () => {
    const fetchMock = mockApi(() => new Response(null, { status: 204 }));
    const user = userEvent.setup();
    renderProfile();

    await user.click(await screen.findByRole('button', { name: fi.profile.deleteButton }));
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false);

    await user.click(screen.getByRole('button', { name: fi.profile.deleteConfirm }));

    expect(await screen.findByText('etusivu')).toBeTruthy();
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(true);
  });

  it('lets the user back out without deleting', async () => {
    const fetchMock = mockApi(() => new Response(null, { status: 204 }));
    const user = userEvent.setup();
    renderProfile();

    await user.click(await screen.findByRole('button', { name: fi.profile.deleteButton }));
    await user.click(screen.getByRole('button', { name: fi.profile.deleteCancel }));

    expect(screen.getByRole('button', { name: fi.profile.deleteButton })).toBeTruthy();
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false);
  });

  it('shows an error and stays on the page when deletion fails', async () => {
    mockApi(() => json({ code: 'INTERNAL_ERROR', message: 'boom' }, 500));
    const user = userEvent.setup();
    renderProfile();

    await user.click(await screen.findByRole('button', { name: fi.profile.deleteButton }));
    await user.click(screen.getByRole('button', { name: fi.profile.deleteConfirm }));

    expect((await screen.findByRole('alert')).textContent).toContain(fi.profile.deleteFailed);
  });
});

describe('SisuDisclaimer (FR-032)', () => {
  it('states that sisu has no monetary value', () => {
    render(<SisuDisclaimer />);
    expect(screen.getByText(fi.sisu.disclaimer)).toBeTruthy();
  });
});
