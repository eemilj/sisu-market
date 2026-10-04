package market.sisu.contract;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import market.sisu.user.LeaderboardEntry;
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

        List<LeaderboardEntry> entries = given().when()
                .get("/api/leaderboard")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList(".", LeaderboardEntry.class);

        int idxB = -1;
        int idxC = -1;
        int idxA = -1;

        for (int i = 0; i < entries.size(); i++) {
            LeaderboardEntry entry = entries.get(i);
            if (entry.username().equals(userB.username)) {
                idxB = i;
            } else if (entry.username().equals(userC.username)) {
                idxC = i;
            } else if (entry.username().equals(userA.username)) {
                idxA = i;
            }
        }

        assertTrue(idxB >= 0, "userB should be in the leaderboard");
        assertTrue(idxC >= 0, "userC should be in the leaderboard");
        assertTrue(idxA >= 0, "userA should be in the leaderboard");

        // Verify relative order by balance: userB (2000) before userC (1000) before userA (500)
        assertTrue(idxB < idxC, "User with higher balance (2000) should appear before user with 1000");
        assertTrue(idxC < idxA, "User with balance 1000 should appear before user with 500");

        // Verify balances
        assertEquals(2000, entries.get(idxB).balanceSisu());
        assertEquals(1000, entries.get(idxC).balanceSisu());
        assertEquals(500, entries.get(idxA).balanceSisu());

        // Verify complete leaderboard is sorted descending by balanceSisu
        for (int i = 0; i < entries.size() - 1; i++) {
            long currentBalance = entries.get(i).balanceSisu();
            long nextBalance = entries.get(i + 1).balanceSisu();
            assertTrue(
                    currentBalance >= nextBalance,
                    "Leaderboard must be sorted by balanceSisu descending (index " + i + " vs " + (i + 1) + ")");
        }

        // Verify leaderboard rank semantics across the complete returned response
        int expectedRank = 1;
        long lastBalance = -1;
        for (int i = 0; i < entries.size(); i++) {
            LeaderboardEntry entry = entries.get(i);
            if (i > 0 && entry.balanceSisu() != lastBalance) {
                expectedRank = i + 1;
            }
            assertEquals(
                    expectedRank,
                    entry.rank(),
                    "Rank at index " + i + " (" + entry.username() + ") must match ranking algorithm");
            lastBalance = entry.balanceSisu();
        }
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
