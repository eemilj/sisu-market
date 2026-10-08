import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, ApiError, tokenStore, type AuthResponse, type User } from './api';

interface AuthState {
  user: User | null;
  /** True until the stored token (if any) has been checked against the backend. */
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  register: (username: string, email: string, password: string) => Promise<void>;
  logout: () => void;
  /** Re-reads the user (and so the balance) from the server. */
  refreshUser: () => Promise<void>;
  /** Applies a balance the server just reported (e.g. after a wager) without another round trip. */
  setBalance: (balance: number) => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(() => tokenStore.get() !== null);

  useEffect(() => {
    if (!tokenStore.get()) return;
    api
      .me()
      .then(setUser)
      .catch((err) => {
        // Expired/invalid token: forget it. Network errors keep the token for the next reload.
        if (err instanceof ApiError && err.status === 401) tokenStore.clear();
      })
      .finally(() => setLoading(false));
  }, []);

  const accept = useCallback((res: AuthResponse) => {
    tokenStore.set(res.token);
    setUser(res.user);
  }, []);

  const refreshUser = useCallback(async () => {
    if (!tokenStore.get()) return;
    try {
      setUser(await api.me());
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        tokenStore.clear();
        setUser(null);
      }
    }
  }, []);

  // Balances can change on the server; pick up the latest when the user returns to the tab.
  useEffect(() => {
    const onFocus = () => void refreshUser();
    window.addEventListener('focus', onFocus);
    return () => window.removeEventListener('focus', onFocus);
  }, [refreshUser]);

  const value = useMemo<AuthState>(
    () => ({
      user,
      loading,
      login: async (username, password) => accept(await api.login(username, password)),
      register: async (username, email, password) => accept(await api.register(username, email, password)),
      logout: () => {
        tokenStore.clear();
        setUser(null);
      },
      refreshUser,
      setBalance: (balance) => setUser((u) => (u ? { ...u, balance } : u)),
    }),
    [user, loading, accept, refreshUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
