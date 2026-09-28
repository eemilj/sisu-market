package market.sisu.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import market.sisu.auth.LoginAttempt;
import market.sisu.auth.LoginAttemptRepository;
import market.sisu.auth.LoginRateLimiter;
import market.sisu.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The 5-attempts/15-minute login cooldown (FR-037) with a fixed clock and no database. */
class LoginRateLimiterTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

    private LoginAttemptRepository attempts;
    private LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        attempts = mock(LoginAttemptRepository.class);
        limiter = new LoginRateLimiter(attempts, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void allowsAttemptsWhileBelowTheThreshold() {
        when(attempts.find("matti", "127.0.0.1")).thenReturn(Optional.of(failures(4, NOW)));

        limiter.checkBlocked("matti", "127.0.0.1");
    }

    @Test
    void blocksTheSixthAttemptInsideTheWindowWithARetryAfterHint() {
        when(attempts.find("matti", "127.0.0.1")).thenReturn(Optional.of(failures(5, NOW)));

        ApiException e = assertThrows(ApiException.class, () -> limiter.checkBlocked("matti", "127.0.0.1"));

        assertEquals(429, e.status());
        assertEquals("TOO_MANY_ATTEMPTS", e.code());
        assertEquals(900L, e.retryAfterSeconds());
    }

    @Test
    void retryAfterCountsDownAsTheWindowElapses() {
        when(attempts.find("matti", "127.0.0.1")).thenReturn(Optional.of(failures(7, NOW.minusSeconds(840))));

        ApiException e = assertThrows(ApiException.class, () -> limiter.checkBlocked("matti", "127.0.0.1"));

        assertEquals(60L, e.retryAfterSeconds());
    }

    @Test
    void anExpiredWindowStartsOver() {
        when(attempts.find("matti", "127.0.0.1")).thenReturn(Optional.of(failures(9, NOW.minusSeconds(901))));

        limiter.checkBlocked("matti", "127.0.0.1");

        verify(attempts).clear("matti", "127.0.0.1");
    }

    @Test
    void recordingARestartAfterAnExpiredWindow() {
        when(attempts.find("matti", "127.0.0.1")).thenReturn(Optional.of(failures(5, NOW.minusSeconds(901))));

        limiter.recordFailure("matti", "127.0.0.1");

        verify(attempts).clear("matti", "127.0.0.1");
        verify(attempts).recordFailure(eq("matti"), eq("127.0.0.1"), eq(NOW));
    }

    @Test
    void recordingInsideTheWindowKeepsCounting() {
        when(attempts.find("matti", "127.0.0.1")).thenReturn(Optional.of(failures(2, NOW)));

        limiter.recordFailure("matti", "127.0.0.1");

        verify(attempts, never()).clear(any(), any());
        verify(attempts).recordFailure(eq("matti"), eq("127.0.0.1"), eq(NOW));
    }

    private static LoginAttempt failures(int count, Instant windowStartedAt) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.username = "matti";
        attempt.ip = "127.0.0.1";
        attempt.failures = count;
        attempt.windowStartedAt = windowStartedAt;
        return attempt;
    }
}
