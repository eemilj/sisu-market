package market.sisu.contract;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import market.sisu.prediction.Prediction;
import market.sisu.prediction.PredictionStatus;
import market.sisu.user.User;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

/**
 * {@code GET /api/predictions/{id}} against contracts/openapi.yaml: the public detail view with full
 * description, resolution criteria, creator and current odds (User Story 2, scenario 2; FR-015,
 * FR-017).
 */
@QuarkusTest
class PredictionDetailContractTest {

    @Inject
    PredictionFixtures fixtures;

    @Test
    void detailIsPublicAndShowsDescriptionCriteriaCreatorAndOdds() {
        User creator = fixtures.creator();
        Prediction prediction = fixtures.prediction(creator, PredictionStatus.OPEN, 48, 250, 750);

        given().when()
                .get("/api/predictions/{id}", prediction.id.toHexString())
                .then()
                .statusCode(200)
                .body("id", equalTo(prediction.id.toHexString()))
                .body("question", equalTo(prediction.question))
                .body("description", equalTo(prediction.description))
                .body("resolutionCriteria", equalTo(prediction.resolutionCriteria))
                .body("creatorUsername", equalTo(creator.username))
                .body("status", equalTo("OPEN"))
                .body("closingAt", equalTo(prediction.closingAt.toString()))
                .body("totalSisuWagered", equalTo(1000))
                .body("oddsYesPercent", equalTo(25.0f))
                .body("oddsNoPercent", equalTo(75.0f))
                .body("winningOutcome", nullValue());
    }

    @Test
    void resolvedPredictionShowsTheWinningOutcome() {
        Prediction resolved = fixtures.prediction(fixtures.creator(), PredictionStatus.RESOLVED, -3, 1, 2);

        given().when()
                .get("/api/predictions/{id}", resolved.id.toHexString())
                .then()
                .statusCode(200)
                .body("status", equalTo("RESOLVED"))
                .body("winningOutcome", equalTo("YES"));
    }

    @Test
    void unknownPredictionIsNotFound() {
        given().when()
                .get("/api/predictions/{id}", new ObjectId().toHexString())
                .then()
                .statusCode(404)
                .body("code", equalTo("PREDICTION_NOT_FOUND"));
    }

    @Test
    void malformedIdIsNotFound() {
        given().when()
                .get("/api/predictions/{id}", "not-an-object-id")
                .then()
                .statusCode(404)
                .body("code", equalTo("PREDICTION_NOT_FOUND"));
    }
}
