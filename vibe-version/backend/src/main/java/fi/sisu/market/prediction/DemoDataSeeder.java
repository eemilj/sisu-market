package fi.sisu.market.prediction;

import fi.sisu.market.user.UserRepository;
import fi.sisu.market.wager.WagerRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/** Creates indexes on startup and seeds demo predictions into an empty database. */
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

    void onStart(@Observes StartupEvent event) {
        users.ensureIndexes();
        predictions.ensureIndexes();
        wagers.ensureIndexes();

        if (seed && predictions.count() == 0) {
            predictions.persist(DemoData.predictions());
            LOG.infof("Seeded %d demo predictions", predictions.count());
        }
    }
}
