package market.sisu.user;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import market.sisu.common.ApiError;
import market.sisu.common.ApiException;
import market.sisu.prediction.PredictionRepository;
import market.sisu.prediction.PredictionSummary;
import market.sisu.transaction.Transaction;
import market.sisu.transaction.TransactionRepository;
import market.sisu.wager.Wager;
import market.sisu.wager.WagerRepository;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * {@code /api/users/*} for the logged-in user (User Story 1 & User Story 6).
 */
@Path("/users")
@Tag(name = "users")
public class UserResource {

    private final UserRepository users;
    private final JsonWebToken jwt;
    private final PredictionRepository predictionRepository;
    private final WagerRepository wagerRepository;
    private final TransactionRepository transactionRepository;
    private final Clock clock;

    UserResource(
            UserRepository users,
            JsonWebToken jwt,
            PredictionRepository predictionRepository,
            WagerRepository wagerRepository,
            TransactionRepository transactionRepository,
            Clock clock) {
        this.users = users;
        this.jwt = jwt;
        this.predictionRepository = predictionRepository;
        this.wagerRepository = wagerRepository;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    @GET
    @Path("/me")
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    @Operation(summary = "The logged-in user's own profile — private (FR-030)")
    @APIResponse(responseCode = "200", description = "OK")
    @APIResponse(
            responseCode = "401",
            description = "Not logged in",
            content =
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    public UserProfile me() {
        User user = getCurrentUser();
        Instant now = clock.instant();
        List<PredictionSummary> created = predictionRepository
                .mongoCollection()
                .find(Filters.eq("creatorId", user.id))
                .sort(Sorts.descending("createdAt"))
                .into(new ArrayList<>())
                .stream()
                .map(p -> PredictionSummary.of(p, now))
                .toList();
        List<Wager> userWagers = wagerRepository
                .mongoCollection()
                .find(Filters.eq("userId", user.id))
                .sort(Sorts.descending("placedAt"))
                .into(new ArrayList<>());
        return UserProfile.of(user, created, userWagers);
    }

    @GET
    @Path("/me/transactions")
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    @Operation(summary = "The logged-in user's transaction history (FR-011)")
    @APIResponse(responseCode = "200", description = "OK")
    @APIResponse(
            responseCode = "401",
            description = "Not logged in",
            content =
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    public List<Transaction> transactions() {
        User user = getCurrentUser();
        return transactionRepository.findByUser(user.id);
    }

    private User getCurrentUser() {
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
        return user;
    }
}
