import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';
import AuthForm from '../components/AuthForm';
import { Field, useFormErrors } from '../forms';

const FIELDS = ['username', 'password'] as const;

export default function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const from: string = useLocation().state?.from ?? '/';
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const form = useFormErrors('login', FIELDS, {
    username: username.trim() ? undefined : 'Kirjoita käyttäjätunnuksesi.',
    password: password ? undefined : 'Kirjoita salasanasi.',
  });

  if (user) return <Navigate to={from} replace />;

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!form.validate()) return;
    setSubmitting(true);
    try {
      await login(username.trim(), password);
      navigate(from, { replace: true });
    } catch (err) {
      form.fail(err);
      setSubmitting(false);
    }
  }

  return (
    <AuthForm
      title="Kirjaudu sisään"
      error={form.formError}
      submitting={submitting}
      submitLabel="Kirjaudu sisään"
      onSubmit={submit}
      footer={<>Uusi käyttäjä? <Link to="/register" state={{ from }}>Luo tili</Link></>}
    >
      <Field id={form.idFor('username')} label="Käyttäjätunnus" error={form.errorFor('username')}>
        {(input) => (
          <input {...input} {...form.track('username')} value={username} onChange={(e) => setUsername(e.target.value)}
                 autoComplete="username" autoCapitalize="none" spellCheck={false} autoFocus />
        )}
      </Field>
      <Field id={form.idFor('password')} label="Salasana" error={form.errorFor('password')}>
        {(input) => (
          <input {...input} {...form.track('password')} type="password" value={password}
                 onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" />
        )}
      </Field>
    </AuthForm>
  );
}
