package market.sisu.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;
import market.sisu.common.ApiException;
import market.sisu.common.MongoTransactions;
import market.sisu.common.Sisu;
import market.sisu.prediction.Outcome;
import market.sisu.prediction.Prediction;
import market.sisu.prediction.PredictionRepository;
import market.sisu.prediction.PredictionStatus;
import market.sisu.transaction.BalanceLedger;
import market.sisu.transaction.BalanceLedger.Related;
import market.sisu.transaction.TransactionReason;
import market.sisu.transaction.TransactionRepository;
import market.sisu.user.User;
import market.sisu.user.UserRepository;
import market.sisu.wager.PlaceWagerRequest;
import market.sisu.wager.WagerRepository;
import market.sisu.wager.WagerService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

/**
 * Concurrent wagers against a real MongoDB replica set (spec.md Edge Cases; FR-009, FR-022,
 * SC-006): a balance is never overspent, the pool total always equals the sum of the recorded
 * wagers, and the ledger always explains the balance.
 */
@QuarkusTest
class WagerConcurrencyTest {

    private static final int ATTEMPTS = 10;

    @Inject
    WagerService service;

    @Inject
    MongoTransactions tx;

    @Inject
    BalanceLedger ledger;

    @Inject
    UserRepository users;

    @Inject
    PredictionRepository predictions;

    @Inject
    WagerRepository wagers;

    @Inject
    TransactionRepository transactions;

    @Test
    void concurrentWagersNeverSpendMoreThanTheBalance() throws Exception {
        ObjectId userId = newUserWithGrant(100);
        Prediction prediction = openPrediction();

        // Each 30-sisu wager is affordable alone; only three fit in 100 together.
        List<Boolean> outcomes = race(ATTEMPTS, i -> userId, prediction, 30);

        assertEquals(3, outcomes.stream().filter(placed -> placed).count());
        assertEquals(10, users.findById(userId).balanceSisu);
        assertEquals(10, transactions.sumForUser(userId));
        assertEquals(3, wagers.count("userId", userId));
        assertEquals(90, predictions.findById(prediction.id).yesPoolSisu);
    }

    @Test
    void concurrentWagersFromDifferentUsersAllLandInThePool() throws Exception {
        List<ObjectId> bettors = new ArrayList<>();
        for (int i = 0; i < ATTEMPTS; i++) {
            bettors.add(newUserWithGrant(100));
        }
        Prediction prediction = openPrediction();

        List<Boolean> outcomes = race(ATTEMPTS, bettors::get, prediction, 50);

        assertEquals(ATTEMPTS, outcomes.stream().filter(placed -> placed).count());
        assertEquals(50L * ATTEMPTS, predictions.findById(prediction.id).yesPoolSisu);
        assertEquals(ATTEMPTS, wagers.count("predictionId", prediction.id));
        for (ObjectId bettor : bettors) {
            assertEquals(50, users.findById(bettor).balanceSisu);
            assertEquals(50, transactions.sumForUser(bettor));
        }
    }

    /** Places {@code attempts} YES wagers at once; {@code true} where the wager was accepted. */
    private List<Boolean> race(int attempts, IntFunction<ObjectId> bettor, Prediction prediction, long amount)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        try {
            List<Callable<Boolean>> calls = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                ObjectId userId = bettor.apply(i);
                calls.add(() -> {
                    try {
                        service.placeWager(
                                userId,
                                prediction.id.toHexString(),
                                new PlaceWagerRequest(Outcome.YES, BigDecimal.valueOf(amount)));
                        return true;
                    } catch (ApiException rejected) {
                        assertEquals("INSUFFICIENT_BALANCE", rejected.code());
                        return false;
                    }
                });
            }
            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> result : pool.invokeAll(calls)) {
                results.add(result.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private ObjectId newUserWithGrant(long amount) {
        User user = new User();
        user.id = new ObjectId();
        user.username = "kilpa-" + UUID.randomUUID();
        user.passwordHash = "not-a-real-hash";
        user.createdAt = Instant.now();
        users.persist(user);
        tx.run(s -> ledger.credit(s, user.id, Sisu.of(amount), TransactionReason.STARTING_GRANT, Related.NONE));
        return user.id;
    }

    private Prediction openPrediction() {
        Prediction prediction = new Prediction();
        prediction.id = new ObjectId();
        prediction.creatorId = new ObjectId();
        prediction.question = "Sataako huomenna? " + UUID.randomUUID();
        prediction.resolutionCriteria = "Ilmatieteen laitoksen mittaus";
        prediction.closingAt = Instant.now().plus(1, ChronoUnit.DAYS);
        prediction.status = PredictionStatus.OPEN;
        prediction.createdAt = Instant.now();
        predictions.persist(prediction);
        return prediction;
    }
}
