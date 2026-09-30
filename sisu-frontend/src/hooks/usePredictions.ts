import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, queryKeys } from '../api/client';
import type { CreatePredictionRequest, PredictionDetail, PredictionStatus } from '../api/types';

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

/**
 * Creates a prediction (User Story 4). The response seeds the detail cache, and every list is
 * refetched so the new prediction shows up without a reload.
 */
export function useCreatePrediction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreatePredictionRequest) => api.createPrediction(body),
    onSuccess: (created: PredictionDetail) => {
      queryClient.setQueryData(queryKeys.prediction(created.id), created);
      void queryClient.invalidateQueries({ queryKey: queryKeys.predictionLists });
    },
  });
}

/** One prediction's detail view (User Story 2), live-patched like the list. */
export function usePrediction(predictionId: string) {
  return useQuery({
    queryKey: queryKeys.prediction(predictionId),
    queryFn: () => api.prediction(predictionId),
  });
}
