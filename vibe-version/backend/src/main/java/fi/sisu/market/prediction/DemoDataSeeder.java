package fi.sisu.market.prediction;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import fi.sisu.market.user.UserRepository;
import fi.sisu.market.wager.WagerRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/** Creates indexes on startup, seeds demo predictions into an empty database and keeps seeded ones up to date. */
@ApplicationScoped
public class DemoDataSeeder {

    private static final Logger LOG = Logger.getLogger(DemoDataSeeder.class);

    @Inject
    UserRepository users;

    @Inject
    PredictionRepository predictions;

    @Inject
    WagerRepository wagers;

    @ConfigProperty(name = "sisu.seed-demo-data", defaultValue = "true")
    boolean seed;

    @ConfigProperty(name = "sisu.market.liquidity")
    long liquidity;

    void onStart(@Observes StartupEvent event) {
        users.ensureIndexes();
        predictions.ensureIndexes();
        wagers.ensureIndexes();

        if (seed && predictions.count() == 0) {
            predictions.persist(DemoData.predictions(liquidity));
            LOG.infof("Seeded %d demo predictions", predictions.count());
        } else {
            translateLegacySeeds();
        }
    }

    /** Databases seeded by earlier versions have the demo predictions in English; switch them to Finnish once. */
    private void translateLegacySeeds() {
        long translated = 0;
        for (DemoData.Seed s : DemoData.SEEDS) {
            translated += predictions.mongoCollection().updateOne(
                    Filters.and(Filters.eq("question", s.legacyQuestion()), Filters.eq("createdById", null)),
                    Updates.combine(
                            Updates.set("question", s.question()),
                            Updates.set("description", s.description()),
                            Updates.set("resolutionCriteria", s.resolutionCriteria()))).getModifiedCount();
        }
        if (translated > 0) {
            LOG.infof("Translated %d demo predictions to Finnish", translated);
        }
    }
}
