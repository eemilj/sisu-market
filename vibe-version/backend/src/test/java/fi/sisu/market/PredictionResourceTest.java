package fi.sisu.market;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class PredictionResourceTest {

    @Test
    void listsSeededPredictionsWithoutLogin() {
        given().when().get("/api/predictions")
                .then().statusCode(200)
                .body("size()", greaterThan(0))
                .body("[0].yesPercent", greaterThan(0));
    }

    @Test
    void filtersByCategory() {
        given().queryParam("category", "sports")
                .when().get("/api/predictions")
                .then().statusCode(200)
                .body("category", everyItem(equalTo("SPORTS")));
    }

    @Test
    void unknownFilterIsBadRequest() {
        given().queryParam("status", "maybe").when().get("/api/predictions").then().statusCode(400);
    }

    @Test
    void getById() {
        String id = given().get("/api/predictions").then().extract().path("[0].id");
        given().when().get("/api/predictions/" + id)
                .then().statusCode(200).body("id", equalTo(id));
        given().when().get("/api/predictions/not-an-id").then().statusCode(404);
    }
}
