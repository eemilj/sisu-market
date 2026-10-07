package fi.sisu.vibe.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window limit on failed logins, tracked separately per username and per IP address. In
 * memory, so it resets on restart and is per instance; good enough for a single-node deployment.
 */
public class LoginThrottle {

    private final Clock clock;
    private final int maxFailures;
    private final Duration window;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginThrottle(Clock clock, int maxFailures, Duration window) {
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.window = window;
    }

    public boolean isBlocked(String username, String ip) {
        return count("u:" + username.toLowerCase()) >= maxFailures || count("ip:" + ip) >= maxFailures * 4;
    }

    public void recordFailure(String username, String ip) {
        Instant now = clock.instant();
        for (String key : new String[] {"u:" + username.toLowerCase(), "ip:" + ip}) {
            failures.compute(key, (k, q) -> {
                Deque<Instant> deque = q == null ? new ArrayDeque<>() : q;
                deque.addLast(now);
                return deque;
            });
        }
    }

    public void recordSuccess(String username) {
        failures.remove("u:" + username.toLowerCase());
    }

    private int count(String key) {
        Instant cutoff = clock.instant().minus(window);
        Deque<Instant> q = failures.computeIfPresent(key, (k, v) -> {
            while (!v.isEmpty() && v.peekFirst().isBefore(cutoff)) {
                v.pollFirst();
            }
            return v.isEmpty() ? null : v;
        });
        return q == null ? 0 : q.size();
    }
}
