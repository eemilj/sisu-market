import { Fragment } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth';
import { useLive } from '../live';
import { formatSisu } from '../format';

export default function Header() {
  const { user, loading, logout } = useAuth();
  const { status } = useLive();

  return (
    <header className="header">
      <div className="container header-inner">
        <Link to="/" className="brand" aria-label="Sisu Market – etusivu">
          <span className="brand-mark">S</span> <span className="brand-text">Sisu Market</span>
        </Link>
        <nav className="nav">
          <Link to="/leaderboard" className="nav-link" aria-label="Tulostaulu">
            <span aria-hidden="true" className="show-sm">🏆</span><span className="hide-sm">Tulostaulu</span>
          </Link>
          <span
            className={`live-dot live-${status}`}
            role="status"
            title={status === 'live' ? 'Reaaliaikainen: muutokset päivittyvät automaattisesti' : 'Yhteys katkesi – yhdistetään uudelleen…'}
          >
            <span className="live-label">{status === 'live' ? 'Ajantasainen' : 'Yhdistetään…'}</span>
          </span>
          {/*
            Distinct keys make React swap the logged-in and logged-out groups as wholes instead of patching
            text inside reused links. That matters when a browser translates the page: it replaces our text
            nodes with its own <font> elements, and removing one of ours by hand would then throw.
          */}
          {loading ? null : user ? (
            <Fragment key="signed-in">
              <Link to="/predictions/new" className="btn btn-primary" aria-label="Uusi ennuste">
                <span aria-hidden="true">+</span><span className="hide-sm">&nbsp;Uusi ennuste</span>
              </Link>
              <Link to={`/users/${encodeURIComponent(user.username)}?tab=transactions`} className="balance"
                    title="Sisu-saldosi – näytä tapahtumat">
                <span className="balance-label">Saldo</span>
                <span className="balance-amount">{formatSisu(user.balance)}</span>
              </Link>
              <Link to={`/users/${encodeURIComponent(user.username)}`} className="username">{user.username}</Link>
              <button
                className="btn btn-ghost"
                onClick={logout}
              >
                <span className="hide-sm">Kirjaudu ulos</span><span className="show-sm">Ulos</span>
              </button>
            </Fragment>
          ) : (
            <Fragment key="signed-out">
              <Link to="/login" className="btn btn-ghost">Kirjaudu</Link>
              <Link to="/register" className="btn btn-primary">Luo tili</Link>
            </Fragment>
          )}
        </nav>
      </div>
    </header>
  );
}
