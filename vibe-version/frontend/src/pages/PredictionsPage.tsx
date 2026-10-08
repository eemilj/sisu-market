import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { api, type Category, type Prediction, type PredictionStatus } from '../api';
import { useAuth } from '../auth';
import { useLiveEvents } from '../live';
import { categoryLabels } from '../format';
import PredictionCard from '../components/PredictionCard';

const categories: (Category | 'ALL')[] = ['ALL', 'POLITICS', 'SPORTS', 'ECONOMY', 'WEATHER', 'CULTURE'];
const statuses: { value: PredictionStatus | 'ALL'; label: string }[] = [
  { value: 'OPEN', label: 'Open' },
  { value: 'CLOSED', label: 'Closed' },
  { value: 'RESOLVED', label: 'Resolved' },
  { value: 'ALL', label: 'All' },
];

type Filters = { category: Category | 'ALL'; status: PredictionStatus | 'ALL' };

const matches = (p: Prediction, f: Filters) =>
  (f.category === 'ALL' || p.category === f.category) && (f.status === 'ALL' || p.status === f.status);

/** Same order as the API: soonest closing first. */
const byClosingTime = (a: Prediction, b: Prediction) => a.closesAt.localeCompare(b.closesAt);

/** Inserts or replaces `p`, keeping whichever copy has the higher version. */
function upsert(list: Prediction[], p: Prediction): Prediction[] {
  const existing = list.find((x) => x.id === p.id);
  if (!existing) return [...list, p].sort(byClosingTime);
  if (p.version <= existing.version) return list;
  return list.map((x) => (x.id === p.id ? p : x));
}

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
  /** Bumped on every live (re)connect so the list is reloaded and nothing missed while offline is lost. */
  const [resync, setResync] = useState(0);

  useLiveEvents((event) => {
    if (event.type === 'ready') {
      setResync((n) => n + 1);
    } else if (event.type === 'prediction.created' || event.type === 'prediction.updated') {
      const p = event.data;
      setPredictions((list) => {
        if (!list) return list;
        if (matches(p, { category, status })) return upsert(list, p);
        return list.filter((x) => x.id !== p.id);
      });
    }
  });

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
      // Keep any copy a live event already made newer than this response.
      .then((fresh) => !cancelled && setPredictions((current) => {
        const known = new Map((current ?? []).map((p) => [p.id, p]));
        return fresh.map((p) => {
          const live = known.get(p.id);
          return live && live.version > p.version ? live : p;
        });
      }))
      .catch((err: Error) => !cancelled && setError(err.message));
    return () => {
      cancelled = true;
    };
  }, [category, status, resync]);

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
              onUpdated={(updated) => setPredictions((list) => (list ? upsert(list, updated) : list))}
            />
          ))}
        </div>
      )}
    </>
  );
}
