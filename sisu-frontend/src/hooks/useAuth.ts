import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, queryKeys } from '../api/client';
import type { LoginRequest, RegisterRequest, UserSummary } from '../api/types';

/**
 * Session state for User Story 1: the logged-in user (with the current sisu balance), plus
 * register/login/logout mutations that keep the `me` cache in sync. A 401 from `GET /users/me`
 * simply means "logged out" — the query resolves to `null` instead of failing.
 *
 * Note: in US1 the backend answers `/users/me` with the `UserSummary` subset (id, username,
 * balanceSisu); US6 extends it to the full private profile. This hook only reads those three
 * fields, so it works unchanged in both phases.
 */
export function useAuth() {
  const queryClient = useQueryClient();

  const meQuery = useQuery({
    queryKey: queryKeys.me,
    queryFn: api.me,
    // A missing/invalid session is a normal state, not a fetch failure.
    retry: false,
    throwOnError: false,
  });

  const register = useMutation({
    mutationFn: (body: RegisterRequest) => api.register(body),
    onSuccess: (summary: UserSummary) => {
      queryClient.setQueryData(queryKeys.me, summary);
    },
  });

  const login = useMutation({
    mutationFn: (body: LoginRequest) => api.login(body),
    onSuccess: (summary: UserSummary) => {
      queryClient.setQueryData(queryKeys.me, summary);
    },
  });

  const logout = useMutation({
    mutationFn: api.logout,
    onSettled: () => {
      queryClient.setQueryData(queryKeys.me, undefined);
      void queryClient.invalidateQueries({ queryKey: queryKeys.me });
    },
  });

  const user = meQuery.status === 'error' ? null : (meQuery.data ?? null);

  return {
    /** The logged-in user, or `null` when logged out (or while the session check 401s). */
    user,
    isLoading: meQuery.isLoading,
    isLoggedIn: user !== null,
    sessionError: meQuery.status === 'error' ? meQuery.error : null,
    register,
    login,
    logout,
  };
}
