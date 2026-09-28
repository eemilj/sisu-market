import { useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate } from 'react-router';
import { ApiError } from '../api/client';
import { useAuth } from '../hooks/useAuth';
import { errorMessage, fi } from '../i18n/strings.fi';

/** Registration form (User Story 1, acceptance scenario 1): unique username + password. */
export default function Register() {
  const navigate = useNavigate();
  const { register, isLoggedIn } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  if (isLoggedIn) {
    return <Navigate to="/" replace />;
  }

  const submit = (event: FormEvent) => {
    event.preventDefault();
    register.mutate(
      { username: username.trim(), password },
      { onSuccess: () => navigate('/', { replace: true }) },
    );
  };

  return (
    <section aria-labelledby="register-title" className="mx-auto max-w-md">
      <h1 id="register-title" className="text-2xl font-bold">
        {fi.auth.registerTitle}
      </h1>
      <p className="mt-2 text-slate-700">{fi.auth.registerIntro}</p>

      <form onSubmit={submit} className="mt-6 flex flex-col gap-4" noValidate={false}>
        {register.isError && (
          <p
            role="alert"
            className="rounded border border-red-300 bg-red-50 px-3 py-2 text-red-900"
          >
            {describeError(register.error)}
          </p>
        )}

        <div className="flex flex-col gap-1">
          <label htmlFor="register-username" className="text-sm font-medium">
            {fi.auth.username}
          </label>
          <input
            id="register-username"
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
          <label htmlFor="register-password" className="text-sm font-medium">
            {fi.auth.password}
          </label>
          <input
            id="register-password"
            name="password"
            type="password"
            autoComplete="new-password"
            required
            minLength={8}
            aria-describedby="register-password-hint"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            className="rounded border border-slate-300 px-3 py-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700"
          />
          <p id="register-password-hint" className="text-sm text-slate-600">
            {fi.auth.passwordHint}
          </p>
        </div>

        <button
          type="submit"
          disabled={register.isPending}
          className="rounded bg-sky-800 px-4 py-2 font-medium text-white disabled:opacity-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700"
        >
          {register.isPending ? fi.auth.submitting : fi.auth.submitRegister}
        </button>
      </form>

      <p className="mt-4">
        <Link to="/login" className="text-sky-800 underline">
          {fi.auth.toLogin}
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
