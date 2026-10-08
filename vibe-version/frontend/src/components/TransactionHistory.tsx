import { useEffect, useState } from 'react';
import { api, type Transaction } from '../api';
import { useLiveEvents } from '../live';
import { formatDateTime, formatSignedSisu, formatSisu, transactionLabels } from '../format';

/** The logged-in user's balance changes, newest first, updating live. */
export default function TransactionHistory() {
  const [items, setItems] = useState<Transaction[] | null>(null);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  /** Bumped on every live (re)connect to reload the newest page. */
  const [resync, setResync] = useState(0);

  useLiveEvents((event) => {
    if (event.type === 'ready') {
      setResync((n) => n + 1);
    } else if (event.type === 'wallet.transaction') {
      const t = event.data.transaction;
      // Newest first; ids sort chronologically. Skip if the initial load already contained it.
      setItems((list) => (list && !list.some((x) => x.id === t.id)
        ? [t, ...list].sort((a, b) => b.id.localeCompare(a.id))
        : list));
    }
  });

  useEffect(() => {
    let cancelled = false;
    api
      .transactions()
      .then((page) => {
        if (cancelled) return;
        // Keep entries that arrived live while this request was in flight.
        setItems((current) => {
          const newest = page.items[0]?.id ?? '';
          const arrivedMeanwhile = (current ?? []).filter((t) => t.id > newest);
          return [...arrivedMeanwhile, ...page.items];
        });
        setNextCursor(page.nextCursor);
      })
      .catch((err: Error) => !cancelled && setError(err.message));
    return () => {
      cancelled = true;
    };
  }, [resync]);

  async function loadMore() {
    setLoadingMore(true);
    try {
      const page = await api.transactions(nextCursor);
      setItems((prev) => [...(prev ?? []), ...page.items]);
      setNextCursor(page.nextCursor);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Vanhempia tapahtumia ei voitu ladata. Yritä uudelleen.');
    } finally {
      setLoadingMore(false);
    }
  }

  if (error) return <p className="alert">{error}</p>;
  if (items === null) return <p className="muted">Ladataan tapahtumia…</p>;
  if (items.length === 0) return <p className="muted">Ei vielä tapahtumia.</p>;

  return (
    <>
      <ul className="ledger">
        {items.map((t) => (
          <li key={t.id} className="ledger-row">
            <div className="ledger-main">
              <span className="ledger-desc">{t.predictionQuestion ?? transactionLabels[t.type] ?? t.description}</span>
              <span className="ledger-meta">
                <span className={`tag tx-${t.type.toLowerCase()}`}>{transactionLabels[t.type] ?? t.type}</span>
                {formatDateTime(t.createdAt)}
              </span>
            </div>
            <div className="ledger-amounts">
              <span className={t.amount >= 0 ? 'amount-credit' : 'amount-debit'}>{formatSignedSisu(t.amount)}</span>
              <span className="ledger-after">Saldo {formatSisu(t.balanceAfter)}</span>
            </div>
          </li>
        ))}
      </ul>
      {nextCursor && (
        <button className="btn btn-ghost btn-block" onClick={loadMore} disabled={loadingMore}>
          {loadingMore ? 'Ladataan…' : 'Näytä vanhemmat tapahtumat'}
        </button>
      )}
    </>
  );
}
