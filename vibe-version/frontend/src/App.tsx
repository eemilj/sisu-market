import { Navigate, Route, Routes } from 'react-router-dom';
import Header from './components/Header';
import { useAuth } from './auth';
import { useLiveEvents } from './live';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import PredictionsPage from './pages/PredictionsPage';
import CreatePredictionPage from './pages/CreatePredictionPage';
import ProfilePage from './pages/ProfilePage';
import LeaderboardPage from './pages/LeaderboardPage';

/** Keeps the logged-in user's balance current, whichever tab or device changed it. */
function BalanceSync() {
  const { applyBalance } = useAuth();
  useLiveEvents((event) => {
    if (event.type === 'wallet.transaction') applyBalance(event.data.balance, event.data.balanceVersion);
  });
  return null;
}

/** Old /wallet links: your own profile's transaction tab. */
function MyTransactions() {
  const { user, loading } = useAuth();
  if (loading) return null;
  if (!user) return <Navigate to="/login" replace state={{ from: '/wallet' }} />;
  return <Navigate to={`/users/${encodeURIComponent(user.username)}?tab=transactions`} replace />;
}

export default function App() {
  return (
    <>
      <BalanceSync />
      <Header />
      <main className="container">
        <Routes>
          <Route path="/" element={<PredictionsPage />} />
          <Route path="/predictions/new" element={<CreatePredictionPage />} />
          <Route path="/users/:username" element={<ProfilePage />} />
          <Route path="/leaderboard" element={<LeaderboardPage />} />
          <Route path="/wallet" element={<MyTransactions />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </>
  );
}
