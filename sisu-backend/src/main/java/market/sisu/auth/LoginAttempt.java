package market.sisu.auth;

import io.quarkus.mongodb.panache.common.MongoEntity;
import java.time.Instant;
import org.bson.types.ObjectId;

/**
 * Failed-login counter for one (username, IP) pair within a 15-minute window (FR-037,
 * research.md section 5). A TTL index deletes the document once the window has passed.
 */
@MongoEntity(collection = "login_attempts")
public class LoginAttempt {

    public ObjectId id;

    /** Lower-cased so the limit cannot be sidestepped by changing letter case. */
    public String username;

    public String ip;

    public int failures;

    /** When the first failure in the current window happened; the TTL index counts from here. */
    public Instant windowStartedAt;
}
