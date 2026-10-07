import { Link, NavLink, Outlet, useNavigate } from 'react-router';
import { useAuth } from '../auth';
import { formatSisu } from '../format';
import { fi } from '../i18n/fi';

export function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  return (
    <>
      <a className="skip-link" href="#main">
        {fi.app.skipToContent}
      </a>
      <header className="site-header">
        <div className="container header-inner">
          <Link to="/" className="brand">
            <span aria-hidden="true" className="brand-mark">
              ◆
            </span>{' '}
            {fi.app.name}
          </Link>
          <nav aria-label={fi.nav.label} className="main-nav">
            <NavLink to="/" end>
              {fi.nav.predictions}
            </NavLink>
            {user && <NavLink to="/new">{fi.nav.create}</NavLink>}
            <NavLink to="/leaderboard">{fi.nav.leaderboard}</NavLink>
          </nav>
          <div className="account">
            {user ? (
              <>
                <Link to={`/u/${user.username}`} className="balance" aria-label={`${fi.nav.profile}: ${user.username}`}>
                  <span className="balance-label">{fi.nav.balance}</span>
                  <strong>{formatSisu(user.balance)}</strong>
                </Link>
                <button
                  type="button"
                  className="btn btn-ghost"
                  onClick={() => {
                    logout();
                    navigate('/');
                  }}
                >
                  {fi.nav.logout}
                </button>
              </>
            ) : (
              <>
                <NavLink to="/login">{fi.nav.login}</NavLink>
                <Link to="/register" className="btn btn-primary">
                  {fi.nav.register}
                </Link>
              </>
            )}
          </div>
        </div>
      </header>
      <main id="main" className="container" tabIndex={-1}>
        <Outlet />
      </main>
      <footer className="site-footer">
        <div className="container">
          <p>{fi.app.disclaimer}</p>
        </div>
      </footer>
    </>
  );
}
