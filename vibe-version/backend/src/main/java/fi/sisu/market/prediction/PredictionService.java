package fi.sisu.market.prediction;

import fi.sisu.market.common.ApiException;
import fi.sisu.market.user.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

@ApplicationScoped
public class PredictionService {

    /** Gives people a meaningful window to take positions. */
    static final Duration MIN_OPEN_TIME = Duration.ofHours(1);
    /** Keeps markets resolvable within a sensible horizon. */
    static final Duration MAX_OPEN_TIME = Duration.ofDays(5 * 365);

    @Inject
    PredictionRepository predictions;

    public Prediction create(CreatePredictionRequest request, User creator) {
        Instant now = Instant.now();
        Instant closesAt = parseClosingDate(request.closesAt());
        if (closesAt.isBefore(now.plus(MIN_OPEN_TIME))) {
            throw ApiException.badRequest("Closing date must be at least 1 hour in the future");
        }
        if (closesAt.isAfter(now.plus(MAX_OPEN_TIME))) {
            throw ApiException.badRequest("Closing date must be within 5 years");
        }

        Prediction p = new Prediction();
        p.question = request.question().trim();
        p.description = request.description().trim();
        p.resolutionCriteria = request.resolutionCriteria().trim();
        p.category = request.category();
        p.closesAt = closesAt;
        p.status = PredictionStatus.OPEN;
        p.yesPool = 0;
        p.noPool = 0;
        p.createdById = creator.id;
        p.createdBy = creator.username;
        p.createdAt = now;
        predictions.persist(p);
        return p;
    }

    /** Requires an explicit offset (e.g. {@code 2027-01-01T12:00:00Z} or {@code +02:00}) so the instant is unambiguous. */
    private static Instant parseClosingDate(String value) {
        try {
            return OffsetDateTime.parse(value.trim()).toInstant();
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Closing date must be an ISO-8601 date-time with time zone, e.g. 2027-01-01T12:00:00Z");
        }
    }
}
