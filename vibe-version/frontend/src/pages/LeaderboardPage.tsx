import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { api, errorMessage } from '../api';
import { useAuth } from '../auth';
import { ErrorBox, Loading } from '../components/Feedback';
import { formatSisu } from '../format';
import { fi } from '../i18n/fi';
import type { LeaderboardEntry } from '../types';

export function LeaderboardPage() {
  const { user } = useAuth();
  const [rows, setRows] = useState<LeaderboardEntry[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .leaderboard()
      .then(setRows)
      .catch((e) => setError(errorMessage(e)));
  }, []);

  return (
    <section className="narrow" aria-labelledby="lb-title">
      <h1 id="lb-title">{fi.leaderboard.title}</h1>
      <p className="muted">{fi.leaderboard.subtitle}</p>
      <ErrorBox message={error} />
      {!rows && !error && <Loading />}
      {rows && rows.length === 0 && <p className="muted">{fi.leaderboard.empty}</p>}
      {rows && rows.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th scope="col" className="num">{fi.leaderboard.rank}</th>
                <th scope="col">{fi.leaderboard.user}</th>
                <th scope="col" className="num">{fi.leaderboard.balance}</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.username} className={r.username === user?.username ? 'me' : undefined}>
                  <td className="num">{r.rank}.</td>
                  <td>
                    <Link to={`/u/${r.username}`}>{r.username}</Link>
                  </td>
                  <td className="num">{formatSisu(r.balance)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
