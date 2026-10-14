package fi.sisu.market;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class AdminResourceTest {

    /** Configured as admin via %test.sisu.admin-usernames. */
    private static final String ADMIN = "sisu_admin";

    private static String token(String name) {
        var r = given().contentType(ContentType.JSON)
                .body(Map.of("username", name, "email", name + "@example.fi", "password", "salasana123"))
                .post("/api/auth/register").then().extract();
        if (r.statusCode() == 409) { // registered by an earlier test
            r = given().contentType(ContentType.JSON).body(Map.of("username", name, "password", "salasana123"))
                    .post("/api/auth/login").then().statusCode(200).extract();
        }
        return r.path("token");
    }

    @Test
    void anonymousIsRejected() {
        given().get("/api/admin/predictions").then().statusCode(401);
        given().get("/api/admin/users").then().statusCode(401);
    }

    @Test
    void regularUserIsForbidden() {
        String t = token("a" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        given().auth().oauth2(t).get("/api/admin/predictions").then().statusCode(403).body("code", equalTo("ADMIN_ONLY"));
        given().auth().oauth2(t).get("/api/admin/users").then().statusCode(403).body("code", equalTo("ADMIN_ONLY"));
    }

    @Test
    void adminSeesPredictionsAndUsersWithoutPasswordHashes() {
        String t = token(ADMIN);
        given().auth().oauth2(t).get("/api/admin/predictions").then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(0));
        given().auth().oauth2(t).get("/api/admin/users").then().statusCode(200)
                .body("username", hasItem(ADMIN))
                .body("[0]", not(hasKey("passwordHash")));
    }

    @Test
    void userLimitIsValidated() {
        String t = token(ADMIN);
        given().auth().oauth2(t).queryParam("limit", 0).get("/api/admin/users").then().statusCode(400);
        given().auth().oauth2(t).queryParam("limit", 1).get("/api/admin/users").then().statusCode(200).body("$", hasSize(1));
    }

    private static String createPrediction(String token) {
        return given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of("question", "Will admin close this? " + UUID.randomUUID(), "description", "Close test",
                        "resolutionCriteria", "Closed by an admin.", "category", "SPORTS",
                        "closesAt", java.time.Instant.now().plus(java.time.Duration.ofDays(3)).toString()))
                .post("/api/predictions").then().statusCode(201).extract().path("id");
    }

    @Test
    void adminClosesOpenPredictionAndWageringStops() {
        String admin = token(ADMIN);
        String id = createPrediction(admin);
        String other = token("c" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));

        given().auth().oauth2(admin).post("/api/admin/predictions/" + id + "/close").then().statusCode(200)
                .body("status", equalTo("CLOSED"));
        // a second close, and any wager, are now refused
        given().auth().oauth2(admin).post("/api/admin/predictions/" + id + "/close").then().statusCode(409);
        given().auth().oauth2(other).contentType(ContentType.JSON).body(Map.of("side", "YES", "amount", 10))
                .post("/api/predictions/" + id + "/wagers").then().statusCode(409).body("code", equalTo("PREDICTION_CLOSED"));
        // and it can be resolved straight away
        given().auth().oauth2(admin).contentType(ContentType.JSON).body(Map.of("outcome", "YES"))
                .post("/api/predictions/" + id + "/resolve").then().statusCode(200);
    }

    @Test
    void closeIsAdminOnlyAndValidatesId() {
        String admin = token(ADMIN);
        String id = createPrediction(admin);
        String other = token("d" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        given().post("/api/admin/predictions/" + id + "/close").then().statusCode(401);
        given().auth().oauth2(other).post("/api/admin/predictions/" + id + "/close").then().statusCode(403);
        given().auth().oauth2(admin).post("/api/admin/predictions/not-an-id/close").then().statusCode(404);
    }
}
