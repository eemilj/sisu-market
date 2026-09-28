package market.sisu.contract;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@code POST /api/auth/register} against contracts/openapi.yaml: success with the starting grant
 * and a session cookie, duplicate usernames, and empty-field validation (User Story 1,
 * acceptance scenarios 1 and 5).
 */
@QuarkusTest
class AuthRegisterContractTest {

    @Test
    void registerCreatesTheAccountLogsItInAndGrantsTheStartingBalance() {
        String username = "matti-" + UUID.randomUUID();

        given().contentType("application/json")
                .body("""
                        {"username":"%s","password":"correct horse battery staple"}""".formatted(username))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(201)
                .body("username", equalTo(username))
                .body("balanceSisu", equalTo(1000))
                .header("Set-Cookie", containsString("sisu_session="))
                .header("Set-Cookie", containsString("HttpOnly"));
    }

    @Test
    void registerRejectsAnAlreadyTakenUsername() {
        String username = "ville-" + UUID.randomUUID();
        String body = """
                {"username":"%s","password":"correct horse battery staple"}""".formatted(username);
        given().contentType("application/json")
                .body(body)
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(201);

        given().contentType("application/json")
                .body(body)
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(409)
                .body("code", equalTo("USERNAME_TAKEN"));
    }

    @Test
    void registerRejectsEmptyUsernamesAndShortPasswords() {
        given().contentType("application/json")
                .body("""
                        {"username":"","password":"correct horse battery staple"}""")
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(422);

        given().contentType("application/json")
                .body("""
                        {"username":"liisa-%s","password":"lyhyt"}""".formatted(UUID.randomUUID()))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(422);
    }
}
