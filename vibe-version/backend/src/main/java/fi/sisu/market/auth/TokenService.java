package fi.sisu.market.auth;

import fi.sisu.market.user.User;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.Set;

@ApplicationScoped
public class TokenService {

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    @ConfigProperty(name = "sisu.jwt.ttl-hours", defaultValue = "24")
    long ttlHours;

    public String issue(User user) {
        return Jwt.issuer(issuer)
                .subject(user.id.toHexString())
                .upn(user.username)
                .groups(Set.of("user"))
                .expiresIn(Duration.ofHours(ttlHours))
                .sign();
    }
}
