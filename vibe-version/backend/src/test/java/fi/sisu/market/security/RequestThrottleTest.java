package fi.sisu.market.security;

import fi.sisu.market.common.ApiException;
import fi.sisu.market.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Login and registration limits, with the production defaults. */
class RequestThrottleTest {

    private final MutableClock clock = new MutableClock();
    private final RequestThrottle throttle =
            new RequestThrottle(5, 20, 50, Duration.ofMinutes(15), 10, Duration.ofHours(1), clock);

    private static ApiException blocked(Executable call) {
        return assertThrows(ApiException.class, call);
    }

    private void fail(String user, String ip, int times) {
        for (int i = 0; i < times; i++) {
            throttle.checkLogin(user, ip);
            throttle.loginFailed(user, ip);
        }
    }

    @Test
    void fiveFailuresLockThatAccountFromThatAddress() {
        fail("matti", "1.1.1.1", 5);
        ApiException e = blocked(() -> throttle.checkLogin("matti", "1.1.1.1"));
        assertEquals(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS, e.code());
        assertEquals(429, e.status().getStatusCode());
        assertEquals(15 * 60L, e.params().get("retryAfterSeconds"));

        // The real owner elsewhere is unaffected, and so are other accounts from the same address.
        assertDoesNotThrow(() -> throttle.checkLogin("matti", "2.2.2.2"));
        assertDoesNotThrow(() -> throttle.checkLogin("aino", "1.1.1.1"));
    }

    @Test
    void usernameCaseAndSpacesDoNotDodgeTheLimit() {
        fail("Matti", "1.1.1.1", 3);
        fail(" MATTI ", "1.1.1.1", 2);
        blocked(() -> throttle.checkLogin("matti", "1.1.1.1"));
    }

    @Test
    void lockExpiresAfterTheWindow() {
        fail("matti", "1.1.1.1", 5);
        clock.advance(Duration.ofMinutes(14));
        assertEquals(60L, blocked(() -> throttle.checkLogin("matti", "1.1.1.1")).params().get("retryAfterSeconds"));
        clock.advance(Duration.ofMinutes(1));
        assertDoesNotThrow(() -> throttle.checkLogin("matti", "1.1.1.1"));
    }

    @Test
    void successResetsTheAccountAndAddressCounter() {
        fail("matti", "1.1.1.1", 4);
        throttle.loginSucceeded("matti", "1.1.1.1");
        fail("matti", "1.1.1.1", 4);
        assertDoesNotThrow(() -> throttle.checkLogin("matti", "1.1.1.1"));
    }

    @Test
    void oneAddressSprayingManyAccountsIsBlocked() {
        for (int i = 0; i < 20; i++) {
            fail("user" + i, "6.6.6.6", 1);
        }
        blocked(() -> throttle.checkLogin("someone-new", "6.6.6.6"));
        assertDoesNotThrow(() -> throttle.checkLogin("someone-new", "7.7.7.7"));
    }

    @Test
    void distributedGuessingOfOneAccountIsSlowedDown() {
        for (int i = 0; i < 50; i++) {
            fail("matti", "10.0.0." + i, 1);
        }
        ApiException e = blocked(() -> throttle.checkLogin("matti", "10.0.1.1"));
        // The per-account window is an hour (4 x 15 min).
        assertEquals(60 * 60L, e.params().get("retryAfterSeconds"));
    }

    @Test
    void retryAfterIsTheLongestApplicableWaitRoundedUp() {
        fail("matti", "1.1.1.1", 4);
        clock.advance(Duration.ofMillis(1500));
        fail("matti", "1.1.1.1", 1);
        ApiException e = blocked(() -> throttle.checkLogin("matti", "1.1.1.1"));
        // The oldest failure expires in 15 min - 1.5 s = 898.5 s -> 899 s.
        assertEquals(899L, e.params().get("retryAfterSeconds"));
    }

    @Test
    void registrationsArePerAddress() {
        for (int i = 0; i < 10; i++) {
            throttle.checkAndRecordRegistration("3.3.3.3");
        }
        ApiException e = blocked(() -> throttle.checkAndRecordRegistration("3.3.3.3"));
        assertEquals(ErrorCode.TOO_MANY_REGISTRATIONS, e.code());
        assertDoesNotThrow(() -> throttle.checkAndRecordRegistration("4.4.4.4"));
        clock.advance(Duration.ofHours(1));
        assertDoesNotThrow(() -> throttle.checkAndRecordRegistration("3.3.3.3"));
    }
}
