import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api';
import { useAuth } from '../auth';
import AuthForm from '../components/AuthForm';
import { Field, useFormErrors } from '../forms';

const FIELDS = ['username', 'email', 'password', 'confirm'] as const;
const USERNAME = /^[A-Za-z0-9_]{3,20}$/;
// Deliberately simple: the server has the final say; this catches typos like a missing @ or domain.
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

function usernameProblem(v: string): string | undefined {
  const name = v.trim();
  if (!name) return 'Valitse käyttäjätunnus.';
  if (name.length < 3) return 'Käyttäjätunnuksessa pitää olla vähintään 3 merkkiä.';
  if (name.length > 20) return 'Käyttäjätunnuksessa saa olla enintään 20 merkkiä.';
  if (!USERNAME.test(name)) {
    return 'Käytä vain kirjaimia a–z, numeroita ja alaviivaa (_). Välilyönnit, ääkköset ja erikoismerkit eivät käy.';
  }
  return undefined;
}

/** Same rules as the server's PasswordPolicy; bcrypt only uses the first 72 bytes, so the limit is in bytes. */
function passwordProblem(v: string, username: string): string | undefined {
  if (!v.trim()) return 'Valitse salasana.';
  const chars = [...v].length;
  if (chars < 8) return `Salasanassa pitää olla vähintään 8 merkkiä (nyt ${chars}).`;
  if (new TextEncoder().encode(v).length > 72) {
    return 'Salasana on liian pitkä: ääkköset ja erikoismerkit vievät enemmän tilaa. Lyhennä salasanaa (enintään 72 tavua).';
  }
  if (username.trim() && v.trim().toLowerCase() === username.trim().toLowerCase()) {
    return 'Salasana ei saa olla sama kuin käyttäjätunnus.';
  }
  return undefined;
}

export default function RegisterPage() {
  const { user, register } = useAuth();
  const navigate = useNavigate();
  const from: string = useLocation().state?.from ?? '/';
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const form = useFormErrors('register', FIELDS, {
    username: usernameProblem(username),
    email: !email.trim() ? 'Kirjoita sähköpostiosoitteesi.'
      : !EMAIL.test(email.trim()) ? 'Tarkista sähköpostiosoite. Sen pitää olla muotoa nimi@esimerkki.fi.' : undefined,
    password: passwordProblem(password, username),
    confirm: !confirm ? 'Kirjoita salasana uudelleen.'
      : confirm !== password ? 'Salasanat eivät täsmää. Kirjoita sama salasana molempiin kenttiin.' : undefined,
  });

  if (user) return <Navigate to={from} replace />;

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!form.validate()) return;
    setSubmitting(true);
    try {
      await register(username.trim(), email.trim(), password);
      navigate(from, { replace: true });
    } catch (err) {
      setSubmitting(false);
      // Point at the field that caused the conflict.
      if (err instanceof ApiError && err.code === 'USERNAME_TAKEN') form.failField('username', err.message);
      else if (err instanceof ApiError && err.code === 'EMAIL_TAKEN') form.failField('email', err.message);
      else form.fail(err);
    }
  }

  return (
    <AuthForm
      title="Luo tili"
      intro="Saat heti 1 000 Sisua aloitusbonuksena. Sisu on virtuaalivaluuttaa, jolla ei ole rahallista arvoa."
      error={form.formError}
      submitting={submitting}
      submitLabel="Luo tili ja saa 1 000 Sisua"
      onSubmit={submit}
      footer={<>Onko sinulla jo tili? <Link to="/login" state={{ from }}>Kirjaudu sisään</Link></>}
    >
      <Field id={form.idFor('username')} label="Käyttäjätunnus" error={form.errorFor('username')}
             hint="3–20 merkkiä: kirjaimia a–z, numeroita tai alaviiva. Näkyy muille käyttäjille.">
        {(input) => (
          <input {...input} {...form.track('username')} value={username} onChange={(e) => setUsername(e.target.value)}
                 autoComplete="username" autoCapitalize="none" spellCheck={false} maxLength={40} autoFocus />
        )}
      </Field>
      <Field id={form.idFor('email')} label="Sähköpostiosoite" error={form.errorFor('email')}>
        {(input) => (
          <input {...input} {...form.track('email')} type="email" inputMode="email" value={email}
                 onChange={(e) => setEmail(e.target.value)} autoComplete="email" autoCapitalize="none" spellCheck={false} />
        )}
      </Field>
      <Field id={form.idFor('password')} label="Salasana" error={form.errorFor('password')} hint="Vähintään 8 merkkiä. Ei sama kuin käyttäjätunnus.">
        {(input) => (
          <input {...input} {...form.track('password')} type="password" value={password}
                 onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" />
        )}
      </Field>
      <Field id={form.idFor('confirm')} label="Salasana uudelleen" error={form.errorFor('confirm')}>
        {(input) => (
          <input {...input} {...form.track('confirm')} type="password" value={confirm}
                 onChange={(e) => setConfirm(e.target.value)} autoComplete="new-password" />
        )}
      </Field>
    </AuthForm>
  );
}
