import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import type { Prediction, ResolutionResult } from '../api';
import { categoryLabels, formatDateTime, formatPercent, formatSisu, formatWhen, outcomeLabels, plural } from '../format';
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
  /** Set when this user resolved it here, to show what was paid. */
  const [resolutionSummary, setResolutionSummary] = useState<ResolutionResult | null>(null);
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
          <span className={`tag tag-outcome-${p.outcome.toLowerCase()}`}>Ratkesi: {outcomeLabels[p.outcome]}</span>
        )}
        {awaitingResolution && <span className="tag tag-status">Suljettu</span>}
      </div>
      <h3 className="card-question">{p.question}</h3>
      <p className="card-description">{p.description}</p>
      {p.resolutionCriteria && (
        <details className="card-criteria">
          <summary>Miten ennuste ratkeaa</summary>
          <p>{p.resolutionCriteria}</p>
        </details>
      )}

      <div className="odds" aria-label={`Todennäköisyys: KYLLÄ ${formatPercent(p.yesPercent)}, EI ${formatPercent(noPercent)}`}>
        <div className="odds-labels">
          <span className="yes">KYLLÄ {formatPercent(p.yesPercent)}</span>
          <span className="no">EI {formatPercent(noPercent)}</span>
        </div>
        <div className="odds-bar">
          <div className="odds-yes" style={{ width: `${p.yesPercent}%` }} />
        </div>
      </div>

      {isOpen && <WagerPanel prediction={p} onWagered={(updated) => onUpdated?.(updated)} />}
      {awaitingResolution && (
        mayResolve
          ? <ResolvePanel prediction={p} onResolved={(result) => { setResolutionSummary(result); onUpdated?.(result.prediction); }} />
          : <p className="wager-closed">Panostus on päättynyt · odottaa ratkaisua</p>
      )}
      {p.status === 'RESOLVED' && p.outcome && (
        <p className={`resolved-banner resolved-${p.outcome.toLowerCase()}`}>
          Ratkesi <strong>{outcomeLabels[p.outcome]}</strong>{p.resolvedAt && <> {formatWhen(p.resolvedAt)}</>}.
          {' '}{outcomeLabels[p.outcome]}-panosten voitot on maksettu.
          {resolutionSummary && (
            <span className="resolved-summary" role="status">
              {resolutionSummary.winningWagers === 0
                ? 'Voittavia panoksia ei ollut, joten mitään ei maksettu.'
                : `Maksettiin ${formatSisu(resolutionSummary.totalPaidOut)} – ${plural(resolutionSummary.winningWagers, 'voittava panos', 'voittavaa panosta')}.`}
            </span>
          )}
        </p>
      )}

      <footer className="card-footer">
        <span>
          Panostettu {formatSisu(p.totalWagered)}
          {p.createdBy && (
            <> · <Link to={`/users/${encodeURIComponent(p.createdBy)}`}>{p.createdBy}</Link></>
          )}
        </span>
        <span>
          <time dateTime={p.closesAt} title={formatDateTime(p.closesAt)}>
            {isOpen ? `Sulkeutuu ${formatWhen(p.closesAt)}` : `Sulkeutui ${formatWhen(p.closesAt)}`}
          </time>
        </span>
      </footer>
    </article>
  );
}
