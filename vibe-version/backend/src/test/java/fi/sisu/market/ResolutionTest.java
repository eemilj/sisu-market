package fi.sisu.market;

import com.mongodb.client.ClientSession;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import fi.sisu.market.prediction.Category;
import fi.sisu.market.prediction.Prediction;
import fi.sisu.market.prediction.PredictionRepository;
import fi.sisu.market.prediction.PredictionStatus;
import fi.sisu.market.pricing.Cpmm;
import fi.sisu.market.resolution.PredictionCloser;
import fi.sisu.market.user.UserRepository;
import fi.sisu.market.wager.Wager;
import fi.sisu.market.wager.WagerRepository;
import fi.sisu.market.wager.WagerStatus;
import fi.sisu.market.wallet.LedgerRepository;
import fi.sisu.market.wallet.TransactionType;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ResolutionTest {

    /** Configured as admin via %test.sisu.admin-usernames. */
    private static final String ADMIN = "sisu_admin";

    @Inject
    PredictionRepository predictions;

    @Inject
    UserRepository users;

    @Inject
    WagerRepository wagers;

    @Inject
    LedgerRepository ledger;

    @Inject
    PredictionCloser closer;

    // ---- helpers ----

    private record Account(ObjectId id, String token) {
    }

    private static Account register() {
        return register("r" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
    }

    private static Account register(String name) {
        var r = given().contentType(ContentType.JSON)
                .body(Map.of("username", name, "email", name + "@example.fi", "password", "salasana123"))
                .post("/api/auth/register").then().extract();
        if (r.statusCode() == 409) { // already registered by an earlier test
            r = given().contentType(ContentType.JSON).body(Map.of("username", name, "password", "salasana123"))
                    .post("/api/auth/login").then().statusCode(200).extract();
        }
        return new Account(new ObjectId((String) r.path("user.id")), r.path("token"));
    }

    private static Account admin() {
        return register(ADMIN);
    }

    private static ObjectId createPrediction(Account by) {
        String id = given().auth().oauth2(by.token()).contentType(ContentType.JSON)
                .body(Map.of("question", "Will this resolve correctly? " + UUID.randomUUID(), "description", "Resolution test",
                        "resolutionCriteria", "Resolves as the test decides.", "category", "SPORTS",
                        "closesAt", Instant.now().plus(Duration.ofDays(3)).toString()))
                .post("/api/predictions").then().statusCode(201).extract().path("id");
        return new ObjectId(id);
    }

    private static long wager(Account by, ObjectId predictionId, String side, int amount) {
        Integer payout = given().auth().oauth2(by.token()).contentType(ContentType.JSON)
                .body(Map.of("side", side, "amount", amount))
                .post("/api/predictions/" + predictionId.toHexString() + "/wagers")
                .then().statusCode(201).extract().path("wager.payoutIfWin");
        return payout;
    }

    /** Simulates the closing time passing. */
    private void closeNow(ObjectId predictionId) {
        predictions.mongoCollection().updateOne(Filters.eq("_id", predictionId),
                Updates.set("closesAt", Instant.now().minusSeconds(1)));
    }

    private static ValidatableResponse resolve(Account by, ObjectId predictionId, String outcome) {
        return given().auth().oauth2(by.token()).contentType(ContentType.JSON)
                .body(Map.of("outcome", outcome))
                .post("/api/predictions/" + predictionId.toHexString() + "/resolve").then();
    }

    private long balance(Account a) {
        return users.findById(a.id()).balance;
    }

    private void assertLedgerMatchesBalance(Account a) {
        long sum = ledger.list("userId", a.id()).stream().mapToLong(e -> e.amount).sum();
        assertEquals(balance(a), sum, "balance must equal ledger sum");
    }

    private long payoutEntries(ObjectId predictionId) {
        return ledger.list("predictionId = ?1 and type = ?2", predictionId, TransactionType.PAYOUT.name()).size();
    }

    // ---- closing ----

    @Test
    void expiredPredictionsAreClosedAutomatically() {
        Prediction p = new Prediction();
        p.question = "Will the closer close me? " + UUID.randomUUID();
        p.description = "d";
        p.resolutionCriteria = "Resolves when closed.";
        p.category = Category.WEATHER;
        p.status = PredictionStatus.OPEN;
        p.closesAt = Instant.now().minusSeconds(5);
        p.updatePool(Cpmm.initialPool(1000, new BigDecimal("0.5")));
        p.createdAt = Instant.now();
        predictions.persist(p);

        closer.closeExpired(); // the scheduler may have beaten us to it; either way it ends CLOSED once
        Prediction reloaded = predictions.findById(p.id);
        assertEquals(PredictionStatus.CLOSED, reloaded.status);
        assertEquals(1, reloaded.version);
        closer.closeExpired();
        assertEquals(1, predictions.findById(p.id).version, "an already-closed prediction is not closed again");
    }

    @Test
    void cannotResolveWhileOpen() {
        ObjectId id = createPrediction(register());
        resolve(admin(), id, "YES").statusCode(409).body("message", containsString("still open"));
    }

    // ---- paying winners ----

    @Test
    void winnersArePaidTheirLockedPayoutAndLosersNothing() {
        Account alice = register();
        Account bob = register();
        ObjectId id = createPrediction(register());
        long a1 = wager(alice, id, "YES", 100);
        long a2 = wager(alice, id, "YES", 50);
        wager(bob, id, "NO", 200);
        closeNow(id);

        resolve(admin(), id, "YES").statusCode(200)
                .body("prediction.status", equalTo("RESOLVED"))
                .body("prediction.outcome", equalTo("YES"))
                .body("winningWagers", equalTo(2))
                .body("losingWagers", equalTo(1))
                .body("totalPaidOut", equalTo((int) (a1 + a2)));

        assertEquals(1000 - 150 + a1 + a2, balance(alice));
        assertEquals(800, balance(bob));
        assertLedgerMatchesBalance(alice);
        assertLedgerMatchesBalance(bob);
        assertEquals(2, payoutEntries(id));

        List<Wager> settled = wagers.list("predictionId", id);
        for (Wager w : settled) {
            assertEquals(w.userId.equals(alice.id()) ? WagerStatus.WON : WagerStatus.LOST, w.status);
            assertEquals(w.status == WagerStatus.WON ? w.payoutIfWin : 0L, w.payout);
        }
        // Resolved predictions take no more wagers.
        given().auth().oauth2(bob.token()).contentType(ContentType.JSON).body(Map.of("side", "NO", "amount", 10))
                .post("/api/predictions/" + id.toHexString() + "/wagers").then().statusCode(409);
    }

    @Test
    void cannotResolveTwice() {
        Account alice = register();
        ObjectId id = createPrediction(register());
        wager(alice, id, "NO", 300);
        closeNow(id);

        resolve(admin(), id, "NO").statusCode(200);
        long after = balance(alice);
        resolve(admin(), id, "NO").statusCode(409).body("message", containsString("already been resolved"));
        resolve(admin(), id, "YES").statusCode(409);

        assertEquals(after, balance(alice));
        assertEquals(1, payoutEntries(id));
        assertEquals("NO", predictions.findById(id).outcome.name());
    }

    @Test
    void concurrentResolutionsSettleExactlyOnce() throws Exception {
        Account alice = register();
        Account bob = register();
        ObjectId id = createPrediction(register());
        long alicePayout = wager(alice, id, "YES", 100);
        long bobPayout = wager(bob, id, "NO", 100);
        closeNow(id);
        Account admin = admin();

        List<Callable<Integer>> attempts = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String outcome = i % 2 == 0 ? "YES" : "NO";
            attempts.add(() -> resolve(admin, id, outcome).extract().statusCode());
        }
        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (Future<Integer> f : pool.invokeAll(attempts)) {
                statuses.add(f.get());
            }
        }

        assertEquals(1, statuses.stream().filter(s -> s == 200).count(), "statuses: " + statuses);
        assertEquals(7, statuses.stream().filter(s -> s == 409).count(), "statuses: " + statuses);
        assertEquals(1, payoutEntries(id));
        boolean yesWon = predictions.findById(id).outcome.name().equals("YES");
        assertEquals(yesWon ? 900 + alicePayout : 900, balance(alice));
        assertEquals(yesWon ? 900 : 900 + bobPayout, balance(bob));
        assertLedgerMatchesBalance(alice);
        assertLedgerMatchesBalance(bob);
    }

    @Test
    void failureWhileSettlingPaysNobody() {
        Account alice = register();
        Account bob = register();
        ObjectId id = createPrediction(register());
        wager(alice, id, "YES", 100);
        wager(bob, id, "YES", 100);
        closeNow(id);

        // Let the first wager settle (and be paid), then fail on the second.
        AtomicInteger calls = new AtomicInteger();
        QuarkusMock.installMockForType(new WagerRepository() {
            @Override
            public boolean settle(ClientSession session, ObjectId wagerId, WagerStatus status, long payout, Instant at) {
                if (calls.incrementAndGet() > 1) {
                    throw new IllegalStateException("simulated failure during settlement");
                }
                return super.settle(session, wagerId, status, payout, at);
            }
        }, WagerRepository.class);

        resolve(admin(), id, "YES").statusCode(500);

        assertEquals(2, calls.get());
        assertEquals(PredictionStatus.CLOSED, predictions.findById(id).effectiveStatus(Instant.now()));
        assertEquals(0, payoutEntries(id));
        assertEquals(900, balance(alice));
        assertEquals(900, balance(bob));
        assertTrue(wagers.list("predictionId", id).stream().allMatch(w -> w.status == WagerStatus.OPEN));
    }

    // ---- who may resolve ----

    @Test
    void onlyAdminsOrUninvolvedCreatorsMayResolve() {
        Account creator = register();
        Account stranger = register();

        ObjectId clean = createPrediction(creator);
        closeNow(clean);
        given().contentType(ContentType.JSON).body(Map.of("outcome", "YES"))
                .post("/api/predictions/" + clean.toHexString() + "/resolve").then().statusCode(401);
        resolve(stranger, clean, "YES").statusCode(403);
        resolve(creator, clean, "YES").statusCode(200);

        ObjectId involved = createPrediction(creator);
        wager(creator, involved, "YES", 10);
        closeNow(involved);
        resolve(creator, involved, "YES").statusCode(403).body("message", containsString("admin must resolve"));
        resolve(admin(), involved, "NO").statusCode(200);

        given().auth().oauth2(admin().token()).get("/api/users/me").then().body("admin", equalTo(true));
        given().auth().oauth2(creator.token()).get("/api/users/me").then().body("admin", equalTo(false));
    }

    @Test
    void rejectsBadInput() {
        ObjectId id = createPrediction(register());
        closeNow(id);
        resolve(admin(), id, "MAYBE").statusCode(400);
        given().auth().oauth2(admin().token()).contentType(ContentType.JSON).body(Map.of())
                .post("/api/predictions/" + id.toHexString() + "/resolve").then().statusCode(400);
        resolve(admin(), new ObjectId(), "YES").statusCode(404);
    }
}
