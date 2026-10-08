package fi.sisu.market;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;

/**
 * End-to-end login throttling. Each test uses its own fake client address via X-Forwarded-For (honoured because
 * the test client connects from 127.0.0.1, a trusted proxy address).
 */
@QuarkusTest
class LoginRateLimitTest {

    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    private static String freshIp() {
        int n = NEXT_IP.getAndIncrement();
        return "203.0." + (n / 250) + "." + (n % 250 + 1);
    }

    private static String register() {
        String name = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        given().contentType(ContentType.JSON)
                .body(Map.of("username", name, "email", name + "@example.fi", "password", "oikea-salasana"))
                .post("/api/auth/register").then().statusCode(201);
        return name;
    }

    private static ValidatableResponse login(String user, String password, String ip) {
        return given().header("X-Forwarded-For", ip).contentType(ContentType.JSON)
                .body(Map.of("username", user, "password", password))
                .post("/api/auth/login").then();
    }

    @Test
    void sixthAttemptIsRefusedEvenWithTheRightPassword() {
        String user = register();
        String ip = freshIp();
        for (int i = 1; i <= 5; i++) {
            login(user, "wrong-" + i, ip).statusCode(401).body("code", equalTo("INVALID_CREDENTIALS"));
        }
        login(user, "oikea-salasana", ip).statusCode(429)
                .header("Retry-After", notNullValue())
                .body("code", equalTo("TOO_MANY_LOGIN_ATTEMPTS"))
                .body("params.retryAfterSeconds", greaterThan(0));

        // The owner logging in from somewhere else is not locked out.
        login(user, "oikea-salasana", freshIp()).statusCode(200);
    }

    @Test
    void successfulLoginClearsTheCounter() {
        String user = register();
        String ip = freshIp();
        for (int i = 0; i < 4; i++) {
            login(user, "wrong", ip).statusCode(401);
        }
        login(user, "oikea-salasana", ip).statusCode(200);
        for (int i = 0; i < 4; i++) {
            login(user, "wrong", ip).statusCode(401);
        }
        login(user, "oikea-salasana", ip).statusCode(200);
    }

    @Test
    void oneAddressCannotSprayManyAccounts() {
        String ip = freshIp();
        for (int i = 0; i < 20; i++) {
            // Unknown usernames count too, and look exactly like wrong passwords.
            login("nobody_" + UUID.randomUUID().toString().substring(0, 8), "guess", ip)
                    .statusCode(401).body("code", equalTo("INVALID_CREDENTIALS"));
        }
        login(register(), "oikea-salasana", ip).statusCode(429).body("code", equalTo("TOO_MANY_LOGIN_ATTEMPTS"));
    }

    @Test
    void throttledRequestsAreCheapAndRevealNothing() {
        String user = register();
        String ip = freshIp();
        for (int i = 0; i < 5; i++) {
            login(user, "wrong", ip).statusCode(401);
        }
        // Same answer for a real and a made-up account once the address is throttled for each.
        String ghost = "ghost_" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 5; i++) {
            login(ghost, "wrong", ip).statusCode(401);
        }
        login(user, "x", ip).statusCode(429).body("code", equalTo("TOO_MANY_LOGIN_ATTEMPTS"));
        login(ghost, "x", ip).statusCode(429).body("code", equalTo("TOO_MANY_LOGIN_ATTEMPTS"));
    }
}
