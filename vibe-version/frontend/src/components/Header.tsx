import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';
import { formatSisu } from '../format';

export default function Header() {
  const { user, loading, logout } = useAuth();
  const navigate = useNavigate();

  return (
    <header className="header">
      <div className="container header-inner">
        <Link to="/" className="brand">
          <span className="brand-mark">S</span> <span className="brand-text">Sisu Market</span>
        </Link>
        <nav className="nav">
          {loading ? null : user ? (
            <>
              <Link to="/predictions/new" className="btn btn-primary">+ New prediction</Link>
              <Link to="/wallet" className="balance" title="Your Sisu balance – view transaction history">
                <span className="balance-label">Balance</span>
                <span className="balance-amount">{formatSisu(user.balance)}</span>
              </Link>
              <span className="username">{user.username}</span>
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
