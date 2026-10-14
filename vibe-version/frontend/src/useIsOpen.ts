import { useEffect, useState } from 'react';
import type { Prediction } from './api';

/** setTimeout can't wait longer than ~24.8 days. */
const MAX_TIMEOUT_MS = 2_147_483_647;

/**
 * Whether wagering is open right now. Flips to false exactly at `closesAt` without waiting for the server's
 * close event (the server refuses late wagers regardless).
 */
export function useIsOpen(p: Prediction): boolean {
  const closesAt = new Date(p.closesAt).getTime();
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const remaining = closesAt - Date.now();
    if (remaining <= 0 || remaining > MAX_TIMEOUT_MS) return;
    const timer = setTimeout(() => setNow(Date.now()), remaining + 50);
    return () => clearTimeout(timer);
  }, [closesAt]);

  return p.status === 'OPEN' && closesAt > Math.max(now, Date.now());
}
