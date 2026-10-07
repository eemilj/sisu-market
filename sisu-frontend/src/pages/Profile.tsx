import { Link } from 'react-router';
import { ApiError } from '../api/client';
import type { Wager } from '../api/types';
import { useAuth } from '../hooks/useAuth';
import { useMyWagers } from '../hooks/useWager';
import { errorMessage, fi } from '../i18n/strings.fi';

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

  return (
    <section aria-labelledby="profile-title" className="flex flex-col gap-6">
      <h1 id="profile-title" className="text-2xl font-bold">
        {fi.profile.title}
      </h1>
      <OpenWagers />
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
