import type { FormEvent, ReactNode } from 'react';

interface Props {
  title: string;
  intro?: ReactNode;
  /** Problem that isn't about a single field, shown at the top. */
  error: string | null;
  submitting: boolean;
  submitLabel: string;
  onSubmit: (e: FormEvent) => void;
  footer: ReactNode;
  children: ReactNode;
}

/** Layout shared by the login and register forms. */
export default function AuthForm({ title, intro, error, submitting, submitLabel, onSubmit, footer, children }: Props) {
  return (
    <div className="auth-wrap">
      <form className="auth-card" onSubmit={onSubmit} noValidate>
        <h1>{title}</h1>
        {intro && <p className="muted form-intro">{intro}</p>}
        {error && <p className="alert" role="alert">{error}</p>}
        {children}
        <button className="btn btn-primary btn-block" type="submit" disabled={submitting}>
          {submitting ? 'Hetkinen…' : submitLabel}
        </button>
        <p className="auth-footer">{footer}</p>
      </form>
    </div>
  );
}
