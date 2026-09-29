package market.sisu.user;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import market.sisu.common.ApiError;
import market.sisu.common.ApiException;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * {@code /api/users/*} for the logged-in user. User Story 1 needs only the session check below so
 * the SPA can restore the login and show the balance; User Story 6 (T066) extends this resource
 * with the full private profile and the transaction history.
 */
@Path("/users")
@Tag(name = "users")
public class UserResource {

    private final UserRepository users;
    private final JsonWebToken jwt;

    UserResource(UserRepository users, JsonWebToken jwt) {
        this.users = users;
        this.jwt = jwt;
    }

    @GET
    @Path("/me")
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    @Operation(summary = "The logged-in user's own account summary (extended to the full private profile by US6)")
    @APIResponse(responseCode = "200", description = "OK")
    @APIResponse(
            responseCode = "401",
            description = "Not logged in",
            content =
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    public UserSummary me() {
        ObjectId userId;
        try {
            userId = new ObjectId(jwt.getSubject());
        } catch (IllegalArgumentException invalidSubject) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "Login required");
        }
        User user = users.findById(userId);
        if (user == null || user.deletedAt != null) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "Login required");
        }
        return UserSummary.of(user);
    }
}
