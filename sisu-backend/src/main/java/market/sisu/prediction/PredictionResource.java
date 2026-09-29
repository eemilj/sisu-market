package market.sisu.prediction;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.Arrays;
import java.util.List;
import market.sisu.common.ApiError;
import market.sisu.common.ApiException;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * {@code /api/predictions}: public browsing for User Story 2 (FR-016 through FR-018). No login is
 * required. Creation (User Story 4) and resolution (User Story 5) are added here later.
 */
@Path("/predictions")
@Tag(name = "predictions")
public class PredictionResource {

    private final PredictionQueryService queries;

    PredictionResource(PredictionQueryService queries) {
        this.queries = queries;
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
