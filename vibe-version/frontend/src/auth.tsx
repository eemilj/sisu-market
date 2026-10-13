import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { api, ApiError, tokenStore, type AuthResponse, type User } from './api';

interface AuthState {
  user: User | null;
  /** True until the stored token (if any) has been checked against the backend. */
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  register: (username: string, email: string, password: string) => Promise<void>;
  /** Logs out and always lands on the prediction list (with a "logged out" notice), from any page. */
  logout: () => void;
  /** Re-reads the user (and so the balance) from the server. */
  refreshUser: () => Promise<void>;
  /**
   * Applies a balance the server reported (wager response or live event). Ignored if it is older than the
   * balance already shown, since updates can arrive out of order.
   */
  applyBalance: (balance: number, balanceVersion: number) => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const location = useLocation();
  const [user, setUser] = useState<User | null>(null);
  /** Between clicking "log out" and the router arriving at the public page. */
  const [loggingOut, setLoggingOut] = useState(false);

  /*
   * React Router applies navigations as a (low-priority) transition, so clearing the user in the same click
   * would first re-render the *current* page without a user; pages that need a login would then start their
   * own redirect to /login and race this one. The user is therefore cleared only once the router is on "/".
   * The token is removed from storage immediately.
   */
  useEffect(() => {
    if (!loggingOut) return;
    if (location.pathname === '/') {
      setUser(null);
      setLoggingOut(false);
      return;
    }
    // Safety net: never stay half-logged-in if the navigation doesn't happen for some reason.
    const fallback = setTimeout(() => {
      setUser(null);
      setLoggingOut(false);
    }, 1000);
    return () => clearTimeout(fallback);
  }, [loggingOut, location.pathname]);
  const [loading, setLoading] = useState(() => tokenStore.get() !== null);

  useEffect(() => {
    const token = tokenStore.get();
    if (!token) return;
    api
      .me()
      // Ignore the answer if the user logged out (or in as someone else) meanwhile.
      .then((u) => tokenStore.get() === token && setUser(u))
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
    const token = tokenStore.get();
    if (!token) {
      setUser(null);
      return;
    }
    try {
      const fresh = await api.me();
      // A request that started before a logout must not log the user back in.
      if (tokenStore.get() === token) setUser(fresh);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401 && tokenStore.get() === token) {
        tokenStore.clear();
        setUser(null);
      }
    }
  }, []);

  // Logging in or out in another tab applies here too, so no tab is left half-logged-in.
  useEffect(() => {
    const onStorage = (e: StorageEvent) => {
      if (e.key !== null && e.key !== tokenStore.key) return;
      if (tokenStore.get()) void refreshUser();
      else setUser(null);
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, [refreshUser]);

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
        setLoggingOut(true);
        // A normal history entry, so the browser's Back button returns to the page you were on.
        navigate('/', { state: { loggedOut: true } });
      },
      refreshUser,
      applyBalance: (balance, balanceVersion) =>
        setUser((u) => (u && balanceVersion > u.balanceVersion ? { ...u, balance, balanceVersion } : u)),
    }),
    [user, loading, accept, refreshUser, navigate],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
