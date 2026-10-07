import type { ReactNode } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router';
import { AuthProvider, useAuth } from './auth';
import { Loading } from './components/Feedback';
import { Layout } from './components/Layout';
import { useLivePredictions } from './live';
import { AuthPage } from './pages/AuthPage';
import { CreatePage } from './pages/CreatePage';
import { LeaderboardPage } from './pages/LeaderboardPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { PredictionPage } from './pages/PredictionPage';
import { PredictionsPage } from './pages/PredictionsPage';
import { ProfilePage } from './pages/ProfilePage';

function RequireAuth({ children }: { children: ReactNode }) {
  const { user, ready } = useAuth();
  if (!ready) return <Loading />;
  return user ? children : <Navigate to="/login" replace />;
}

/** Payouts can land at any time when someone else resolves a prediction; keep the balance fresh. */
function BalanceSync() {
  const { user, refresh } = useAuth();
  useLivePredictions((p) => {
    if (user && p.status === 'RESOLVED') void refresh();
  });
  return null;
}

export function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <BalanceSync />
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<PredictionsPage />} />
            <Route path="p/:id" element={<PredictionPage />} />
            <Route
              path="new"
              element={
                <RequireAuth>
                  <CreatePage />
                </RequireAuth>
              }
            />
            <Route path="leaderboard" element={<LeaderboardPage />} />
            <Route path="u/:username" element={<ProfilePage />} />
            <Route path="login" element={<AuthPage key="login" mode="login" />} />
            <Route path="register" element={<AuthPage key="register" mode="register" />} />
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
