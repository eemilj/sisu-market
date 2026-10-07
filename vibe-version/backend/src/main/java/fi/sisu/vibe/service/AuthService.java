package fi.sisu.vibe.service;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import fi.sisu.vibe.api.Views.Credentials;
import fi.sisu.vibe.api.Views.Session;
import fi.sisu.vibe.api.Views.UserView;
import fi.sisu.vibe.domain.LoginThrottle;
import fi.sisu.vibe.domain.Role;
import fi.sisu.vibe.domain.TxReason;
import fi.sisu.vibe.store.Db;
import fi.sisu.vibe.store.Docs;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AuthService {

    private static final Logger LOG = Logger.getLogger(AuthService.class);
    // Verified against when the username does not exist, so timing does not reveal valid names.
    private static final String DUMMY_HASH = BcryptUtil.bcryptHash("not-a-real-password");

    private final Db db;
    private final Clock clock;
    private final LoginThrottle throttle;
    private final long startingBalance;
    private final Duration tokenTtl;
    private final Set<String> admins;

    public AuthService(
            Db db,
            Clock clock,
            @ConfigProperty(name = "sisu.starting-balance") long startingBalance,
            @ConfigProperty(name = "sisu.token-ttl-hours") long ttlHours,
            @ConfigProperty(name = "sisu.admins") List<String> admins) {
        this.db = db;
        this.clock = clock;
        this.throttle = new LoginThrottle(clock, 5, Duration.ofMinutes(15));
        this.startingBalance = startingBalance;
        this.tokenTtl = Duration.ofHours(ttlHours);
        this.admins = Set.copyOf(admins.stream().map(a -> a.trim().toLowerCase(Locale.ROOT)).toList());
    }

    public Session register(Credentials c) {
        String key = c.username().toLowerCase(Locale.ROOT);
        Role role = admins.contains(key) ? Role.ADMIN : Role.USER;
        Instant now = clock.instant();
        ObjectId id = new ObjectId();
        Document user = new Document("_id", id)
                .append("username", c.username())
                .append("usernameKey", key)
                .append("passwordHash", BcryptUtil.bcryptHash(c.password()))
                .append("balance", startingBalance)
                .append("role", role.name())
                .append("createdAt", Date.from(now));
        try {
            db.tx(s -> {
                db.users().insertOne(s, user);
                db.transactions().insertOne(s, Docs.newTx(id, startingBalance, startingBalance, TxReason.STARTING_GRANT, null, now));
                return null;
            });
        } catch (MongoWriteException e) {
            if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                throw ApiException.conflict("USERNAME_TAKEN", "Username is already taken");
            }
            throw e;
        }
        LOG.infof("Registered user %s (%s)", c.username(), role);
        return session(Docs.user(user));
    }

    public Session login(Credentials c, String ip) {
        if (throttle.isBlocked(c.username(), ip)) {
            throw new ApiException(429, "TOO_MANY_ATTEMPTS", "Too many failed logins, try again later");
        }
        Document user = db.users()
                .find(new Document("usernameKey", c.username().toLowerCase(Locale.ROOT)))
                .first();
        String hash = user == null ? DUMMY_HASH : user.getString("passwordHash");
        boolean ok = BcryptUtil.matches(c.password(), hash) && user != null;
        if (!ok) {
            throttle.recordFailure(c.username(), ip);
            throw new ApiException(401, "INVALID_CREDENTIALS", "Wrong username or password");
        }
        throttle.recordSuccess(c.username());
        return session(Docs.user(user));
    }

    private Session session(UserView user) {
        String token = Jwt.subject(user.id())
                .upn(user.username())
                .groups(user.role() == Role.ADMIN ? Set.of("USER", "ADMIN") : Set.of("USER"))
                .expiresIn(tokenTtl)
                .sign();
        return new Session(token, user);
    }
}
