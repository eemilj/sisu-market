package fi.sisu.market;

import com.mongodb.client.ClientSession;
import fi.sisu.market.user.User;
import fi.sisu.market.user.UserRepository;
import fi.sisu.market.wager.Wager;
import fi.sisu.market.wager.WagerRepository;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class SecurityHardeningTest {

    @Inject
    UserRepository users;

    private static String newName() {
        return "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static io.restassured.response.ValidatableResponse register(String name, String email, String password) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("username", name, "email", email, "password", password))
                .post("/api/auth/register").then();
    }

    private static String token(String name) {
        return register(name, name + "@example.fi", "salasana123").statusCode(201).extract().path("token");
    }

    // ---- passwords ----

    @Test
    void passwordsAreStoredAsSaltedBcrypt() {
        String name = newName();
        register(name, name + "@example.fi", "salasana123").statusCode(201)
                .body("user.passwordHash", equalTo(null));
        User stored = users.findByUsername(name).orElseThrow();
        assertTrue(stored.passwordHash.matches("^\\$2[aby]?\\$\\d{2}\\$[./A-Za-z0-9]{53}$"), stored.passwordHash);
        assertFalse(stored.passwordHash.contains("salasana"));
    }

    /** Regression for the bcrypt 72-byte truncation bypass found in review. */
    @Test
    void passwordsLongerThan72BytesAreRefusedAndCannotLogIn() {
        String name = newName();
        register(name, name + "@example.fi", "ä".repeat(37)).statusCode(400)
                .body("code", equalTo("VALIDATION_FAILED"))
                .body("fieldErrors.rule", hasItem("MaxBytes"));

        String exact = "ä".repeat(36); // 72 bytes: allowed
        register(name, name + "@example.fi", exact).statusCode(201);
        given().contentType(ContentType.JSON).body(Map.of("username", name, "password", exact + "extra"))
                .post("/api/auth/login").then().statusCode(401).body("code", equalTo("INVALID_CREDENTIALS"));
        given().contentType(ContentType.JSON).body(Map.of("username", name, "password", exact))
                .post("/api/auth/login").then().statusCode(200);
    }

    @Test
    void passwordMayNotBeTheUsername() {
        String name = newName();
        register(name, name + "@example.fi", name.toUpperCase()).statusCode(400)
                .body("fieldErrors.find { it.field == 'password' }.rule", equalTo("SameAsUsername"));
    }

    // ---- server-side validation of what the UI also checks ----

    @Test
    void backendEnforcesTheSameRulesAsTheForms() {
        String name = newName();
        register(name, "x@localhost", "salasana123").statusCode(400)
                .body("fieldErrors.find { it.field == 'email' }.rule", equalTo("Email"));
        register("  " + name + "  ", "  " + name + "@example.fi ", "salasana123").statusCode(201)
                .body("user.username", equalTo(name))
                .body("user.email", equalTo(name + "@example.fi"));

        String t = token(newName());
        for (String question : new String[] {"Sataako\nlunta huomenna?", "Sataako lunta\u0000?", "     Lumi?     "}) {
            given().auth().oauth2(t).contentType(ContentType.JSON)
                    .body(Map.of("question", question, "description", "d", "resolutionCriteria", "KYLLÄ, jos sataa.",
                            "category", "WEATHER", "closesAt", Instant.now().plus(Duration.ofDays(2)).toString()))
                    .post("/api/predictions").then().statusCode(400)
                    .body("fieldErrors.field", hasItem("question"));
        }
    }

    @Test
    void clientsCannotSetProtectedFields() {
        String name = newName();
        register(name, name + "@example.fi", "salasana123").statusCode(201);
        given().contentType(ContentType.JSON)
                .body(Map.of("username", newName(), "email", newName() + "@example.fi", "password", "salasana123",
                        "balance", 1_000_000, "admin", true, "balanceVersion", 99))
                .post("/api/auth/register").then().statusCode(201)
                .body("user.balance", equalTo(1000))
                .body("user.admin", equalTo(false))
                .body("user.balanceVersion", equalTo(1));
    }

    @Test
    void wagerAmountsAreValidatedOnTheServer() {
        String t = token(newName());
        String id = given().get("/api/predictions?status=OPEN").then().extract().path("[0].id");
        for (Object amount : new Object[] {0, -5, 10.5, "abc", 1e20}) {
            given().auth().oauth2(t).contentType(ContentType.JSON).body(Map.of("side", "YES", "amount", amount))
                    .post("/api/predictions/" + id + "/wagers").then().statusCode(400);
        }
        given().queryParam("side", "YES").queryParam("amount", "1e999999999")
                .get("/api/predictions/" + id + "/quote").then().statusCode(400).body("code", equalTo("AMOUNT_TOO_LARGE"));
    }

    // ---- error responses ----

    @Test
    void unexpectedErrorsRevealNoInternals() {
        String t = token(newName());
        String id = given().get("/api/predictions?status=OPEN").then().extract().path("[0].id");
        QuarkusMock.installMockForType(new WagerRepository() {
            @Override
            public void insert(ClientSession session, Wager wager) {
                throw new IllegalStateException("mongodb://admin:hunter2@db-internal:27017 refused");
            }
        }, WagerRepository.class);

        String body = given().auth().oauth2(t).contentType(ContentType.JSON).body(Map.of("side", "YES", "amount", 10))
                .post("/api/predictions/" + id + "/wagers").then()
                .statusCode(500)
                .contentType(ContentType.JSON)
                .body("code", equalTo("INTERNAL_ERROR"))
                .body("params.errorId", matchesPattern("[0-9a-f-]{36}"))
                .extract().asString();
        for (String secret : new String[] {"hunter2", "db-internal", "IllegalStateException", "mongodb", "at fi.sisu"}) {
            assertFalse(body.contains(secret), "leaked '" + secret + "': " + body);
        }
    }

    @Test
    void oversizedAndUnknownRequestsGetPlainErrors() {
        String big = "{\"username\":\"" + "x".repeat(100_000) + "\",\"password\":\"y\"}";
        given().contentType(ContentType.JSON).body(big).post("/api/auth/login").then().statusCode(413);

        given().get("/api/does-not-exist").then().statusCode(404)
                .body("code", equalTo("HTTP_ERROR"))
                .body(not(containsString("Exception")));
    }
}
