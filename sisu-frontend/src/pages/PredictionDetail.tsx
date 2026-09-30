import { Link, useParams } from 'react-router';
import { ApiError } from '../api/client';
import OddsBar from '../components/OddsBar';
import StatusBadge from '../components/StatusBadge';
import { usePrediction } from '../hooks/usePredictions';
import { errorMessage, fi } from '../i18n/strings.fi';

const backLinkClass =
  'text-sky-800 underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700';

/**
 * One prediction's public detail view (User Story 2, scenario 2; FR-015, FR-017): full description,
 * resolution criteria, creator and current odds. User Story 3 adds the wager form here.
 */
export default function PredictionDetail() {
  const { id = '' } = useParams();
  const query = usePrediction(id);

  if (query.isLoading) {
    return <p aria-live="polite">{fi.app.loading}</p>;
  }

  if (query.isError) {
    const notFound = query.error instanceof ApiError && query.error.status === 404;
    return (
      <section aria-labelledby="prediction-error-title">
        <h1 id="prediction-error-title" className="text-2xl font-bold">
          {notFound ? fi.predictions.notFoundTitle : fi.predictions.loadFailed}
        </h1>
        <p role="alert" className="mt-2">
          {notFound
            ? fi.predictions.notFoundBody
            : query.error instanceof ApiError
              ? errorMessage(query.error.code)
              : fi.errors.unknown}
        </p>
        <Link to="/predictions" className={`mt-4 inline-block ${backLinkClass}`}>
          {fi.predictions.backToList}
        </Link>
      </section>
    );
  }

  const prediction = query.data;
  if (!prediction) {
    return null;
  }

  return (
    <article aria-labelledby="prediction-title" className="flex flex-col gap-6">
      <p>
        <Link to="/predictions" className={backLinkClass}>
          {fi.predictions.backToList}
        </Link>
      </p>

      <header className="flex flex-col gap-2">
        <div>
          <StatusBadge status={prediction.status} />
        </div>
        <h1 id="prediction-title" className="text-2xl font-bold">
          {prediction.question}
        </h1>
      </header>

      <section
        aria-labelledby="odds-title"
        className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm"
      >
        <h2 id="odds-title" className="mb-3 text-lg font-semibold">
          {fi.odds.title}
        </h2>
        <OddsBar
          oddsYesPercent={prediction.oddsYesPercent}
          oddsNoPercent={prediction.oddsNoPercent}
        />
        {prediction.winningOutcome && (
          <p className="mt-3 font-medium">
            {fi.predictions.winningOutcome}: {fi.outcome[prediction.winningOutcome]}
          </p>
        )}
      </section>

      <dl className="grid gap-4 sm:grid-cols-3">
        <div>
          <dt className="text-sm text-slate-600">{fi.predictions.totalWagered}</dt>
          <dd className="font-medium">{fi.sisu.amount(prediction.totalSisuWagered)}</dd>
        </div>
        <div>
          <dt className="text-sm text-slate-600">{fi.predictions.closingAt}</dt>
          <dd className="font-medium">
            <time dateTime={prediction.closingAt}>{fi.date.dateTime(prediction.closingAt)}</time>
          </dd>
        </div>
        <div>
          <dt className="text-sm text-slate-600">{fi.predictions.creator}</dt>
          <dd className="font-medium">
            {prediction.creatorUsername ?? fi.predictions.deletedCreator}
          </dd>
        </div>
      </dl>

      <section aria-labelledby="description-title">
        <h2 id="description-title" className="text-lg font-semibold">
          {fi.predictions.description}
        </h2>
        <p className="mt-1 whitespace-pre-line text-slate-800">{prediction.description}</p>
      </section>

      <section aria-labelledby="criteria-title">
        <h2 id="criteria-title" className="text-lg font-semibold">
          {fi.predictions.resolutionCriteria}
        </h2>
        <p className="mt-1 whitespace-pre-line text-slate-800">{prediction.resolutionCriteria}</p>
      </section>
    </article>
  );
}
