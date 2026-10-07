package market.sisu.contract;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import market.sisu.prediction.Prediction;
import market.sisu.prediction.PredictionStatus;
import market.sisu.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code POST /api/predictions/{id}/wagers} against contracts/openapi.yaml: a logged-in user wagers
 * on an open prediction and their balance and its odds move together; wagers over the balance, on a
 * prediction that no longer accepts them, or of an invalid amount are rejected with nothing changed
 * (User Story 3; FR-006, FR-019 through FR-023, FR-025).
 */
@QuarkusTest
class WagerContractTest {

    @Inject
    PredictionFixtures fixtures;

    private User creator;
    private String session;

    @BeforeEach
    void registerBettor() {
        creator = fixtures.creator();
        session = given().contentType("application/json")
                .body(credentials("pelaaja-" + UUID.randomUUID()))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(201)
                .body("balanceSisu", equalTo(1000))
                .extract()
                .cookie("sisu_session");
    }

    @Test
    void recordsTheWagerAndMovesBalanceAndOddsTogether() {
        Prediction prediction = fixtures.prediction(creator, PredictionStatus.OPEN, 24, 600, 400);

        wager(prediction, "NO", "400")
                .statusCode(201)
                .body("id", notNullValue())
                .body("predictionId", equalTo(prediction.id.toHexString()))
                .body("predictionQuestion", equalTo(prediction.question))
                .body("outcome", equalTo("NO"))
                .body("amountSisu", equalTo(400))
                .body("status", equalTo("OPEN"))
                // 600 yes / 800 no after the wager: 400 of the 800 would win half of the 600.
                .body("potentialPayoutSisu", equalTo(700))
                .body("placedAt", notNullValue());

        assertBalance(600);
        given().when()
                .get("/api/predictions/{id}", prediction.id.toHexString())
                .then()
                .statusCode(200)
                .body("totalSisuWagered", equalTo(1400))
                .body("oddsYesPercent", equalTo(42.9f))
                .body("oddsNoPercent", equalTo(57.1f));
    }

    @Test
    void theWholeBalanceCanBeWagered() {
        Prediction prediction = fixtures.prediction(creator, PredictionStatus.OPEN, 24, 0, 0);

        wager(prediction, "YES", "1000").statusCode(201).body("potentialPayoutSisu", equalTo(1000));

        assertBalance(0);
    }

    @Test
    void rejectsAWagerOverTheBalanceAndChangesNothing() {
        Prediction prediction = fixtures.prediction(creator, PredictionStatus.OPEN, 24, 0, 0);

        wager(prediction, "YES", "1001").statusCode(422).body("code", equalTo("INSUFFICIENT_BALANCE"));

        assertBalance(1000);
        assertTotalWagered(prediction, 0);
        myWagers().body("size()", equalTo(0));
    }

    @Test
    void rejectsAWagerOnAClosedResolvedOrCancelledPrediction() {
        for (PredictionStatus status :
                List.of(PredictionStatus.CLOSED, PredictionStatus.RESOLVED, PredictionStatus.CANCELLED)) {
            Prediction prediction = fixtures.prediction(creator, status, -1, 10, 10);

            wager(prediction, "YES", "10").statusCode(409).body("code", equalTo("PREDICTION_NOT_OPEN"));

            assertTotalWagered(prediction, 20);
        }
        assertBalance(1000);
    }

    @Test
    void rejectsAWagerAfterClosingTimeEvenBeforeTheStatusIsUpdated() {
        // Stored status is still OPEN, but closingAt passed an hour ago (FR-025).
        Prediction prediction = fixtures.prediction(creator, PredictionStatus.OPEN, -1, 0, 0);

        wager(prediction, "YES", "10").statusCode(409).body("code", equalTo("PREDICTION_NOT_OPEN"));

        assertBalance(1000);
        assertTotalWagered(prediction, 0);
    }

    @Test
    void rejectsAmountsThatAreNotPositiveWholeNumbers() {
        Prediction prediction = fixtures.prediction(creator, PredictionStatus.OPEN, 24, 0, 0);

        for (String amount : List.of("0", "-5", "1.5", "null")) {
            wager(prediction, "YES", amount).statusCode(422).body("code", equalTo("INVALID_WAGER_AMOUNT"));
        }
        assertBalance(1000);
        assertTotalWagered(prediction, 0);
    }

    @Test
    void requiresAnOutcome() {
        Prediction prediction = fixtures.prediction(creator, PredictionStatus.OPEN, 24, 0, 0);

        given().cookie("sisu_session", session)
                .contentType("application/json")
                .body("""
                        {"amountSisu":10}""")
                .when()
                .post("/api/predictions/{id}/wagers", prediction.id.toHexString())
                .then()
                .statusCode(422)
                .body("code", equalTo("VALIDATION_FAILED"));
    }

    @Test
    void rejectsAnUnknownPrediction() {
        given().cookie("sisu_session", session)
                .contentType("application/json")
                .body(body("YES", "10"))
                .when()
                .post("/api/predictions/{id}/wagers", "665f1c2e0000000000000000")
                .then()
                .statusCode(404)
                .body("code", equalTo("PREDICTION_NOT_FOUND"));
    }

    @Test
    void requiresLogin() {
        Prediction prediction = fixtures.prediction(creator, PredictionStatus.OPEN, 24, 0, 0);

        given().contentType("application/json")
                .body(body("YES", "10"))
                .when()
                .post("/api/predictions/{id}/wagers", prediction.id.toHexString())
                .then()
                .statusCode(401)
                .body("code", equalTo("UNAUTHENTICATED"));
        assertTotalWagered(prediction, 0);
    }

    private ValidatableResponse wager(Prediction prediction, String outcome, String amount) {
        return given().cookie("sisu_session", session)
                .contentType("application/json")
                .body(body(outcome, amount))
                .when()
                .post("/api/predictions/{id}/wagers", prediction.id.toHexString())
                .then();
    }

    private ValidatableResponse myWagers() {
        return given().cookie("sisu_session", session)
                .when()
                .get("/api/users/me/wagers")
                .then()
                .statusCode(200);
    }

    private void assertBalance(long expected) {
        given().cookie("sisu_session", session)
                .when()
                .get("/api/users/me")
                .then()
                .statusCode(200)
                .body("balanceSisu", equalTo((int) expected));
    }

    private static void assertTotalWagered(Prediction prediction, long expected) {
        given().when()
                .get("/api/predictions/{id}", prediction.id.toHexString())
                .then()
                .statusCode(200)
                .body("totalSisuWagered", equalTo((int) expected));
    }

    /** {@code amount} is raw JSON, so non-integers and {@code null} can be sent as such. */
    private static String body(String outcome, String amount) {
        return """
                {"outcome":"%s","amountSisu":%s}""".formatted(outcome, amount);
    }

    private static String credentials(String username) {
        return """
                {"username":"%s","password":"correct horse battery staple"}""".formatted(username);
    }
}
