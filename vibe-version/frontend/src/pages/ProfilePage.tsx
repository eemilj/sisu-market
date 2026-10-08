import { useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { api, type Prediction, type Profile } from '../api';
import { useAuth } from '../auth';
import { useLiveEvents } from '../live';
import { formatDate, formatSisu } from '../format';
import { useDebounced } from '../useDebounced';
import PredictionCard from '../components/PredictionCard';
import TransactionHistory from '../components/TransactionHistory';
import WagerHistory from '../components/WagerHistory';

type Tab = 'wagers' | 'transactions' | 'predictions';
const tabLabels: Record<Tab, string> = { wagers: 'Wagers', transactions: 'Transactions', predictions: 'Predictions created' };

export default function ProfilePage() {
  const { username = '' } = useParams();
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const [profile, setProfile] = useState<Profile | null>(null);
  const [created, setCreated] = useState<Prediction[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);

  const isMe = !!user && user.username.toLowerCase() === username.toLowerCase();
  const tabs: Tab[] = isMe ? ['wagers', 'transactions', 'predictions'] : ['predictions'];
  const requested = params.get('tab') as Tab | null;
  const tab: Tab = requested && tabs.includes(requested) ? requested : tabs[0];

  // Balances and ranks move as people wager and predictions resolve.
  const scheduleReload = useDebounced(() => setReload((n) => n + 1), 1000);
  useLiveEvents((event) => {
    if (event.type === 'ready' || event.type === 'wallet.transaction') scheduleReload();
    if (event.type === 'prediction.created' && event.data.createdBy?.toLowerCase() === username.toLowerCase()) scheduleReload();
    if (event.type === 'prediction.updated') {
      setCreated((list) => list?.map((p) => (p.id === event.data.id && event.data.version > p.version ? event.data : p)) ?? list);
      scheduleReload(); // rank can change when anyone's balance does
    }
  });

  useEffect(() => {
    let cancelled = false;
    Promise.all([api.profile(username), api.profilePredictions(username)])
      .then(([p, list]) => {
        if (cancelled) return;
        setProfile(p);
        setCreated(list);
        setError(null);
      })
      .catch((err: Error) => !cancelled && setError(err.message));
    return () => {
      cancelled = true;
    };
  }, [username, reload]);

  if (error) return <div className="profile"><p className="alert">{error}</p></div>;
  if (!profile) return <div className="profile"><p className="muted">Loading profile…</p></div>;

  // Your own balance comes from the live auth state so it never lags behind the header.
  const balance = isMe && user ? user.balance : profile.balance;

  return (
    <div className="profile">
      <section className="balance-card profile-card" aria-label="Profile summary">
        <div className="profile-head">
          <h1>{profile.username}</h1>
          {profile.admin && <span className="tag tag-admin">Admin</span>}
        </div>
        <span className="balance-card-label">{isMe ? 'Your balance' : 'Balance'}</span>
        <span className="balance-card-amount">{formatSisu(balance)}</span>
        <dl className="profile-stats">
          <div><dt>Rank</dt><dd><Link to="/leaderboard">#{profile.rank}</Link></dd></div>
          <div><dt>Predictions created</dt><dd>{profile.predictionsCreated}</dd></div>
          <div><dt>Joined</dt><dd>{formatDate(profile.joinedAt)}</dd></div>
        </dl>
      </section>

      {tabs.length > 1 && (
        <div className="tabs profile-tabs" role="tablist">
          {tabs.map((t) => (
            <button key={t} role="tab" aria-selected={tab === t} className={`tab ${tab === t ? 'tab-active' : ''}`}
                    onClick={() => setParams(t === tabs[0] ? {} : { tab: t }, { replace: true })}>
              {tabLabels[t]}
            </button>
          ))}
        </div>
      )}
      {tabs.length === 1 && <h2>{tabLabels[tab]}</h2>}

      {tab === 'wagers' && <WagerHistory />}
      {tab === 'transactions' && <TransactionHistory />}
      {tab === 'predictions' && (
        created === null ? <p className="muted">Loading…</p>
          : created.length === 0 ? <p className="muted">{isMe ? "You haven't" : `${profile.username} hasn't`} created any predictions yet.</p>
          : (
            <div className="grid">
              {created.map((p) => (
                <PredictionCard key={p.id} prediction={p}
                                onUpdated={(u) => setCreated((list) => list?.map((x) => (x.id === u.id && u.version > x.version ? u : x)) ?? list)} />
              ))}
            </div>
          )
      )}
    </div>
  );
}
