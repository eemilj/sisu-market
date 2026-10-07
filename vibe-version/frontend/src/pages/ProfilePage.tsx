import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { api, errorMessage } from '../api';
import { useAuth } from '../auth';
import { ErrorBox, Loading } from '../components/Feedback';
import { PredictionCard } from '../components/PredictionCard';
import { formatDate, formatSisu } from '../format';
import { fi } from '../i18n/fi';
import { useLivePredictions } from '../live';
import type { Profile, Tx } from '../types';

export function ProfilePage() {
  const { username = '' } = useParams();
  const { user } = useAuth();
  const isSelf = user?.username.toLowerCase() === username.toLowerCase();
  const [profile, setProfile] = useState<Profile | null>(null);
  const [txs, setTxs] = useState<Tx[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    api
      .profile(username)
      .then(setProfile)
      .catch((e) => setError(errorMessage(e)));
    if (isSelf) api.transactions().then(setTxs).catch(() => setTxs([]));
  }, [username, isSelf]);

  useEffect(() => {
    setProfile(null);
    setError(null);
    load();
  }, [load]);

  // Keep potential payouts and statuses fresh when any of this user's predictions change.
  useLivePredictions((p) => {
    if (profile?.wagers.some((w) => w.predictionId === p.id) || profile?.predictions.some((x) => x.id === p.id)) load();
  });

  if (error) return <ErrorBox message={error} />;
  if (!profile) return <Loading />;

  return (
    <section aria-labelledby="profile-title">
      <div className="page-head">
        <h1 id="profile-title">{profile.user.username}</h1>
        <p className="big-balance">
          <span className="muted">{fi.profile.balance}</span> {formatSisu(isSelf && user ? user.balance : profile.user.balance)}
        </p>
      </div>

      <h2>{fi.profile.wagers}</h2>
      {profile.wagers.length === 0 ? (
        <p className="muted">{fi.profile.noWagers}</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th scope="col">{fi.profile.prediction}</th>
                <th scope="col">{fi.profile.side}</th>
                <th scope="col" className="num">{fi.profile.stake}</th>
                <th scope="col">{fi.profile.state}</th>
                <th scope="col" className="num">{fi.profile.payout}</th>
              </tr>
            </thead>
            <tbody>
              {profile.wagers.map((w) => (
                <tr key={w.id}>
                  <td>
                    <Link to={`/p/${w.predictionId}`}>{w.question}</Link>
                  </td>
                  <td className={w.side === 'YES' ? 'yes-text' : 'no-text'}>{fi.side[w.side]}</td>
                  <td className="num">{formatSisu(w.amount)}</td>
                  <td>
                    <span className={`badge badge-w-${w.status.toLowerCase()}`}>{fi.wagerStatus[w.status]}</span>
                  </td>
                  <td className="num">
                    {w.status === 'OPEN'
                      ? w.potentialPayout !== null && <span title={fi.wager.potential}>≈ {formatSisu(w.potentialPayout)}</span>
                      : formatSisu(w.payout ?? 0)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <h2>{fi.profile.created}</h2>
      {profile.predictions.length === 0 ? (
        <p className="muted">{fi.profile.noCreated}</p>
      ) : (
        <ul className="grid">
          {profile.predictions.map((p) => (
            <PredictionCard key={p.id} p={p} />
          ))}
        </ul>
      )}

      {isSelf && (
        <>
          <h2>{fi.profile.history}</h2>
          {!txs ? (
            <Loading />
          ) : txs.length === 0 ? (
            <p className="muted">{fi.profile.noHistory}</p>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th scope="col">{fi.profile.date}</th>
                    <th scope="col">{fi.profile.reason}</th>
                    <th scope="col" className="num">{fi.profile.change}</th>
                    <th scope="col" className="num">{fi.profile.balanceAfter}</th>
                  </tr>
                </thead>
                <tbody>
                  {txs.map((t) => (
                    <tr key={t.id}>
                      <td>{formatDate(t.createdAt)}</td>
                      <td>
                        {t.predictionId ? <Link to={`/p/${t.predictionId}`}>{fi.txReason[t.reason]}</Link> : fi.txReason[t.reason]}
                      </td>
                      <td className={`num ${t.amount < 0 ? 'neg' : 'pos'}`}>
                        {t.amount > 0 ? '+' : ''}
                        {formatSisu(t.amount)}
                      </td>
                      <td className="num">{formatSisu(t.balanceAfter)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </section>
  );
}
