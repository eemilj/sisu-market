package fi.sisu.market.resolution;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import fi.sisu.market.common.ApiException;
import fi.sisu.market.common.ErrorCode;
import fi.sisu.market.live.EventHub;
import fi.sisu.market.live.LiveEvent;
import fi.sisu.market.prediction.Prediction;
import fi.sisu.market.prediction.PredictionDto;
import fi.sisu.market.prediction.PredictionRepository;
import fi.sisu.market.prediction.PredictionStatus;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;

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

    /**
     * Closes one OPEN prediction right now, ahead of its scheduled time (admin action). Closing time is moved
     * to now, because wagering and resolution both key off {@code closesAt}: wagers are refused from this
     * instant and the prediction can be resolved straight away.
     *
     * @throws ApiException 404 unknown id; 409 if it is already closed or resolved
     */
    public PredictionDto closeNow(ObjectId predictionId) {
        Instant now = Instant.now();
        Prediction updated = predictions.mongoCollection().findOneAndUpdate(
                Filters.and(
                        Filters.eq("_id", predictionId),
                        Filters.eq("status", PredictionStatus.OPEN.name()),
                        Filters.gt("closesAt", now)),
                Updates.combine(
                        Updates.set("status", PredictionStatus.CLOSED.name()),
                        Updates.set("closesAt", now),
                        Updates.inc("version", 1)),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
        if (updated == null) {
            Prediction p = predictions.mongoCollection().find(Filters.eq("_id", predictionId)).first();
            if (p == null) {
                throw ApiException.notFound(ErrorCode.PREDICTION_NOT_FOUND, "Prediction not found");
            }
            throw p.effectiveStatus(now) == PredictionStatus.RESOLVED
                    ? ApiException.conflict(ErrorCode.ALREADY_RESOLVED, "This prediction has already been resolved")
                    : ApiException.conflict(ErrorCode.PREDICTION_CLOSED, "This prediction is already closed");
        }
        PredictionDto dto = PredictionDto.from(updated, now);
        events.publish(LiveEvent.toEveryone(LiveEvent.PREDICTION_UPDATED, dto));
        return dto;
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
