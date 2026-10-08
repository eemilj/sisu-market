import { useEffect, useState } from 'react';
import { api, type WagerHistoryItem, type WagerStatus } from '../api';
import { useLiveEvents } from '../live';
import { formatDateTime, formatProbability, formatSisu } from '../format';
import { useDebounced } from '../useDebounced';

const statusLabels: Record<WagerStatus, string> = {
  OPEN: 'Open',
  WON: 'Won',
  LOST: 'Lost',
  REFUNDED: 'Refunded',
};

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
      setError(err instanceof Error ? err.message : 'Could not load more');
    } finally {
      setLoadingMore(false);
    }
  }

  if (error) return <p className="alert">{error}</p>;
  if (items === null) return <p className="muted">Loading wagers…</p>;
  if (items.length === 0) return <p className="muted">No wagers yet. Pick a prediction and back YES or NO.</p>;

  return (
    <>
      <ul className="ledger">
        {items.map(({ wager: w, question, predictionStatus }) => (
          <li key={w.id} className="ledger-row">
            <div className="ledger-main">
              <span className="ledger-desc">{question}</span>
              <span className="ledger-meta">
                <span className={`tag side-${w.side.toLowerCase()}`}>{w.side}</span>
                <span className={`tag wager-${w.status.toLowerCase()}`}>
                  {w.status === 'OPEN' && predictionStatus === 'CLOSED' ? 'Awaiting result' : statusLabels[w.status]}
                </span>
                {formatDateTime(w.createdAt)} · avg price {formatProbability(w.averagePrice)}
              </span>
            </div>
            <div className="ledger-amounts">
              <span>{formatSisu(w.amount)} staked</span>
              <span className="ledger-after">
                {w.status === 'WON' && <span className="amount-credit">Won {formatSisu(w.payout ?? 0)}</span>}
                {w.status === 'LOST' && <span className="amount-debit">Lost stake</span>}
                {w.status === 'REFUNDED' && 'Stake refunded'}
                {w.status === 'OPEN' && `Pays ${formatSisu(w.payoutIfWin)} if ${w.side}`}
              </span>
            </div>
          </li>
        ))}
      </ul>
      {nextCursor && (
        <button className="btn btn-ghost btn-block" onClick={loadMore} disabled={loadingMore}>
          {loadingMore ? 'Loading…' : 'Load older wagers'}
        </button>
      )}
    </>
  );
}
