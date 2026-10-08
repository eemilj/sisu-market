package fi.sisu.market.profile;

import fi.sisu.market.common.ApiException;
import fi.sisu.market.prediction.PredictionDto;
import fi.sisu.market.prediction.PredictionRepository;
import fi.sisu.market.user.AdminPolicy;
import fi.sisu.market.user.User;
import fi.sisu.market.user.UserRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.Instant;
import java.util.List;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class ProfileResource {

    private static final int MAX_LEADERBOARD = 100;

    @Inject
    UserRepository users;

    @Inject
    PredictionRepository predictions;

    @Inject
    AdminPolicy adminPolicy;

    @Inject
    JsonWebToken jwt;

    @GET
    @Path("/profiles/{username}")
    public ProfileDto profile(@PathParam("username") String username) {
        User user = findUser(username);
        return new ProfileDto(user.username, user.balance, users.rankOf(user.balance), user.createdAt,
                (int) predictions.count("createdById", user.id), adminPolicy.isAdmin(user));
    }

    @GET
    @Path("/profiles/{username}/predictions")
    public List<PredictionDto> predictionsCreatedBy(@PathParam("username") String username) {
        User user = findUser(username);
        Instant now = Instant.now();
        return predictions.createdBy(user.id).stream().map(p -> PredictionDto.from(p, now)).toList();
    }

    /** Public. When called with a token, also returns the caller's own row if they are outside the top {@code limit}. */
    @GET
    @Path("/leaderboard")
    public LeaderboardDto leaderboard(@QueryParam("limit") @DefaultValue("25") int limit) {
        if (limit < 1 || limit > MAX_LEADERBOARD) {
            throw ApiException.badRequest("limit must be between 1 and " + MAX_LEADERBOARD);
        }
        List<User> top = users.topByBalance(limit);
        List<LeaderboardDto.Entry> entries = new java.util.ArrayList<>(top.size());
        for (int i = 0; i < top.size(); i++) {
            User u = top.get(i);
            // The list is sorted by balance, so a tie shares the previous rank; otherwise rank = position.
            long rank = i > 0 && u.balance == top.get(i - 1).balance ? entries.get(i - 1).rank() : i + 1;
            entries.add(new LeaderboardDto.Entry(rank, u.username, u.balance));
        }

        LeaderboardDto.Entry me = null;
        String subject = jwt.getSubject();
        if (subject != null && ObjectId.isValid(subject)) {
            User caller = users.findById(new ObjectId(subject));
            if (caller != null && top.stream().noneMatch(u -> u.id.equals(caller.id))) {
                me = new LeaderboardDto.Entry(users.rankOf(caller.balance), caller.username, caller.balance);
            }
        }
        return new LeaderboardDto(entries, me);
    }

    private User findUser(String username) {
        return users.findByUsername(username).orElseThrow(() -> ApiException.notFound("User not found"));
    }
}
