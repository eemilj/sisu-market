package market.sisu.integration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/** Every failure must come back in the contract's {@code Error} shape with no internals leaked. */
@QuarkusTest
class ErrorHandlingTest {

    @Test
    void apiExceptionKeepsStatusAndCode() {
        given().when()
                .get("/api/test-probe/api-exception/409")
                .then()
                .statusCode(409)
                .contentType(ContentType.JSON)
                .body("code", equalTo("PROBE_409"))
                .body("message", equalTo("probe message"));
    }

    @Test
    void rateLimitCarriesRetryAfter() {
        given().when()
                .get("/api/test-probe/api-exception/429")
                .then()
                .statusCode(429)
                .header("Retry-After", "900")
                .body("code", equalTo("TOO_MANY_ATTEMPTS"));
    }

    @Test
    void beanValidationFailureIs422() {
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"\"}")
                .when()
                .post("/api/test-probe/validated")
                .then()
                .statusCode(422)
                .body("code", equalTo("VALIDATION_FAILED"))
                .body("message", containsString("name"));
    }

    @Test
    void malformedJsonIs400() {
        given().contentType(ContentType.JSON)
                .body("{not json")
                .when()
                .post("/api/test-probe/validated")
                .then()
                .statusCode(400)
                .body("code", equalTo("BAD_REQUEST"));
    }

    @Test
    void unexpectedErrorsHideInternals() {
        given().when()
                .get("/api/test-probe/boom")
                .then()
                .statusCode(500)
                .body("code", equalTo("INTERNAL_ERROR"))
                .body(not(containsString("secret internal detail")))
                .body(not(containsString("IllegalStateException")));
    }

    @Test
    void unknownRouteIs404InErrorShape() {
        given().when().get("/api/does-not-exist").then().statusCode(404).body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void nonJsonStateChangeIsRejectedAsCsrfDefence() {
        given().contentType(ContentType.URLENC)
                .formParam("name", "x")
                .when()
                .post("/api/test-probe/validated")
                .then()
                .statusCode(415)
                .body("code", equalTo("UNSUPPORTED_MEDIA_TYPE"));
    }
}
