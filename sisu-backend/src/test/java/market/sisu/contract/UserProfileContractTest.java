package market.sisu.contract;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@code GET /api/users/me} and {@code GET /api/users/me/transactions} against contracts/openapi.yaml:
 * private profile (FR-030), transaction history (FR-011), and 401 when logged out.
 */
@QuarkusTest
class UserProfileContractTest {

    @Test
    void requiresAuthentication() {
        given().when().get("/api/users/me").then().statusCode(401);

        given().when().get("/api/users/me/transactions").then().statusCode(401);
    }

    @Test
    void returnsOwnProfileAndTransactionsWhenLoggedIn() {
        String username = "profile-user-" + UUID.randomUUID();
        String password = "correct horse battery staple";

        String cookie = given().contentType("application/json")
                .body("""
                        {"username":"%s","password":"%s"}""".formatted(username, password))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(201)
                .extract()
                .cookie("sisu_session");

        given().cookie("sisu_session", cookie)
                .when()
                .get("/api/users/me")
                .then()
                .statusCode(200)
                .body("username", equalTo(username))
                .body("balanceSisu", equalTo(1000))
                .body("role", equalTo("USER"))
                .body("createdPredictions", notNullValue())
                .body("wagers", notNullValue());

        given().cookie("sisu_session", cookie)
                .when()
                .get("/api/users/me/transactions")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].reason", equalTo("STARTING_GRANT"))
                .body("[0].amountSisu", equalTo(1000));
    }
}
