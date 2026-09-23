# Real-time Contract: SSE Update Stream

Complements `openapi.yaml`, which covers request/response REST endpoints only. This stream is how
the frontend satisfies SC-002 / Constitution III (balance and odds update within 1 second of a
successful wager) without polling. Decision and rationale: `research.md` §1.

## Endpoint

```
GET /api/realtime/updates
Accept: text/event-stream
Cookie: sisu_session=<jwt>   (same httpOnly cookie as REST calls; see research.md §3)
```

- Requires a valid session cookie; unauthenticated requests get `401` before the stream opens.
- One long-lived connection per logged-in browser tab, opened via the native `EventSource` API.
- The server keeps the connection open and pushes events as they occur; the browser's built-in
  reconnect-with-backoff handles drops transparently (no custom reconnection logic needed on the
  frontend).

## Event types

Each SSE event's `event:` field names the event; `data:` is a JSON payload.

### `balance-updated`

Sent only to the connection belonging to the affected user (never broadcast).

```json
{ "balanceSisu": 950 }
```

Triggered by: a wager placed by this user, a payout, a refund, or the starting grant.

### `odds-updated`

Broadcast to every connected client currently viewing predictions (the frontend filters client-side
by whether the given prediction is on screen; the server does not track per-client subscriptions to
keep this simple).

```json
{
  "predictionId": "665f1c2e...",
  "oddsYesPercent": 63.2,
  "oddsNoPercent": 36.8,
  "totalSisuWagered": 1420
}
```

Triggered by: any wager placed on that prediction (FR-023).

### `prediction-status-changed`

Broadcast to all connected clients.

```json
{ "predictionId": "665f1c2e...", "status": "CLOSED" | "RESOLVED" | "CANCELLED", "winningOutcome": "YES" | "NO" | null }
```

Triggered by: automatic closing at `closingAt`, resolution (FR-026), or cancellation (FR-036).

## Frontend integration

`useLiveUpdates()` opens one `EventSource`, dispatches incoming events into TanStack Query's cache
(`queryClient.setQueryData`) for the matching prediction/user query keys, so components re-render
from the same cache REST calls already populate — no separate real-time state store.

## Explicitly out of scope for this stream

- Client → server messages (there are none; all writes are REST POSTs per `openapi.yaml`).
- Guaranteed delivery / replay of missed events while disconnected — on reconnect the frontend
  refetches the relevant REST endpoint (e.g. the prediction list) to resync, then resumes live
  updates. This is acceptable because every event here is also derivable from a REST GET; the
  stream is a low-latency notification channel, not the system of record.
