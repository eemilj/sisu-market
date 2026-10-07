import { useQuery } from '@tanstack/react-query';
import { api, ApiError, queryKeys } from '../api/client';
import { errorMessage, fi } from '../i18n/strings.fi';

/**
 * The public leaderboard (User Story 6; FR-029): users ranked by sisu balance.
 */
export default function Leaderboard() {
  const query = useQuery({
    queryKey: queryKeys.leaderboard,
    queryFn: api.leaderboard,
  });

  return (
    <section aria-labelledby="leaderboard-title">
      <h1 id="leaderboard-title" className="text-2xl font-bold">
        {fi.leaderboard.title}
      </h1>
      <p className="mt-2 text-slate-700">{fi.leaderboard.intro}</p>

      <div className="mt-6" aria-live="polite" aria-busy={query.isLoading}>
        {query.isLoading && <p>{fi.app.loading}</p>}

        {query.isError && (
          <div
            role="alert"
            className="rounded border border-red-300 bg-red-50 px-3 py-2 text-red-900"
          >
            <p>
              {fi.leaderboard.loadFailed}{' '}
              {query.error instanceof ApiError ? errorMessage(query.error.code) : ''}
            </p>
            <button
              type="button"
              onClick={() => void query.refetch()}
              className="mt-2 font-medium underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700"
            >
              {fi.predictions.retry}
            </button>
          </div>
        )}

        {query.isSuccess && query.data.length === 0 && (
          <p className="text-slate-700">{fi.leaderboard.empty}</p>
        )}

        {query.isSuccess && query.data.length > 0 && (
          <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-sm">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 bg-slate-100 font-semibold text-slate-700">
                <tr>
                  <th scope="col" className="px-4 py-3">
                    {fi.leaderboard.rank}
                  </th>
                  <th scope="col" className="px-4 py-3">
                    {fi.leaderboard.user}
                  </th>
                  <th scope="col" className="px-4 py-3 text-right">
                    {fi.leaderboard.balance}
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200">
                {query.data.map((entry) => (
                  <tr key={entry.username} className="hover:bg-slate-50">
                    <td className="px-4 py-3 font-medium text-slate-600">{entry.rank}</td>
                    <td className="px-4 py-3 font-semibold text-sky-900">{entry.username}</td>
                    <td className="px-4 py-3 text-right font-medium">
                      {fi.sisu.amount(entry.balanceSisu)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </section>
  );
}
