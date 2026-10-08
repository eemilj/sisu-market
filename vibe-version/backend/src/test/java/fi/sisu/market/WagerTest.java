package fi.sisu.market;

import fi.sisu.market.prediction.Category;
import fi.sisu.market.prediction.Outcome;
import fi.sisu.market.prediction.Prediction;
import fi.sisu.market.prediction.PredictionRepository;
import fi.sisu.market.prediction.PredictionStatus;
import fi.sisu.market.user.UserRepository;
import fi.sisu.market.wager.WagerRepository;
import fi.sisu.market.wallet.LedgerRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class WagerTest {

    @Inject
    PredictionRepository predictions;

    @Inject
    UserRepository users;

    @Inject
    LedgerRepository ledger;

    @Inject
    WagerRepository wagers;

    private String token;
    private ObjectId userId;

    @BeforeEach
    void registerUser() {
        String name = "g" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var response = given().contentType(ContentType.JSON)
                .body(Map.of("username", name, "email", name + "@example.fi", "password", "salasana123"))
                .post("/api/auth/register")
                .then().statusCode(201).extract();
        token = response.path("token");
        userId = new ObjectId((String) response.path("user.id"));
    }

    private Prediction prediction(PredictionStatus status, Instant closesAt, long yesPool, long noPool) {
        Prediction p = new Prediction();
        p.question = "Will the wager test pass? " + UUID.randomUUID();
        p.description = "Test fixture";
        p.resolutionCriteria = "Resolves YES if the build is green.";
        p.category = Category.CULTURE;
        p.status = status;
        p.closesAt = closesAt;
        p.yesPool = yesPool;
        p.noPool = noPool;
        p.createdAt = Instant.now();
        predictions.persist(p);
        return p;
    }

    private Prediction openPrediction() {
        return prediction(PredictionStatus.OPEN, Instant.now().plus(Duration.ofDays(7)), 300, 700);
    }

    private ValidatableResponse wager(Prediction p, Object body) {
        return wager(p.id.toHexString(), body);
    }

    private ValidatableResponse wager(String predictionId, Object body) {
        return given().auth().oauth2(token).contentType(ContentType.JSON).body(body)
                .when().post("/api/predictions/" + predictionId + "/wagers")
                .then();
    }

    /** Asserts that nothing about the user or prediction changed. */
    private void assertUntouched(Prediction p) {
        assertEquals(1000, users.findById(userId).balance);
        assertEquals(1, ledger.count("userId", userId));
        assertEquals(0, wagers.count("userId", userId));
        Prediction reloaded = predictions.findById(p.id);
        assertEquals(p.yesPool, reloaded.yesPool);
        assertEquals(p.noPool, reloaded.noPool);
    }

    @Test
    void successfulWagerUpdatesBalancePoolsOddsAndLedger() {
        Prediction p = openPrediction(); // 300 YES / 700 NO -> 30%

        wager(p, Map.of("side", "YES", "amount", 200))
                .statusCode(201)
                .body("wager.id", notNullValue())
                .body("wager.side", equalTo("YES"))
                .body("wager.amount", equalTo(200))
                .body("balance", equalTo(800))
                .body("prediction.volume", equalTo(1200))
                .body("prediction.yesPercent", equalTo(42)); // 500 / 1200

        assertEquals(800, users.findById(userId).balance);
        Prediction reloaded = predictions.findById(p.id);
        assertEquals(500, reloaded.yesPool);
        assertEquals(700, reloaded.noPool);
        assertEquals(1, wagers.count("userId", userId));

        given().auth().oauth2(token).get("/api/users/me/transactions")
                .then().body("items[0].type", equalTo("WAGER"))
                .body("items[0].amount", equalTo(-200))
                .body("items[0].balanceAfter", equalTo(800))
                .body("items[0].predictionId", equalTo(p.id.toHexString()))
                .body("items[0].description", containsString("Wager on YES"));
        given().get("/api/predictions/" + p.id.toHexString()).then().body("yesPercent", equalTo(42));
    }

    @Test
    void noWagerGrowsNoPool() {
        Prediction p = openPrediction();
        wager(p, Map.of("side", "NO", "amount", 1000))
                .statusCode(201)
                .body("balance", equalTo(0))
                .body("prediction.yesPercent", equalTo(15)); // 300 / 2000
        assertEquals(1700, predictions.findById(p.id).noPool);
    }

    @Test
    void wagerLargerThanBalanceIsRejectedAndRolledBack() {
        Prediction p = openPrediction();
        wager(p, Map.of("side", "YES", "amount", 1001))
                .statusCode(409).body("message", equalTo("Insufficient Sisu balance"));
        assertUntouched(p);
    }

    @Test
    void cumulativeWagersCannotExceedBalance() {
        Prediction p = openPrediction();
        wager(p, Map.of("side", "YES", "amount", 600)).statusCode(201);
        wager(p, Map.of("side", "NO", "amount", 401)).statusCode(409);
        wager(p, Map.of("side", "NO", "amount", 400)).statusCode(201).body("balance", equalTo(0));
    }

    @Test
    void wagerAfterCloseIsRejected() {
        Prediction p = prediction(PredictionStatus.OPEN, Instant.now().minus(Duration.ofMinutes(1)), 100, 100);
        wager(p, Map.of("side", "YES", "amount", 10))
                .statusCode(409).body("message", equalTo("This prediction is closed for wagering"));
        assertUntouched(p);
    }

    @Test
    void wagerOnClosedOrResolvedStatusIsRejected() {
        Instant future = Instant.now().plus(Duration.ofDays(1));
        for (PredictionStatus status : new PredictionStatus[] {PredictionStatus.CLOSED, PredictionStatus.RESOLVED}) {
            Prediction p = prediction(status, future, 100, 100);
            wager(p, Map.of("side", "NO", "amount", 10)).statusCode(409);
            assertUntouched(p);
        }
    }

    @Test
    void rejectsNonPositiveOrFractionalAmounts() {
        Prediction p = openPrediction();
        wager(p, Map.of("side", "YES", "amount", 0)).statusCode(400).body("message", containsString("greater than zero"));
        wager(p, Map.of("side", "YES", "amount", -5)).statusCode(400).body("message", containsString("greater than zero"));
        wager(p, Map.of("side", "YES", "amount", 10.5)).statusCode(400).body("message", containsString("whole number"));
        wager(p, Map.of("side", "YES")).statusCode(400).body("message", containsString("Amount is required"));
        assertUntouched(p);
    }

    @Test
    void rejectsMissingOrUnknownSide() {
        Prediction p = openPrediction();
        wager(p, Map.of("amount", 10)).statusCode(400).body("message", containsString("Side is required"));
        wager(p, Map.of("side", "MAYBE", "amount", 10)).statusCode(400).body("message", equalTo("Malformed request body"));
        assertUntouched(p);
    }

    @Test
    void unknownPredictionIsNotFound() {
        wager(new ObjectId().toHexString(), Map.of("side", "YES", "amount", 10)).statusCode(404);
        wager("not-an-id", Map.of("side", "YES", "amount", 10)).statusCode(404);
        assertEquals(1000, users.findById(userId).balance);
    }

    @Test
    void requiresLogin() {
        Prediction p = openPrediction();
        given().contentType(ContentType.JSON).body(Map.of("side", "YES", "amount", 10))
                .when().post("/api/predictions/" + p.id.toHexString() + "/wagers")
                .then().statusCode(401);
        Prediction reloaded = predictions.findById(p.id);
        assertEquals(300, reloaded.yesPool);
    }

    @Test
    void concurrentWagersNeverOverspend() throws Exception {
        Prediction p = openPrediction();
        // 10 simultaneous wagers of 200 against a balance of 1000: exactly 5 may succeed.
        Callable<Integer> attempt = () -> given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of("side", "YES", "amount", 200))
                .post("/api/predictions/" + p.id.toHexString() + "/wagers")
                .statusCode();
        List<Integer> statuses;
        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            List<Future<Integer>> futures = pool.invokeAll(IntStream.range(0, 10).mapToObj(i -> attempt).toList());
            statuses = futures.stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).toList();
        }

        assertEquals(5, statuses.stream().filter(s -> s == 201).count(), "statuses: " + statuses);
        assertEquals(5, statuses.stream().filter(s -> s == 409).count(), "statuses: " + statuses);
        assertEquals(0, users.findById(userId).balance);
        assertEquals(5, wagers.count("userId", userId));
        assertEquals(300 + 5 * 200, predictions.findById(p.id).yesPool);
        long ledgerSum = ledger.list("userId", userId).stream().mapToLong(e -> e.amount).sum();
        assertEquals(0, ledgerSum);
    }
}
