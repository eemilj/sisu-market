import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { api, type Category, type Prediction, type PredictionStatus } from '../api';
import { useAuth } from '../auth';
import { categoryLabels } from '../format';
import PredictionCard from '../components/PredictionCard';

const categories: (Category | 'ALL')[] = ['ALL', 'POLITICS', 'SPORTS', 'ECONOMY', 'WEATHER', 'CULTURE'];
const statuses: { value: PredictionStatus | 'ALL'; label: string }[] = [
  { value: 'OPEN', label: 'Open' },
  { value: 'CLOSED', label: 'Closed' },
  { value: 'RESOLVED', label: 'Resolved' },
  { value: 'ALL', label: 'All' },
];

export default function PredictionsPage() {
  const { user } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  // Set when arriving from the create page. Captured once, then cleared from history so a reload doesn't repeat it.
  const [justCreated, setJustCreated] = useState<Prediction | null>(() => location.state?.created ?? null);
  const [category, setCategory] = useState<Category | 'ALL'>('ALL');
  const [status, setStatus] = useState<PredictionStatus | 'ALL'>('OPEN');
  const [predictions, setPredictions] = useState<Prediction[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (location.state?.created) navigate(location.pathname, { replace: true, state: null });
  }, [location, navigate]);

  // Bring the new card into view once the list has rendered.
  useEffect(() => {
    if (!justCreated || !predictions) return;
    document.getElementById(`prediction-${justCreated.id}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }, [justCreated, predictions]);

  useEffect(() => {
    let cancelled = false;
    setError(null);
    api
      .predictions({ category, status })
      .then((list) => !cancelled && setPredictions(list))
      .catch((err: Error) => !cancelled && setError(err.message));
    return () => {
      cancelled = true;
    };
  }, [category, status]);

  return (
    <>
      <section className="hero">
        <h1>Predict Finland's future. Risk nothing but your Sisu.</h1>
        <p>
          Trade on Finnish politics, sports, weather and more with virtual Sisu currency.
          {!user && (
            <>
              {' '}
              <Link to="/register">Sign up</Link> and get 1 000 Sisu to start.
            </>
          )}
        </p>
      </section>

      {justCreated && (
        <div className="notice" role="status">
          <span>
            Your prediction <strong>“{justCreated.question}”</strong> is live.
          </span>
          <button className="notice-close" onClick={() => setJustCreated(null)} aria-label="Dismiss">
            ×
          </button>
        </div>
      )}

      <div className="filters">
        <div className="chips" role="group" aria-label="Category">
          {categories.map((c) => (
            <button
              key={c}
              className={`chip ${category === c ? 'chip-active' : ''}`}
              onClick={() => setCategory(c)}
              aria-pressed={category === c}
            >
              {c === 'ALL' ? 'All topics' : categoryLabels[c]}
            </button>
          ))}
        </div>
        <div className="tabs" role="group" aria-label="Status">
          {statuses.map((s) => (
            <button
              key={s.value}
              className={`tab ${status === s.value ? 'tab-active' : ''}`}
              onClick={() => setStatus(s.value)}
              aria-pressed={status === s.value}
            >
              {s.label}
            </button>
          ))}
        </div>
      </div>

      {error ? (
        <p className="alert">{error}</p>
      ) : predictions === null ? (
        <p className="muted">Loading predictions…</p>
      ) : predictions.length === 0 ? (
        <p className="muted">No predictions match these filters.</p>
      ) : (
        <div className="grid">
          {predictions.map((p) => (
            <PredictionCard
              key={p.id}
              prediction={p}
              highlighted={p.id === justCreated?.id}
              onUpdated={(updated) =>
                setPredictions((list) => list?.map((x) => (x.id === updated.id ? updated : x)) ?? list)
              }
            />
          ))}
        </div>
      )}
    </>
  );
}
