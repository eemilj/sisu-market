import { Link, NavLink, Route, Routes } from 'react-router';
import type { ReactNode } from 'react';
import { fi } from './i18n/strings.fi';

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  [
    'rounded px-3 py-2 text-sm font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700',
    isActive ? 'bg-sky-800 text-white' : 'text-slate-800 hover:bg-slate-200',
  ].join(' ');

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
          <nav aria-label={fi.app.mainNavigation}>
            <ul className="flex flex-wrap gap-1">
              <li>
                <NavLink to="/" end className={navLinkClass}>
                  {fi.nav.predictions}
                </NavLink>
              </li>
            </ul>
          </nav>
        </div>
      </header>
      <main id="main" tabIndex={-1} className="mx-auto w-full max-w-5xl flex-1 px-4 py-6">
        {children}
      </main>
    </div>
  );
}

function Home() {
  return (
    <section aria-labelledby="home-title">
      <h1 id="home-title" className="text-2xl font-bold">
        {fi.home.title}
      </h1>
      <p className="mt-2 max-w-prose text-slate-700">{fi.home.intro}</p>
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
        <Route path="*" element={<NotFound />} />
      </Routes>
    </Layout>
  );
}
