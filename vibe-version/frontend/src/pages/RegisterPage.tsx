import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';
import AuthForm from '../components/AuthForm';

export default function RegisterPage() {
  const { user, register } = useAuth();
  const navigate = useNavigate();
  const from: string = useLocation().state?.from ?? '/';
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');

  if (user) return <Navigate to={from} replace />;

  return (
    <AuthForm
      title="Create account"
      submitLabel="Sign up and get 1 000 Sisu"
      onSubmit={async () => {
        if (password !== confirm) throw new Error('Passwords do not match');
        await register(username, email, password);
        navigate(from, { replace: true });
      }}
      footer={
        <>
          Already have an account? <Link to="/login" state={{ from }}>Log in</Link>
        </>
      }
    >
      <label>
        Username
        <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" required autoFocus />
        <small>3–20 characters: letters, digits or underscore</small>
      </label>
      <label>
        Email
        <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required />
      </label>
      <label>
        Password
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="new-password"
          required
        />
        <small>At least 8 characters</small>
      </label>
      <label>
        Confirm password
        <input
          type="password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          autoComplete="new-password"
          required
        />
      </label>
    </AuthForm>
  );
}
