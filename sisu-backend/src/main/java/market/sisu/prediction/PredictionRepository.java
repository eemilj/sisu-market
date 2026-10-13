package market.sisu.prediction;

import com.mongodb.client.ClientSession;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import market.sisu.common.Sisu;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

@ApplicationScoped
public class PredictionRepository implements PanacheMongoRepository<Prediction> {

    void createIndexes(@Observes StartupEvent event) {
        mongoCollection().createIndex(Indexes.ascending("status"), new IndexOptions().name("status"));
        // Lets the closing job find due OPEN predictions without a collection scan.
        mongoCollection()
                .createIndex(Indexes.ascending("status", "closingAt"), new IndexOptions().name("status_closingAt"));
    }

    /**
     * Predictions for the public list (FR-016, FR-018), filtered by their <em>effective</em> status:
     * an {@code OPEN} prediction whose {@code closingAt} has passed counts as {@code CLOSED} even
     * before the closing job updates it (FR-025). Open predictions come soonest-closing first;
     * every other listing comes most-recently-closing first.
     *
     * @param status the status to filter by, or {@code null} for every prediction
     */
    public List<Prediction> findForListing(PredictionStatus status, Instant now) {
        Bson filter;
        if (status == null) {
            filter = new Document();
        } else if (status == PredictionStatus.OPEN) {
            filter = Filters.and(Filters.eq("status", PredictionStatus.OPEN.name()), Filters.gt("closingAt", now));
        } else if (status == PredictionStatus.CLOSED) {
            filter = Filters.or(
                    Filters.eq("status", PredictionStatus.CLOSED.name()),
                    Filters.and(Filters.eq("status", PredictionStatus.OPEN.name()), Filters.lte("closingAt", now)));
        } else {
            filter = Filters.eq("status", status.name());
        }
        Bson order = status == PredictionStatus.OPEN ? Sorts.ascending("closingAt") : Sorts.descending("closingAt");
        return mongoCollection().find(filter).sort(order).into(new ArrayList<>());
    }

    /** The predictions with these ids, in no particular order; unknown ids are skipped. */
    public List<Prediction> findByIds(Collection<ObjectId> ids) {
        return mongoCollection().find(Filters.in("_id", ids)).into(new ArrayList<>());
    }

    public void insert(ClientSession session, Prediction prediction) {
        mongoCollection().insertOne(session, prediction);
    }

    public Optional<Prediction> findById(ClientSession session, ObjectId id) {
        return Optional.ofNullable(
                mongoCollection().find(session, Filters.eq("_id", id)).first());
    }

    /**
     * Adds a wager's stake to one side's pool, but only if the prediction still accepts wagers:
     * status {@code OPEN} (FR-021) <em>and</em> {@code closingAt} strictly after {@code now}
     * (FR-025, checked against the clock rather than trusting the cached status). Must run in the
     * same transaction as the balance debit and wager insert.
     *
     * @return {@code false} if the prediction does not exist or no longer accepts wagers
     */
    public boolean addToPool(ClientSession session, ObjectId predictionId, Outcome outcome, Sisu amount, Instant now) {
        String pool = outcome == Outcome.YES ? "yesPoolSisu" : "noPoolSisu";
        return mongoCollection()
                        .updateOne(
                                session,
                                Filters.and(
                                        Filters.eq("_id", predictionId),
                                        Filters.eq("status", PredictionStatus.OPEN.name()),
                                        Filters.gt("closingAt", now)),
                                Updates.inc(pool, amount.amount()))
                        .getModifiedCount()
                == 1;
    }

    /**
     * Atomically moves a prediction from one of {@code from} to {@code to}, applying
     * {@code extraUpdates} in the same write. Because the current status is part of the filter, of
     * two concurrent attempts (e.g. two resolutions) exactly one succeeds (FR-027).
     *
     * @return {@code false} if the prediction was not in one of the {@code from} states
     */
    public boolean compareAndSetStatus(
            ClientSession session,
            ObjectId predictionId,
            Set<PredictionStatus> from,
            PredictionStatus to,
            Bson... extraUpdates) {
        List<Bson> updates = new ArrayList<>(List.of(extraUpdates));
        updates.add(Updates.set("status", to.name()));
        Bson filter = Filters.and(
                Filters.eq("_id", predictionId),
                Filters.in("status", from.stream().map(Enum::name).toList()));
        Bson update = Updates.combine(updates);
        long modified = session == null
                ? mongoCollection().updateOne(filter, update).getModifiedCount()
                : mongoCollection().updateOne(session, filter, update).getModifiedCount();
        return modified == 1;
    }
}
