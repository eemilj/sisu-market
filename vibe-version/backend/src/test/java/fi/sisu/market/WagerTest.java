package fi.sisu.market;

import com.mongodb.client.ClientSession;
import fi.sisu.market.prediction.Category;
import fi.sisu.market.prediction.Outcome;
import fi.sisu.market.prediction.Prediction;
import fi.sisu.market.prediction.PredictionRepository;
import fi.sisu.market.prediction.PredictionStatus;
import fi.sisu.market.pricing.Cpmm;
import fi.sisu.market.user.UserRepository;
import fi.sisu.market.wager.Wager;
import fi.sisu.market.wager.WagerRepository;
import fi.sisu.market.wallet.LedgerRepository;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
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
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class WagerTest {

    private static final long LIQUIDITY = 1000;

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

    // ---- fixtures & helpers ----

    private Prediction prediction(PredictionStatus status, Instant closesAt, String yesProbability) {
        Prediction p = new Prediction();
        p.question = "Will the wager test pass? " + UUID.randomUUID();
        p.description = "Test fixture";
        p.resolutionCriteria = "Resolves YES if the build is green.";
        p.category = Category.CULTURE;
        p.status = status;
        p.closesAt = closesAt;
        p.updatePool(Cpmm.initialPool(LIQUIDITY, new BigDecimal(yesProbability)));
        p.totalWagered = 0;
        p.createdAt = Instant.now();
        predictions.persist(p);
        return p;
    }

    private Prediction openPrediction() {
        return prediction(PredictionStatus.OPEN, Instant.now().plus(Duration.ofDays(7)), "0.3");
    }

    private static Map<String, Object> body(String side, Object amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("side", side);
        body.put("amount", amount);
        return body;
    }

    private ValidatableResponse wager(Prediction p, Object body) {
        return wager(p.id.toHexString(), body);
    }

    private ValidatableResponse wager(String predictionId, Object body) {
        return given().auth().oauth2(token).contentType(ContentType.JSON).body(body)
                .when().post("/api/predictions/" + predictionId + "/wagers")
                .then();
    }

    private ValidatableResponse quote(Prediction p, String side, Object amount) {
        return given().queryParam("side", side).queryParam("amount", amount)
                .when().get("/api/predictions/" + p.id.toHexString() + "/quote")
                .then();
    }

    /** The invariants that must hold no matter what failed. */
    private void assertConsistent(Prediction p) {
        long balance = users.findById(userId).balance;
        long ledgerSum = ledger.list("userId", userId).stream().mapToLong(e -> e.amount).sum();
        assertEquals(balance, ledgerSum, "balance must equal ledger sum");

        List<Wager> placed = wagers.list("predictionId", p.id);
        long staked = placed.stream().mapToLong(w -> w.amount).sum();
        assertEquals(staked, predictions.findById(p.id).totalWagered, "totalWagered must equal sum of wagers");
        long wagerDebits = ledger.list("userId", userId).stream().filter(e -> e.amount < 0).count();
        assertEquals(wagers.count("userId", userId), wagerDebits, "one ledger debit per wager");
    }

    /** Asserts that nothing about the user or prediction changed. */
    private void assertUntouched(Prediction p) {
        assertEquals(1000, users.findById(userId).balance);
        assertEquals(1, ledger.count("userId", userId));
        assertEquals(0, wagers.count("userId", userId));
        Prediction reloaded = predictions.findById(p.id);
        assertEquals(0, p.poolYesShares.compareTo(reloaded.poolYesShares));
        assertEquals(0, p.poolNoShares.compareTo(reloaded.poolNoShares));
        assertEquals(0, reloaded.totalWagered);
        assertConsistent(p);
    }

    // ---- happy path ----

    @Test
    void quotedPayoutIsExactlyWhatTheWagerGets() {
        Prediction p = openPrediction();
        Cpmm.Trade expected = Cpmm.buy(p.pool(), Outcome.YES, 200);

        int quoted = quote(p, "YES", 200)
                .statusCode(200)
                .body("payoutIfWin", equalTo((int) expected.payoutIfWin()))
                .body("yesProbabilityBefore", equalTo((float) p.pool().yesProbability()))
                .body("yesProbabilityAfter", greaterThan((float) p.pool().yesProbability()))
                .extract().path("payoutIfWin");
        assertTrue(quoted > 200, "a winning wager pays more than its stake");

        Map<String, Object> req = body("YES", 200);
        req.put("minPayout", quoted);
        wager(p, req)
                .statusCode(201)
                .body("wager.payoutIfWin", equalTo(quoted))
                .body("wager.status", equalTo("OPEN"))
                .body("balance", equalTo(800))
                .body("prediction.totalWagered", equalTo(200))
                .body("prediction.yesProbability", equalTo((float) expected.poolAfter().yesProbability()));

        Prediction reloaded = predictions.findById(p.id);
        assertEquals(0, expected.poolAfter().yesShares().compareTo(reloaded.poolYesShares));
        assertEquals(0, expected.poolAfter().noShares().compareTo(reloaded.poolNoShares));
        Wager stored = wagers.find("userId", userId).firstResult();
        assertEquals(0, expected.shares().compareTo(stored.shares));

        given().auth().oauth2(token).get("/api/users/me/transactions")
                .then().body("items[0].type", equalTo("WAGER"))
                .body("items[0].amount", equalTo(-200))
                .body("items[0].predictionId", equalTo(p.id.toHexString()));
        assertConsistent(p);
    }

    @Test
    void noWagerLowersYesProbability() {
        Prediction p = openPrediction();
        double before = p.pool().yesProbability();
        wager(p, body("NO", 1000))
                .statusCode(201)
                .body("balance", equalTo(0))
                .body("prediction.yesProbability", lessThan((float) before));
        assertConsistent(p);
    }

    // ---- business rules ----

    @Test
    void wagerLargerThanBalanceIsRejectedAndRolledBack() {
        Prediction p = openPrediction();
        wager(p, body("YES", 1001)).statusCode(409).body("message", equalTo("Insufficient Sisu balance"));
        assertUntouched(p);
    }

    @Test
    void cumulativeWagersCannotExceedBalance() {
        Prediction p = openPrediction();
        wager(p, body("YES", 600)).statusCode(201);
        wager(p, body("NO", 401)).statusCode(409);
        wager(p, body("NO", 400)).statusCode(201).body("balance", equalTo(0));
        assertConsistent(p);
    }

    @Test
    void wagerAndQuoteAfterCloseAreRejected() {
        Prediction p = prediction(PredictionStatus.OPEN, Instant.now().minus(Duration.ofMinutes(1)), "0.5");
        wager(p, body("YES", 10)).statusCode(409).body("message", equalTo("This prediction is closed for wagering"));
        quote(p, "YES", 10).statusCode(409);
        assertUntouched(p);
    }

    @Test
    void wagerOnClosedOrResolvedStatusIsRejected() {
        Instant future = Instant.now().plus(Duration.ofDays(1));
        for (PredictionStatus status : new PredictionStatus[] {PredictionStatus.CLOSED, PredictionStatus.RESOLVED}) {
            Prediction p = prediction(status, future, "0.5");
            wager(p, body("NO", 10)).statusCode(409);
            assertUntouched(p);
        }
    }

    @Test
    void rejectsNonPositiveOrFractionalAmounts() {
        Prediction p = openPrediction();
        wager(p, body("YES", 0)).statusCode(400).body("message", containsString("greater than zero"));
        wager(p, body("YES", -5)).statusCode(400).body("message", containsString("greater than zero"));
        wager(p, body("YES", 10.5)).statusCode(400).body("message", containsString("whole number"));
        wager(p, Map.of("side", "YES")).statusCode(400).body("message", containsString("Amount is required"));
        quote(p, "YES", "10.5").statusCode(400);
        quote(p, "YES", 0).statusCode(400);
        quote(p, "MAYBE", 10).statusCode(400);
        assertUntouched(p);
    }

    @Test
    void rejectsMissingOrUnknownSide() {
        Prediction p = openPrediction();
        wager(p, Map.of("amount", 10)).statusCode(400).body("message", containsString("Side is required"));
        wager(p, body("MAYBE", 10)).statusCode(400).body("message", equalTo("Malformed request body"));
        assertUntouched(p);
    }

    @Test
    void unknownPredictionIsNotFound() {
        wager(new ObjectId().toHexString(), body("YES", 10)).statusCode(404);
        wager("not-an-id", body("YES", 10)).statusCode(404);
        assertEquals(1000, users.findById(userId).balance);
    }

    @Test
    void requiresLogin() {
        Prediction p = openPrediction();
        given().contentType(ContentType.JSON).body(body("YES", 10))
                .when().post("/api/predictions/" + p.id.toHexString() + "/wagers")
                .then().statusCode(401);
        assertUntouched(p);
    }

    // ---- consistency under failure, races and retries ----

    @Test
    void movedOddsBelowMinPayoutAreRejected() {
        Prediction p = openPrediction();
        long payout = Cpmm.buy(p.pool(), Outcome.YES, 100).payoutIfWin();
        Map<String, Object> req = body("YES", 100);
        req.put("minPayout", payout + 1);
        wager(p, req).statusCode(409).body("message", containsString("odds have moved"));
        assertUntouched(p);
    }

    @Test
    void failureAfterDebitRollsEverythingBack() {
        Prediction p = openPrediction();
        // Simulate a crash at the last step, after the pool update and the balance debit.
        QuarkusMock.installMockForType(new WagerRepository() {
            @Override
            public void insert(ClientSession session, Wager wager) {
                throw new IllegalStateException("simulated failure while recording the wager");
            }
        }, WagerRepository.class);

        wager(p, body("YES", 300)).statusCode(500);
        assertUntouched(p);
    }

    @Test
    void retryWithSameRequestIdDoesNotWagerTwice() {
        Prediction p = openPrediction();
        Map<String, Object> req = body("YES", 100);
        req.put("requestId", UUID.randomUUID().toString());

        String first = wager(p, req).statusCode(201).body("balance", equalTo(900)).extract().path("wager.id");
        wager(p, req).statusCode(200)
                .body("wager.id", equalTo(first))
                .body("balance", equalTo(900))
                .body("prediction.totalWagered", equalTo(100));
        assertEquals(1, wagers.count("userId", userId));

        Map<String, Object> different = body("NO", 100);
        different.put("requestId", req.get("requestId"));
        wager(p, different).statusCode(409).body("message", containsString("already used"));
        assertConsistent(p);
    }

    @Test
    void concurrentRetriesWithSameRequestIdCreateOneWager() throws Exception {
        Prediction p = openPrediction();
        Map<String, Object> req = body("NO", 50);
        req.put("requestId", UUID.randomUUID().toString());
        List<Integer> statuses = runConcurrently(8, () -> given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(req).post("/api/predictions/" + p.id.toHexString() + "/wagers").statusCode());

        assertEquals(1, statuses.stream().filter(s -> s == 201).count(), "statuses: " + statuses);
        assertTrue(statuses.stream().allMatch(s -> s == 201 || s == 200), "statuses: " + statuses);
        assertEquals(950, users.findById(userId).balance);
        assertEquals(1, wagers.count("userId", userId));
        assertConsistent(p);
    }

    @Test
    void concurrentWagersNeverOverspendAndPriceSequentially() throws Exception {
        Prediction p = openPrediction();
        // 10 simultaneous wagers of 200 against a balance of 1000: exactly 5 may succeed.
        List<Integer> statuses = runConcurrently(10, () -> given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(body("YES", 200)).post("/api/predictions/" + p.id.toHexString() + "/wagers").statusCode());

        assertEquals(5, statuses.stream().filter(s -> s == 201).count(), "statuses: " + statuses);
        assertEquals(5, statuses.stream().filter(s -> s == 409).count(), "statuses: " + statuses);
        assertEquals(0, users.findById(userId).balance);
        assertEquals(1000, predictions.findById(p.id).totalWagered);

        // Every successful wager was priced against the pool left by the previous one.
        Cpmm.Pool expected = p.pool();
        for (int i = 0; i < 5; i++) {
            expected = Cpmm.buy(expected, Outcome.YES, 200).poolAfter();
        }
        Prediction reloaded = predictions.findById(p.id);
        assertEquals(0, expected.yesShares().compareTo(reloaded.poolYesShares));
        assertEquals(0, expected.noShares().compareTo(reloaded.poolNoShares));
        assertConsistent(p);
    }

    private static List<Integer> runConcurrently(int n, Callable<Integer> task) throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(n)) {
            List<Future<Integer>> futures = pool.invokeAll(IntStream.range(0, n).mapToObj(i -> task).toList());
            List<Integer> results = new java.util.ArrayList<>();
            for (Future<Integer> f : futures) {
                results.add(f.get());
            }
            return results;
        }
    }
}
