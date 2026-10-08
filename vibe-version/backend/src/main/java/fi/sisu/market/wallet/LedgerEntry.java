package fi.sisu.market.wallet;

import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.types.ObjectId;

import java.time.Instant;

/**
 * One immutable balance change. Entries are only ever inserted, never updated or deleted,
 * so for every user {@code user.balance == sum(amount)} over their entries.
 */
@MongoEntity(collection = "ledger")
public class LedgerEntry {

    public ObjectId id;
    public ObjectId userId;
    public TransactionType type;
    /** Signed change: positive credits, negative debits. Never zero. */
    public long amount;
    /** User's balance immediately after this change. */
    public long balanceAfter;
    public String description;
    /** Related prediction, when the change came from one. */
    public ObjectId predictionId;
    public Instant createdAt;
}
