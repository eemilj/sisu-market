import { fi } from '../i18n/strings.fi';
import { useAuth } from '../hooks/useAuth';

/**
 * The always-visible sisu balance (FR-008). Rendered only while logged in; the value comes from
 * the `me` cache, which the `balance-updated` SSE events keep current (see `useLiveUpdates`).
 */
export default function BalanceBadge() {
  const { user } = useAuth();

  if (!user) {
    return null;
  }

  return (
    <p
      aria-live="polite"
      aria-label={fi.auth.balanceLabel}
      className="rounded-full bg-emerald-100 px-3 py-1 text-sm font-semibold text-emerald-900"
    >
      {fi.sisu.amount(user.balanceSisu)}
    </p>
  );
}
