package fi.sisu.vibe.api;

import fi.sisu.vibe.api.Views.LeaderboardEntry;
import fi.sisu.vibe.api.Views.Profile;
import fi.sisu.vibe.api.Views.TxView;
import fi.sisu.vibe.api.Views.UserView;
import fi.sisu.vibe.service.UserService;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.util.List;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/")
public class UserResource {

    private final UserService users;
    private final JsonWebToken jwt;

    public UserResource(UserService users, JsonWebToken jwt) {
        this.users = users;
        this.jwt = jwt;
    }

    @GET
    @Path("me")
    @RolesAllowed("USER")
    public UserView me() {
        return users.me(jwt.getSubject());
    }

    @GET
    @Path("me/transactions")
    @RolesAllowed("USER")
    public List<TxView> transactions() {
        return users.transactions(jwt.getSubject());
    }

    @GET
    @Path("users/{username}")
    @PermitAll
    public Profile profile(@PathParam("username") String username) {
        return users.profile(username);
    }

    @GET
    @Path("leaderboard")
    @PermitAll
    public List<LeaderboardEntry> leaderboard() {
        return users.leaderboard(50);
    }
}
