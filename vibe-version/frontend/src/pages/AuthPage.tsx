import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { errorMessage } from '../api';
import { useAuth } from '../auth';
import { ErrorBox } from '../components/Feedback';
import { fi } from '../i18n/fi';

export function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const auth = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const isLogin = mode === 'login';

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!isLogin) {
      if (!/^[A-Za-z0-9_]{3,20}$/.test(username)) return setError(fi.auth.invalidUsername);
      if (password.length < 8) return setError(fi.auth.invalidPassword);
    }
    setBusy(true);
    setError(null);
    try {
      await (isLogin ? auth.login(username, password) : auth.register(username, password));
      navigate('/');
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <section className="narrow" aria-labelledby="auth-title">
      <h1 id="auth-title">{isLogin ? fi.auth.loginTitle : fi.auth.registerTitle}</h1>
      {!isLogin && <p className="muted">{fi.auth.startingGrant}</p>}
      <form className="card" onSubmit={submit} noValidate>
        <div className="field">
          <label htmlFor="username">{fi.auth.username}</label>
          <input
            id="username"
            autoComplete="username"
            value={username}
            onChange={(e) => setUsername(e.target.value.trim())}
            aria-describedby={isLogin ? undefined : 'username-hint'}
            required
          />
          {!isLogin && (
            <p id="username-hint" className="hint">
              {fi.auth.usernameHint}
            </p>
          )}
        </div>
        <div className="field">
          <label htmlFor="password">{fi.auth.password}</label>
          <input
            id="password"
            type="password"
            autoComplete={isLogin ? 'current-password' : 'new-password'}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            aria-describedby={isLogin ? undefined : 'password-hint'}
            required
          />
          {!isLogin && (
            <p id="password-hint" className="hint">
              {fi.auth.passwordHint}
            </p>
          )}
        </div>
        <ErrorBox message={error} />
        <button type="submit" className="btn btn-primary btn-block" disabled={busy}>
          {busy ? fi.auth.working : isLogin ? fi.auth.loginSubmit : fi.auth.registerSubmit}
        </button>
      </form>
      <p>
        {isLogin ? fi.auth.noAccount : fi.auth.haveAccount}{' '}
        <Link to={isLogin ? '/register' : '/login'}>{isLogin ? fi.nav.register : fi.nav.login}</Link>
      </p>
    </section>
  );
}
