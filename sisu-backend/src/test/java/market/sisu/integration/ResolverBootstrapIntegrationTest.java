package market.sisu.integration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mongodb.client.model.Filters;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.UUID;
import market.sisu.contract.PredictionFixtures;
import market.sisu.prediction.Prediction;
import market.sisu.prediction.PredictionStatus;
import market.sisu.transaction.TransactionRepository;
import market.sisu.user.Role;
import market.sisu.user.User;
import market.sisu.user.UserRepository;
import market.sisu.user.UserService;
import market.sisu.user.UserService.Provisioning;
import org.junit.jupiter.api.Test;

/** Bootstrap resolver provisioning against a real MongoDB (FR-039, FR-040, SC-006, SC-009). */
@QuarkusTest
class ResolverBootstrapIntegrationTest {

    private static final String PASSWORD = "a long resolver passphrase";

    @Inject
    UserService userService;

    @Inject
    UserRepository users;

    @Inject
    TransactionRepository transactions;

    @Inject
    PredictionFixtures fixtures;

    @Test
    void provisioningTwiceLeavesOneResolverWhoCanLogInAndResolve() {
        String name = "resolver-" + UUID.randomUUID();

        assertEquals(Provisioning.CREATED, userService.provisionResolver(name, PASSWORD));
        assertEquals(Provisioning.UNCHANGED, userService.provisionResolver(name, PASSWORD));

        assertEquals(1, users.mongoCollection().countDocuments(Filters.eq("username", name)));
        User resolver = users.findByUsername(name).orElseThrow();
        assertEquals(Role.RESOLVER, resolver.role);
        assertTrue(resolver.balanceSisu > 0);
        assertEquals(1, transactions.findByUser(resolver.id).size(), "exactly one starting-grant ledger entry");
        assertEquals(resolver.balanceSisu, transactions.sumForUser(resolver.id));

        String session = given().contentType("application/json")
                .body("{\"username\":\"" + name + "\",\"password\":\"" + PASSWORD + "\"}")
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .cookie("sisu_session");

        Prediction closed = fixtures.prediction(fixtures.creator(), PredictionStatus.CLOSED, -1, 100, 100);
        given().cookie("sisu_session", session)
                .contentType("application/json")
                .body("{\"outcome\":\"YES\"}")
                .post("/api/predictions/{id}/resolve", closed.id.toHexString())
                .then()
                .statusCode(200)
                .body("status", equalTo("RESOLVED"));
    }

    @Test
    void registrationNeverYieldsTheResolverRole() {
        String name = "regular-" + UUID.randomUUID();
        given().contentType("application/json")
                .body("{\"username\":\"" + name + "\",\"password\":\"" + PASSWORD + "\"}")
                .post("/api/auth/register")
                .then()
                .statusCode(201);

        assertEquals(Role.USER, users.findByUsername(name).orElseThrow().role);
    }

    @Test
    void existingRegularUserIsPromotedKeepingPasswordAndBalance() {
        String name = "promote-" + UUID.randomUUID();
        User before = userService.register(name, PASSWORD);

        assertEquals(Provisioning.PROMOTED, userService.provisionResolver(name, "ignored-new-password"));

        User after = users.findByUsername(name).orElseThrow();
        assertEquals(Role.RESOLVER, after.role);
        assertEquals(before.passwordHash, after.passwordHash);
        assertEquals(before.balanceSisu, after.balanceSisu);
    }
}
