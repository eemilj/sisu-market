package market.sisu.prediction;

import io.quarkus.mongodb.panache.common.MongoEntity;
import java.time.Instant;
import org.bson.types.ObjectId;

/**
 * A yes/no question users wager on ({@code predictions} collection, data-model.md).
 *
 * <p>The two pool totals are updated in the same transaction as each wager so odds can be derived
 * from this single document without summing wagers (FR-023, FR-034).
 */
@MongoEntity(collection = "predictions")
public class Prediction {

    public ObjectId id;

    public ObjectId creatorId;

    public String question;

    public String description;

    public String resolutionCriteria;

    public Instant closingAt;

    public PredictionStatus status = PredictionStatus.OPEN;

    public long yesPoolSisu;

    public long noPoolSisu;

    public Outcome winningOutcome;

    public Instant resolvedAt;

    public ObjectId resolvedBy;

    public Instant createdAt;

    /**
     * The status users should see at {@code now}: an {@code OPEN} prediction stops being open the
     * instant its {@code closingAt} passes (FR-025), even if the closing job has not caught up yet.
     */
    public PredictionStatus statusAt(Instant now) {
        if (status == PredictionStatus.OPEN && !closingAt.isAfter(now)) {
            return PredictionStatus.CLOSED;
        }
        return status;
    }
}
