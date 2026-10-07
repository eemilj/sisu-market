import { fi } from '../i18n/fi';

export function ErrorBox({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <div className="alert alert-error" role="alert">
      {message}
    </div>
  );
}

export function SuccessBox({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <div className="alert alert-success" role="status">
      {message}
    </div>
  );
}

export function Loading() {
  return (
    <p className="muted" role="status">
      {fi.app.loading}
    </p>
  );
}
