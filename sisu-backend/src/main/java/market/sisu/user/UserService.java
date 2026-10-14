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
        return createAccount(username, password, Role.USER);
    }

    /** What {@link #provisionResolver} did, so the caller can log it. */
    public enum Provisioning {
        CREATED,
        PROMOTED,
        UNCHANGED
    }

    /**
     * Ensures the designated resolver account exists (FR-039) and is idempotent (FR-040): a missing
     * account is created through the registration path (hashing, starting grant, ledger entry), an
     * existing {@link Role#USER} account only has its role promoted, and an existing resolver is left
     * untouched. Passwords and balances of existing accounts are never modified.
     *
     * @throws ApiException {@code 422 VALIDATION_FAILED} for an empty username or short password
     */
    public Provisioning provisionResolver(String username, String password) {
        String name = username == null ? "" : username.strip();
        if (name.isEmpty()) {
            throw ApiException.unprocessable("VALIDATION_FAILED", "Username must not be empty");
        }
        if (users.findByUsername(name).isEmpty()) {
            try {
                createAccount(name, password, Role.RESOLVER);
                return Provisioning.CREATED;
            } catch (ApiException e) {
                if (!"USERNAME_TAKEN".equals(e.code())) {
                    throw e;
                }
                // A concurrent start created it first; fall through to the role check.
            }
        }
        User existing = users.findByUsername(name).orElseThrow();
        if (existing.role == Role.RESOLVER) {
            return Provisioning.UNCHANGED;
        }
        users.setRole(existing.id, Role.RESOLVER);
        return Provisioning.PROMOTED;
    }

    private User createAccount(String username, String password, Role role) {
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
                user.role = role;
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
