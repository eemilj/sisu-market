package market.sisu.auth;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.Set;
import market.sisu.user.Role;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Issues the signed session JWT (research.md section 3). The subject is the user id, the
 * {@code upn} the username and the single group the user's {@link Role}, so resources can use
 * {@code @RolesAllowed("RESOLVER")} directly. Validation is done by SmallRye JWT, which reads the
 * token from the {@code sisu_session} cookie.
 */
@ApplicationScoped
public class JwtService {

    private final String issuer;
    private final Duration ttl;

    JwtService(
            @ConfigProperty(name = "mp.jwt.verify.issuer") String issuer,
            @ConfigProperty(name = "sisu.session.ttl") Duration ttl) {
        this.issuer = issuer;
        this.ttl = ttl;
    }

    public String issue(ObjectId userId, String username, Role role) {
        return Jwt.issuer(issuer)
                .subject(userId.toHexString())
                .upn(username)
                .groups(Set.of(role.name()))
                .expiresIn(ttl)
                .sign();
    }

    public Duration ttl() {
        return ttl;
    }
}
