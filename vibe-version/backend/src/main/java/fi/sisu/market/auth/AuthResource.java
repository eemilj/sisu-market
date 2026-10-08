package fi.sisu.market.auth;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
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
    public AuthResponse register(@NotNull(message = "Request body is required") @Valid RegisterRequest request) {
        return auth.register(request);
    }

    @POST
    @Path("/login")
    public AuthResponse login(@NotNull(message = "Request body is required") @Valid LoginRequest request) {
        return auth.login(request);
    }
}
