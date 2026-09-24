package market.sisu.prediction;

import com.mongodb.client.ClientSession;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import market.sisu.common.Sisu;
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
        return mongoCollection()
                        .updateOne(
                                session,
                                Filters.and(
                                        Filters.eq("_id", predictionId),
                                        Filters.in(
                                                "status",
                                                from.stream().map(Enum::name).toList())),
                                Updates.combine(updates))
                        .getModifiedCount()
                == 1;
    }
}
