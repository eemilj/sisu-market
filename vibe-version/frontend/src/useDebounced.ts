import { useEffect, useMemo, useRef } from 'react';

/** Returns a stable function that runs `fn` once `ms` after the last call (latest `fn` wins). */
export function useDebounced(fn: () => void, ms: number): () => void {
  const latest = useRef(fn);
  latest.current = fn;
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined);
  useEffect(() => () => clearTimeout(timer.current), []);
  return useMemo(
    () => () => {
      clearTimeout(timer.current);
      timer.current = setTimeout(() => latest.current(), ms);
    },
    [ms],
  );
}
