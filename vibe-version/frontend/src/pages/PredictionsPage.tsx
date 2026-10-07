import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { api, errorMessage } from '../api';
import { useAuth } from '../auth';
import { ErrorBox, Loading } from '../components/Feedback';
import { PredictionCard } from '../components/PredictionCard';
import { fi } from '../i18n/fi';
import { useLivePredictions } from '../live';
import type { Prediction, PredictionStatus } from '../types';

const FILTERS: (PredictionStatus | 'ALL')[] = ['OPEN', 'CLOSED', 'RESOLVED', 'ALL'];

export function PredictionsPage() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const raw = params.get('tila')?.toUpperCase() ?? 'OPEN';
  const filter = (FILTERS as string[]).includes(raw) ? (raw as PredictionStatus | 'ALL') : 'OPEN';
  const status = filter === 'ALL' ? undefined : filter;

  const [items, setItems] = useState<Prediction[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setItems(null);
    setError(null);
    api
      .predictions(status)
      .then((list) => !cancelled && setItems(list))
      .catch((e) => !cancelled && setError(errorMessage(e)));
    return () => {
      cancelled = true;
    };
  }, [status]);

  useLivePredictions((p) => {
    setItems((cur) => {
      if (!cur) return cur;
      const matches = !status || p.status === status;
      const exists = cur.some((x) => x.id === p.id);
      if (exists) return matches ? cur.map((x) => (x.id === p.id ? p : x)) : cur.filter((x) => x.id !== p.id);
      return matches ? [p, ...cur] : cur;
    });
  });

  return (
    <section aria-labelledby="list-title">
      <div className="page-head">
        <div>
          <h1 id="list-title">{fi.list.title}</h1>
          <p className="muted">{fi.app.tagline}</p>
        </div>
        <p className="live-dot small">{fi.list.live}</p>
      </div>

      <div className="filters" role="group" aria-label={fi.list.filterLabel}>
        {FILTERS.map((f) => (
          <button
            key={f}
            type="button"
            className={`chip ${filter === f ? 'chip-active' : ''}`}
            aria-pressed={filter === f}
            onClick={() => setParams(f === 'OPEN' ? {} : { tila: f.toLowerCase() })}
          >
            {fi.status[f]}
          </button>
        ))}
      </div>

      <ErrorBox message={error} />
      {!items && !error && <Loading />}
      {items && items.length === 0 && (
        <div className="empty">
          <p>{fi.list.empty}</p>
          {user && (
            <Link to="/new" className="btn btn-primary">
              {fi.list.emptyCta}
            </Link>
          )}
        </div>
      )}
      {items && items.length > 0 && (
        <ul className="grid">
          {items.map((p) => (
            <PredictionCard key={p.id} p={p} />
          ))}
        </ul>
      )}
    </section>
  );
}
