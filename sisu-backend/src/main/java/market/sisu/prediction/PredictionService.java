package market.sisu.prediction;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import market.sisu.common.ApiException;
import market.sisu.user.User;
import market.sisu.user.UserRepository;
import org.bson.types.ObjectId;

/**
 * Write side of predictions for User Story 4 (FR-012 through FR-015): a logged-in user creates a
 * yes/no prediction, attributed to them, that opens for wagering immediately.
 */
@ApplicationScoped
public class PredictionService {

    private final PredictionRepository predictions;
    private final UserRepository users;
    private final Clock clock;

    public PredictionService(PredictionRepository predictions, UserRepository users, Clock clock) {
        this.predictions = predictions;
        this.users = users;
        this.clock = clock;
    }

    /**
     * Creates an {@code OPEN} prediction with empty pools. Question and resolution criteria are
     * stored trimmed; a missing description is stored as empty.
     *
     * @throws ApiException {@code 401 UNAUTHENTICATED} if the creator's account no longer exists;
     *     {@code 422} with {@code QUESTION_REQUIRED}, {@code RESOLUTION_CRITERIA_REQUIRED} or
     *     {@code CLOSING_DATE_NOT_IN_FUTURE} for invalid input (FR-013, FR-014)
     */
    public PredictionDetail create(ObjectId creatorId, CreatePredictionRequest request) {
        if (request == null) {
            throw ApiException.unprocessable("VALIDATION_FAILED", "Request body is required");
        }
        String question = strip(request.question());
        if (question.isEmpty()) {
            throw ApiException.unprocessable("QUESTION_REQUIRED", "Question must not be empty");
        }
        String criteria = strip(request.resolutionCriteria());
        if (criteria.isEmpty()) {
            throw ApiException.unprocessable("RESOLUTION_CRITERIA_REQUIRED", "Resolution criteria must not be empty");
        }
        Instant now = clock.instant();
        if (request.closingAt() == null || !request.closingAt().isAfter(now)) {
            throw ApiException.unprocessable("CLOSING_DATE_NOT_IN_FUTURE", "Closing date must be in the future");
        }
        User creator = users.findById(creatorId);
        if (creator == null || creator.deletedAt != null) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "Login required");
        }

        Prediction prediction = new Prediction();
        prediction.id = new ObjectId();
        prediction.creatorId = creator.id;
        prediction.question = question;
        prediction.description = strip(request.description());
        prediction.resolutionCriteria = criteria;
        // MongoDB keeps milliseconds; truncate now so this response matches every later read.
        prediction.closingAt = request.closingAt().truncatedTo(ChronoUnit.MILLIS);
        prediction.status = PredictionStatus.OPEN;
        prediction.createdAt = now;
        predictions.persist(prediction);
        return PredictionDetail.of(prediction, now, creator.username);
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }
}
