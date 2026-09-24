package market.sisu.transaction;

import io.quarkus.mongodb.panache.common.MongoEntity;
import java.time.Instant;
import org.bson.types.ObjectId;

/**
 * One append-only ledger entry ({@code transactions} collection). The sum of a user's entries
 * always equals their {@code balanceSisu} (SC-006). Entries are never updated or deleted.
 */
@MongoEntity(collection = "transactions")
public class Transaction {

    public ObjectId id;

    public ObjectId userId;

    public TransactionDirection direction;

    /** Always positive; {@link #direction} gives the sign. */
    public long amountSisu;

    public TransactionReason reason;

    public ObjectId relatedWagerId;

    public ObjectId relatedPredictionId;

    public Instant createdAt;

    /** This entry's effect on the balance: positive for a credit, negative for a debit. */
    public long signedAmount() {
        return direction == TransactionDirection.CREDIT ? amountSisu : -amountSisu;
    }
}
