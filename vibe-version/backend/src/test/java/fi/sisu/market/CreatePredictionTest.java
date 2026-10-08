package fi.sisu.market;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class CreatePredictionTest {

    private String username;
    private String token;

    @BeforeEach
    void registerUser() {
        username = "c" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        token = given().contentType(ContentType.JSON)
                .body(Map.of("username", username, "email", username + "@example.fi", "password", "salasana123"))
                .post("/api/auth/register")
                .then().statusCode(201)
                .extract().path("token");
    }

    private static Map<String, Object> validBody(String question) {
        Map<String, Object> body = new HashMap<>();
        body.put("question", question);
        body.put("description", "Tampere vs Helsinki, a classic rivalry.");
        body.put("resolutionCriteria", "YES if Ilves wins the next Liiga derby against HIFK, per liiga.fi.");
        body.put("category", "SPORTS");
        body.put("closesAt", Instant.now().plus(Duration.ofDays(10)).toString());
        return body;
    }

    private io.restassured.response.ValidatableResponse create(Map<String, Object> body) {
        return given().auth().oauth2(token).contentType(ContentType.JSON).body(body)
                .when().post("/api/predictions")
                .then();
    }

    @Test
    void createdPredictionAppearsInOpenList() {
        String question = "Will Ilves beat HIFK in the next derby? " + UUID.randomUUID();
        String id = create(validBody(question))
                .statusCode(201)
                .body("id", notNullValue())
                .body("question", equalTo(question))
                .body("status", equalTo("OPEN"))
                .body("createdBy", equalTo(username))
                .body("yesPercent", equalTo(50))
                .body("volume", equalTo(0))
                .extract().path("id");

        given().queryParam("status", "OPEN").when().get("/api/predictions")
                .then().statusCode(200).body("id", hasItem(id));
        given().when().get("/api/predictions/" + id)
                .then().statusCode(200)
                .body("resolutionCriteria", containsString("Ilves wins"));
    }

    @Test
    void requiresLogin() {
        given().contentType(ContentType.JSON).body(validBody("Will this be rejected without a token?"))
                .when().post("/api/predictions")
                .then().statusCode(401);
    }

    @Test
    void rejectsPastClosingDate() {
        Map<String, Object> body = validBody("Did this already happen yesterday?");
        body.put("closesAt", Instant.now().minus(Duration.ofDays(1)).toString());
        create(body).statusCode(400).body("message", containsString("at least 1 hour in the future"));
    }

    @Test
    void rejectsClosingDateTooSoon() {
        Map<String, Object> body = validBody("Will this close in ten minutes?");
        body.put("closesAt", Instant.now().plus(Duration.ofMinutes(10)).toString());
        create(body).statusCode(400).body("message", containsString("at least 1 hour in the future"));
    }

    @Test
    void rejectsClosingDateTooFar() {
        Map<String, Object> body = validBody("Will this matter in the year 2040?");
        body.put("closesAt", Instant.now().plus(Duration.ofDays(6 * 365)).toString());
        create(body).statusCode(400).body("message", containsString("within 5 years"));
    }

    @Test
    void rejectsMalformedOrZonelessDates() {
        for (String bad : new String[] {"next friday", "2027-02-30T12:00:00Z", "2027-01-01T12:00:00"}) {
            Map<String, Object> body = validBody("Will this date parse correctly?");
            body.put("closesAt", bad);
            create(body).statusCode(400).body("message", containsString("ISO-8601"));
        }
    }

    @Test
    void acceptsOffsetDates() {
        Map<String, Object> body = validBody("Will a Helsinki-time closing date work?");
        body.put("closesAt", "2027-06-01T18:00:00+03:00");
        create(body).statusCode(201).body("closesAt", equalTo("2027-06-01T15:00:00Z"));
    }

    @Test
    void rejectsMissingFields() {
        create(Map.of("question", "   ", "category", "SPORTS"))
                .statusCode(400)
                .body("message", containsString("Question is required"))
                .body("message", containsString("Resolution criteria are required"))
                .body("message", containsString("Closing date is required"));
    }

    @Test
    void rejectsUnknownCategory() {
        Map<String, Object> body = validBody("Will an unknown category be rejected?");
        body.put("category", "ASTROLOGY");
        create(body).statusCode(400).body("message", equalTo("Malformed request body"));
    }

    @Test
    void rejectsBrokenJson() {
        given().auth().oauth2(token).contentType(ContentType.JSON).body("{\"question\": ")
                .when().post("/api/predictions")
                .then().statusCode(400).body("message", equalTo("Malformed request body"));
    }
}
