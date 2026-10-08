import { useState } from 'react';
import { api, type Outcome, type Prediction } from '../api';
import { formatSisu } from '../format';

interface Props {
  prediction: Prediction;
  onResolved: (updated: Prediction) => void;
}

/** Shown on closed predictions to admins and to the creator. Asks for confirmation; resolution is final. */
export default function ResolvePanel({ prediction: p, onResolved }: Props) {
  const [choice, setChoice] = useState<Outcome | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);

  async function confirm() {
    if (!choice) return;
    setSubmitting(true);
    setError(null);
    try {
      const result = await api.resolve(p.id, choice);
      setDone(`Resolved ${choice}. Paid ${formatSisu(result.totalPaidOut)} to ${result.winningWagers} winning wager${result.winningWagers === 1 ? '' : 's'}.`);
      onResolved(result.prediction);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not resolve');
    } finally {
      setSubmitting(false);
      setChoice(null);
    }
  }

  if (done) return <p className="wager-confirmation" role="status">{done}</p>;

  return (
    <div className="resolve">
      {!choice ? (
        <>
          <p className="resolve-title">Resolve this prediction</p>
          <div className="wager-sides">
            <button type="button" className="btn btn-yes" onClick={() => setChoice('YES')}>Resolve YES</button>
            <button type="button" className="btn btn-no" onClick={() => setChoice('NO')}>Resolve NO</button>
          </div>
        </>
      ) : (
        <div className="resolve-confirm" role="alertdialog" aria-label="Confirm resolution">
          <p>
            Resolve as <strong>{choice}</strong>? Everyone who wagered {choice} gets paid. <strong>This can't be undone.</strong>
          </p>
          <div className="wager-actions">
            <button type="button" className="btn btn-ghost" onClick={() => setChoice(null)} disabled={submitting}>Cancel</button>
            <button type="button" className={`btn ${choice === 'YES' ? 'btn-yes' : 'btn-no'} is-selected`}
                    onClick={confirm} disabled={submitting}>
              {submitting ? 'Resolving…' : `Confirm ${choice}`}
            </button>
          </div>
        </div>
      )}
      {error && <p className="alert" role="alert">{error}</p>}
    </div>
  );
}
