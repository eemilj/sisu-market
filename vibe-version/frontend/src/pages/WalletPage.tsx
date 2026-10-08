import { useEffect, useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { api, type Transaction, type TransactionType } from '../api';
import { useAuth } from '../auth';
import { formatDateTime, formatSignedSisu, formatSisu } from '../format';

const typeLabels: Record<TransactionType, string> = {
  SIGNUP_BONUS: 'Bonus',
  OPENING_BALANCE: 'Opening',
  WAGER: 'Wager',
};

export default function WalletPage() {
  const { user, loading, refreshUser } = useAuth();
  const location = useLocation();
  const [items, setItems] = useState<Transaction[] | null>(null);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const userId = user?.id;

  // Load the newest page, and make sure the balance shown matches the history.
  useEffect(() => {
    if (!userId) return;
    let cancelled = false;
    void refreshUser();
    api
      .transactions()
      .then((page) => {
        if (cancelled) return;
        setItems(page.items);
        setNextCursor(page.nextCursor);
      })
      .catch((err: Error) => !cancelled && setError(err.message));
    return () => {
      cancelled = true;
    };
  }, [userId, refreshUser]);

  if (loading) return <p className="muted">Loading…</p>;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;

  async function loadMore() {
    setLoadingMore(true);
    try {
      const page = await api.transactions(nextCursor);
      setItems((prev) => [...(prev ?? []), ...page.items]);
      setNextCursor(page.nextCursor);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load more');
    } finally {
      setLoadingMore(false);
    }
  }

  return (
    <div className="wallet">
      <section className="balance-card" aria-label="Current balance">
        <span className="balance-card-label">Your balance</span>
        <span className="balance-card-amount">{formatSisu(user.balance)}</span>
        <span className="balance-card-note">Sisu is virtual currency with no real-world value.</span>
      </section>

      <h2>Transaction history</h2>
      {error && <p className="alert">{error}</p>}
      {items === null ? (
        !error && <p className="muted">Loading history…</p>
      ) : items.length === 0 ? (
        <p className="muted">No transactions yet.</p>
      ) : (
        <>
          <ul className="ledger">
            {items.map((t) => (
              <li key={t.id} className="ledger-row">
                <div className="ledger-main">
                  <span className="ledger-desc">{t.description}</span>
                  <span className="ledger-meta">
                    <span className="tag">{typeLabels[t.type] ?? t.type}</span>
                    {formatDateTime(t.createdAt)}
                  </span>
                </div>
                <div className="ledger-amounts">
                  <span className={t.amount >= 0 ? 'amount-credit' : 'amount-debit'}>{formatSignedSisu(t.amount)}</span>
                  <span className="ledger-after">Balance {formatSisu(t.balanceAfter)}</span>
                </div>
              </li>
            ))}
          </ul>
          {nextCursor && (
            <button className="btn btn-ghost btn-block" onClick={loadMore} disabled={loadingMore}>
              {loadingMore ? 'Loading…' : 'Load older transactions'}
            </button>
          )}
        </>
      )}
    </div>
  );
}
