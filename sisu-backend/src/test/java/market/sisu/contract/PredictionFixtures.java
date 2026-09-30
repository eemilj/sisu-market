package market.sisu.contract;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import market.sisu.prediction.Outcome;
import market.sisu.prediction.Prediction;
import market.sisu.prediction.PredictionRepository;
import market.sisu.prediction.PredictionStatus;
import market.sisu.user.Role;
import market.sisu.user.User;
import market.sisu.user.UserRepository;
import org.bson.types.ObjectId;

/**
 * Seeds predictions straight into MongoDB so the browse endpoints (User Story 2) can be tested
 * independently of prediction creation (User Story 4) and wagering (User Story 3).
 */
@ApplicationScoped
public class PredictionFixtures {

    private final PredictionRepository predictions;
    private final UserRepository users;

    PredictionFixtures(PredictionRepository predictions, UserRepository users) {
        this.predictions = predictions;
        this.users = users;
    }

    public User creator() {
        User user = new User();
        user.id = new ObjectId();
        user.username = "luoja-" + UUID.randomUUID();
        user.passwordHash = "not-a-real-hash";
        user.role = Role.USER;
        user.createdAt = Instant.now();
        users.persist(user);
        return user;
    }

    /** A prediction in {@code status} closing {@code closesInHours} from now (negative = past). */
    public Prediction prediction(User creator, PredictionStatus status, long closesInHours, long yesPool, long noPool) {
        Prediction prediction = new Prediction();
        prediction.id = new ObjectId();
        prediction.creatorId = creator.id;
        prediction.question = "Sataako Helsingissä? " + UUID.randomUUID();
        prediction.description = "Kuvaus " + prediction.question;
        prediction.resolutionCriteria = "Ilmatieteen laitoksen mittaus";
        // MongoDB stores millisecond precision; truncate so the API echoes this exact instant.
        prediction.closingAt =
                Instant.now().plus(closesInHours, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        prediction.status = status;
        prediction.yesPoolSisu = yesPool;
        prediction.noPoolSisu = noPool;
        prediction.createdAt = Instant.now();
        if (status == PredictionStatus.RESOLVED) {
            prediction.winningOutcome = Outcome.YES;
            prediction.resolvedAt = Instant.now();
        }
        predictions.persist(prediction);
        return prediction;
    }
}
