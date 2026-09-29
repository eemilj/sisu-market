package market.sisu.prediction;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import market.sisu.common.ApiException;
import market.sisu.user.User;
import market.sisu.user.UserRepository;
import org.bson.types.ObjectId;

/**
 * Read side of predictions for User Story 2 (FR-016 through FR-018): the public list with a status
 * filter and the detail view. Odds come from the pool totals stored on each prediction, so every
 * read is a single-document fetch (plan.md, Constitution IV).
 */
@ApplicationScoped
public class PredictionQueryService {

    private final PredictionRepository predictions;
    private final UserRepository users;
    private final Clock clock;

    public PredictionQueryService(PredictionRepository predictions, UserRepository users, Clock clock) {
        this.predictions = predictions;
        this.users = users;
        this.clock = clock;
    }

    /** @param status only predictions currently in this status, or {@code null} for all */
    public List<PredictionSummary> list(PredictionStatus status) {
        Instant now = clock.instant();
        return predictions.findForListing(status, now).stream()
                .map(prediction -> PredictionSummary.of(prediction, now))
                .toList();
    }

    /** @throws ApiException {@code 404 PREDICTION_NOT_FOUND} for an unknown or malformed id */
    public PredictionDetail detail(String id) {
        Prediction prediction = ObjectId.isValid(id) ? predictions.findById(new ObjectId(id)) : null;
        if (prediction == null) {
            throw ApiException.notFound("PREDICTION_NOT_FOUND", "No such prediction");
        }
        User creator = prediction.creatorId == null ? null : users.findById(prediction.creatorId);
        // Deleted accounts have their username scrubbed to null (FR-033).
        return PredictionDetail.of(prediction, clock.instant(), creator == null ? null : creator.username);
    }
}
