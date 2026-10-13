package fi.sisu.market.resolution;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import fi.sisu.market.live.EventHub;
import fi.sisu.market.live.LiveEvent;
import fi.sisu.market.prediction.Prediction;
import fi.sisu.market.prediction.PredictionDto;
import fi.sisu.market.prediction.PredictionRepository;
import fi.sisu.market.prediction.PredictionStatus;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;

/**
 * Flips OPEN predictions whose closing time has passed to CLOSED and tells live clients.
 *
 * <p>This is not what stops wagering: a wager's own transaction refuses any prediction whose {@code closesAt}
 * has passed, to the millisecond. This job makes the stored status and every open screen catch up.
 */
@ApplicationScoped
public class PredictionCloser {

    @Inject
    PredictionRepository predictions;

    @Inject
    EventHub events;

    @Scheduled(every = "${sisu.close-check-interval}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledRun() {
        closeExpired();
    }

    /** @return how many predictions this call closed */
    public int closeExpired() {
        Instant now = Instant.now();
        int closed = 0;
        var expired = predictions.mongoCollection()
                .find(Filters.and(Filters.eq("status", PredictionStatus.OPEN.name()), Filters.lte("closesAt", now)))
                .into(new ArrayList<>());
        for (Prediction p : expired) {
            // Conditional, so concurrent runs (or another instance) close each prediction once.
            Prediction updated = predictions.mongoCollection().findOneAndUpdate(
                    Filters.and(Filters.eq("_id", p.id), Filters.eq("status", PredictionStatus.OPEN.name())),
                    Updates.combine(Updates.set("status", PredictionStatus.CLOSED.name()), Updates.inc("version", 1)),
                    new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
            if (updated != null) {
                events.publish(LiveEvent.toEveryone(LiveEvent.PREDICTION_UPDATED, PredictionDto.from(updated, now)));
                closed++;
            }
        }
        return closed;
    }
}
