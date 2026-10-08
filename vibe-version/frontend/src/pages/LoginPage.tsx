import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';
import AuthForm from '../components/AuthForm';

export default function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const from: string = useLocation().state?.from ?? '/';
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  if (user) return <Navigate to={from} replace />;

  return (
    <AuthForm
      title="Log in"
      submitLabel="Log in"
      onSubmit={async () => {
        await login(username, password);
        navigate(from, { replace: true });
      }}
      footer={
        <>
          New here? <Link to="/register" state={{ from }}>Create an account</Link>
        </>
      }
    >
      <label>
        Username
        <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" required autoFocus />
      </label>
      <label>
        Password
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="current-password"
          required
        />
      </label>
    </AuthForm>
  );
}
