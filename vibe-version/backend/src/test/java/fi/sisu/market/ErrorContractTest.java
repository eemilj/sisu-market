package fi.sisu.market;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.notNullValue;

/** The machine-readable part of error responses that the (Finnish) UI builds its messages from. */
@QuarkusTest
class ErrorContractTest {

    private static String[] register() {
        String name = "e" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var r = given().contentType(ContentType.JSON)
                .body(Map.of("username", name, "email", name + "@example.fi", "password", "salasana123"))
                .post("/api/auth/register").then().statusCode(201).extract();
        return new String[] {name, r.path("token")};
    }

    @Test
    void validationErrorsListEachFieldWithRuleAndLimits() {
        given().contentType(ContentType.JSON)
                .body(Map.of("username", "a!", "email", "nope", "password", "short"))
                .post("/api/auth/register")
                .then().statusCode(400)
                .body("code", equalTo("VALIDATION_FAILED"))
                .body("fieldErrors.field", hasItems("username", "email", "password"))
                .body("fieldErrors.find { it.field == 'password' }.rule", equalTo("Size"))
                .body("fieldErrors.find { it.field == 'password' }.params.min", equalTo(8))
                .body("fieldErrors.find { it.field == 'password' }.params.max", equalTo(72))
                .body("fieldErrors.find { it.field == 'email' }.rule", equalTo("Email"))
                .body("fieldErrors.find { it.field == 'username' }.rule", equalTo("Pattern"));
    }

    @Test
    void businessErrorsCarryCodesAndParams() {
        String[] user = register();
        given().contentType(ContentType.JSON).body(Map.of("username", user[0], "password", "wrong-password"))
                .post("/api/auth/login").then().statusCode(401).body("code", equalTo("INVALID_CREDENTIALS"));

        given().contentType(ContentType.JSON)
                .body(Map.of("username", user[0], "email", "x" + user[0] + "@example.fi", "password", "salasana123"))
                .post("/api/auth/register").then().statusCode(409).body("code", equalTo("USERNAME_TAKEN"));

        given().auth().oauth2(user[1]).contentType(ContentType.JSON)
                .body(Map.of("question", "Will this close too soon?", "description", "d",
                        "resolutionCriteria", "Resolves on the closing check.", "category", "SPORTS",
                        "closesAt", Instant.now().plus(Duration.ofMinutes(5)).toString()))
                .post("/api/predictions").then().statusCode(400)
                .body("code", equalTo("CLOSING_TOO_SOON"))
                .body("params.earliest", notNullValue());

        String id = given().get("/api/predictions?status=OPEN").then().extract().path("[0].id");
        given().auth().oauth2(user[1]).contentType(ContentType.JSON).body(Map.of("side", "YES", "amount", 5000))
                .post("/api/predictions/" + id + "/wagers").then().statusCode(409)
                .body("code", equalTo("INSUFFICIENT_BALANCE"))
                .body("params.balance", equalTo(1000))
                .body("params.required", equalTo(5000));

        given().auth().oauth2(user[1]).contentType(ContentType.JSON).body("{\"side\": ")
                .post("/api/predictions/" + id + "/wagers").then().statusCode(400).body("code", equalTo("MALFORMED_BODY"));
        given().get("/api/predictions/" + UUID.randomUUID()).then().statusCode(404).body("code", equalTo("PREDICTION_NOT_FOUND"));
    }

    @Test
    void transactionsCarryThePredictionQuestion() {
        String[] user = register();
        var p = given().get("/api/predictions?status=OPEN").then().extract();
        String id = p.path("[0].id");
        String question = p.path("[0].question");
        given().auth().oauth2(user[1]).contentType(ContentType.JSON).body(Map.of("side", "NO", "amount", 10))
                .post("/api/predictions/" + id + "/wagers").then().statusCode(201);
        given().auth().oauth2(user[1]).get("/api/users/me/transactions").then()
                .body("items[0].type", equalTo("WAGER"))
                .body("items[0].predictionQuestion", equalTo(question));
    }

    @Test
    void seededPredictionsAreFinnish() {
        given().get("/api/predictions").then()
                .body("question", hasItems("Voittaako Suomi kultaa jääkiekon MM-kisoissa 2027?"));
    }
}
