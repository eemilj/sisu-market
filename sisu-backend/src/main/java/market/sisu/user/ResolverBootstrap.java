package market.sisu.user;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.Optional;
import market.sisu.common.ApiException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Provisions the designated resolver account at startup (FR-039, FR-040; research.md section 8).
 *
 * <p>Credentials come only from configuration (never a committed default). When they are missing or
 * invalid the application still starts, but no one can resolve predictions until a resolver is
 * configured, so a clear warning is logged. The password is never logged.
 */
@ApplicationScoped
public class ResolverBootstrap {

    private static final Logger LOG = Logger.getLogger(ResolverBootstrap.class);

    private final UserService users;
    private final Optional<String> username;
    private final Optional<String> password;

    public ResolverBootstrap(
            UserService users,
            @ConfigProperty(name = "sisu.bootstrap.resolver.username") Optional<String> username,
            @ConfigProperty(name = "sisu.bootstrap.resolver.password") Optional<String> password) {
        this.users = users;
        this.username = username.filter(v -> !v.isBlank());
        this.password = password.filter(v -> !v.isEmpty());
    }

    void onStart(@Observes StartupEvent event) {
        provision();
    }

    /** Runs the provisioning outside a startup event (used by tests). */
    public void provision() {
        if (username.isEmpty() || password.isEmpty()) {
            LOG.warn("No resolver configured (set SISU_RESOLVER_USERNAME and SISU_RESOLVER_PASSWORD): "
                    + "predictions cannot be resolved until a resolver account exists.");
            return;
        }
        try {
            UserService.Provisioning result = users.provisionResolver(username.get(), password.get());
            LOG.infof("Resolver account '%s': %s", username.get(), result.name().toLowerCase());
        } catch (ApiException e) {
            LOG.warnf(
                    "Resolver account '%s' was not provisioned: %s. Predictions cannot be resolved "
                            + "until this is fixed.",
                    username.get(), e.getMessage());
        }
    }
}
