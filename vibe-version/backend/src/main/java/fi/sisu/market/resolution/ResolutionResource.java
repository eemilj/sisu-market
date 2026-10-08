package fi.sisu.market.resolution;

import fi.sisu.market.common.ApiException;
import fi.sisu.market.user.CurrentUser;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.bson.types.ObjectId;

@Path("/api/predictions/{predictionId}/resolve")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class ResolutionResource {

    @Inject
    ResolutionService service;

    @Inject
    CurrentUser currentUser;

    /** 200 on success; 403 not allowed; 409 still open or already resolved. */
    @POST
    public ResolutionResult resolve(@PathParam("predictionId") String predictionId,
                                    @NotNull(message = "Request body is required") @Valid ResolveRequest request) {
        if (!ObjectId.isValid(predictionId)) {
            throw ApiException.notFound("Prediction not found");
        }
        return service.resolve(new ObjectId(predictionId), request.outcome(), currentUser.require());
    }
}
