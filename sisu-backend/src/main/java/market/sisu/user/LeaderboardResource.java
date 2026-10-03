package market.sisu.user;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * {@code /api/leaderboard}: public leaderboard ranking users by sisu balance (User Story 6; FR-029).
 */
@Path("/leaderboard")
@Tag(name = "leaderboard")
public class LeaderboardResource {

    private final UserRepository users;

    LeaderboardResource(UserRepository users) {
        this.users = users;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Public leaderboard ranked by balance (FR-029)")
    @APIResponse(
            responseCode = "200",
            description = "OK",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(type = SchemaType.ARRAY, implementation = LeaderboardEntry.class)))
    public List<LeaderboardEntry> getLeaderboard() {
        List<User> liveUsers = users.mongoCollection()
                .find(Filters.eq("deletedAt", null))
                .sort(Sorts.orderBy(
                        Sorts.descending("balanceSisu"),
                        Sorts.ascending("createdAt"),
                        Sorts.ascending("_id")))
                .into(new ArrayList<>());

        List<LeaderboardEntry> entries = new ArrayList<>();
        int rank = 1;
        long lastBalance = -1;
        for (int i = 0; i < liveUsers.size(); i++) {
            User user = liveUsers.get(i);
            if (i > 0 && user.balanceSisu != lastBalance) {
                rank = i + 1;
            }
            entries.add(new LeaderboardEntry(rank, user.username, user.balanceSisu));
            lastBalance = user.balanceSisu;
        }
        return entries;
    }
}
