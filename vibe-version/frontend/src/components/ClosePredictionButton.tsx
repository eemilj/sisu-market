import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { api, type Prediction } from '../api';

interface Props {
  prediction: Prediction;
  onClosed: (prediction: Prediction) => void;
}

/** Compact "close now" button for the admin table; opens a confirmation pop-up before anything happens. */
export default function ClosePredictionButton({ prediction: p, onClosed }: Props) {
  const [open, setOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && !submitting && setOpen(false);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, submitting]);

  async function confirm() {
    setSubmitting(true);
    setError(null);
    try {
      const closed = await api.adminClosePrediction(p.id);
      setOpen(false);
      onClosed(closed);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Sulkeminen epäonnistui. Yritä uudelleen.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <button type="button" className="btn-close-now" onClick={() => setOpen(true)}
              aria-label={`Sulje ennuste: ${p.question}`}>
        <span aria-hidden="true">■</span> Sulje
      </button>
      {open && createPortal(
        <div className="modal-backdrop" onClick={() => !submitting && setOpen(false)}>
          <div className="modal" role="alertdialog" aria-modal="true" aria-labelledby={`close-title-${p.id}`}
               onClick={(e) => e.stopPropagation()}>
            <h2 id={`close-title-${p.id}`}>Suljetaanko ennuste?</h2>
            <p className="modal-question">{p.question}</p>
            <p className="muted">
              Panostaminen loppuu heti, ja ennuste voidaan ratkaista. Sulkemista ei voi perua.
            </p>
            {error && <p className="alert" role="alert">{error}</p>}
            <div className="modal-actions">
              <button type="button" className="btn btn-ghost" onClick={() => setOpen(false)} disabled={submitting} autoFocus>
                Peruuta
              </button>
              <button type="button" className="btn btn-danger" onClick={confirm} disabled={submitting}>
                {submitting ? 'Suljetaan…' : 'Sulje ennuste'}
              </button>
            </div>
          </div>
        </div>,
        document.body,
      )}
    </>
  );
}
