import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, type Leaderboard, type LeaderboardEntry } from '../api';
import { useAuth } from '../auth';
import { useLiveEvents } from '../live';
import { formatSisu } from '../format';
import { useDebounced } from '../useDebounced';

const LIMIT = 50;

export default function LeaderboardPage() {
  const { user } = useAuth();
  const [board, setBoard] = useState<Leaderboard | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);

  // Wagers and payouts move balances; refresh shortly after any market activity.
  const scheduleReload = useDebounced(() => setReload((n) => n + 1), 1000);
  useLiveEvents((event) => {
    if (event.type === 'ready' || event.type === 'prediction.updated' || event.type === 'wallet.transaction') scheduleReload();
  });

  useEffect(() => {
    let cancelled = false;
    api
      .leaderboard(LIMIT)
      .then((b) => {
        if (cancelled) return;
        setBoard(b);
        setError(null);
      })
      .catch((err: Error) => !cancelled && setError(err.message));
    return () => {
      cancelled = true;
    };
  }, [reload, user?.id]);

  const rankCounts = new Map<number, number>();
  board?.entries.forEach((e) => rankCounts.set(e.rank, (rankCounts.get(e.rank) ?? 0) + 1));

  /** 🥇🥈🥉 for an unshared top-3 rank; shared ranks show "=1." so twenty players aren't all "gold". */
  const rankLabel = (e: LeaderboardEntry) => {
    const shared = (rankCounts.get(e.rank) ?? 1) > 1;
    if (!shared && e.rank <= 3) return ['🥇', '🥈', '🥉'][e.rank - 1];
    return `${shared ? '=' : ''}${e.rank}.`;
  };

  const row = (e: LeaderboardEntry, extraClass = '') => {
    const mine = user?.username === e.username;
    return (
      <tr key={e.username} className={`${mine ? 'is-me' : ''} ${extraClass}`}>
        <td className="lb-rank" title={(rankCounts.get(e.rank) ?? 1) > 1 ? 'Jaettu sijoitus' : undefined}>{rankLabel(e)}</td>
        <td><Link to={`/users/${encodeURIComponent(e.username)}`}>{e.username}</Link>{mine && <span className="muted"> (sinä)</span>}</td>
        <td className="lb-balance">{formatSisu(e.balance)}</td>
      </tr>
    );
  };

  return (
    <div className="leaderboard">
      <h1>Tulostaulu</h1>
      <p className="muted">Järjestys nykyisen Sisu-saldon mukaan. Saman saldon pelaajat jakavat sijoituksen (merkitty =).</p>
      {error && <p className="alert">{error}</p>}
      {!board ? (
        !error && <p className="muted">Ladataan…</p>
      ) : board.entries.length === 0 ? (
        <p className="muted">Ei vielä pelaajia.</p>
      ) : (
        <table className="lb-table">
          <thead>
            <tr><th scope="col">Sija</th><th scope="col">Pelaaja</th><th scope="col" className="lb-balance">Saldo</th></tr>
          </thead>
          <tbody>
            {board.entries.map((e) => row(e))}
            {board.me && (
              <>
                <tr className="lb-gap" aria-hidden="true"><td colSpan={3}>⋯</td></tr>
                {row(board.me)}
              </>
            )}
          </tbody>
        </table>
      )}
    </div>
  );
}
