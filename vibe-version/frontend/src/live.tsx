import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { tokenStore, type LiveEvent } from './api';
import { useAuth } from './auth';

export type LiveStatus = 'connecting' | 'live' | 'reconnecting';
type Listener = (event: LiveEvent) => void;

interface LiveState {
  status: LiveStatus;
  /** Registers a listener; returns the unsubscribe function. */
  subscribe: (listener: Listener) => () => void;
}

const LiveContext = createContext<LiveState | null>(null);

const MIN_RETRY_MS = 1_000;
const MAX_RETRY_MS = 30_000;
/** The server sends a heartbeat every 20 s; silence for longer than this means the connection is dead. */
const SILENCE_LIMIT_MS = 45_000;

/**
 * Keeps one Server-Sent Events connection to /api/events open for the whole app. Uses fetch rather than
 * EventSource because EventSource can't send the Authorization header. Reconnects (with backoff) when the
 * connection drops and whenever the logged-in user changes.
 */
export function LiveProvider({ children }: { children: ReactNode }) {
  const { user, loading, refreshUser } = useAuth();
  const [status, setStatus] = useState<LiveStatus>('connecting');
  const listeners = useRef(new Set<Listener>());
  const userId = user?.id ?? null;

  useEffect(() => {
    if (loading) return;
    let stopped = false;
    let controller: AbortController | null = null;
    let retryTimer: ReturnType<typeof setTimeout> | undefined;
    let retryDelay = MIN_RETRY_MS;

    const dispatch = (event: LiveEvent) => listeners.current.forEach((listener) => listener(event));

    async function connect() {
      controller = new AbortController();
      let watchdog: ReturnType<typeof setTimeout> | undefined;
      const feedWatchdog = () => {
        clearTimeout(watchdog);
        watchdog = setTimeout(() => controller?.abort(), SILENCE_LIMIT_MS);
      };
      try {
        const token = tokenStore.get();
        const response = await fetch('/api/events', {
          headers: { Accept: 'text/event-stream', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
          signal: controller.signal,
        });
        if (response.status === 401) {
          // Expired token: let auth clear it; the user change then reconnects anonymously.
          void refreshUser();
          throw new Error('unauthorized');
        }
        if (!response.ok || !response.body) throw new Error(`HTTP ${response.status}`);

        feedWatchdog();
        const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
        let buffer = '';
        for (;;) {
          const { value, done } = await reader.read();
          if (done) break;
          feedWatchdog();
          buffer += value.replace(/\r\n/g, '\n');
          let boundary;
          while ((boundary = buffer.indexOf('\n\n')) >= 0) {
            const event = parseEvent(buffer.slice(0, boundary));
            buffer = buffer.slice(boundary + 2);
            if (!event) continue;
            if (event.type === 'ready') {
              setStatus('live');
              retryDelay = MIN_RETRY_MS;
            }
            dispatch(event);
          }
        }
      } catch {
        // Fall through to reconnect.
      } finally {
        clearTimeout(watchdog);
      }
      if (stopped) return;
      setStatus('reconnecting');
      retryTimer = setTimeout(connect, retryDelay);
      retryDelay = Math.min(retryDelay * 2, MAX_RETRY_MS);
    }

    setStatus('connecting');
    void connect();
    return () => {
      stopped = true;
      clearTimeout(retryTimer);
      controller?.abort();
    };
  }, [userId, loading, refreshUser]);

  const subscribe = useCallback((listener: Listener) => {
    listeners.current.add(listener);
    return () => {
      listeners.current.delete(listener);
    };
  }, []);
  const value = useMemo(() => ({ status, subscribe }), [status, subscribe]);

  return <LiveContext.Provider value={value}>{children}</LiveContext.Provider>;
}

/** Parses one SSE block ("event: x\ndata: {...}"). Comments (heartbeats) and unnamed events yield null. */
function parseEvent(block: string): LiveEvent | null {
  let type: string | null = null;
  const data: string[] = [];
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) type = line.slice(6).trim();
    else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''));
  }
  if (!type || data.length === 0) return null;
  try {
    return { type, data: JSON.parse(data.join('\n')) } as LiveEvent;
  } catch {
    return null;
  }
}

export function useLive(): LiveState {
  const ctx = useContext(LiveContext);
  if (!ctx) throw new Error('useLive must be used inside <LiveProvider>');
  return ctx;
}

/** Calls `handler` for every live event while the component is mounted. Always sees the latest handler. */
export function useLiveEvents(handler: Listener) {
  const { subscribe } = useLive();
  const latest = useRef(handler);
  latest.current = handler;
  useEffect(() => subscribe((event) => latest.current(event)), [subscribe]);
}
