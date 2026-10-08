import { useEffect, useState } from 'react';
import { api, type WagerHistoryItem } from '../api';
import { useLiveEvents } from '../live';
import { formatDateTime, formatProbability, formatSisu, outcomeLabels, wagerStatusLabels } from '../format';
import { useDebounced } from '../useDebounced';

/** The logged-in user's wagers with their outcome, newest first. Reloads when one of them settles. */
export default function WagerHistory() {
  const [items, setItems] = useState<WagerHistoryItem[] | null>(null);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);
  const scheduleReload = useDebounced(() => setReload((n) => n + 1), 300);

  useLiveEvents((event) => {
    if (event.type === 'ready') scheduleReload();
    // A new wager of ours, or a payout.
    if (event.type === 'wallet.transaction' && ['WAGER', 'PAYOUT'].includes(event.data.transaction.type)) scheduleReload();
    // One of our predictions closed or resolved (losing wagers get no wallet event).
    if (event.type === 'prediction.updated' && event.data.status !== 'OPEN'
        && items?.some((i) => i.wager.predictionId === event.data.id)) scheduleReload();
  });

  useEffect(() => {
    let cancelled = false;
    api
      .myWagers()
      .then((page) => {
        if (cancelled) return;
        setItems(page.items);
        setNextCursor(page.nextCursor);
      })
      .catch((err: Error) => !cancelled && setError(err.message));
    return () => {
      cancelled = true;
    };
  }, [reload]);

  async function loadMore() {
    setLoadingMore(true);
    try {
      const page = await api.myWagers(nextCursor);
      setItems((prev) => [...(prev ?? []), ...page.items]);
      setNextCursor(page.nextCursor);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Vanhempia panoksia ei voitu ladata. Yritä uudelleen.');
    } finally {
      setLoadingMore(false);
    }
  }

  if (error) return <p className="alert">{error}</p>;
  if (items === null) return <p className="muted">Ladataan panoksia…</p>;
  if (items.length === 0) return <p className="muted">Ei vielä panoksia. Valitse ennuste etusivulta ja panosta KYLLÄ tai EI.</p>;

  return (
    <>
      <ul className="ledger">
        {items.map(({ wager: w, question, predictionStatus }) => (
          <li key={w.id} className="ledger-row">
            <div className="ledger-main">
              <span className="ledger-desc">{question}</span>
              <span className="ledger-meta">
                <span className={`tag side-${w.side.toLowerCase()}`}>{outcomeLabels[w.side]}</span>
                <span className={`tag wager-${w.status.toLowerCase()}`}>
                  {w.status === 'OPEN' && predictionStatus === 'CLOSED' ? 'Odottaa ratkaisua' : wagerStatusLabels[w.status]}
                </span>
                {formatDateTime(w.createdAt)} · keskihinta {formatProbability(w.averagePrice)}
              </span>
            </div>
            <div className="ledger-amounts">
              <span>Panos {formatSisu(w.amount)}</span>
              <span className="ledger-after">
                {w.status === 'WON' && <span className="amount-credit">Voitit {formatSisu(w.payout ?? 0)}</span>}
                {w.status === 'LOST' && <span className="amount-debit">Panos hävittiin</span>}
                {w.status === 'REFUNDED' && 'Panos palautettiin'}
                {w.status === 'OPEN' && `Voitto ${formatSisu(w.payoutIfWin)}, jos ${outcomeLabels[w.side]}`}
              </span>
            </div>
          </li>
        ))}
      </ul>
      {nextCursor && (
        <button className="btn btn-ghost btn-block" onClick={loadMore} disabled={loadingMore}>
          {loadingMore ? 'Ladataan…' : 'Näytä vanhemmat panokset'}
        </button>
      )}
    </>
  );
}
