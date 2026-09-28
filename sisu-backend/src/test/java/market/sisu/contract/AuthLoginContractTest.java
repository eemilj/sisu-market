package market.sisu.contract;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@code POST /api/auth/login} and {@code POST /api/auth/logout} against contracts/openapi.yaml:
 * success, the single generic 401 (FR-003), the 429 cooldown after 5 failures (FR-037), and logout
 * (User Story 1, acceptance scenarios 2, 3, 4, and 6).
 */
@QuarkusTest
class AuthLoginContractTest {

    private static final String PASSWORD = "correct horse battery staple";

    @Test
    void loginSucceedsWithCorrectCredentials() {
        String username = "pekka-" + UUID.randomUUID();
        register(username, PASSWORD);

        given().contentType("application/json")
                .body("""
                        {"username":"%s","password":"%s"}""".formatted(username, PASSWORD))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .body("username", equalTo(username))
                .body("balanceSisu", equalTo(1000))
                .header("Set-Cookie", containsString("sisu_session="));
    }

    @Test
    void wrongPasswordAndUnknownUserYieldTheSameGenericError() {
        String username = "aino-" + UUID.randomUUID();
        register(username, PASSWORD);

        given().contentType("application/json")
                .body("""
                        {"username":"%s","password":"wrong password"}""".formatted(username))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(401)
                .body("code", equalTo("INVALID_CREDENTIALS"))
                .body("message", equalTo("Invalid username or password"));

        given().contentType("application/json")
                .body("""
                        {"username":"nobody-%s","password":"%s"}""".formatted(UUID.randomUUID(), PASSWORD))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(401)
                .body("code", equalTo("INVALID_CREDENTIALS"))
                .body("message", equalTo("Invalid username or password"));
    }

    @Test
    void sixAttemptsInARowTriggerTheCooldown() {
        String username = "kaisa-" + UUID.randomUUID();
        register(username, PASSWORD);
        String wrong = """
                {"username":"%s","password":"wrong password"}""".formatted(username);

        for (int i = 0; i < 5; i++) {
            given().contentType("application/json")
                    .body(wrong)
                    .when()
                    .post("/api/auth/login")
                    .then()
                    .statusCode(401);
        }

        given().contentType("application/json")
                .body(wrong)
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(429)
                .body("code", equalTo("TOO_MANY_ATTEMPTS"))
                .header("Retry-After", notNullValue());

        // Even the correct password is rejected while the cooldown lasts.
        given().contentType("application/json")
                .body("""
                        {"username":"%s","password":"%s"}""".formatted(username, PASSWORD))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(429);
    }

    @Test
    void logoutClearsTheSessionCookie() {
        given().when()
                .post("/api/auth/logout")
                .then()
                .statusCode(204)
                .header("Set-Cookie", containsString("Max-Age=0"));
    }

    private static void register(String username, String password) {
        given().contentType("application/json")
                .body("""
                        {"username":"%s","password":"%s"}""".formatted(username, password))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(201);
    }
}
