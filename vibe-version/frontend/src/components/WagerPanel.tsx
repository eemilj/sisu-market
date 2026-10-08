import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiError, type Outcome, type Prediction } from '../api';
import { useAuth } from '../auth';
import { formatSisu } from '../format';

interface Props {
  prediction: Prediction;
  onWagered: (updated: Prediction) => void;
}

const QUICK_AMOUNTS = [10, 50, 100];

/** Validates the typed amount against the rules the server enforces. Returns an error message or null. */
function amountProblem(raw: string, balance: number): string | null {
  if (raw.trim() === '') return 'Enter an amount';
  if (!/^\d+$/.test(raw.trim())) return 'Use a whole number of Sisu';
  const amount = Number(raw);
  if (amount <= 0) return 'Amount must be greater than zero';
  if (amount > balance) return `You only have ${formatSisu(balance)}`;
  return null;
}

export default function WagerPanel({ prediction: p, onWagered }: Props) {
  const { user, setBalance, refreshUser } = useAuth();
  const [side, setSide] = useState<Outcome | null>(null);
  const [amount, setAmount] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [confirmation, setConfirmation] = useState<string | null>(null);

  const noPercent = 100 - p.yesPercent;
  const isOpen = p.status === 'OPEN' && new Date(p.closesAt).getTime() > Date.now();

  if (!isOpen) return <p className="wager-closed">Wagering closed</p>;

  if (!user) {
    return (
      <div className="wager-sides">
        <Link to="/login" state={{ from: '/' }} className="btn btn-yes">Yes {p.yesPercent}%</Link>
        <Link to="/login" state={{ from: '/' }} className="btn btn-no">No {noPercent}%</Link>
      </div>
    );
  }

  const balance = user.balance;
  const problem = amountProblem(amount, balance);

  function choose(next: Outcome) {
    setSide(next);
    setError(null);
    setConfirmation(null);
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!side || problem) return;
    setSubmitting(true);
    setError(null);
    try {
      const result = await api.placeWager(p.id, side, Number(amount));
      setBalance(result.balance);
      onWagered(result.prediction);
      setConfirmation(`You wagered ${formatSisu(result.wager.amount)} on ${result.wager.side}.`);
      setSide(null);
      setAmount('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Wager failed');
      // A conflict means our view was stale (balance spent elsewhere, or the market closed): resync the balance.
      if (err instanceof ApiError && err.status === 409) void refreshUser();
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="wager">
      <div className="wager-sides" role="group" aria-label="Choose a side">
        <button type="button" className={`btn btn-yes ${side === 'YES' ? 'is-selected' : ''}`}
                onClick={() => choose('YES')} aria-pressed={side === 'YES'}>
          Yes {p.yesPercent}%
        </button>
        <button type="button" className={`btn btn-no ${side === 'NO' ? 'is-selected' : ''}`}
                onClick={() => choose('NO')} aria-pressed={side === 'NO'}>
          No {noPercent}%
        </button>
      </div>

      {confirmation && !side && <p className="wager-confirmation" role="status">{confirmation}</p>}

      {side && (
        <form className="wager-form" onSubmit={submit} noValidate>
          <label className="wager-amount">
            <span>Amount</span>
            <input
              type="number"
              inputMode="numeric"
              min={1}
              step={1}
              max={balance}
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="0"
              autoFocus
              aria-describedby={`wager-hint-${p.id}`}
            />
            <span className="wager-unit">Sisu</span>
          </label>
          <div className="wager-quick">
            {QUICK_AMOUNTS.filter((q) => q <= balance).map((q) => (
              <button type="button" key={q} className="chip" onClick={() => setAmount(String(q))}>{q}</button>
            ))}
            {balance > 0 && (
              <button type="button" className="chip" onClick={() => setAmount(String(balance))}>Max</button>
            )}
          </div>
          <p id={`wager-hint-${p.id}`} className={`wager-hint ${amount && problem ? 'is-error' : ''}`}>
            {amount && problem ? problem : `Balance: ${formatSisu(balance)}`}
          </p>
          {error && <p className="alert" role="alert">{error}</p>}
          <div className="wager-actions">
            <button type="button" className="btn btn-ghost" onClick={() => setSide(null)}>Cancel</button>
            <button type="submit" className={`btn ${side === 'YES' ? 'btn-yes' : 'btn-no'} is-selected`}
                    disabled={submitting || problem !== null}>
              {submitting ? 'Placing…' : `Wager ${problem ? '' : formatSisu(Number(amount)) + ' '}on ${side}`}
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
