package market.sisu.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
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
 * form or text bodies, so they never reach a resource. A request that carries no entity body at
 * all (e.g. {@code POST /api/auth/logout}) is let through: there is nothing smuggleable in it,
 * every HTML form submission declares a content type and carries a body, and the session cookie
 * itself is SameSite=Strict.
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
        if (type == null || MediaType.APPLICATION_JSON_TYPE.isCompatible(type) || !hasEntityBody(request)) {
            return null;
        }
        return Response.status(Response.Status.UNSUPPORTED_MEDIA_TYPE)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ApiError("UNSUPPORTED_MEDIA_TYPE", "Request body must be application/json"))
                .build();
    }

    /**
     * Whether the request carries an entity body. A declared non-JSON media type only matters when
     * there are actual bytes to smuggle; bare posts (no {@code Content-Length}, or zero, with no
     * chunked encoding) have none.
     */
    private static boolean hasEntityBody(ContainerRequestContext request) {
        String length = request.getHeaderString(HttpHeaders.CONTENT_LENGTH);
        if (length != null) {
            try {
                return Long.parseLong(length.trim()) > 0;
            } catch (NumberFormatException notANumber) {
                return true;
            }
        }
        String encoding = request.getHeaderString("Transfer-Encoding");
        return encoding != null && !encoding.isBlank();
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
