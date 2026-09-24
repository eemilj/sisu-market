package market.sisu.auth;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.bson.conversions.Bson;

@ApplicationScoped
public class LoginAttemptRepository implements PanacheMongoRepository<LoginAttempt> {

    /** FR-037 cooldown window. */
    public static final Duration WINDOW = Duration.ofMinutes(15);

    void createIndexes(@Observes StartupEvent event) {
        mongoCollection()
                .createIndex(
                        Indexes.ascending("username", "ip"),
                        new IndexOptions().name("username_ip_unique").unique(true));
        // MongoDB's TTL monitor runs about once a minute, so the rate limiter must still compare
        // windowStartedAt against its own clock; this index only guarantees cleanup.
        mongoCollection()
                .createIndex(
                        Indexes.ascending("windowStartedAt"),
                        new IndexOptions()
                                .name("windowStartedAt_ttl")
                                .expireAfter(WINDOW.toSeconds(), TimeUnit.SECONDS));
    }

    public Optional<LoginAttempt> find(String username, String ip) {
        return Optional.ofNullable(mongoCollection().find(key(username, ip)).first());
    }

    /**
     * Atomically counts one more failure for this pair, starting a new window at {@code now} if
     * none exists yet.
     *
     * @return the updated attempt record
     */
    public LoginAttempt recordFailure(String username, String ip, Instant now) {
        return mongoCollection()
                .findOneAndUpdate(
                        key(username, ip),
                        // On insert, username and ip are seeded from the equality filter.
                        Updates.combine(Updates.inc("failures", 1), Updates.setOnInsert("windowStartedAt", now)),
                        new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
    }

    /** Forgets the pair's failures (successful login, or an expired window). */
    public void clear(String username, String ip) {
        mongoCollection().deleteOne(key(username, ip));
    }

    private static Bson key(String username, String ip) {
        return Filters.and(Filters.eq("username", normalize(username)), Filters.eq("ip", ip));
    }

    private static String normalize(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}
