import { useQueryClient, type QueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { API_BASE, queryKeys } from '../api/client';
import type {
  BalanceUpdatedEvent,
  OddsUpdatedEvent,
  PredictionDetail,
  PredictionStatusChangedEvent,
  PredictionSummary,
  UserProfile,
} from '../api/types';

export const LIVE_UPDATES_URL = `${API_BASE}/realtime/updates`;

export type LiveStatus = 'idle' | 'connecting' | 'open' | 'reconnecting';

/**
 * Subscribes to the SSE stream (contracts/realtime.md) while `enabled` (i.e. while logged in) and
 * writes each event straight into the TanStack Query cache, so components re-render from the same
 * data their REST queries populate. After a dropped connection comes back, everything is refetched
 * because events missed while offline are not replayed.
 */
export function useLiveUpdates(enabled: boolean): LiveStatus {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<Exclude<LiveStatus, 'idle'>>('connecting');

  useEffect(() => {
    if (!enabled) {
      return;
    }
    const source = new EventSource(LIVE_UPDATES_URL, { withCredentials: true });
    let dropped = false;

    source.onopen = () => {
      setStatus('open');
      if (dropped) {
        dropped = false;
        void queryClient.invalidateQueries();
      }
    };
    // EventSource reconnects on its own; we only record that a resync is needed.
    source.onerror = () => {
      dropped = true;
      setStatus('reconnecting');
    };

    source.addEventListener('balance-updated', (event) =>
      applyBalance(queryClient, parse<BalanceUpdatedEvent>(event)),
    );
    source.addEventListener('odds-updated', (event) =>
      applyOdds(queryClient, parse<OddsUpdatedEvent>(event)),
    );
    source.addEventListener('prediction-status-changed', (event) =>
      applyStatus(queryClient, parse<PredictionStatusChangedEvent>(event)),
    );

    return () => {
      source.close();
      setStatus('connecting');
    };
  }, [enabled, queryClient]);

  return enabled ? status : 'idle';
}

function parse<T>(event: Event): T {
  return JSON.parse((event as MessageEvent<string>).data) as T;
}

export function applyBalance(queryClient: QueryClient, { balanceSisu }: BalanceUpdatedEvent) {
  queryClient.setQueryData<UserProfile>(queryKeys.me, (me) => (me ? { ...me, balanceSisu } : me));
  void queryClient.invalidateQueries({ queryKey: queryKeys.myTransactions });
  void queryClient.invalidateQueries({ queryKey: queryKeys.leaderboard });
}

export function applyOdds(queryClient: QueryClient, event: OddsUpdatedEvent) {
  const { predictionId, ...odds } = event;
  updatePrediction(queryClient, predictionId, (p) => ({ ...p, ...odds }));
  // Potential payouts of open wagers depend on the pools.
  void queryClient.invalidateQueries({ queryKey: queryKeys.myWagers });
}

export function applyStatus(queryClient: QueryClient, event: PredictionStatusChangedEvent) {
  const { predictionId, status, winningOutcome } = event;
  queryClient.setQueryData<PredictionDetail>(queryKeys.prediction(predictionId), (p) =>
    p ? { ...p, status, winningOutcome } : p,
  );
  // A status change moves the prediction between status-filtered lists, so refetch them.
  void queryClient.invalidateQueries({ queryKey: queryKeys.predictionLists });
  void queryClient.invalidateQueries({ queryKey: queryKeys.me });
  void queryClient.invalidateQueries({ queryKey: queryKeys.myWagers });
}

function updatePrediction(
  queryClient: QueryClient,
  predictionId: string,
  update: <P extends PredictionSummary>(prediction: P) => P,
) {
  queryClient.setQueryData<PredictionDetail>(queryKeys.prediction(predictionId), (p) =>
    p ? update(p) : p,
  );
  queryClient.setQueriesData<PredictionSummary[]>({ queryKey: queryKeys.predictionLists }, (list) =>
    list?.map((p) => (p.id === predictionId ? update(p) : p)),
  );
}
