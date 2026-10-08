package fi.sisu.market.prediction;

import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.types.ObjectId;

import java.time.Instant;

@MongoEntity(collection = "predictions")
public class Prediction {

    public ObjectId id;
    public String question;
    public String description;
    /** How the outcome will be decided: source, cut-off and edge cases. */
    public String resolutionCriteria;
    public Category category;
    public Instant closesAt;
    /** Stored status. OPEN predictions past {@code closesAt} are reported as CLOSED; see {@link #effectiveStatus}. */
    public PredictionStatus status;
    /** Set only when RESOLVED. */
    public Outcome outcome;
    /** Sisu staked on YES and NO. The ratio drives the displayed probability. */
    public long yesPool;
    public long noPool;
    /** Creator; null for seeded demo predictions. */
    public ObjectId createdById;
    /** Creator's username at creation time, denormalised for listing. */
    public String createdBy;
    public Instant createdAt;

    public PredictionStatus effectiveStatus(Instant now) {
        if (status == PredictionStatus.OPEN && !now.isBefore(closesAt)) {
            return PredictionStatus.CLOSED;
        }
        return status;
    }
}
