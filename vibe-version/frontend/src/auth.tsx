import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, setUnauthorizedHandler, tokenStore } from './api';
import type { Session, User } from './types';

interface AuthState {
  user: User | null;
  ready: boolean;
  login: (username: string, password: string) => Promise<void>;
  register: (username: string, password: string) => Promise<void>;
  logout: () => void;
  refresh: () => Promise<void>;
  setBalance: (balance: number) => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [ready, setReady] = useState(false);

  const logout = useCallback(() => {
    tokenStore.clear();
    setUser(null);
  }, []);

  const refresh = useCallback(async () => {
    if (!tokenStore.get()) return;
    try {
      setUser(await api.me());
    } catch {
      // A 401 already logged us out via the unauthorized handler; other errors keep the old state.
    }
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(logout);
    refresh().finally(() => setReady(true));
  }, [logout, refresh]);

  const accept = (s: Session) => {
    tokenStore.set(s.token);
    setUser(s.user);
  };

  const value = useMemo<AuthState>(
    () => ({
      user,
      ready,
      login: async (u, p) => accept(await api.login(u, p)),
      register: async (u, p) => accept(await api.register(u, p)),
      logout,
      refresh,
      setBalance: (balance) => setUser((cur) => (cur ? { ...cur, balance } : cur)),
    }),
    [user, ready, logout, refresh],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth outside AuthProvider');
  return ctx;
}
