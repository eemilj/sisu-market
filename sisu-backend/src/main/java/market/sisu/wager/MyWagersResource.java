package market.sisu.wager;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import market.sisu.common.ApiError;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * {@code GET /api/users/me/wagers}: the logged-in user's own open wagers with their potential
 * payout (User Story 3, scenario 4; FR-024). Its own class for the same routing reason as
 * {@link WagerResource}: under {@code UserResource}'s {@code /users} it would never be reached.
 */
@Path("/users/me/wagers")
@Tag(name = "wagers")
public class MyWagersResource {

    private final WagerService wagers;
    private final JsonWebToken jwt;

    MyWagersResource(WagerService wagers, JsonWebToken jwt) {
        this.wagers = wagers;
        this.jwt = jwt;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    @Operation(summary = "The logged-in user's own open wagers, with potential payout (FR-024)")
    @APIResponse(
            responseCode = "200",
            description = "OK",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(type = SchemaType.ARRAY, implementation = WagerView.class)))
    @APIResponse(
            responseCode = "401",
            description = "Not logged in",
            content =
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    public List<WagerView> myOpenWagers() {
        return wagers.openWagers(WagerResource.currentUserId(jwt));
    }
}
