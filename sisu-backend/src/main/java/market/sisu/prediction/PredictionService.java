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

    /** FR-038. A question should fit on a list card: one sentence, like a headline. */
    public static final int QUESTION_MAX_LENGTH = 200;

    /** Background for the question; a few paragraphs at most. */
    public static final int DESCRIPTION_MAX_LENGTH = 2000;

    /** How the outcome is decided and from which source; a paragraph or two. */
    public static final int RESOLUTION_CRITERIA_MAX_LENGTH = 1000;

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
     * stored trimmed; a missing description is stored as empty. Length limits apply to the
     * trimmed text.
     *
     * @throws ApiException {@code 401 UNAUTHENTICATED} if the creator's account no longer exists;
     *     {@code 422} with {@code QUESTION_REQUIRED}, {@code QUESTION_TOO_LONG},
     *     {@code DESCRIPTION_TOO_LONG}, {@code RESOLUTION_CRITERIA_REQUIRED},
     *     {@code RESOLUTION_CRITERIA_TOO_LONG} or {@code CLOSING_DATE_NOT_IN_FUTURE} for invalid
     *     input (FR-013, FR-014, FR-038)
     */
    public PredictionDetail create(ObjectId creatorId, CreatePredictionRequest request) {
        if (request == null) {
            throw ApiException.unprocessable("VALIDATION_FAILED", "Request body is required");
        }
        String question = strip(request.question());
        if (question.isEmpty()) {
            throw ApiException.unprocessable("QUESTION_REQUIRED", "Question must not be empty");
        }
        requireMaxLength(question, QUESTION_MAX_LENGTH, "QUESTION_TOO_LONG", "Question");
        String description = strip(request.description());
        requireMaxLength(description, DESCRIPTION_MAX_LENGTH, "DESCRIPTION_TOO_LONG", "Description");
        String criteria = strip(request.resolutionCriteria());
        if (criteria.isEmpty()) {
            throw ApiException.unprocessable("RESOLUTION_CRITERIA_REQUIRED", "Resolution criteria must not be empty");
        }
        requireMaxLength(
                criteria, RESOLUTION_CRITERIA_MAX_LENGTH, "RESOLUTION_CRITERIA_TOO_LONG", "Resolution criteria");
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
        prediction.description = description;
        prediction.resolutionCriteria = criteria;
        // MongoDB keeps milliseconds; truncate now so this response matches every later read.
        prediction.closingAt = request.closingAt().truncatedTo(ChronoUnit.MILLIS);
        prediction.status = PredictionStatus.OPEN;
        prediction.createdAt = now;
        predictions.persist(prediction);
        return PredictionDetail.of(prediction, now, creator.username);
    }

    /** Lengths count UTF-16 code units, matching the frontend's {@code maxLength}. */
    private static void requireMaxLength(String value, int max, String code, String field) {
        if (value.length() > max) {
            throw ApiException.unprocessable(code, field + " must be at most " + max + " characters");
        }
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }
}
