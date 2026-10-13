package fi.sisu.market;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class AuthFlowTest {

    private static String uniqueName() {
        return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static Map<String, String> registration(String username) {
        return Map.of("username", username, "email", username + "@example.fi", "password", "salasana123");
    }

    @Test
    void registerGrantsStartingBalanceAndToken() {
        String name = uniqueName();
        String token = given().contentType(ContentType.JSON).body(registration(name))
                .when().post("/api/auth/register")
                .then().statusCode(201)
                .body("token", notNullValue())
                .body("user.username", equalTo(name))
                .body("user.balance", equalTo(1000))
                .body("user.passwordHash", nullValue())
                .extract().path("token");

        given().auth().oauth2(token)
                .when().get("/api/users/me")
                .then().statusCode(200)
                .body("username", equalTo(name))
                .body("balance", equalTo(1000));
    }

    @Test
    void loginIsCaseInsensitiveOnUsername() {
        String name = uniqueName();
        given().contentType(ContentType.JSON).body(registration(name)).post("/api/auth/register").then().statusCode(201);

        given().contentType(ContentType.JSON)
                .body(Map.of("username", name.toUpperCase(), "password", "salasana123"))
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .body("token", notNullValue());
    }

    @Test
    void wrongPasswordAndUnknownUserGiveSameError() {
        String name = uniqueName();
        given().contentType(ContentType.JSON).body(registration(name)).post("/api/auth/register").then().statusCode(201);

        given().contentType(ContentType.JSON).body(Map.of("username", name, "password", "wrong-password"))
                .when().post("/api/auth/login")
                .then().statusCode(401).body("message", equalTo("Invalid username or password"));

        given().contentType(ContentType.JSON).body(Map.of("username", uniqueName(), "password", "whatever123"))
                .when().post("/api/auth/login")
                .then().statusCode(401).body("message", equalTo("Invalid username or password"));
    }

    @Test
    void duplicateUsernameIsRejectedRegardlessOfCase() {
        String name = uniqueName();
        given().contentType(ContentType.JSON).body(registration(name)).post("/api/auth/register").then().statusCode(201);

        given().contentType(ContentType.JSON)
                .body(Map.of("username", name.toUpperCase(), "email", "other-" + name + "@example.fi", "password", "salasana123"))
                .when().post("/api/auth/register")
                .then().statusCode(409).body("message", equalTo("Username is already taken"));
    }

    @Test
    void invalidRegistrationReturnsReadableMessage() {
        given().contentType(ContentType.JSON)
                .body(Map.of("username", "a!", "email", "not-an-email", "password", "short"))
                .when().post("/api/auth/register")
                .then().statusCode(400)
                .body("message", containsString("Username must be"))
                .body("message", containsString("Password must be"));
    }

    @Test
    void meRequiresToken() {
        given().when().get("/api/users/me").then().statusCode(401);
    }
}
