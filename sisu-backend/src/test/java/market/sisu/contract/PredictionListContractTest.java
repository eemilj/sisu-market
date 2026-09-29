package market.sisu.contract;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import market.sisu.prediction.Prediction;
import market.sisu.prediction.PredictionStatus;
import market.sisu.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code GET /api/predictions} against contracts/openapi.yaml: public listing with question, odds,
 * total wagered and closing date, and the status filter (User Story 2, scenarios 1 and 3; FR-016,
 * FR-018).
 */
@QuarkusTest
class PredictionListContractTest {

    @Inject
    PredictionFixtures fixtures;

    private User creator;

    @BeforeEach
    void setUp() {
        creator = fixtures.creator();
    }

    @Test
    void listIsPublicAndShowsQuestionOddsTotalAndClosingDate() {
        Prediction open = fixtures.prediction(creator, PredictionStatus.OPEN, 24, 600, 400);

        given().when()
                .get("/api/predictions")
                .then()
                .statusCode(200)
                .body("find { it.id == '%s' }.question".formatted(open.id.toHexString()), equalTo(open.question))
                .body("find { it.id == '%s' }.status".formatted(open.id.toHexString()), equalTo("OPEN"))
                .body("find { it.id == '%s' }.totalSisuWagered".formatted(open.id.toHexString()), equalTo(1000))
                .body("find { it.id == '%s' }.oddsYesPercent".formatted(open.id.toHexString()), equalTo(60.0f))
                .body("find { it.id == '%s' }.oddsNoPercent".formatted(open.id.toHexString()), equalTo(40.0f))
                .body(
                        "find { it.id == '%s' }.closingAt".formatted(open.id.toHexString()),
                        equalTo(open.closingAt.toString()));
    }

    @Test
    void predictionWithoutWagersShowsEvenOdds() {
        Prediction fresh = fixtures.prediction(creator, PredictionStatus.OPEN, 24, 0, 0);

        given().when()
                .get("/api/predictions")
                .then()
                .statusCode(200)
                .body("find { it.id == '%s' }.totalSisuWagered".formatted(fresh.id.toHexString()), equalTo(0))
                .body("find { it.id == '%s' }.oddsYesPercent".formatted(fresh.id.toHexString()), equalTo(50.0f))
                .body("find { it.id == '%s' }.oddsNoPercent".formatted(fresh.id.toHexString()), equalTo(50.0f));
    }

    @Test
    void statusFilterReturnsOnlyMatchingPredictions() {
        Prediction open = fixtures.prediction(creator, PredictionStatus.OPEN, 24, 0, 0);
        Prediction closed = fixtures.prediction(creator, PredictionStatus.CLOSED, -1, 10, 0);
        Prediction resolved = fixtures.prediction(creator, PredictionStatus.RESOLVED, -2, 10, 5);
        Prediction cancelled = fixtures.prediction(creator, PredictionStatus.CANCELLED, 5, 0, 0);

        assertFilter("OPEN", open, closed, resolved, cancelled);
        assertFilter("CLOSED", closed, open, resolved, cancelled);
        assertFilter("RESOLVED", resolved, open, closed, cancelled);
        assertFilter("CANCELLED", cancelled, open, closed, resolved);

        given().when()
                .get("/api/predictions")
                .then()
                .statusCode(200)
                .body(
                        "id",
                        hasItems(
                                open.id.toHexString(),
                                closed.id.toHexString(),
                                resolved.id.toHexString(),
                                cancelled.id.toHexString()));
    }

    @Test
    void openPredictionPastItsClosingTimeIsListedAsClosed() {
        // The closing job may lag closingAt by up to a minute (FR-025); browsing must not.
        Prediction overdue = fixtures.prediction(creator, PredictionStatus.OPEN, -1, 0, 0);
        String id = overdue.id.toHexString();

        given().queryParam("status", "OPEN")
                .when()
                .get("/api/predictions")
                .then()
                .statusCode(200)
                .body("id", not(hasItem(id)));

        given().queryParam("status", "CLOSED")
                .when()
                .get("/api/predictions")
                .then()
                .statusCode(200)
                .body("find { it.id == '%s' }.status".formatted(id), equalTo("CLOSED"));
    }

    @Test
    void unknownStatusIsAValidationFailure() {
        given().queryParam("status", "MAYBE")
                .when()
                .get("/api/predictions")
                .then()
                .statusCode(422)
                .body("code", equalTo("VALIDATION_FAILED"));
    }

    private static void assertFilter(String status, Prediction expected, Prediction... excluded) {
        var response = given().queryParam("status", status)
                .when()
                .get("/api/predictions")
                .then()
                .statusCode(200)
                .body("status", everyItem(equalTo(status)))
                .body("id", hasItem(expected.id.toHexString()));
        for (Prediction other : excluded) {
            response.body("id", not(hasItem(other.id.toHexString())));
        }
    }
}
