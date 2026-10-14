package fi.sisu.market;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/** Runs with a low registration limit (its own Quarkus instance) to check the wiring end to end. */
@QuarkusTest
@TestProfile(RegistrationLimitTest.LowLimit.class)
class RegistrationLimitTest {

    public static class LowLimit implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("sisu.rate-limit.register.per-ip", "3");
        }
    }

    private static ValidatableResponse register(String ip) {
        String name = "g" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return given().header("X-Forwarded-For", ip).contentType(ContentType.JSON)
                .body(Map.of("username", name, "email", name + "@example.fi", "password", "salasana123"))
                .post("/api/auth/register").then();
    }

    @Test
    void fourthAccountFromOneAddressWithinTheHourIsRefused() {
        for (int i = 0; i < 3; i++) {
            register("198.51.100.7").statusCode(201);
        }
        register("198.51.100.7").statusCode(429)
                .header("Retry-After", notNullValue())
                .body("code", equalTo("TOO_MANY_REGISTRATIONS"));
        register("198.51.100.8").statusCode(201);
    }

    @Test
    void invalidFormsDoNotUseUpTheAllowance() {
        for (int i = 0; i < 5; i++) {
            given().header("X-Forwarded-For", "198.51.100.9").contentType(ContentType.JSON)
                    .body(Map.of("username", "x", "email", "bad", "password", "short"))
                    .post("/api/auth/register").then().statusCode(400);
        }
        register("198.51.100.9").statusCode(201);
    }
}
