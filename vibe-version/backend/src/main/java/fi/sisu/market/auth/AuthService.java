package fi.sisu.market.auth;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoException;
import fi.sisu.market.common.ApiException;
import fi.sisu.market.user.AdminPolicy;
import fi.sisu.market.user.User;
import fi.sisu.market.user.UserDto;
import fi.sisu.market.user.UserRepository;
import fi.sisu.market.wallet.TransactionType;
import fi.sisu.market.wallet.WalletService;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid username or password";
    /** Compared against when the user doesn't exist, so a miss costs as much time as a wrong password. */
    private static final String DUMMY_HASH = BcryptUtil.bcryptHash("timing-equalizer");

    @Inject
    UserRepository users;

    @Inject
    TokenService tokens;

    @Inject
    WalletService wallet;

    @Inject
    AdminPolicy adminPolicy;

    @ConfigProperty(name = "sisu.starting-balance")
    long startingBalance;

    public AuthResponse register(RegisterRequest request) {
        if (users.usernameTaken(request.username())) {
            throw ApiException.conflict("Username is already taken");
        }
        if (users.emailTaken(request.email())) {
            throw ApiException.conflict("Email is already registered");
        }

        User user = new User();
        user.id = new ObjectId();
        user.username = request.username().trim();
        user.usernameKey = UserRepository.normalize(request.username());
        user.email = UserRepository.normalize(request.email());
        user.passwordHash = BcryptUtil.bcryptHash(request.password());
        user.balance = 0;
        user.createdAt = Instant.now();

        try {
            // Account and welcome bonus are created together or not at all.
            user.balance = wallet.inTransaction(session -> {
                users.mongoCollection().insertOne(session, user);
                if (startingBalance == 0) {
                    return 0L;
                }
                return wallet.apply(session, user.id, startingBalance, TransactionType.SIGNUP_BONUS, "Welcome bonus", null)
                        .balanceAfter;
            });
        } catch (MongoException e) {
            // Lost a race with a concurrent registration; the unique index is the real guard.
            if (ErrorCategory.fromErrorCode(e.getCode()) == ErrorCategory.DUPLICATE_KEY) {
                throw ApiException.conflict("Username or email is already registered");
            }
            throw e;
        }
        return new AuthResponse(tokens.issue(user), UserDto.from(user, adminPolicy.isAdmin(user)));
    }

    public AuthResponse login(LoginRequest request) {
        Optional<User> found = users.findByUsername(request.username());
        String hash = found.map(u -> u.passwordHash).orElse(DUMMY_HASH);
        boolean matches = BcryptUtil.matches(request.password(), hash);
        if (found.isEmpty() || !matches) {
            throw ApiException.unauthorized(INVALID_CREDENTIALS);
        }
        User user = found.get();
        return new AuthResponse(tokens.issue(user), UserDto.from(user, adminPolicy.isAdmin(user)));
    }
}
