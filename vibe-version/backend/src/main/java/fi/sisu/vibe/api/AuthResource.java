package fi.sisu.vibe.api;

import fi.sisu.vibe.api.Views.Credentials;
import fi.sisu.vibe.api.Views.Session;
import fi.sisu.vibe.service.AuthService;
import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;

@Path("/auth")
@PermitAll
public class AuthResource {

    private final AuthService auth;

    public AuthResource(AuthService auth) {
        this.auth = auth;
    }

    @POST
    @Path("/register")
    public Session register(@Valid @NotNull Credentials credentials) {
        return auth.register(credentials);
    }

    @POST
    @Path("/login")
    public Session login(@Valid @NotNull Credentials credentials, @Context HttpServerRequest request) {
        return auth.login(credentials, request.remoteAddress().hostAddress());
    }
}
