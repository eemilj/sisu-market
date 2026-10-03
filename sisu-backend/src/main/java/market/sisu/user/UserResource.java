package market.sisu.user;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.Clock;
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
 * {@code /api/users/*} for the logged-in user. User Story 1 needs only the session check below so
 * the SPA can restore the login and show the balance; User Story 6 (T066) extends this resource
 * with the full private profile and the transaction history.
 */
@Path("/users")
@Tag(name = "users")
public class UserResource {

    private final UserRepository users;
    private final TransactionRepository transactionsRepo;
    private final PredictionRepository predictions;
    private final WagerRepository wagers;
    private final Clock clock;
    private final JsonWebToken jwt;

    UserResource(
            UserRepository users,
            TransactionRepository transactionsRepo,
            PredictionRepository predictions,
            WagerRepository wagers,
            Clock clock,
            JsonWebToken jwt) {
        this.users = users;
        this.transactionsRepo = transactionsRepo;
        this.predictions = predictions;
        this.wagers = wagers;
        this.clock = clock;
        this.jwt = jwt;
    }

    @GET
    @Path("/me")
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    @Operation(summary = "The logged-in user's own private profile (FR-030)")
    @APIResponse(responseCode = "200", description = "OK")
    @APIResponse(
            responseCode = "401",
            description = "Not logged in",
            content =
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    public UserProfile me() {
        User user = authenticatedUser();
        List<PredictionSummary> createdPredictions = predictions.list("creatorId", user.id).stream()
                .sorted((a, b) -> b.createdAt.compareTo(a.createdAt))
                .map(p -> PredictionSummary.of(p, clock.instant()))
                .toList();
        List<Wager> userWagers = wagers.list("userId", user.id).stream()
                .sorted((a, b) -> b.placedAt.compareTo(a.placedAt))
                .toList();
        return UserProfile.of(user, createdPredictions, userWagers);
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
        User user = authenticatedUser();
        return transactionsRepo.findByUser(user.id);
    }

    private User authenticatedUser() {
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
