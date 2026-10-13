package market.sisu.contract;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.UUID;
import market.sisu.prediction.Prediction;
import market.sisu.prediction.PredictionStatus;
import market.sisu.user.Role;
import market.sisu.user.User;
import market.sisu.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contract test for POST /api/predictions/{id}/resolve (resolver-only, single-resolution guard)
 * (User Story 5; FR-026, FR-027, FR-035).
 */
@QuarkusTest
class ResolvePredictionContractTest {

    @Inject
    PredictionFixtures fixtures;

    @Inject
    UserRepository users;

    private User resolver;
    private String resolverSession;
    private User normalUser;
    private String normalSession;
    private Prediction prediction;

    @BeforeEach
    void setUp() {
        String resolverName = "resolver-" + UUID.randomUUID();
        resolverSession = registerAndLogin(resolverName);
        resolver = users.findByUsername(resolverName).orElseThrow();
        resolver.role = Role.RESOLVER;
        users.update(resolver);

        String normalName = "normal-" + UUID.randomUUID();
        normalSession = registerAndLogin(normalName);
        normalUser = users.findByUsername(normalName).orElseThrow();

        prediction = fixtures.prediction(normalUser, PredictionStatus.CLOSED, -1, 100, 100);
    }

    @Test
    void resolverCanResolvePredictionSuccessfully() {
        given().cookie("sisu_session", resolverSession)
                .contentType("application/json")
                .body("{\"outcome\":\"YES\"}")
                .when()
                .post("/api/predictions/{id}/resolve", prediction.id.toHexString())
                .then()
                .statusCode(200)
                .body("status", equalTo("RESOLVED"))
                .body("winningOutcome", equalTo("YES"));
    }

    @Test
    void nonResolverIsForbidden() {
        given().cookie("sisu_session", normalSession)
                .contentType("application/json")
                .body("{\"outcome\":\"YES\"}")
                .when()
                .post("/api/predictions/{id}/resolve", prediction.id.toHexString())
                .then()
                .statusCode(403);
    }

    @Test
    void singleResolutionGuardPreventsReResolution() {
        // First resolution succeeds
        given().cookie("sisu_session", resolverSession)
                .contentType("application/json")
                .body("{\"outcome\":\"YES\"}")
                .when()
                .post("/api/predictions/{id}/resolve", prediction.id.toHexString())
                .then()
                .statusCode(200);

        // Second resolution attempt fails
        given().cookie("sisu_session", resolverSession)
                .contentType("application/json")
                .body("{\"outcome\":\"NO\"}")
                .when()
                .post("/api/predictions/{id}/resolve", prediction.id.toHexString())
                .then()
                .statusCode(409);
    }

    private String registerAndLogin(String username) {
        given().contentType("application/json")
                .body("{\"username\":\"%s\",\"password\":\"correct horse battery staple\"}".formatted(username))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(201);
        return login(username);
    }

    private String login(String username) {
        return given().contentType("application/json")
                .body("{\"username\":\"%s\",\"password\":\"correct horse battery staple\"}".formatted(username))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .cookie("sisu_session");
    }
}
