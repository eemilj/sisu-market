import { useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate } from 'react-router';
import { ApiError } from '../api/client';
import { useAuth } from '../hooks/useAuth';
import { errorMessage, fi } from '../i18n/strings.fi';

/** Login form (User Story 1, acceptance scenarios 2, 3, and 6). */
export default function Login() {
  const navigate = useNavigate();
  const { login, isLoggedIn } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  if (isLoggedIn) {
    return <Navigate to="/" replace />;
  }

  const submit = (event: FormEvent) => {
    event.preventDefault();
    login.mutate(
      { username: username.trim(), password },
      { onSuccess: () => navigate('/', { replace: true }) },
    );
  };

  return (
    <section aria-labelledby="login-title" className="mx-auto max-w-md">
      <h1 id="login-title" className="text-2xl font-bold">
        {fi.auth.loginTitle}
      </h1>
      <p className="mt-2 text-slate-700">{fi.auth.loginIntro}</p>

      <form onSubmit={submit} className="mt-6 flex flex-col gap-4">
        {login.isError && (
          <p
            role="alert"
            className="rounded border border-red-300 bg-red-50 px-3 py-2 text-red-900"
          >
            {describeError(login.error)}
          </p>
        )}

        <div className="flex flex-col gap-1">
          <label htmlFor="login-username" className="text-sm font-medium">
            {fi.auth.username}
          </label>
          <input
            id="login-username"
            name="username"
            type="text"
            autoComplete="username"
            required
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            className="rounded border border-slate-300 px-3 py-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700"
          />
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="login-password" className="text-sm font-medium">
            {fi.auth.password}
          </label>
          <input
            id="login-password"
            name="password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            className="rounded border border-slate-300 px-3 py-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700"
          />
        </div>

        <button
          type="submit"
          disabled={login.isPending}
          className="rounded bg-sky-800 px-4 py-2 font-medium text-white disabled:opacity-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700"
        >
          {login.isPending ? fi.auth.submitting : fi.auth.submitLogin}
        </button>
      </form>

      <p className="mt-4">
        <Link to="/register" className="text-sky-800 underline">
          {fi.auth.toRegister}
        </Link>
      </p>
    </section>
  );
}

function describeError(error: Error): string {
  if (error instanceof ApiError && error.code === 'TOO_MANY_ATTEMPTS') {
    const base = errorMessage(error.code);
    return error.retryAfterSeconds !== null
      ? `${base} ${fi.auth.retryAfterMinutes(Math.max(1, Math.ceil(error.retryAfterSeconds / 60)))}`
      : base;
  }
  return error instanceof ApiError ? errorMessage(error.code) : fi.errors.unknown;
}
