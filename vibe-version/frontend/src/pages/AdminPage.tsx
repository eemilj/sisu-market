import { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { api, type AdminUser, type Prediction } from '../api';
import { useAuth } from '../auth';
import ClosePredictionButton from '../components/ClosePredictionButton';
import ResolvePanel from '../components/ResolvePanel';
import { categoryLabels, formatDate, formatDateTime, formatSisu, outcomeLabels, statusLabels } from '../format';

export default function AdminPage() {
  const { user, loading } = useAuth();
  const [predictions, setPredictions] = useState<Prediction[] | null>(null);
  const [users, setUsers] = useState<AdminUser[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const isAdmin = !!user?.admin;

  useEffect(() => {
    if (!isAdmin) return;
    let cancelled = false;
    Promise.all([api.adminPredictions(), api.adminUsers()])
      .then(([ps, us]) => {
        if (cancelled) return;
        setPredictions(ps);
        setUsers(us);
        setError(null);
      })
      .catch((err: Error) => !cancelled && setError(err.message));
    return () => {
      cancelled = true;
    };
  }, [isAdmin]);

  if (loading) return null;
  if (!user) return <Navigate to="/login" replace state={{ from: '/admin' }} />;
  if (!user.admin) return <Navigate to="/" replace />;

  return (
    <div className="admin">
      <h1>Ylläpito</h1>
      {error && <p className="alert" role="alert">{error}</p>}

      <h2>Ennusteet</h2>
      {!predictions ? (
        !error && <p className="muted">Ladataan…</p>
      ) : predictions.length === 0 ? (
        <p className="muted">Ei ennusteita.</p>
      ) : (
        <div className="admin-scroll">
          <table className="admin-table">
            <thead>
              <tr>
                <th scope="col">Ennuste</th>
                <th scope="col">Tila</th>
                <th scope="col">Luoja</th>
                <th scope="col">Sulkeutuu</th>
                <th scope="col" className="num">Panokset</th>
                <th scope="col"><span className="sr-only">Toiminnot</span></th>
              </tr>
            </thead>
            <tbody>
              {predictions.map((p) => (
                <tr key={p.id}>
                  <td>
                    <strong>{p.question}</strong>
                    <div className="muted">{categoryLabels[p.category]}</div>
                    {p.status === 'CLOSED' && (
                      <ResolvePanel
                        prediction={p}
                        onResolved={(r) =>
                          setPredictions((list) => list && list.map((x) => (x.id === r.prediction.id ? r.prediction : x)))
                        }
                      />
                    )}
                  </td>
                  <td>
                    {statusLabels[p.status]}
                    {p.outcome && <div className="muted">{outcomeLabels[p.outcome]}</div>}
                  </td>
                  <td>{p.createdBy ?? <span className="muted">–</span>}</td>
                  <td>{formatDateTime(p.closesAt)}</td>
                  <td className="num">{formatSisu(p.totalWagered)}</td>
                  <td className="admin-actions">
                    {p.status === 'OPEN' && (
                      <ClosePredictionButton
                        prediction={p}
                        onClosed={(c) => setPredictions((list) => list && list.map((x) => (x.id === c.id ? c : x)))}
                      />
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <h2>Käyttäjät</h2>
      {!users ? (
        !error && <p className="muted">Ladataan…</p>
      ) : (
        <div className="admin-scroll">
          <table className="admin-table">
            <thead>
              <tr>
                <th scope="col">Käyttäjä</th>
                <th scope="col">Sähköposti</th>
                <th scope="col">Liittynyt</th>
                <th scope="col" className="num">Saldo</th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id}>
                  <td>
                    <Link to={`/users/${encodeURIComponent(u.username)}`}>{u.username}</Link>
                    {u.admin && <> <span className="tag tag-admin">Ylläpitäjä</span></>}
                  </td>
                  <td>{u.email}</td>
                  <td>{formatDate(u.createdAt)}</td>
                  <td className="num">{formatSisu(u.balance)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
