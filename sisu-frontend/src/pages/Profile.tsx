import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { ApiError, api, queryKeys } from '../api/client';
import type { PredictionSummary, Transaction, Wager } from '../api/types';
import { useAuth } from '../hooks/useAuth';
import { useMyWagers } from '../hooks/useWager';
import { errorMessage, fi } from '../i18n/strings.fi';
import StatusBadge from '../components/StatusBadge';
import OddsBar from '../components/OddsBar';

const linkClass =
  'text-sky-800 underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700';

/**
 * The logged-in user's own profile (FR-030). User Story 3 provides the open wagers with their
 * potential payout (FR-024); User Story 6 adds balance, created predictions and transaction history.
 */
export default function Profile() {
  const { user, isLoading } = useAuth();

  if (isLoading) {
    return <p aria-live="polite">{fi.app.loading}</p>;
  }

  if (!user) {
    return (
      <section aria-labelledby="profile-title" className="mx-auto max-w-2xl">
        <h1 id="profile-title" className="text-2xl font-bold">
          {fi.profile.title}
        </h1>
        <p className="mt-2">{fi.profile.loginRequired}</p>
        <Link to="/login" className={`mt-4 inline-block ${linkClass}`}>
          {fi.nav.login}
        </Link>
      </section>
    );
  }

  const profileUser = user as {
    balanceSisu: number;
    createdPredictions?: PredictionSummary[];
  };

  return (
    <section aria-labelledby="profile-title" className="flex flex-col gap-8">
      <h1 id="profile-title" className="text-2xl font-bold">
        {fi.profile.title}
      </h1>

      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-sm font-medium text-slate-600">{fi.profile.balanceTitle}</h2>
        <p className="mt-1 text-3xl font-bold text-slate-900">
          {fi.sisu.amount(profileUser.balanceSisu)}
        </p>
      </div>

      <CreatedPredictions predictions={profileUser.createdPredictions ?? []} />
      <OpenWagers />
      <TransactionHistory />
    </section>
  );
}

function CreatedPredictions({ predictions }: { predictions: PredictionSummary[] }) {
  let body;
  if (predictions.length === 0) {
    body = <p>{fi.profile.noCreatedPredictions}</p>;
  } else {
    body = (
      <ul className="flex flex-col gap-4">
        {predictions.map((p) => (
          <li key={p.id}>
            <article className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex items-start justify-between gap-2">
                <h3 className="font-medium">
                  <Link to={`/predictions/${encodeURIComponent(p.id)}`} className={linkClass}>
                    {p.question}
                  </Link>
                </h3>
                <StatusBadge status={p.status} />
              </div>
              <div className="mt-3">
                <OddsBar oddsYesPercent={p.oddsYesPercent} oddsNoPercent={p.oddsNoPercent} />
              </div>
              <dl className="mt-3 flex flex-wrap gap-x-6 gap-y-1 text-sm text-slate-600">
                <div>
                  <dt className="inline">{fi.predictions.totalWagered}: </dt>
                  <dd className="inline font-medium text-slate-900">
                    {fi.sisu.amount(p.totalSisuWagered)}
                  </dd>
                </div>
                <div>
                  <dt className="inline">{fi.predictions.closingAt}: </dt>
                  <dd className="inline font-medium text-slate-900">
                    <time dateTime={p.closingAt}>{fi.date.dateTime(p.closingAt)}</time>
                  </dd>
                </div>
              </dl>
            </article>
          </li>
        ))}
      </ul>
    );
  }

  return (
    <section aria-labelledby="created-predictions-title">
      <h2 id="created-predictions-title" className="text-lg font-semibold">
        {fi.profile.createdPredictionsTitle}
      </h2>
      <p className="mb-3 text-sm text-slate-600">{fi.profile.createdPredictionsIntro}</p>
      {body}
    </section>
  );
}

function TransactionHistory() {
  const query = useQuery<Transaction[]>({
    queryKey: queryKeys.myTransactions,
    queryFn: api.myTransactions,
  });

  let body;
  if (query.isLoading) {
    body = <p aria-live="polite">{fi.app.loading}</p>;
  } else if (query.isError) {
    body = (
      <p role="alert">
        {fi.profile.loadTransactionsFailed}{' '}
        {query.error instanceof ApiError ? errorMessage(query.error.code) : fi.errors.unknown}
      </p>
    );
  } else if (!query.data || query.data.length === 0) {
    body = <p>{fi.profile.noTransactions}</p>;
  } else {
    body = (
      <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-sm">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-slate-700">
            <tr>
              <th scope="col" className="p-3">
                {fi.profile.reason}
              </th>
              <th scope="col" className="p-3">
                {fi.profile.amount}
              </th>
              <th scope="col" className="p-3">
                {fi.profile.placedAt}
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-200">
            {query.data.map((tx) => {
              const isCredit = tx.direction === 'CREDIT';
              const reasonKey = tx.reason as keyof typeof fi.profile.transactionReason;
              const reasonText =
                reasonKey in fi.profile.transactionReason
                  ? fi.profile.transactionReason[reasonKey]
                  : tx.reason;
              return (
                <tr key={tx.id}>
                  <td className="p-3 font-medium text-slate-900">{reasonText}</td>
                  <td
                    className={`p-3 font-semibold ${
                      isCredit ? 'text-emerald-600' : 'text-slate-900'
                    }`}
                  >
                    {isCredit ? '+' : '-'}
                    {fi.sisu.amount(tx.amountSisu)}
                  </td>
                  <td className="p-3 text-slate-600">
                    <time dateTime={tx.createdAt}>{fi.date.dateTime(tx.createdAt)}</time>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    );
  }

  return (
    <section aria-labelledby="transactions-title">
      <h2 id="transactions-title" className="text-lg font-semibold">
        {fi.profile.transactionsTitle}
      </h2>
      <p className="mb-3 text-sm text-slate-600">{fi.profile.transactionsIntro}</p>
      {body}
    </section>
  );
}

function OpenWagers() {
  const query = useMyWagers(true);

  let body;
  if (query.isLoading) {
    body = <p aria-live="polite">{fi.app.loading}</p>;
  } else if (query.isError) {
    body = (
      <p role="alert">
        {fi.profile.loadFailed}{' '}
        {query.error instanceof ApiError ? errorMessage(query.error.code) : fi.errors.unknown}
      </p>
    );
  } else if (!query.data || query.data.length === 0) {
    body = <p>{fi.profile.noOpenWagers}</p>;
  } else {
    body = (
      <ul className="flex flex-col gap-3">
        {query.data.map((wager) => (
          <li key={wager.id}>
            <OpenWager wager={wager} />
          </li>
        ))}
      </ul>
    );
  }

  return (
    <section aria-labelledby="open-wagers-title">
      <h2 id="open-wagers-title" className="text-lg font-semibold">
        {fi.profile.openWagersTitle}
      </h2>
      <p className="mb-3 text-sm text-slate-600">{fi.profile.openWagersIntro}</p>
      {body}
    </section>
  );
}

function OpenWager({ wager }: { wager: Wager }) {
  return (
    <article
      aria-label={wager.predictionQuestion ?? fi.profile.unknownPrediction}
      className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm"
    >
      <h3 className="font-medium">
        <Link to={`/predictions/${encodeURIComponent(wager.predictionId)}`} className={linkClass}>
          {wager.predictionQuestion ?? fi.profile.unknownPrediction}
        </Link>
      </h3>
      <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-2 sm:grid-cols-4">
        <div>
          <dt className="text-sm text-slate-600">{fi.profile.yourPick}</dt>
          <dd className="font-medium">{fi.outcome[wager.outcome]}</dd>
        </div>
        <div>
          <dt className="text-sm text-slate-600">{fi.profile.stake}</dt>
          <dd className="font-medium">{fi.sisu.amount(wager.amountSisu)}</dd>
        </div>
        <div>
          <dt className="text-sm text-slate-600">{fi.profile.potentialPayout}</dt>
          <dd className="font-medium">{fi.sisu.amount(wager.potentialPayoutSisu)}</dd>
        </div>
        <div>
          <dt className="text-sm text-slate-600">{fi.profile.placedAt}</dt>
          <dd className="font-medium">
            <time dateTime={wager.placedAt}>{fi.date.dateTime(wager.placedAt)}</time>
          </dd>
        </div>
      </dl>
    </article>
  );
}
