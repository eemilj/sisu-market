package market.sisu.contract;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.UUID;
import market.sisu.user.Role;
import market.sisu.user.User;
import market.sisu.user.UserRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

/**
 * {@code GET /api/leaderboard} against contracts/openapi.yaml: public leaderboard ranking users by
 * sisu balance (User Story 6; FR-029).
 */
@QuarkusTest
class LeaderboardContractTest {

    @Inject
    UserRepository users;

    @Test
    void leaderboardIsPublicAndRanksUsersByBalance() {
        User userA = createUser("alpha-" + UUID.randomUUID(), 500);
        User userB = createUser("beta-" + UUID.randomUUID(), 2000);
        User userC = createUser("gamma-" + UUID.randomUUID(), 1000);

        given().when()
                .get("/api/leaderboard")
                .then()
                .statusCode(200)
                .body("find { it.username == '%s' }.rank".formatted(userB.username), equalTo(1))
                .body("find { it.username == '%s' }.balanceSisu".formatted(userB.username), equalTo(2000))
                .body("find { it.username == '%s' }.rank".formatted(userC.username), equalTo(2))
                .body("find { it.username == '%s' }.balanceSisu".formatted(userC.username), equalTo(1000))
                .body("find { it.username == '%s' }.rank".formatted(userA.username), equalTo(3))
                .body("find { it.username == '%s' }.balanceSisu".formatted(userA.username), equalTo(500));
    }

    private User createUser(String username, long balanceSisu) {
        User user = new User();
        user.id = new ObjectId();
        user.username = username;
        user.passwordHash = "not-a-real-hash";
        user.role = Role.USER;
        user.balanceSisu = balanceSisu;
        user.createdAt = Instant.now();
        users.persist(user);
        return user;
    }
}
