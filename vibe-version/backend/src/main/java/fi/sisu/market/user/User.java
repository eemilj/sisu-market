package fi.sisu.market.user;

import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.types.ObjectId;

import java.time.Instant;

@MongoEntity(collection = "users")
public class User {

    public ObjectId id;
    /** Display form, as the user typed it. */
    public String username;
    /** Lower-cased username; carries the unique index so "Matti" and "matti" can't both exist. */
    public String usernameKey;
    /** Stored lower-cased; also uniquely indexed. */
    public String email;
    public String passwordHash;
    /** Virtual Sisu balance, whole units. Change it only through WalletService so the ledger stays in sync. */
    public long balance;
    /** Incremented with every balance change, so clients can order balance updates. */
    public long balanceVersion;
    public Instant createdAt;
}
