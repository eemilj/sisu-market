package market.sisu.user;

import com.mongodb.MongoWriteException;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Instant;
import market.sisu.common.ApiException;
import market.sisu.common.MongoTransactions;
import market.sisu.common.Sisu;
import market.sisu.transaction.BalanceLedger;
import market.sisu.transaction.BalanceLedger.Related;
import market.sisu.transaction.TransactionReason;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Account lifecycle for User Story 1 (FR-001, FR-007, FR-009, FR-010): registration with a unique
 * username, a securely hashed password, and an atomically granted starting sisu balance.
 */
@ApplicationScoped
public class UserService {

    /** Mirrors contracts/openapi.yaml {@code RegisterRequest.password} {@code minLength}. */
    public static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository users;
    private final MongoTransactions tx;
    private final BalanceLedger ledger;
    private final Clock clock;
    private final long startingBalance;

    public UserService(
            UserRepository users,
            MongoTransactions tx,
            BalanceLedger ledger,
            Clock clock,
            @ConfigProperty(name = "sisu.starting-balance", defaultValue = "1000") long startingBalance) {
        this.users = users;
        this.tx = tx;
        this.ledger = ledger;
        this.clock = clock;
        this.startingBalance = startingBalance;
    }

    /**
     * Creates the account and grants the starting balance in one MongoDB transaction, so a new user
     * never exists without their grant ledger entry (FR-007, FR-010) and the ledger sum always
     * explains the balance (SC-006).
     *
     * @throws ApiException {@code 422 VALIDATION_FAILED} for an empty username or short password,
     *     {@code 409 USERNAME_TAKEN} when the username is already in use
     */
    public User register(String username, String password) {
        String name = username == null ? "" : username.strip();
        if (name.isEmpty()) {
            throw ApiException.unprocessable("VALIDATION_FAILED", "Username must not be empty");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw ApiException.unprocessable(
                    "VALIDATION_FAILED", "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (users.findByUsername(name).isPresent()) {
            throw ApiException.conflict("USERNAME_TAKEN", "Username is already taken");
        }
        String hash = BcryptUtil.bcryptHash(password);
        Instant now = clock.instant();
        try {
            return tx.inTransaction(session -> {
                User user = new User();
                user.id = new ObjectId();
                user.username = name;
                user.passwordHash = hash;
                user.role = Role.USER;
                user.balanceSisu = 0;
                user.createdAt = now;
                users.insert(session, user);
                Sisu granted = ledger.credit(
                        session, user.id, Sisu.of(startingBalance), TransactionReason.STARTING_GRANT, Related.NONE);
                user.balanceSisu = granted.amount();
                return user;
            });
        } catch (MongoWriteException duplicate) {
            // A concurrent registration won the race on the unique username index.
            if (duplicate.getError() != null && duplicate.getError().getCode() == 11000) {
                throw ApiException.conflict("USERNAME_TAKEN", "Username is already taken");
            }
            throw duplicate;
        }
    }
}
