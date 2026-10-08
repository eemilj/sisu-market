import { useState } from 'react';
import { api, type Outcome, type Prediction, type ResolutionResult } from '../api';
import { formatSisu, outcomeLabels, plural } from '../format';

interface Props {
  prediction: Prediction;
  /** The card then shows the resolved state with the payout summary. */
  onResolved: (result: ResolutionResult) => void;
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
      setDone(result.winningWagers === 0
        ? `Ratkaistu: ${outcomeLabels[choice]}. Voittavia panoksia ei ollut, joten mitään ei maksettu.`
        : `Ratkaistu: ${outcomeLabels[choice]}. Maksettiin ${formatSisu(result.totalPaidOut)} – ${plural(result.winningWagers, 'voittava panos', 'voittavaa panosta')}.`);
      onResolved(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Ratkaiseminen epäonnistui. Yritä uudelleen.');
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
          <p className="resolve-title">Ratkaise ennuste</p>
          <p className="resolve-help">Valitse lopputulos ratkaisuperusteiden mukaan.</p>
          <div className="wager-sides">
            <button type="button" className="btn btn-yes" onClick={() => setChoice('YES')}>Ratkesi KYLLÄ</button>
            <button type="button" className="btn btn-no" onClick={() => setChoice('NO')}>Ratkesi EI</button>
          </div>
        </>
      ) : (
        <div className="resolve-confirm" role="alertdialog" aria-label="Vahvista ratkaisu">
          <p>
            Ratkaistaanko ennuste <strong>{outcomeLabels[choice]}</strong>? Kaikille {outcomeLabels[choice]}-vaihtoehdolle
            panostaneille maksetaan voitot. <strong>Ratkaisua ei voi perua.</strong>
          </p>
          <div className="wager-actions">
            <button type="button" className="btn btn-ghost" onClick={() => setChoice(null)} disabled={submitting}>Peruuta</button>
            <button type="button" className={`btn ${choice === 'YES' ? 'btn-yes' : 'btn-no'} is-selected`}
                    onClick={confirm} disabled={submitting}>
              {submitting ? 'Ratkaistaan…' : `Vahvista: ${outcomeLabels[choice]}`}
            </button>
          </div>
        </div>
      )}
      {error && <p className="alert" role="alert">{error}</p>}
    </div>
  );
}
