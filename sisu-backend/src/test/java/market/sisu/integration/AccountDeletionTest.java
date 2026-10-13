package market.sisu.integration;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.mongodb.client.model.Filters;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import market.sisu.contract.PredictionFixtures;
import market.sisu.prediction.Prediction;
import market.sisu.prediction.PredictionRepository;
import market.sisu.prediction.PredictionStatus;
import market.sisu.transaction.TransactionRepository;
import market.sisu.user.User;
import market.sisu.user.UserRepository;
import market.sisu.wager.Wager;
import market.sisu.wager.WagerRepository;
import market.sisu.wager.WagerStatus;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

/**
 * Account-deletion cascade against a real MongoDB (FR-033, FR-036): the user's own open wagers are
 * refunded, their unresolved predictions are cancelled with every wager on them refunded, personal
 * data is scrubbed, and every balance still equals its ledger sum (SC-006).
 */
@QuarkusTest
class AccountDeletionTest {

    private static final String PASSWORD = "correct horse battery staple";

    @Inject
    UserRepository users;

    @Inject
    PredictionRepository predictions;

    @Inject
    WagerRepository wagers;

    @Inject
    TransactionRepository transactions;

    @Inject
    PredictionFixtures fixtures;

    private String register(String username) {
        return given().contentType("application/json")
                .body("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, PASSWORD))
                .post("/api/auth/register")
                .then()
                .statusCode(201)
                .extract()
                .cookie("sisu_session");
    }

    private void wager(String session, Prediction prediction, String outcome, int amount) {
        given().cookie("sisu_session", session)
                .contentType("application/json")
                .body("{\"outcome\":\"%s\",\"amountSisu\":%d}".formatted(outcome, amount))
                .post("/api/predictions/{id}/wagers", prediction.id.toHexString())
                .then()
                .statusCode(201);
    }

    @Test
    void deletionRefundsOwnWagersCancelsOwnPredictionsAndScrubsPersonalData() {
        String aliceName = "alice-" + UUID.randomUUID();
        String bobName = "bob-" + UUID.randomUUID();
        String aliceSession = register(aliceName);
        String bobSession = register(bobName);
        User alice = users.findByUsername(aliceName).orElseThrow();
        User bob = users.findByUsername(bobName).orElseThrow();

        Prediction aliceOwn = fixtures.prediction(alice, PredictionStatus.OPEN, 24, 0, 0);
        Prediction other = fixtures.prediction(fixtures.creator(), PredictionStatus.OPEN, 24, 0, 0);
        wager(bobSession, aliceOwn, "YES", 300); // wager by someone else on alice's prediction
        wager(aliceSession, other, "NO", 200); // alice's own open wager elsewhere
        wager(bobSession, other, "YES", 100);

        given().cookie("sisu_session", aliceSession)
                .delete("/api/users/me")
                .then()
                .statusCode(204);

        // Alice: scrubbed, refunded, ledger consistent.
        User deleted = users.findById(alice.id);
        assertNotNull(deleted.deletedAt);
        assertNull(deleted.username);
        assertNull(deleted.passwordHash);
        assertEquals(deleted.balanceSisu, transactions.sumForUser(alice.id));

        // Bob: his wager on the cancelled prediction is refunded; his other wager stays open.
        User bobAfter = users.findById(bob.id);
        assertEquals(1000 - 100, bobAfter.balanceSisu);
        assertEquals(bobAfter.balanceSisu, transactions.sumForUser(bob.id));

        assertEquals(PredictionStatus.CANCELLED, predictions.findById(aliceOwn.id).status);
        List<Wager> onCancelled = wagers.mongoCollection()
                .find(Filters.eq("predictionId", aliceOwn.id))
                .into(new java.util.ArrayList<>());
        assertEquals(1, onCancelled.size());
        assertEquals(WagerStatus.REFUNDED, onCancelled.get(0).status);
        assertEquals(300, onCancelled.get(0).payoutSisu);

        // Alice's own wager elsewhere is refunded and removed from the pool; bob's remains.
        Prediction otherAfter = predictions.findById(other.id);
        assertEquals(PredictionStatus.OPEN, otherAfter.status);
        assertEquals(100, otherAfter.yesPoolSisu);
        assertEquals(0, otherAfter.noPoolSisu);
        List<Wager> aliceWagers =
                wagers.mongoCollection().find(Filters.eq("userId", alice.id)).into(new java.util.ArrayList<>());
        assertEquals(1, aliceWagers.size());
        for (Wager w : aliceWagers) {
            assertEquals(WagerStatus.REFUNDED, w.status);
        }
    }

    @Test
    void deletedAccountCannotLogInAndSessionIsRejected() {
        String name = "gone-" + UUID.randomUUID();
        String session = register(name);

        given().cookie("sisu_session", session).delete("/api/users/me").then().statusCode(204);

        given().contentType("application/json")
                .body("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(name, PASSWORD))
                .post("/api/auth/login")
                .then()
                .statusCode(401);
        given().cookie("sisu_session", session).get("/api/users/me").then().statusCode(401);
    }

    @Test
    void deletionRequiresLogin() {
        given().delete("/api/users/me").then().statusCode(401);
    }

    @Test
    void usernameCanBeReusedAfterDeletion() {
        String name = "reuse-" + UUID.randomUUID();
        String session = register(name);
        given().cookie("sisu_session", session).delete("/api/users/me").then().statusCode(204);

        register(name);
        ObjectId fresh = users.findByUsername(name).orElseThrow().id;
        assertNotNull(fresh);
    }
}
