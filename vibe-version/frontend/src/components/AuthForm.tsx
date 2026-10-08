import { useState, type FormEvent, type ReactNode } from 'react';

interface Props {
  title: string;
  submitLabel: string;
  onSubmit: () => Promise<void>;
  footer: ReactNode;
  children: ReactNode;
}

/** Shared shell for the login and register forms: handles submit state and error display. */
export default function AuthForm({ title, submitLabel, onSubmit, footer, children }: Props) {
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await onSubmit();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong');
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-wrap">
      <form className="auth-card" onSubmit={handleSubmit} noValidate>
        <h1>{title}</h1>
        {error && (
          <p className="alert" role="alert">
            {error}
          </p>
        )}
        {children}
        <button className="btn btn-primary btn-block" type="submit" disabled={submitting}>
          {submitting ? 'Please wait…' : submitLabel}
        </button>
        <p className="auth-footer">{footer}</p>
      </form>
    </div>
  );
}
