import { Link, NavLink, Route, Routes } from 'react-router';
import type { ReactNode } from 'react';
import BalanceBadge from './components/BalanceBadge';
import SisuDisclaimer from './components/SisuDisclaimer';
import { useAuth } from './hooks/useAuth';
import { useLiveUpdates } from './hooks/useLiveUpdates';
import { fi } from './i18n/strings.fi';
import CreatePrediction from './pages/CreatePrediction';
import Leaderboard from './pages/Leaderboard';
import Login from './pages/Login';
import PredictionDetail from './pages/PredictionDetail';
import PredictionList from './pages/PredictionList';
import Profile from './pages/Profile';
import Register from './pages/Register';

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  [
    'rounded px-3 py-2 text-sm font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700',
    isActive ? 'bg-sky-800 text-white' : 'text-slate-800 hover:bg-slate-200',
  ].join(' ');

/** Right side of the header: balance + account actions that follow the session state. */
function AuthNav() {
  const { user, isLoggedIn, logout } = useAuth();
  // Live balance/odds updates only make sense with a session (the stream is authenticated).
  useLiveUpdates(isLoggedIn);

  if (!isLoggedIn || !user) {
    return (
      <ul className="flex flex-wrap items-center gap-1">
        <li>
          <NavLink to="/login" className={navLinkClass}>
            {fi.nav.login}
          </NavLink>
        </li>
        <li>
          <NavLink to="/register" className={navLinkClass}>
            {fi.nav.register}
          </NavLink>
        </li>
      </ul>
    );
  }

  return (
    <div className="flex flex-wrap items-center gap-2">
      <NavLink to="/create-prediction" className={navLinkClass}>
        {fi.nav.createPrediction}
      </NavLink>
      <NavLink to="/profile" className={navLinkClass}>
        {fi.nav.profile}
      </NavLink>
      <span
        className="hidden text-sm text-slate-600 sm:inline"
        title={fi.auth.loggedInAs(user.username)}
      >
        {user.username}
      </span>
      <BalanceBadge />
      <button
        type="button"
        onClick={() => logout.mutate()}
        disabled={logout.isPending}
        className="rounded px-3 py-2 text-sm font-medium text-slate-800 hover:bg-slate-200 disabled:opacity-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700"
      >
        {fi.nav.logout}
      </button>
    </div>
  );
}

function Layout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col bg-slate-50 text-slate-900">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:rounded focus:bg-white focus:px-3 focus:py-2"
      >
        {fi.app.skipToContent}
      </a>
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center justify-between gap-2 px-4 py-3">
          <Link to="/" className="text-lg font-bold text-sky-900">
            {fi.app.name}
          </Link>
          <nav aria-label={fi.app.mainNavigation} className="flex flex-wrap items-center gap-2">
            <ul className="flex flex-wrap gap-1">
              <li>
                <NavLink to="/predictions" className={navLinkClass}>
                  {fi.nav.predictions}
                </NavLink>
              </li>
              <li>
                <NavLink to="/leaderboard" className={navLinkClass}>
                  {fi.nav.leaderboard}
                </NavLink>
              </li>
            </ul>
            <AuthNav />
          </nav>
        </div>
      </header>
      <main id="main" tabIndex={-1} className="mx-auto w-full max-w-5xl flex-1 px-4 py-6">
        {children}
      </main>
      <footer className="border-t border-slate-200 bg-white px-4 py-3">
        <SisuDisclaimer />
      </footer>
    </div>
  );
}

function Home() {
  const { isLoggedIn, isLoading } = useAuth();
  return (
    <section aria-labelledby="home-title">
      <h1 id="home-title" className="text-2xl font-bold">
        {fi.home.title}
      </h1>
      <p className="mt-2 max-w-prose text-slate-700">{fi.home.intro}</p>
      <p className="mt-4 flex flex-wrap gap-2">
        <Link
          to="/predictions"
          className="rounded bg-sky-800 px-4 py-2 font-medium text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700"
        >
          {fi.predictions.browse}
        </Link>
        {!isLoading && !isLoggedIn && (
          <>
            <Link
              to="/register"
              className="rounded border border-sky-800 px-4 py-2 font-medium text-sky-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700"
            >
              {fi.nav.register}
            </Link>
            <Link
              to="/login"
              className="rounded border border-sky-800 px-4 py-2 font-medium text-sky-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700"
            >
              {fi.nav.login}
            </Link>
          </>
        )}
      </p>
    </section>
  );
}

function NotFound() {
  return (
    <section aria-labelledby="not-found-title">
      <h1 id="not-found-title" className="text-2xl font-bold">
        {fi.notFound.title}
      </h1>
      <p className="mt-2">{fi.notFound.body}</p>
      <Link to="/" className="mt-4 inline-block text-sky-800 underline">
        {fi.notFound.backHome}
      </Link>
    </section>
  );
}

/** Route table; user-story phases add their pages here. */
export default function App() {
  return (
    <Layout>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/predictions" element={<PredictionList />} />
        <Route path="/leaderboard" element={<Leaderboard />} />
        <Route path="/create-prediction" element={<CreatePrediction />} />
        <Route path="/predictions/:id" element={<PredictionDetail />} />
        <Route path="/profile" element={<Profile />} />
        <Route path="/register" element={<Register />} />
        <Route path="/login" element={<Login />} />
        <Route path="*" element={<NotFound />} />
      </Routes>
    </Layout>
  );
}
