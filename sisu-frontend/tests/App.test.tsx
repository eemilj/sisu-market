import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import App from '../src/App';
import { errorMessage, fi } from '../src/i18n/strings.fi';

function renderAt(path: string) {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('App shell', () => {
  it('renders the layout landmarks and home page in Finnish', () => {
    renderAt('/');
    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: fi.app.mainNavigation })).toBeInTheDocument();
    expect(screen.getByRole('main')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: fi.home.title })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: fi.app.skipToContent })).toHaveAttribute(
      'href',
      '#main',
    );
  });

  it('shows a not-found page for unknown routes', () => {
    renderAt('/ei-ole');
    expect(screen.getByRole('heading', { name: fi.notFound.title })).toBeInTheDocument();
  });
});

describe('Finnish strings', () => {
  it('maps known error codes and falls back for unknown ones', () => {
    expect(errorMessage('INSUFFICIENT_BALANCE')).toBe(fi.errors.INSUFFICIENT_BALANCE);
    expect(errorMessage('SOMETHING_NEW')).toBe(fi.errors.unknown);
    expect(errorMessage('unknown')).toBe(fi.errors.unknown);
  });

  it('formats sisu amounts with Finnish grouping', () => {
    expect(fi.sisu.amount(1250).replace(/\s/g, ' ')).toBe('1 250 sisua');
  });
});
