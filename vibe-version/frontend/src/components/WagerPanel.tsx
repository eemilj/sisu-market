import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiError, type Outcome, type Prediction, type Quote } from '../api';
import { useAuth } from '../auth';
import { formatProbability, formatSisu } from '../format';
import { useIsOpen } from '../useIsOpen';

interface Props {
  prediction: Prediction;
  onWagered: (updated: Prediction) => void;
}

const QUICK_AMOUNTS = [10, 50, 100];
const QUOTE_DELAY_MS = 250;

/** Validates the typed amount against the rules the server enforces. Returns an error message or null. */
function amountProblem(raw: string, balance: number): string | null {
  if (raw.trim() === '') return 'Enter an amount';
  if (!/^\d+$/.test(raw.trim())) return 'Use a whole number of Sisu';
  const amount = Number(raw);
  if (amount <= 0) return 'Amount must be greater than zero';
  if (amount > balance) return `You only have ${formatSisu(balance)}`;
  return null;
}

function newRequestId(): string {
  // randomUUID needs a secure context (https or localhost); fall back elsewhere.
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}

export default function WagerPanel({ prediction: p, onWagered }: Props) {
  const { user, applyBalance, refreshUser } = useAuth();
  const [side, setSide] = useState<Outcome | null>(null);
  const [amount, setAmount] = useState('');
  const [quote, setQuote] = useState<Quote | null>(null);
  const [quoteError, setQuoteError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [confirmation, setConfirmation] = useState<string | null>(null);
  const [quoteVersion, setQuoteVersion] = useState(0);
  /** One idempotency key per intended wager, reused if the same submit is retried after a lost response. */
  const requestId = useRef<string | null>(null);

  const balance = user?.balance ?? 0;
  const problem = amountProblem(amount, balance);
  const isOpen = useIsOpen(p);

  // Fetch a fresh quote whenever the wager or the market changes (debounced while typing).
  useEffect(() => {
    setQuote(null);
    setQuoteError(null);
    if (!side || problem) return;
    const controller = new AbortController();
    const timer = setTimeout(() => {
      api
        .quote(p.id, side, Number(amount), controller.signal)
        .then(setQuote)
        .catch((err: Error) => !controller.signal.aborted && setQuoteError(err.message));
    }, QUOTE_DELAY_MS);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [p.id, p.yesProbability, side, amount, problem, quoteVersion]);

  // A different wager is a different request.
  useEffect(() => {
    requestId.current = null;
  }, [side, amount]);

  if (!isOpen) return <p className="wager-closed">Wagering closed</p>;

  const noPercent = 100 - p.yesPercent;
  if (!user) {
    return (
      <div className="wager-sides">
        <Link to="/login" state={{ from: '/' }} className="btn btn-yes">Yes {p.yesPercent}%</Link>
        <Link to="/login" state={{ from: '/' }} className="btn btn-no">No {noPercent}%</Link>
      </div>
    );
  }

  function choose(next: Outcome) {
    setSide(next);
    setError(null);
    setConfirmation(null);
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!side || problem || !quote) return;
    requestId.current ??= newRequestId();
    setSubmitting(true);
    setError(null);
    try {
      const result = await api.placeWager(p.id, {
        side,
        amount: quote.amount,
        minPayout: quote.payoutIfWin,
        requestId: requestId.current,
      });
      requestId.current = null;
      applyBalance(result.balance, result.balanceVersion);
      onWagered(result.prediction);
      setConfirmation(
        `You wagered ${formatSisu(result.wager.amount)} on ${result.wager.side}. ` +
          `If ${result.wager.side} wins you receive ${formatSisu(result.wager.payoutIfWin)}.`,
      );
      setSide(null);
      setAmount('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Wager failed');
      if (err instanceof ApiError && err.status === 409) {
        // Odds moved, market closed or balance spent elsewhere: nothing was placed, so resync and requote.
        requestId.current = null;
        void refreshUser();
        setQuoteVersion((v) => v + 1);
      }
      // Network failure (status 0): keep the requestId so resubmitting can't double-wager.
    } finally {
      setSubmitting(false);
    }
  }

  const sideProbability = side === 'NO' ? 1 - p.yesProbability : p.yesProbability;
  const quoteForCurrentInput = quote && side === quote.side && Number(amount) === quote.amount ? quote : null;

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

          {!problem && (
            <dl className="quote" aria-live="polite">
              <div className="quote-payout">
                <dt>Payout if {side} wins</dt>
                <dd>
                  {quoteForCurrentInput ? (
                    <>
                      {formatSisu(quoteForCurrentInput.payoutIfWin)}{' '}
                      <span className="quote-multiple">
                        ({(quoteForCurrentInput.payoutIfWin / quoteForCurrentInput.amount).toFixed(2)}×)
                      </span>
                    </>
                  ) : quoteError ? '—' : 'Calculating…'}
                </dd>
              </div>
              <div>
                <dt>{side} odds now</dt>
                <dd>{formatProbability(sideProbability)}</dd>
              </div>
              {quoteForCurrentInput && (
                <>
                  <div>
                    <dt>Your average price</dt>
                    <dd>{formatProbability(quoteForCurrentInput.averagePrice)}</dd>
                  </div>
                  <div>
                    <dt>{side} odds after</dt>
                    <dd>
                      {formatProbability(side === 'YES'
                        ? quoteForCurrentInput.yesProbabilityAfter
                        : 1 - quoteForCurrentInput.yesProbabilityAfter)}
                    </dd>
                  </div>
                </>
              )}
            </dl>
          )}
          {quoteError && <p className="alert">{quoteError}</p>}
          <p className="wager-terms">The payout is locked when you place the wager. If {side} loses, you lose your stake.</p>
          {error && <p className="alert" role="alert">{error}</p>}

          <div className="wager-actions">
            <button type="button" className="btn btn-ghost" onClick={() => setSide(null)}>Cancel</button>
            <button type="submit" className={`btn ${side === 'YES' ? 'btn-yes' : 'btn-no'} is-selected`}
                    disabled={submitting || problem !== null || !quoteForCurrentInput}>
              {submitting ? 'Placing…' : `Wager ${problem ? '' : formatSisu(Number(amount)) + ' '}on ${side}`}
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
