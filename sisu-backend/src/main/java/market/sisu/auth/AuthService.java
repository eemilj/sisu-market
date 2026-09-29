package market.sisu.auth;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import market.sisu.common.ApiException;
import market.sisu.user.User;
import market.sisu.user.UserRepository;

/**
 * Credential checking for User Story 1 (FR-002, FR-003, FR-037). A failed attempt is always
 * answered with the same generic 401 — the response never reveals whether the username or the
 * password was wrong — and counts against the username/IP rate limit.
 */
@ApplicationScoped
public class AuthService {

    private final UserRepository users;
    private final LoginRateLimiter rateLimiter;
    private final JwtService jwtService;

    public AuthService(UserRepository users, LoginRateLimiter rateLimiter, JwtService jwtService) {
        this.users = users;
        this.rateLimiter = rateLimiter;
        this.jwtService = jwtService;
    }

    /** A successful login: the account together with a freshly issued session token. */
    public record LoginResult(User user, String jwt) {}

    /**
     * @throws ApiException {@code 429 TOO_MANY_ATTEMPTS} while the pair is in cooldown, {@code 401
     *     INVALID_CREDENTIALS} for any wrong or unknown credential
     */
    public LoginResult login(String username, String password, String ip) {
        String name = username == null ? "" : username.strip();
        rateLimiter.checkBlocked(name, ip);
        Optional<User> found = users.findByUsername(name).filter(user -> user.deletedAt == null);
        boolean matches = found.map(user -> BcryptUtil.matches(password == null ? "" : password, user.passwordHash))
                .orElse(false);
        if (!matches) {
            rateLimiter.recordFailure(name, ip);
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid username or password");
        }
        User user = found.orElseThrow();
        rateLimiter.clear(name, ip);
        return new LoginResult(user, jwtService.issue(user.id, user.username, user.role));
    }
}
