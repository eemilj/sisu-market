package market.sisu.auth;

import io.vertx.core.http.HttpServerRequest;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import market.sisu.auth.AuthService.LoginResult;
import market.sisu.common.ApiError;
import market.sisu.user.User;
import market.sisu.user.UserService;
import market.sisu.user.UserSummary;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * {@code /api/auth/*}: register, login, logout (FR-001 through FR-004, FR-037). Every success sets
 * or clears the hardened {@code sisu_session} cookie owned by {@link SessionCookieFilter}; every
 * failure leaves the API as an {@link ApiError} body.
 */
@Path("/auth")
@Tag(name = "auth")
public class AuthResource {

    private final UserService userService;
    private final AuthService authService;
    private final JwtService jwtService;
    private final SessionCookieFilter cookies;

    AuthResource(UserService userService, AuthService authService, JwtService jwtService, SessionCookieFilter cookies) {
        this.userService = userService;
        this.authService = authService;
        this.jwtService = jwtService;
        this.cookies = cookies;
    }

    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Register a new account (FR-001)")
    @APIResponse(
            responseCode = "201",
            description = "Account created, session cookie set, starting balance granted (FR-007)")
    @APIResponse(
            responseCode = "409",
            description = "Username already taken",
            content =
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    @APIResponse(
            responseCode = "422",
            description = "Validation failure (empty username/password)",
            content =
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    public Response register(@Valid RegisterRequest body) {
        User user = userService.register(body.username(), body.password());
        String jwt = jwtService.issue(user.id, user.username, user.role);
        return Response.status(Response.Status.CREATED)
                .cookie(cookies.sessionCookie(jwt))
                .entity(UserSummary.of(user))
                .build();
    }

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Log in (FR-002)")
    @APIResponse(responseCode = "200", description = "Session cookie set")
    @APIResponse(
            responseCode = "401",
            description = "Invalid username or password, without revealing which (FR-003)",
            content =
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    @APIResponse(
            responseCode = "429",
            description = "5 failed attempts reached for this username/IP (FR-037)",
            content =
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    public Response login(LoginRequest body, @Context HttpServerRequest request) {
        LoginResult result = authService.login(
                body == null ? null : body.username(), body == null ? null : body.password(), clientIp(request));
        return Response.ok(UserSummary.of(result.user()))
                .cookie(cookies.sessionCookie(result.jwt()))
                .build();
    }

    @POST
    @Path("/logout")
    @Operation(summary = "Log out (FR-004)")
    @APIResponse(responseCode = "204", description = "Session cookie cleared")
    public Response logout() {
        return Response.noContent().cookie(cookies.clearedCookie()).build();
    }

    private static String clientIp(HttpServerRequest request) {
        if (request != null && request.remoteAddress() != null) {
            return request.remoteAddress().host();
        }
        return "unknown";
    }
}
