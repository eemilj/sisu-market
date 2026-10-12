import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, queryKeys } from '../api/client';
import type { PlaceWagerRequest, Wager } from '../api/types';

/**
 * Places a wager on one prediction (User Story 3). The `balance-updated` and `odds-updated` SSE
 * events normally refresh the balance and odds within a second; the refetches here keep the page
 * correct even when the live stream is down.
 */
export function usePlaceWager(predictionId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: PlaceWagerRequest) => api.placeWager(predictionId, body),
    onSettled: () => {
      // On failure too: e.g. PREDICTION_NOT_OPEN means our cached status is stale.
      void queryClient.invalidateQueries({ queryKey: queryKeys.me, exact: true });
      void queryClient.invalidateQueries({ queryKey: queryKeys.prediction(predictionId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.predictionLists });
      void queryClient.invalidateQueries({ queryKey: queryKeys.myWagers });
    },
  });
}

/** The logged-in user's own open wagers with their potential payout (FR-024). */
export function useMyWagers(enabled: boolean) {
  return useQuery<Wager[]>({
    queryKey: queryKeys.myWagers,
    queryFn: api.myWagers,
    enabled,
  });
}
