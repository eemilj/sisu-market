package fi.sisu.market.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Allows at most {@code maxEvents} recorded events per key within any {@code window}. Pure and in-memory
 * (one backend instance); unit-tested with a controllable clock.
 */
public class SlidingWindowLimiter {

    private final int maxEvents;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> events = new ConcurrentHashMap<>();

    public SlidingWindowLimiter(int maxEvents, Duration window, Clock clock) {
        if (maxEvents < 1 || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("maxEvents must be >= 1 and window positive");
        }
        this.maxEvents = maxEvents;
        this.window = window;
        this.clock = clock;
    }

    /** How long {@code key} must wait before another event is allowed; empty if allowed now. */
    public Optional<Duration> blockedFor(String key) {
        Deque<Instant> q = events.get(key);
        if (q == null) {
            return Optional.empty();
        }
        synchronized (q) {
            prune(q, clock.instant());
            if (q.size() < maxEvents) {
                return Optional.empty();
            }
            // Allowed again once the oldest event in the window expires.
            Duration wait = Duration.between(clock.instant(), q.peekFirst().plus(window));
            return Optional.of(wait.isNegative() ? Duration.ZERO : wait);
        }
    }

    public void record(String key) {
        Deque<Instant> q = events.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            Instant now = clock.instant();
            prune(q, now);
            q.addLast(now);
            // Never keep more than needed to decide.
            while (q.size() > maxEvents) {
                q.removeFirst();
            }
        }
    }

    public void reset(String key) {
        events.remove(key);
    }

    /** Drops keys with no events left in the window; call periodically to bound memory. */
    public void evictExpired() {
        Instant now = clock.instant();
        events.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                prune(e.getValue(), now);
                return e.getValue().isEmpty();
            }
        });
    }

    int trackedKeys() {
        return events.size();
    }

    private void prune(Deque<Instant> q, Instant now) {
        Instant cutoff = now.minus(window);
        while (!q.isEmpty() && !q.peekFirst().isAfter(cutoff)) {
            q.removeFirst();
        }
    }
}
