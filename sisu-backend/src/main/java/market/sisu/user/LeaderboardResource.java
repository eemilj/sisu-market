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
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

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
    @APIResponse(responseCode = "200", description = "OK")
    public List<LeaderboardEntry> leaderboard() {
        List<User> list = users.mongoCollection()
                .find(Filters.eq("deletedAt", null))
                .sort(Sorts.orderBy(Sorts.descending("balanceSisu"), Sorts.ascending("createdAt")))
                .into(new ArrayList<>());
        List<LeaderboardEntry> entries = new ArrayList<>();
        int rank = 1;
        for (User u : list) {
            entries.add(new LeaderboardEntry(rank++, u.username, u.balanceSisu));
        }
        return entries;
    }

    public record LeaderboardEntry(int rank, String username, long balanceSisu) {}
}
