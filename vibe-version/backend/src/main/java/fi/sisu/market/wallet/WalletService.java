package fi.sisu.market.wallet;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import fi.sisu.market.common.ApiException;
import fi.sisu.market.user.User;
import fi.sisu.market.user.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.function.Function;

/**
 * The only code allowed to change {@link User#balance}. Every change updates the balance and appends a
 * {@link LedgerEntry} in the same MongoDB transaction, so the two can never disagree.
 */
@ApplicationScoped
public class WalletService {

    @Inject
    MongoClient mongoClient;

    @Inject
    UserRepository users;

    @Inject
    LedgerRepository ledger;

    /**
     * Runs {@code work} in a MongoDB transaction (requires a replica set). The driver retries on transient
     * errors, so {@code work} may run more than once and must only touch the database through the session.
     */
    public <T> T inTransaction(Function<ClientSession, T> work) {
        try (ClientSession session = mongoClient.startSession()) {
            return session.withTransaction(() -> work.apply(session));
        }
    }

    /** Applies one balance change in its own transaction. */
    public LedgerEntry apply(ObjectId userId, long amount, TransactionType type, String description, ObjectId predictionId) {
        return inTransaction(session -> apply(session, userId, amount, type, description, predictionId));
    }

    /**
     * Applies one balance change within the caller's transaction.
     *
     * @throws InsufficientBalanceException if a debit would take the balance below zero
     */
    public LedgerEntry apply(ClientSession session, ObjectId userId, long amount, TransactionType type,
                             String description, ObjectId predictionId) {
        if (amount == 0) {
            throw new IllegalArgumentException("Balance change must be non-zero");
        }
        // The balance guard lives in the filter so check-and-update is a single atomic operation.
        Bson filter = amount > 0
                ? Filters.eq("_id", userId)
                : Filters.and(Filters.eq("_id", userId), Filters.gte("balance", -amount));
        User updated = users.mongoCollection().findOneAndUpdate(session, filter, Updates.inc("balance", amount),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
        if (updated == null) {
            if (users.mongoCollection().countDocuments(session, Filters.eq("_id", userId)) == 0) {
                throw ApiException.notFound("User not found");
            }
            throw new InsufficientBalanceException();
        }

        LedgerEntry entry = new LedgerEntry();
        entry.id = new ObjectId();
        entry.userId = userId;
        entry.type = type;
        entry.amount = amount;
        entry.balanceAfter = updated.balance;
        entry.description = description;
        entry.predictionId = predictionId;
        entry.createdAt = Instant.now();
        ledger.mongoCollection().insertOne(session, entry);
        return entry;
    }
}
