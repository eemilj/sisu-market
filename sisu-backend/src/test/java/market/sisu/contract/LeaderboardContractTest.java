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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code GET /api/leaderboard} against contracts/openapi.yaml: public leaderboard ranked by balance (FR-029).
 */
@QuarkusTest
class LeaderboardContractTest {

    @Inject
    UserRepository users;

    @BeforeEach
    void setUp() {
        users.deleteAll();
    }

    @Test
    void leaderboardReturnsRankedUsersByBalance() {
        User u1 = new User();
        u1.id = new ObjectId();
        u1.username = "user1-" + UUID.randomUUID();
        u1.passwordHash = "hash";
        u1.balanceSisu = 1500;
        u1.role = Role.USER;
        u1.createdAt = Instant.now();
        users.persist(u1);

        User u2 = new User();
        u2.id = new ObjectId();
        u2.username = "user2-" + UUID.randomUUID();
        u2.passwordHash = "hash";
        u2.balanceSisu = 500;
        u2.role = Role.USER;
        u2.createdAt = Instant.now();
        users.persist(u2);

        given().when()
                .get("/api/leaderboard")
                .then()
                .statusCode(200)
                .body("find { it.username == '%s' }.rank".formatted(u1.username), equalTo(1))
                .body("find { it.username == '%s' }.balanceSisu".formatted(u1.username), equalTo(1500))
                .body("find { it.username == '%s' }.rank".formatted(u2.username), equalTo(2))
                .body("find { it.username == '%s' }.balanceSisu".formatted(u2.username), equalTo(500));
    }
}
