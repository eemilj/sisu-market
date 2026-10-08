import { Navigate, Route, Routes } from 'react-router-dom';
import Header from './components/Header';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import PredictionsPage from './pages/PredictionsPage';
import CreatePredictionPage from './pages/CreatePredictionPage';
import WalletPage from './pages/WalletPage';

export default function App() {
  return (
    <>
      <Header />
      <main className="container">
        <Routes>
          <Route path="/" element={<PredictionsPage />} />
          <Route path="/predictions/new" element={<CreatePredictionPage />} />
          <Route path="/wallet" element={<WalletPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </>
  );
}
