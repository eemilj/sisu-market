package fi.sisu.market.prediction;

import fi.sisu.market.live.EventHub;
import fi.sisu.market.live.LiveEvent;
import fi.sisu.market.pricing.Cpmm;
import fi.sisu.market.user.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.time.Instant;

@ApplicationScoped
public class PredictionService {

    /** New predictions open at even odds. */
    private static final BigDecimal OPENING_PROBABILITY = new BigDecimal("0.5");

    @Inject
    PredictionRepository predictions;

    @ConfigProperty(name = "sisu.market.liquidity")
    long liquidity;

    @Inject
    EventHub events;

    public Prediction create(CreatePredictionRequest request, User creator) {
        Instant now = Instant.now();
        Instant closesAt = ClosingWindow.validate(request.closesAt(), now);

        Prediction p = new Prediction();
        p.question = request.question();
        p.description = request.description();
        p.resolutionCriteria = request.resolutionCriteria();
        p.category = request.category();
        p.closesAt = closesAt;
        p.status = PredictionStatus.OPEN;
        p.updatePool(Cpmm.initialPool(liquidity, OPENING_PROBABILITY));
        p.totalWagered = 0;
        p.createdById = creator.id;
        p.createdBy = creator.username;
        p.createdAt = now;
        predictions.persist(p);
        events.publish(LiveEvent.toEveryone(LiveEvent.PREDICTION_CREATED, PredictionDto.from(p, now)));
        return p;
    }
}
