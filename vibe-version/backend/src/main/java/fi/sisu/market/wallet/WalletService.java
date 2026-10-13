package fi.sisu.market.wallet;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import fi.sisu.market.common.ApiException;
import fi.sisu.market.common.ErrorCode;
import fi.sisu.market.live.EventHub;
import fi.sisu.market.live.LiveEvent;
import fi.sisu.market.prediction.Prediction;
import fi.sisu.market.prediction.PredictionRepository;
import fi.sisu.market.user.User;
import fi.sisu.market.user.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The only code allowed to change {@link User#balance}. Every change updates the balance and appends a
 * {@link LedgerEntry} in the same MongoDB transaction, so the two can never disagree.
 */
@ApplicationScoped
public class WalletService {

    /**
     * Snapshot reads give each attempt a consistent view; majority writes mean a committed transaction
     * survives a primary failover. Concurrent transactions touching the same document hit a write
     * conflict, which the driver retries from the start.
     */
    private static final TransactionOptions TX_OPTIONS = TransactionOptions.builder()
            .readConcern(ReadConcern.SNAPSHOT)
            .writeConcern(WriteConcern.MAJORITY)
            .readPreference(ReadPreference.primary())
            .build();

    @Inject
    MongoClient mongoClient;

    @Inject
    UserRepository users;

    @Inject
    LedgerRepository ledger;

    @Inject
    EventHub events;

    @Inject
    PredictionRepository predictions;

    /** Ledger entries written by each open transaction, announced only once it commits. */
    private final Map<ClientSession, List<LedgerEntry>> uncommitted = Collections.synchronizedMap(new IdentityHashMap<>());

    /**
     * Runs {@code work} in a MongoDB transaction (requires a replica set). The driver retries on transient
     * errors, so {@code work} may run more than once and must only touch the database through the session.
     * Balance changes made inside are pushed to their users after the commit, never for a rolled-back attempt.
     */
    public <T> T inTransaction(Function<ClientSession, T> work) {
        try (ClientSession session = mongoClient.startSession()) {
            List<LedgerEntry> written = new ArrayList<>();
            uncommitted.put(session, written);
            try {
                T result = session.withTransaction(() -> {
                    written.clear(); // a retried attempt starts over
                    return work.apply(session);
                }, TX_OPTIONS);
                written.forEach(this::announce);
                return result;
            } finally {
                uncommitted.remove(session);
            }
        }
    }

    private void announce(LedgerEntry entry) {
        events.publish(LiveEvent.toUser(entry.userId, LiveEvent.WALLET_TRANSACTION,
                new WalletEvent(LedgerEntryDto.from(entry, questionOf(entry)), entry.balanceAfter, entry.balanceVersion)));
    }

    private String questionOf(LedgerEntry entry) {
        if (entry.predictionId == null) {
            return null;
        }
        Prediction p = predictions.findById(entry.predictionId);
        return p == null ? null : p.question;
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
        User updated = users.mongoCollection().findOneAndUpdate(session, filter, Updates.combine(Updates.inc("balance", amount), Updates.inc("balanceVersion", 1)),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
        if (updated == null) {
            User current = users.mongoCollection().find(session, Filters.eq("_id", userId)).first();
            if (current == null) {
                throw ApiException.notFound(ErrorCode.USER_NOT_FOUND, "User not found");
            }
            throw new InsufficientBalanceException(current.balance, -amount);
        }

        LedgerEntry entry = new LedgerEntry();
        entry.id = new ObjectId();
        entry.userId = userId;
        entry.type = type;
        entry.amount = amount;
        entry.balanceAfter = updated.balance;
        entry.balanceVersion = updated.balanceVersion;
        entry.description = description;
        entry.predictionId = predictionId;
        entry.createdAt = Instant.now();
        ledger.mongoCollection().insertOne(session, entry);
        List<LedgerEntry> written = uncommitted.get(session);
        if (written != null) {
            written.add(entry);
        }
        return entry;
    }
}
