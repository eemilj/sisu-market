package fi.sisu.market.wager;

import com.mongodb.client.ClientSession;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import com.mongodb.client.model.Updates;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class WagerRepository implements PanacheMongoRepository<Wager> {

    /** Inserts within the caller's transaction. A separate method so tests can inject a failure here. */
    public void insert(ClientSession session, Wager wager) {
        mongoCollection().insertOne(session, wager);
    }

    public List<Wager> openWagersFor(ClientSession session, ObjectId predictionId) {
        return mongoCollection()
                .find(session, Filters.and(Filters.eq("predictionId", predictionId), Filters.eq("status", WagerStatus.OPEN.name())))
                .into(new ArrayList<>());
    }

    /**
     * Marks an OPEN wager as settled within the caller's transaction.
     *
     * @return false if it was not OPEN any more (already settled)
     */
    public boolean settle(ClientSession session, ObjectId wagerId, WagerStatus status, long payout, Instant at) {
        return mongoCollection().updateOne(session,
                Filters.and(Filters.eq("_id", wagerId), Filters.eq("status", WagerStatus.OPEN.name())),
                Updates.combine(Updates.set("status", status.name()), Updates.set("payout", payout), Updates.set("settledAt", at)))
                .getModifiedCount() == 1;
    }

    public boolean hasActiveWager(ObjectId userId, ObjectId predictionId) {
        return mongoCollection().countDocuments(Filters.and(
                Filters.eq("userId", userId),
                Filters.eq("predictionId", predictionId),
                Filters.ne("status", WagerStatus.REFUNDED.name()))) > 0;
    }

    /** A user's wagers, newest first. {@code before} is an exclusive id cursor. */
    public List<Wager> page(ObjectId userId, ObjectId before, int limit) {
        var filter = before == null
                ? Filters.eq("userId", userId)
                : Filters.and(Filters.eq("userId", userId), Filters.lt("_id", before));
        return mongoCollection().find(filter)
                .sort(com.mongodb.client.model.Sorts.descending("_id"))
                .limit(limit)
                .into(new ArrayList<>());
    }

    public Optional<Wager> findByRequestId(ObjectId userId, String requestId) {
        return Optional.ofNullable(mongoCollection()
                .find(Filters.and(Filters.eq("userId", userId), Filters.eq("requestId", requestId)))
                .first());
    }

    public void ensureIndexes() {
        mongoCollection().createIndex(Indexes.ascending("predictionId"));
        mongoCollection().createIndex(Indexes.compoundIndex(Indexes.ascending("userId"), Indexes.descending("_id")));
        // Makes a retried request (same key) unable to create a second wager, even under a race.
        mongoCollection().createIndex(Indexes.ascending("userId", "requestId"), new IndexOptions()
                .unique(true)
                .partialFilterExpression(Filters.type("requestId", "string")));
    }
}
