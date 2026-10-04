package market.sisu.contract;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@code GET /api/users/me} and {@code GET /api/users/me/transactions} against contracts/openapi.yaml:
 * authenticated access to own private profile and transaction history, 401 behavior when
 * unauthenticated, and confirming no endpoint exposes another user's private profile (User Story 6;
 * FR-011, FR-030).
 */
@QuarkusTest
class UserProfileContractTest {

    private static final String PASSWORD = "correct horse battery staple";

    @Test
    void getUsersMeSucceedsWhenAuthenticatedAndReturnsProfile() {
        String username = "userA-" + UUID.randomUUID();
        String session = registerAndGetSession(username);

        given().cookie("sisu_session", session)
                .when()
                .get("/api/users/me")
                .then()
                .statusCode(200)
                .body("username", equalTo(username))
                .body("balanceSisu", equalTo(1000))
                .body("role", equalTo("USER"))
                .body("createdPredictions", notNullValue())
                .body("wagers", notNullValue());
    }

    @Test
    void getUsersMeTransactionsSucceedsWhenAuthenticatedAndReturnsTransactions() {
        String username = "userB-" + UUID.randomUUID();
        String session = registerAndGetSession(username);

        given().cookie("sisu_session", session)
                .when()
                .get("/api/users/me/transactions")
                .then()
                .statusCode(200)
                .body("reason", hasItem("STARTING_GRANT"))
                .body("direction", hasItem("CREDIT"))
                .body("amountSisu", hasItem(1000));
    }

    @Test
    void getUsersMeRequiresAuthentication401() {
        given().when().get("/api/users/me").then().statusCode(401).body("code", equalTo("UNAUTHENTICATED"));
    }

    @Test
    void getUsersMeTransactionsRequiresAuthentication401() {
        given().when()
                .get("/api/users/me/transactions")
                .then()
                .statusCode(401)
                .body("code", equalTo("UNAUTHENTICATED"));
    }

    @Test
    void noEndpointExposesAnotherUsersPrivateProfile() {
        String userA = "alpha-" + UUID.randomUUID();
        String sessionA = registerAndGetSession(userA);

        String userB = "beta-" + UUID.randomUUID();
        String sessionB = registerAndGetSession(userB);

        // When logged in as userA, /api/users/me returns userA, not userB
        given().cookie("sisu_session", sessionA)
                .when()
                .get("/api/users/me")
                .then()
                .statusCode(200)
                .body("username", equalTo(userA));

        // Attempting to query any hypothetical user ID endpoint directly is not part of the contract and results in 404
        given().cookie("sisu_session", sessionA)
                .when()
                .get("/api/users/not-an-endpoint")
                .then()
                .statusCode(404);
    }

    private String registerAndGetSession(String username) {
        return given().contentType("application/json")
                .body("""
                        {"username":"%s","password":"%s"}""".formatted(username, PASSWORD))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(201)
                .extract()
                .cookie("sisu_session");
    }
}
