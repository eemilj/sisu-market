import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';
import { useLive } from '../live';
import { formatSisu } from '../format';

export default function Header() {
  const { user, loading, logout } = useAuth();
  const { status } = useLive();
  const navigate = useNavigate();

  return (
    <header className="header">
      <div className="container header-inner">
        <Link to="/" className="brand">
          <span className="brand-mark">S</span> <span className="brand-text">Sisu Market</span>
        </Link>
        <nav className="nav">
          <Link to="/leaderboard" className="nav-link" aria-label="Leaderboard">
            <span aria-hidden="true" className="show-sm">🏆</span><span className="hide-sm">Leaderboard</span>
          </Link>
          <span
            className={`live-dot live-${status}`}
            role="status"
            title={status === 'live' ? 'Live: updates appear automatically' : 'Reconnecting to live updates…'}
          >
            <span className="live-label">{status === 'live' ? 'Live' : 'Reconnecting…'}</span>
          </span>
          {loading ? null : user ? (
            <>
              <Link to="/predictions/new" className="btn btn-primary" aria-label="New prediction">
                +<span className="hide-sm">&nbsp;New prediction</span>
              </Link>
              <Link to={`/users/${encodeURIComponent(user.username)}?tab=transactions`} className="balance"
                    title="Your Sisu balance – view transaction history">
                <span className="balance-label">Balance</span>
                <span className="balance-amount">{formatSisu(user.balance)}</span>
              </Link>
              <Link to={`/users/${encodeURIComponent(user.username)}`} className="username">{user.username}</Link>
              <button
                className="btn btn-ghost"
                onClick={() => {
                  logout();
                  navigate('/');
                }}
              >
                Log out
              </button>
            </>
          ) : (
            <>
              <Link to="/login" className="btn btn-ghost">Log in</Link>
              <Link to="/register" className="btn btn-primary">Sign up</Link>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}
