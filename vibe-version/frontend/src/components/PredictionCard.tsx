import type { Prediction } from '../api';
import { categoryLabels, formatDate, formatSisu } from '../format';
import WagerPanel from './WagerPanel';

const statusLabels = { OPEN: 'Closes', CLOSED: 'Closed', RESOLVED: 'Resolved' } as const;

interface Props {
  prediction: Prediction;
  highlighted?: boolean;
  onUpdated?: (updated: Prediction) => void;
}

export default function PredictionCard({ prediction: p, highlighted = false, onUpdated }: Props) {
  const noPercent = 100 - p.yesPercent;
  return (
    <article id={`prediction-${p.id}`} className={`card ${highlighted ? 'card-highlight' : ''}`}>
      <div className="card-top">
        <span className={`tag tag-${p.category.toLowerCase()}`}>{categoryLabels[p.category]}</span>
        {p.status !== 'OPEN' && <span className="tag tag-status">{p.status.toLowerCase()}</span>}
      </div>
      <h3 className="card-question">{p.question}</h3>
      <p className="card-description">{p.description}</p>
      {p.resolutionCriteria && (
        <details className="card-criteria">
          <summary>How it resolves</summary>
          <p>{p.resolutionCriteria}</p>
        </details>
      )}

      <div className="odds" aria-label={`Yes ${p.yesPercent} percent, No ${noPercent} percent`}>
        <div className="odds-labels">
          <span className="yes">Yes {p.yesPercent}%</span>
          <span className="no">No {noPercent}%</span>
        </div>
        <div className="odds-bar">
          <div className="odds-yes" style={{ width: `${p.yesPercent}%` }} />
        </div>
      </div>

      <WagerPanel prediction={p} onWagered={(updated) => onUpdated?.(updated)} />

      <footer className="card-footer">
        <span>
          {formatSisu(p.volume)} staked
          {p.createdBy && <> · by {p.createdBy}</>}
        </span>
        <span>
          {p.status === 'RESOLVED' && p.outcome
            ? `Resolved ${p.outcome}`
            : `${statusLabels[p.status]} ${formatDate(p.closesAt)}`}
        </span>
      </footer>
    </article>
  );
}
