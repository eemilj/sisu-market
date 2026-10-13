package fi.sisu.market.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlidingWindowLimiterTest {

    private final MutableClock clock = new MutableClock();
    private final SlidingWindowLimiter limiter = new SlidingWindowLimiter(3, Duration.ofMinutes(10), clock);

    @Test
    void allowsUpToTheLimitThenBlocks() {
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.blockedFor("a").isEmpty(), "attempt " + (i + 1));
            limiter.record("a");
        }
        assertEquals(Optional.of(Duration.ofMinutes(10)), limiter.blockedFor("a"));
    }

    @Test
    void windowSlidesWithTheOldestEvent() {
        limiter.record("a");
        clock.advance(Duration.ofMinutes(4));
        limiter.record("a");
        limiter.record("a");
        // Blocked until the first event is 10 minutes old.
        assertEquals(Optional.of(Duration.ofMinutes(6)), limiter.blockedFor("a"));
        clock.advance(Duration.ofMinutes(6));
        assertTrue(limiter.blockedFor("a").isEmpty(), "first event expired");
        limiter.record("a");
        assertEquals(Optional.of(Duration.ofMinutes(4)), limiter.blockedFor("a"));
    }

    @Test
    void keysAreIndependentAndResettable() {
        for (int i = 0; i < 3; i++) {
            limiter.record("a");
        }
        assertTrue(limiter.blockedFor("b").isEmpty());
        limiter.reset("a");
        assertTrue(limiter.blockedFor("a").isEmpty());
    }

    @Test
    void evictionForgetsIdleKeys() {
        limiter.record("a");
        limiter.record("b");
        clock.advance(Duration.ofMinutes(5));
        limiter.record("b");
        clock.advance(Duration.ofMinutes(6));
        limiter.evictExpired();
        assertEquals(1, limiter.trackedKeys(), "only b still has an event in the window");
    }

    @Test
    void neverStoresMoreThanTheLimitPerKey() {
        for (int i = 0; i < 1000; i++) {
            limiter.record("a");
        }
        clock.advance(Duration.ofMinutes(10));
        assertTrue(limiter.blockedFor("a").isEmpty());
    }

    @Test
    void rejectsNonsenseConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowLimiter(0, Duration.ofMinutes(1), clock));
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowLimiter(1, Duration.ZERO, clock));
    }
}
