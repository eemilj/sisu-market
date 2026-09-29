package market.sisu.auth;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import market.sisu.common.ApiException;

/**
 * Login throttling (FR-037, research.md section 5): after 5 consecutive failures for one
 * (username, IP) pair, further attempts are rejected for 15 minutes — even with correct
 * credentials. The window slides: once it has fully passed, the counter starts over.
 */
@ApplicationScoped
public class LoginRateLimiter {

    /** Consecutive failures after which the pair enters the cooldown. */
    public static final int MAX_FAILURES = 5;

    private final LoginAttemptRepository attempts;
    private final Clock clock;

    public LoginRateLimiter(LoginAttemptRepository attempts, Clock clock) {
        this.attempts = attempts;
        this.clock = clock;
    }

    /**
     * @throws ApiException {@code 429 TOO_MANY_ATTEMPTS} with a {@code Retry-After} hint while the
     *     pair is inside its cooldown window
     */
    public void checkBlocked(String username, String ip) {
        Optional<LoginAttempt> existing = attempts.find(username, ip);
        if (existing.isEmpty()) {
            return;
        }
        LoginAttempt attempt = existing.get();
        Instant windowEnd = attempt.windowStartedAt.plus(LoginAttemptRepository.WINDOW);
        Instant now = clock.instant();
        if (!now.isBefore(windowEnd)) {
            attempts.clear(username, ip);
            return;
        }
        if (attempt.failures >= MAX_FAILURES) {
            long retryAfter = Math.max(1, Duration.between(now, windowEnd).toSeconds());
            throw ApiException.tooManyRequests(
                    "TOO_MANY_ATTEMPTS", "Too many failed login attempts, try again later", retryAfter);
        }
    }

    /** Counts one more failure, restarting the window when the previous one has fully passed. */
    public LoginAttempt recordFailure(String username, String ip) {
        Instant now = clock.instant();
        attempts.find(username, ip)
                .filter(attempt -> !now.isBefore(attempt.windowStartedAt.plus(LoginAttemptRepository.WINDOW)))
                .ifPresent(stale -> attempts.clear(username, ip));
        return attempts.recordFailure(username, ip, now);
    }

    /** Forgets the pair's failures after a successful login. */
    public void clear(String username, String ip) {
        attempts.clear(username, ip);
    }
}
