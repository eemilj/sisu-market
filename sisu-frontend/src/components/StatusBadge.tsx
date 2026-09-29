import type { PredictionStatus } from '../api/types';
import { fi } from '../i18n/strings.fi';

const styles: Record<PredictionStatus, string> = {
  OPEN: 'bg-emerald-100 text-emerald-900',
  CLOSED: 'bg-amber-100 text-amber-900',
  RESOLVED: 'bg-sky-100 text-sky-900',
  CANCELLED: 'bg-slate-200 text-slate-800',
};

/** A prediction's lifecycle status as a labelled pill. */
export default function StatusBadge({ status }: { status: PredictionStatus }) {
  return (
    <span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${styles[status]}`}>
      {fi.predictionStatus[status]}
    </span>
  );
}
