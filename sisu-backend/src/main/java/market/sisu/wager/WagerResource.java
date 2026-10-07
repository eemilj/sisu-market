package market.sisu.wager;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
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
 * {@code POST /api/predictions/{id}/wagers}: a logged-in user places a wager (User Story 3; FR-006,
 * FR-019 through FR-023). The user's own open wagers are listed by {@link MyWagersResource}.
 *
 * <p>The full path sits on the class: JAX-RS picks the most specific class-level path first, so a
 * method path below a broader class path such as {@code /} would be shadowed by
 * {@code PredictionResource}'s {@code /predictions}.
 */
@Path("/predictions/{id}/wagers")
@Tag(name = "wagers")
public class WagerResource {

    private final WagerService wagers;
    private final JsonWebToken jwt;

    WagerResource(WagerService wagers, JsonWebToken jwt) {
        this.wagers = wagers;
        this.jwt = jwt;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    @Operation(summary = "Place a wager on this prediction (FR-019 through FR-023)")
    @APIResponse(
            responseCode = "201",
            description = "Wager recorded; balance debited and prediction odds updated atomically in the same"
                    + " transaction (FR-022)",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = WagerView.class)))
    @APIResponse(
            responseCode = "401",
            description = "Not logged in",
            content =
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    @APIResponse(
            responseCode = "404",
            description = "No such prediction",
            content =
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    @APIResponse(
            responseCode = "409",
            description = "Prediction is not open (closed, resolved, or cancelled, or its closing time has passed)"
                    + " (FR-021, FR-025)",
            content =
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    @APIResponse(
            responseCode = "422",
            description = "Wager amount is not a positive whole number, or exceeds the user's balance (FR-020)",
            content =
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    public Response place(@PathParam("id") String id, PlaceWagerRequest body) {
        WagerView placed = wagers.placeWager(currentUserId(jwt), id, body);
        return Response.status(Response.Status.CREATED).entity(placed).build();
    }

    /** The logged-in user's id from the session token. */
    static ObjectId currentUserId(JsonWebToken jwt) {
        String subject = jwt.getSubject();
        if (subject == null || !ObjectId.isValid(subject)) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "Login required");
        }
        return new ObjectId(subject);
    }
}
