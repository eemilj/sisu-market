package market.sisu.user;

import io.quarkus.mongodb.panache.common.MongoEntity;
import java.time.Instant;
import org.bson.types.ObjectId;

/**
 * A registered account ({@code users} collection, data-model.md).
 *
 * <p>{@code balanceSisu} is a denormalized cache of the sum of this user's ledger entries and is
 * only ever changed through {@code BalanceLedger}, never written directly.
 */
@MongoEntity(collection = "users")
public class User {

    public ObjectId id;

    /** Unique, case-insensitively. Set to {@code null} when the account is deleted (FR-033). */
    public String username;

    /** Bcrypt hash; never returned by any API response. Scrubbed on account deletion. */
    public String passwordHash;

    public Role role = Role.USER;

    public long balanceSisu;

    public Instant createdAt;

    public Instant deletedAt;
}
