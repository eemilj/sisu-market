import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import type { Prediction } from '../api';
import { categoryLabels, formatDate, formatSisu } from '../format';
import WagerPanel from './WagerPanel';
import ResolvePanel from './ResolvePanel';
import { useAuth } from '../auth';
import { useIsOpen } from '../useIsOpen';

interface Props {
  prediction: Prediction;
  highlighted?: boolean;
  onUpdated?: (updated: Prediction) => void;
}

export default function PredictionCard({ prediction: p, highlighted = false, onUpdated }: Props) {
  const noPercent = 100 - p.yesPercent;
  const { user } = useAuth();
  const isOpen = useIsOpen(p);
  const awaitingResolution = !isOpen && p.status !== 'RESOLVED';
  // The server also refuses creators who wagered on their own prediction; it explains why if they try.
  const mayResolve = !!user && (user.admin || user.username === p.createdBy);

  // Briefly flash the card when its odds change while it is on screen (e.g. someone else wagered).
  const [flash, setFlash] = useState(false);
  const shownVersion = useRef(p.version);
  useEffect(() => {
    if (p.version === shownVersion.current) return;
    shownVersion.current = p.version;
    setFlash(true);
    const timer = setTimeout(() => setFlash(false), 1200);
    return () => clearTimeout(timer);
  }, [p.version]);

  return (
    <article
      id={`prediction-${p.id}`}
      className={`card ${highlighted ? 'card-highlight' : ''} ${flash ? 'card-flash' : ''}`}
    >
      <div className="card-top">
        <span className={`tag tag-${p.category.toLowerCase()}`}>{categoryLabels[p.category]}</span>
        {p.status === 'RESOLVED' && p.outcome && (
          <span className={`tag tag-outcome-${p.outcome.toLowerCase()}`}>Resolved {p.outcome}</span>
        )}
        {awaitingResolution && <span className="tag tag-status">Closed</span>}
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

      {isOpen && <WagerPanel prediction={p} onWagered={(updated) => onUpdated?.(updated)} />}
      {awaitingResolution && (
        mayResolve
          ? <ResolvePanel prediction={p} onResolved={(updated) => onUpdated?.(updated)} />
          : <p className="wager-closed">Wagering closed · awaiting resolution</p>
      )}
      {p.status === 'RESOLVED' && p.outcome && (
        <p className={`resolved-banner resolved-${p.outcome.toLowerCase()}`}>
          Resolved <strong>{p.outcome}</strong>{p.resolvedAt && <> on {formatDate(p.resolvedAt)}</>}. {p.outcome} wagers were paid out.
        </p>
      )}

      <footer className="card-footer">
        <span>
          {formatSisu(p.totalWagered)} wagered
          {p.createdBy && (
            <> · by <Link to={`/users/${encodeURIComponent(p.createdBy)}`}>{p.createdBy}</Link></>
          )}
        </span>
        <span>
          {isOpen ? `Closes ${formatDate(p.closesAt)}` : `Closed ${formatDate(p.closesAt)}`}
        </span>
      </footer>
    </article>
  );
}
