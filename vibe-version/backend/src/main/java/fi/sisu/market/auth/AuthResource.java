package fi.sisu.market.auth;

import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.ResponseStatus;

@Path("/api/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService auth;

    @POST
    @Path("/register")
    @ResponseStatus(201)
    public AuthResponse register(@NotNull(message = "Request body is required") @Valid RegisterRequest request,
                                 @Context HttpServerRequest http) {
        return auth.register(request, clientIp(http));
    }

    /** 401 for wrong credentials; 429 (with Retry-After) after too many failures. */
    @POST
    @Path("/login")
    public AuthResponse login(@NotNull(message = "Request body is required") @Valid LoginRequest request,
                              @Context HttpServerRequest http) {
        return auth.login(request, clientIp(http));
    }

    /**
     * The caller's address. Behind nginx, Quarkus replaces it with X-Forwarded-For, but only when the request
     * comes from a trusted proxy (see quarkus.http.proxy.* config), so clients can't spoof it.
     */
    static String clientIp(HttpServerRequest http) {
        return http.remoteAddress() == null ? "unknown" : http.remoteAddress().hostAddress();
    }
}
