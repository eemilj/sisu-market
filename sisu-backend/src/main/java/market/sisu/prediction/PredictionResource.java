package market.sisu.prediction;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.Arrays;
import java.util.List;
import market.sisu.common.ApiError;
import market.sisu.common.ApiException;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * {@code /api/predictions}: public browsing for User Story 2 (FR-016 through FR-018) and, for
 * logged-in users, creation for User Story 4 (FR-006, FR-012). Resolution (User Story 5) is added
 * here later.
 */
@Path("/predictions")
@Tag(name = "predictions")
public class PredictionResource {

    private final PredictionQueryService queries;
    private final PredictionService commands;
    private final JsonWebToken jwt;

    PredictionResource(PredictionQueryService queries, PredictionService commands, JsonWebToken jwt) {
        this.queries = queries;
        this.commands = commands;
        this.jwt = jwt;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List predictions, optionally filtered by status (FR-016, FR-018)")
    @APIResponse(
            responseCode = "200",
            description = "OK",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(type = SchemaType.ARRAY, implementation = PredictionSummary.class)))
    @APIResponse(
            responseCode = "422",
            description = "Unknown status value",
            content =
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    public List<PredictionSummary> list(
            @QueryParam("status") @Parameter(schema = @Schema(implementation = PredictionStatus.class)) String status) {
        return queries.list(parseStatus(status));
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Prediction detail (FR-017)")
    @APIResponse(responseCode = "200", description = "OK")
    @APIResponse(
            responseCode = "404",
            description = "No such prediction",
            content =
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    public PredictionDetail detail(@PathParam("id") String id) {
        return queries.detail(id);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    @Operation(summary = "Create a prediction (FR-012, FR-013, FR-014)")
    @APIResponse(
            responseCode = "201",
            description = "Created",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = PredictionDetail.class)))
    @APIResponse(
            responseCode = "401",
            description = "Not logged in",
            content =
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    @APIResponse(
            responseCode = "422",
            description = "Empty question/resolution criteria, or a closing date that isn't in the future",
            content =
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    public Response create(CreatePredictionRequest body, @Context UriInfo uri) {
        PredictionDetail created = commands.create(currentUserId(), body);
        return Response.created(uri.getAbsolutePathBuilder().path(created.id()).build())
                .entity(created)
                .build();
    }

    private ObjectId currentUserId() {
        String subject = jwt.getSubject();
        if (subject == null || !ObjectId.isValid(subject)) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "Login required");
        }
        return new ObjectId(subject);
    }

    /** Parsed by hand so an unknown value is a 422 in the {@code Error} shape, not a bare 404. */
    private static PredictionStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return PredictionStatus.valueOf(status);
        } catch (IllegalArgumentException unknown) {
            throw ApiException.unprocessable(
                    "VALIDATION_FAILED", "status must be one of " + Arrays.toString(PredictionStatus.values()));
        }
    }
}
