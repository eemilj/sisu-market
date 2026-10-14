package fi.sisu.market.auth;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import fi.sisu.market.common.ApiError;
import fi.sisu.market.common.ApiException;
import fi.sisu.market.common.ErrorCode;
import fi.sisu.market.security.PasswordHasher;
import fi.sisu.market.security.PasswordPolicy;
import fi.sisu.market.security.RequestThrottle;
import fi.sisu.market.user.AdminPolicy;
import fi.sisu.market.user.User;
import fi.sisu.market.user.UserDto;
import fi.sisu.market.user.UserRepository;
import fi.sisu.market.wallet.LedgerEntry;
import fi.sisu.market.wallet.TransactionType;
import fi.sisu.market.wallet.WalletService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid username or password";

    @Inject
    UserRepository users;

    @Inject
    TokenService tokens;

    @Inject
    WalletService wallet;

    @Inject
    AdminPolicy adminPolicy;

    @Inject
    PasswordHasher hasher;

    @Inject
    RequestThrottle throttle;

    @ConfigProperty(name = "sisu.starting-balance")
    long startingBalance;

    public AuthResponse register(RegisterRequest request, String clientIp) {
        List<ApiError.FieldError> passwordProblems = PasswordPolicy.check(request.password(), request.username());
        if (!passwordProblems.isEmpty()) {
            throw ApiException.validation(passwordProblems);
        }
        // Counted only for otherwise valid attempts, so typos in the form don't use up the allowance.
        throttle.checkAndRecordRegistration(clientIp);
        if (users.usernameTaken(request.username())) {
            throw ApiException.conflict(ErrorCode.USERNAME_TAKEN, "Username is already taken");
        }
        if (users.emailTaken(request.email())) {
            throw ApiException.conflict(ErrorCode.EMAIL_TAKEN, "Email is already registered");
        }

        User user = new User();
        user.id = new ObjectId();
        user.username = request.username();
        user.usernameKey = UserRepository.normalize(request.username());
        user.email = UserRepository.normalize(request.email());
        user.passwordHash = hasher.hash(request.password());
        user.balance = 0;
        user.createdAt = Instant.now();

        try {
            // Account and welcome bonus are created together or not at all.
            LedgerEntry bonus = wallet.inTransaction(session -> {
                users.mongoCollection().insertOne(session, user);
                return startingBalance == 0 ? null
                        : wallet.apply(session, user.id, startingBalance, TransactionType.SIGNUP_BONUS, "Welcome bonus", null);
            });
            if (bonus != null) {
                // Report exactly what was stored, including the version clients use to order balance updates.
                user.balance = bonus.balanceAfter;
                user.balanceVersion = bonus.balanceVersion;
            }
        } catch (MongoException e) {
            // Lost a race with a concurrent registration; the unique index is the real guard.
            if (ErrorCategory.fromErrorCode(e.getCode()) == ErrorCategory.DUPLICATE_KEY) {
                throw ApiException.conflict(ErrorCode.ACCOUNT_EXISTS, "Username or email is already registered");
            }
            throw e;
        }
        return new AuthResponse(tokens.issue(user), UserDto.from(user, adminPolicy.isAdmin(user)));
    }

    /**
     * Rate-limited before the (deliberately slow) password check, so a blocked client costs almost nothing.
     * Unknown usernames and wrong passwords look and take the same.
     */
    public AuthResponse login(LoginRequest request, String clientIp) {
        throttle.checkLogin(request.username(), clientIp);

        Optional<User> found = users.findByUsername(request.username());
        boolean matches = hasher.matches(request.password(), found.map(u -> u.passwordHash).orElse(null));
        if (found.isEmpty() || !matches) {
            throttle.loginFailed(request.username(), clientIp);
            throw ApiException.unauthorized(ErrorCode.INVALID_CREDENTIALS, INVALID_CREDENTIALS);
        }
        throttle.loginSucceeded(request.username(), clientIp);

        User user = found.get();
        if (hasher.needsRehash(user.passwordHash)) {
            // Upgrade old hashes (e.g. after raising sisu.bcrypt-cost) while we know the plain password.
            user.passwordHash = hasher.hash(request.password());
            users.mongoCollection().updateOne(Filters.eq("_id", user.id), Updates.set("passwordHash", user.passwordHash));
        }
        return new AuthResponse(tokens.issue(user), UserDto.from(user, adminPolicy.isAdmin(user)));
    }
}
