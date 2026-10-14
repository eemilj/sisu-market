package fi.sisu.market.admin;

import fi.sisu.market.common.ApiException;
import fi.sisu.market.common.ErrorCode;
import fi.sisu.market.prediction.PredictionDto;
import fi.sisu.market.prediction.PredictionRepository;
import fi.sisu.market.resolution.PredictionCloser;
import fi.sisu.market.user.AdminPolicy;
import fi.sisu.market.user.CurrentUser;
import fi.sisu.market.user.UserRepository;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.List;

/** Overviews and manual closing for admins. Every call re-checks {@link AdminPolicy}; the UI's hidden button is not security. */
@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class AdminResource {

    private static final int MAX_USERS = 500;

    @Inject
    CurrentUser currentUser;

    @Inject
    AdminPolicy adminPolicy;

    @Inject
    PredictionRepository predictions;

    @Inject
    UserRepository users;

    @Inject
    PredictionCloser closer;

    /** All predictions of any status, newest first. */
    @GET
    @Path("/predictions")
    public List<PredictionDto> predictions() {
        requireAdmin();
        Instant now = Instant.now();
        return predictions.newestFirst().stream().map(p -> PredictionDto.from(p, now)).toList();
    }

    /** Newest users first. */
    @GET
    @Path("/users")
    public List<AdminUserDto> users(@QueryParam("limit") @DefaultValue("200") int limit) {
        requireAdmin();
        if (limit < 1 || limit > MAX_USERS) {
            throw ApiException.badRequest(ErrorCode.INVALID_LIMIT, "limit must be between 1 and " + MAX_USERS);
        }
        return users.newest(limit).stream().map(u -> AdminUserDto.from(u, adminPolicy.isAdmin(u))).toList();
    }

    /** Closes an open prediction now; 404 unknown, 409 already closed or resolved. */
    @POST
    @Path("/predictions/{predictionId}/close")
    public PredictionDto close(@PathParam("predictionId") String predictionId) {
        requireAdmin();
        if (!ObjectId.isValid(predictionId)) {
            throw ApiException.notFound(ErrorCode.PREDICTION_NOT_FOUND, "Prediction not found");
        }
        return closer.closeNow(new ObjectId(predictionId));
    }

    private void requireAdmin() {
        if (!adminPolicy.isAdmin(currentUser.require())) {
            throw ApiException.forbidden(ErrorCode.ADMIN_ONLY, "Admins only");
        }
    }
}
