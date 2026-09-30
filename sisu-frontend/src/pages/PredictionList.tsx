import { Link, useSearchParams } from 'react-router';
import { ApiError } from '../api/client';
import type { PredictionStatus, PredictionSummary } from '../api/types';
import OddsBar from '../components/OddsBar';
import StatusBadge from '../components/StatusBadge';
import { usePredictionList } from '../hooks/usePredictions';
import { errorMessage, fi } from '../i18n/strings.fi';

const STATUSES: PredictionStatus[] = ['OPEN', 'CLOSED', 'RESOLVED', 'CANCELLED'];
const ALL = 'ALL';
type Filter = PredictionStatus | typeof ALL;

/** `?status=` in the URL, so a filtered list can be linked and survives reloads. Defaults to open. */
function useStatusFilter(): [Filter, (filter: Filter) => void] {
  const [params, setParams] = useSearchParams();
  const raw = params.get('status');
  const filter: Filter =
    raw === ALL ? ALL : STATUSES.includes(raw as PredictionStatus) ? (raw as Filter) : 'OPEN';
  const setFilter = (next: Filter) =>
    setParams(next === 'OPEN' ? {} : { status: next }, { replace: true });
  return [filter, setFilter];
}

/**
 * The public prediction list (User Story 2, scenarios 1 and 3; FR-016, FR-018): question, odds,
 * total wagered and closing date for each prediction, filterable by status. No login required.
 */
export default function PredictionList() {
  const [filter, setFilter] = useStatusFilter();
  const list = usePredictionList(filter === ALL ? undefined : filter);

  return (
    <section aria-labelledby="predictions-title">
      <h1 id="predictions-title" className="text-2xl font-bold">
        {fi.predictions.listTitle}
      </h1>
      <p className="mt-2 text-slate-700">{fi.predictions.listIntro}</p>

      <div
        role="group"
        aria-label={fi.predictions.filterLabel}
        className="mt-4 flex flex-wrap gap-2"
      >
        {[...STATUSES, ALL].map((option) => (
          <button
            key={option}
            type="button"
            aria-pressed={filter === option}
            onClick={() => setFilter(option as Filter)}
            className={[
              'rounded-full border px-3 py-1 text-sm font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700',
              filter === option
                ? 'border-sky-800 bg-sky-800 text-white'
                : 'border-slate-300 bg-white text-slate-800 hover:bg-slate-100',
            ].join(' ')}
          >
            {option === ALL
              ? fi.predictions.filterAll
              : fi.predictionStatus[option as PredictionStatus]}
          </button>
        ))}
      </div>

      <div className="mt-6" aria-live="polite" aria-busy={list.isLoading}>
        {list.isLoading && <p>{fi.app.loading}</p>}

        {list.isError && (
          <div
            role="alert"
            className="rounded border border-red-300 bg-red-50 px-3 py-2 text-red-900"
          >
            <p>
              {fi.predictions.loadFailed}{' '}
              {list.error instanceof ApiError ? errorMessage(list.error.code) : ''}
            </p>
            <button
              type="button"
              onClick={() => void list.refetch()}
              className="mt-2 font-medium underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700"
            >
              {fi.predictions.retry}
            </button>
          </div>
        )}

        {list.isSuccess && list.data.length === 0 && (
          <p className="text-slate-700">{fi.predictions.empty}</p>
        )}

        {list.isSuccess && list.data.length > 0 && (
          <ul className="grid gap-4 sm:grid-cols-2">
            {list.data.map((prediction) => (
              <li key={prediction.id}>
                <PredictionCard prediction={prediction} />
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}

function PredictionCard({ prediction }: { prediction: PredictionSummary }) {
  return (
    <article className="flex h-full flex-col gap-3 rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-start justify-between gap-2">
        <h2 className="text-lg font-semibold">
          <Link
            to={`/predictions/${encodeURIComponent(prediction.id)}`}
            className="text-sky-900 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700"
          >
            {prediction.question}
          </Link>
        </h2>
        <StatusBadge status={prediction.status} />
      </div>
      <OddsBar
        oddsYesPercent={prediction.oddsYesPercent}
        oddsNoPercent={prediction.oddsNoPercent}
      />
      <dl className="mt-auto grid grid-cols-2 gap-2 text-sm">
        <div>
          <dt className="text-slate-600">{fi.predictions.totalWagered}</dt>
          <dd className="font-medium">{fi.sisu.amount(prediction.totalSisuWagered)}</dd>
        </div>
        <div>
          <dt className="text-slate-600">{fi.predictions.closingAt}</dt>
          <dd className="font-medium">
            <time dateTime={prediction.closingAt}>{fi.date.dateTime(prediction.closingAt)}</time>
          </dd>
        </div>
      </dl>
    </article>
  );
}
