import { Link } from 'react-router';
import { formatDate, formatSisu } from '../format';
import { fi } from '../i18n/fi';
import type { Prediction } from '../types';
import { OddsBar } from './OddsBar';
import { StatusBadge } from './StatusBadge';

export function PredictionCard({ p }: { p: Prediction }) {
  return (
    <li className="card prediction-card">
      <div className="card-top">
        <StatusBadge status={p.status} outcome={p.outcome} />
        <span className="muted small">{fi.list.wagers(p.wagerCount)}</span>
      </div>
      <h2 className="card-title">
        <Link to={`/p/${p.id}`}>{p.question}</Link>
      </h2>
      <OddsBar yesProbability={p.yesProbability} />
      <dl className="facts">
        <div>
          <dt>{fi.list.pool}</dt>
          <dd>{formatSisu(p.totalPool)}</dd>
        </div>
        <div>
          <dt>{p.status === 'OPEN' ? fi.list.closes : fi.list.closed}</dt>
          <dd>
            <time dateTime={p.closesAt}>{formatDate(p.closesAt)}</time>
          </dd>
        </div>
      </dl>
    </li>
  );
}
