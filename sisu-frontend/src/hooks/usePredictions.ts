import { useQuery } from '@tanstack/react-query';
import { api, queryKeys } from '../api/client';
import type { PredictionStatus } from '../api/types';

/**
 * Public prediction list (User Story 2), optionally filtered by status. Cached under
 * `queryKeys.predictionList`, which `useLiveUpdates` patches as odds and statuses change.
 */
export function usePredictionList(status?: PredictionStatus) {
  return useQuery({
    queryKey: queryKeys.predictionList(status),
    queryFn: () => api.predictions(status),
  });
}

/** One prediction's detail view (User Story 2), live-patched like the list. */
export function usePrediction(predictionId: string) {
  return useQuery({
    queryKey: queryKeys.prediction(predictionId),
    queryFn: () => api.prediction(predictionId),
  });
}
