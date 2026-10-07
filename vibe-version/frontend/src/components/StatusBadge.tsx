import { fi } from '../i18n/fi';
import type { PredictionStatus, Side } from '../types';

export function StatusBadge({ status, outcome }: { status: PredictionStatus; outcome?: Side | null }) {
  return (
    <span className={`badge badge-${status.toLowerCase()}`}>
      {fi.status[status]}
      {status === 'RESOLVED' && outcome ? `: ${fi.side[outcome]}` : ''}
    </span>
  );
}
