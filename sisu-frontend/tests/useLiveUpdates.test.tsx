import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { queryKeys } from '../src/api/client';
import type { PredictionDetail, PredictionSummary, UserProfile } from '../src/api/types';
import { LIVE_UPDATES_URL, useLiveUpdates } from '../src/hooks/useLiveUpdates';

class FakeEventSource {
  static instances: FakeEventSource[] = [];
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  closed = false;
  private listeners = new Map<string, ((event: MessageEvent<string>) => void)[]>();

  constructor(
    readonly url: string,
    readonly init?: EventSourceInit,
  ) {
    FakeEventSource.instances.push(this);
  }

  addEventListener(name: string, listener: (event: MessageEvent<string>) => void) {
    this.listeners.set(name, [...(this.listeners.get(name) ?? []), listener]);
  }

  close() {
    this.closed = true;
  }

  emit(name: string, payload: unknown) {
    const event = new MessageEvent('message', { data: JSON.stringify(payload) });
    this.listeners.get(name)?.forEach((listener) => listener(event));
  }
}

const summary: PredictionSummary = {
  id: 'p1',
  question: 'Sataako huomenna?',
  status: 'OPEN',
  closingAt: '2026-10-01T12:00:00Z',
  totalSisuWagered: 100,
  oddsYesPercent: 50,
  oddsNoPercent: 50,
};
const detail: PredictionDetail = {
  ...summary,
  description: '',
  resolutionCriteria: 'FMI',
  creatorUsername: 'matti',
  winningOutcome: null,
};
const me: UserProfile = {
  id: 'u1',
  username: 'matti',
  balanceSisu: 1000,
  role: 'USER',
  createdPredictions: [],
  wagers: [],
};

describe('useLiveUpdates', () => {
  let queryClient: QueryClient;
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  const source = () => FakeEventSource.instances.at(-1)!;

  beforeEach(() => {
    FakeEventSource.instances = [];
    vi.stubGlobal('EventSource', FakeEventSource);
    queryClient = new QueryClient();
    queryClient.setQueryData(queryKeys.me, me);
    queryClient.setQueryData(queryKeys.prediction('p1'), detail);
    queryClient.setQueryData(queryKeys.predictionList('OPEN'), [summary]);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('does not connect while logged out', () => {
    const { result } = renderHook(() => useLiveUpdates(false), { wrapper });
    expect(FakeEventSource.instances).toHaveLength(0);
    expect(result.current).toBe('idle');
  });

  it('connects with credentials and closes on unmount', () => {
    const { result, unmount } = renderHook(() => useLiveUpdates(true), { wrapper });
    expect(source().url).toBe(LIVE_UPDATES_URL);
    expect(source().init).toEqual({ withCredentials: true });
    expect(result.current).toBe('connecting');

    act(() => source().onopen?.());
    expect(result.current).toBe('open');

    unmount();
    expect(source().closed).toBe(true);
  });

  it('writes balance-updated into the profile cache', () => {
    renderHook(() => useLiveUpdates(true), { wrapper });
    act(() => source().emit('balance-updated', { balanceSisu: 950 }));
    expect(queryClient.getQueryData<UserProfile>(queryKeys.me)?.balanceSisu).toBe(950);
  });

  it('writes odds-updated into detail and list caches', () => {
    renderHook(() => useLiveUpdates(true), { wrapper });
    act(() =>
      source().emit('odds-updated', {
        predictionId: 'p1',
        oddsYesPercent: 63.2,
        oddsNoPercent: 36.8,
        totalSisuWagered: 1420,
      }),
    );

    expect(queryClient.getQueryData<PredictionDetail>(queryKeys.prediction('p1'))).toMatchObject({
      oddsYesPercent: 63.2,
      totalSisuWagered: 1420,
      question: 'Sataako huomenna?',
    });
    expect(
      queryClient.getQueryData<PredictionSummary[]>(queryKeys.predictionList('OPEN'))?.[0],
    ).toMatchObject({ oddsNoPercent: 36.8 });
  });

  it('applies status changes and refetches status-filtered lists', () => {
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
    renderHook(() => useLiveUpdates(true), { wrapper });
    act(() =>
      source().emit('prediction-status-changed', {
        predictionId: 'p1',
        status: 'RESOLVED',
        winningOutcome: 'YES',
      }),
    );

    expect(queryClient.getQueryData<PredictionDetail>(queryKeys.prediction('p1'))).toMatchObject({
      status: 'RESOLVED',
      winningOutcome: 'YES',
    });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.predictionLists });
  });

  it('refetches everything after reconnecting, since missed events are not replayed', () => {
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = renderHook(() => useLiveUpdates(true), { wrapper });

    act(() => source().onopen?.());
    expect(invalidate).not.toHaveBeenCalled();

    act(() => source().onerror?.());
    expect(result.current).toBe('reconnecting');

    act(() => source().onopen?.());
    expect(invalidate).toHaveBeenCalledWith();
    expect(result.current).toBe('open');
  });
});
