package fi.sisu.market.security;

import fi.sisu.market.common.ApiException;
import fi.sisu.market.common.ErrorCode;
import fi.sisu.market.user.UserRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Brute-force protection for login and registration.
 *
 * <p>Failed logins are limited three ways: per account <em>and</em> client IP (tight; stops guessing one
 * account from one place without letting an attacker lock the owner out everywhere), per client IP (stops one
 * client spraying many accounts), and per account across all IPs (looser; slows distributed guessing).
 * A successful login clears only the account+IP counter. Registrations are limited per IP, since each new
 * account comes with free Sisu.
 */
@ApplicationScoped
public class RequestThrottle {

    private final SlidingWindowLimiter loginPerAccountAndIp;
    private final SlidingWindowLimiter loginPerIp;
    private final SlidingWindowLimiter loginPerAccount;
    private final SlidingWindowLimiter registrationsPerIp;

    @Inject
    public RequestThrottle(
            @ConfigProperty(name = "sisu.rate-limit.login.per-account-ip", defaultValue = "5") int perAccountIp,
            @ConfigProperty(name = "sisu.rate-limit.login.per-ip", defaultValue = "20") int perIp,
            @ConfigProperty(name = "sisu.rate-limit.login.per-account", defaultValue = "50") int perAccount,
            @ConfigProperty(name = "sisu.rate-limit.login.window", defaultValue = "15M") Duration loginWindow,
            @ConfigProperty(name = "sisu.rate-limit.register.per-ip", defaultValue = "10") int registrations,
            @ConfigProperty(name = "sisu.rate-limit.register.window", defaultValue = "1H") Duration registerWindow) {
        this(perAccountIp, perIp, perAccount, loginWindow, registrations, registerWindow, Clock.systemUTC());
    }

    RequestThrottle(int perAccountIp, int perIp, int perAccount, Duration loginWindow,
                    int registrations, Duration registerWindow, Clock clock) {
        this.loginPerAccountAndIp = new SlidingWindowLimiter(perAccountIp, loginWindow, clock);
        this.loginPerIp = new SlidingWindowLimiter(perIp, loginWindow, clock);
        // The per-account window is 4x longer: a slow, distributed attack still hits it.
        this.loginPerAccount = new SlidingWindowLimiter(perAccount, loginWindow.multipliedBy(4), clock);
        this.registrationsPerIp = new SlidingWindowLimiter(registrations, registerWindow, clock);
    }

    /** Call before checking the password; throws 429 if this login attempt is not allowed right now. */
    public void checkLogin(String username, String ip) {
        String account = UserRepository.normalize(username);
        longest(loginPerAccountAndIp.blockedFor(account + "|" + ip), loginPerIp.blockedFor(ip),
                loginPerAccount.blockedFor(account))
                .ifPresent(wait -> {
                    throw ApiException.tooManyRequests(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS,
                            "Too many failed login attempts; try again later", seconds(wait));
                });
    }

    public void loginFailed(String username, String ip) {
        String account = UserRepository.normalize(username);
        loginPerAccountAndIp.record(account + "|" + ip);
        loginPerIp.record(ip);
        loginPerAccount.record(account);
    }

    public void loginSucceeded(String username, String ip) {
        loginPerAccountAndIp.reset(UserRepository.normalize(username) + "|" + ip);
    }

    /** Counts this registration attempt; throws 429 if the IP has registered too many accounts recently. */
    public void checkAndRecordRegistration(String ip) {
        registrationsPerIp.blockedFor(ip).ifPresent(wait -> {
            throw ApiException.tooManyRequests(ErrorCode.TOO_MANY_REGISTRATIONS,
                    "Too many accounts created from this address; try again later", seconds(wait));
        });
        registrationsPerIp.record(ip);
    }

    @Scheduled(every = "10m")
    void evictExpired() {
        Stream.of(loginPerAccountAndIp, loginPerIp, loginPerAccount, registrationsPerIp)
                .forEach(SlidingWindowLimiter::evictExpired);
    }

    @SafeVarargs
    private static Optional<Duration> longest(Optional<Duration>... waits) {
        return Stream.of(waits).flatMap(Optional::stream).max(Duration::compareTo);
    }

    /** Rounded up, at least 1, so clients never retry too early. */
    private static long seconds(Duration wait) {
        return Math.max(1, (wait.toMillis() + 999) / 1000);
    }
}
