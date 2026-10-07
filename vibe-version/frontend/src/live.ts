import { useEffect, useRef } from 'react';
import type { Prediction } from './types';

type Listener = (p: Prediction) => void;

// One shared EventSource for the whole app; it reconnects automatically if the server drops.
const listeners = new Set<Listener>();
let source: EventSource | null = null;

function ensureConnected() {
  if (source) return;
  source = new EventSource('/api/live');
  source.onmessage = (msg) => {
    try {
      const event = JSON.parse(msg.data) as { type: string; prediction: Prediction | null };
      if (event.type === 'prediction' && event.prediction) {
        listeners.forEach((l) => l(event.prediction!));
      }
    } catch {
      // ignore malformed events
    }
  };
}

/** Calls {@code onUpdate} whenever any prediction changes on the server. */
export function useLivePredictions(onUpdate: Listener) {
  const ref = useRef(onUpdate);
  useEffect(() => {
    ref.current = onUpdate;
  });
  useEffect(() => {
    const listener: Listener = (p) => ref.current(p);
    listeners.add(listener);
    ensureConnected();
    return () => {
      listeners.delete(listener);
    };
  }, []);
}
