package fi.sisu.vibe.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginThrottleTest {

    private static class MovableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MovableClock clock = new MovableClock();
    private final LoginThrottle throttle = new LoginThrottle(clock, 3, Duration.ofMinutes(10));

    @Test
    void blocksUsernameAfterMaxFailuresCaseInsensitively() {
        for (int i = 0; i < 3; i++) {
            assertFalse(throttle.isBlocked("Matti", "1.1.1.1"));
            throttle.recordFailure("matti", "1.1.1." + i);
        }
        assertTrue(throttle.isBlocked("MATTI", "9.9.9.9"));
        assertFalse(throttle.isBlocked("liisa", "9.9.9.9"));
    }

    @Test
    void windowExpires() {
        for (int i = 0; i < 3; i++) {
            throttle.recordFailure("matti", "1.1.1.1");
        }
        clock.now = clock.now.plus(Duration.ofMinutes(11));
        assertFalse(throttle.isBlocked("matti", "1.1.1.1"));
    }

    @Test
    void successResetsUsernameCounter() {
        throttle.recordFailure("matti", "1.1.1.1");
        throttle.recordFailure("matti", "1.1.1.1");
        throttle.recordSuccess("matti");
        throttle.recordFailure("matti", "1.1.1.1");
        assertFalse(throttle.isBlocked("matti", "2.2.2.2"));
    }

    @Test
    void blocksIpSprayingManyUsernames() {
        for (int i = 0; i < 12; i++) {
            throttle.recordFailure("user" + i, "6.6.6.6");
        }
        assertTrue(throttle.isBlocked("someoneelse", "6.6.6.6"));
    }
}
