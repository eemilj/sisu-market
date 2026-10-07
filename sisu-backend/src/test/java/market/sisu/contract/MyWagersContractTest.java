package market.sisu.contract;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.UUID;
import market.sisu.prediction.Outcome;
import market.sisu.prediction.Prediction;
import market.sisu.prediction.PredictionStatus;
import market.sisu.user.User;
import market.sisu.wager.Wager;
import market.sisu.wager.WagerRepository;
import market.sisu.wager.WagerStatus;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code GET /api/users/me/wagers} against contracts/openapi.yaml: a logged-in user sees only their
 * own open wagers, each with amount, chosen outcome and a potential payout that follows the current
 * pools (User Story 3, scenario 4; FR-024, FR-034).
 */
@QuarkusTest
class MyWagersContractTest {

    @Inject
    PredictionFixtures fixtures;

    @Inject
    WagerRepository wagers;

    private User creator;
    private String session;
    private String userId;

    @BeforeEach
    void registerBettor() {
        creator = fixtures.creator();
        var registered = register();
        session = registered.cookie("sisu_session");
        userId = registered.path("id");
    }

    @Test
    void listsOwnOpenWagersNewestFirstWithAmountOutcomeAndPotentialPayout() {
        Prediction first = fixtures.prediction(creator, PredictionStatus.OPEN, 24, 0, 0);
        Prediction second = fixtures.prediction(creator, PredictionStatus.OPEN, 48, 300, 0);
        wager(session, first, "YES", 100);
        wager(session, second, "NO", 200);

        given().cookie("sisu_session", session)
                .when()
                .get("/api/users/me/wagers")
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("[0].predictionId", equalTo(second.id.toHexString()))
                .body("[0].predictionQuestion", equalTo(second.question))
                .body("[0].outcome", equalTo("NO"))
                .body("[0].amountSisu", equalTo(200))
                .body("[0].status", equalTo("OPEN"))
                // The only "no" stake would take all 300 on "yes".
                .body("[0].potentialPayoutSisu", equalTo(500))
                .body("[0].placedAt", notNullValue())
                .body("[1].predictionId", equalTo(first.id.toHexString()))
                .body("[1].outcome", equalTo("YES"))
                .body("[1].amountSisu", equalTo(100))
                .body("[1].potentialPayoutSisu", equalTo(100));
    }

    @Test
    void potentialPayoutGrowsAsOthersWagerOnTheOtherSide() {
        Prediction prediction = fixtures.prediction(creator, PredictionStatus.OPEN, 24, 0, 0);
        wager(session, prediction, "YES", 100);
        String rival = register().cookie("sisu_session");
        wager(rival, prediction, "NO", 300);

        given().cookie("sisu_session", session)
                .when()
                .get("/api/users/me/wagers")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].potentialPayoutSisu", equalTo(400));
    }

    @Test
    void leavesOutOtherUsersWagersAndSettledWagers() {
        Prediction prediction = fixtures.prediction(creator, PredictionStatus.OPEN, 24, 0, 0);
        String rival = register().cookie("sisu_session");
        wager(rival, prediction, "NO", 300);
        settledWager(new ObjectId(userId), prediction);

        given().cookie("sisu_session", session)
                .when()
                .get("/api/users/me/wagers")
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
    void requiresLogin() {
        given().when().get("/api/users/me/wagers").then().statusCode(401).body("code", equalTo("UNAUTHENTICATED"));
    }

    private static ExtractableResponse<Response> register() {
        return given().contentType("application/json")
                .body(credentials("pelaaja-" + UUID.randomUUID()))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(201)
                .extract();
    }

    private static void wager(String session, Prediction prediction, String outcome, long amount) {
        given().cookie("sisu_session", session)
                .contentType("application/json")
                .body("""
                        {"outcome":"%s","amountSisu":%d}""".formatted(outcome, amount))
                .when()
                .post("/api/predictions/{id}/wagers", prediction.id.toHexString())
                .then()
                .statusCode(201);
    }

    /** A wager already settled by resolution (User Story 5), seeded directly. */
    private void settledWager(ObjectId owner, Prediction prediction) {
        Wager wager = new Wager();
        wager.id = new ObjectId();
        wager.userId = owner;
        wager.predictionId = prediction.id;
        wager.outcome = Outcome.YES;
        wager.amountSisu = 50;
        wager.status = WagerStatus.LOST;
        wager.placedAt = Instant.now();
        wager.settledAt = Instant.now();
        wagers.persist(wager);
    }

    private static String credentials(String username) {
        return """
                {"username":"%s","password":"correct horse battery staple"}""".formatted(username);
    }
}
