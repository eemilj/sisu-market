import { formatPercent } from '../format';
import { fi } from '../i18n/fi';

export function OddsBar({ yesProbability, large = false }: { yesProbability: number; large?: boolean }) {
  const yes = Math.round(yesProbability * 100);
  return (
    <div className={`odds ${large ? 'odds-large' : ''}`}>
      <div className="odds-labels">
        <span className="yes-text">
          {fi.side.YES} {formatPercent(yesProbability)}
        </span>
        <span className="no-text">
          {fi.side.NO} {formatPercent(1 - yesProbability)}
        </span>
      </div>
      <div
        className="odds-bar"
        role="meter"
        aria-label={fi.list.yesChance}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={yes}
        aria-valuetext={formatPercent(yesProbability)}
      >
        <div className="odds-yes" style={{ width: `${yes}%` }} />
      </div>
    </div>
  );
}
