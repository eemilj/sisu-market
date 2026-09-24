package market.sisu.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import java.util.Set;
import market.sisu.common.ApiError;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * Owns the {@code sisu_session} cookie (research.md section 3): builds the httpOnly, Secure,
 * SameSite=Strict cookie that carries the JWT, and guards state-changing requests against CSRF.
 *
 * <p>CSRF defence is SameSite=Strict plus this filter: a POST/PUT/PATCH/DELETE that declares a
 * body type other than {@code application/json} is rejected. Cross-site HTML forms can only send
 * form or text bodies, so they never reach a resource.
 */
@ApplicationScoped
public class SessionCookieFilter {

    public static final String COOKIE_NAME = "sisu_session";

    private static final Set<String> STATE_CHANGING = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final JwtService jwtService;
    private final boolean secure;

    SessionCookieFilter(JwtService jwtService, @ConfigProperty(name = "sisu.session.cookie-secure") boolean secure) {
        this.jwtService = jwtService;
        this.secure = secure;
    }

    @ServerRequestFilter(preMatching = true)
    public Response requireJsonForStateChanges(ContainerRequestContext request) {
        if (!STATE_CHANGING.contains(request.getMethod())) {
            return null;
        }
        MediaType type = request.getMediaType();
        if (type == null || MediaType.APPLICATION_JSON_TYPE.isCompatible(type)) {
            return null;
        }
        return Response.status(Response.Status.UNSUPPORTED_MEDIA_TYPE)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ApiError("UNSUPPORTED_MEDIA_TYPE", "Request body must be application/json"))
                .build();
    }

    /** The cookie that logs the browser in with {@code jwt}; expires with the token. */
    public NewCookie sessionCookie(String jwt) {
        return cookie(jwt, (int) jwtService.ttl().toSeconds());
    }

    /** A cookie that immediately removes the session cookie (logout, account deletion). */
    public NewCookie clearedCookie() {
        return cookie("", 0);
    }

    private NewCookie cookie(String value, int maxAgeSeconds) {
        return new NewCookie.Builder(COOKIE_NAME)
                .value(value)
                .path("/")
                .maxAge(maxAgeSeconds)
                .httpOnly(true)
                .secure(secure)
                .sameSite(NewCookie.SameSite.STRICT)
                .build();
    }
}
