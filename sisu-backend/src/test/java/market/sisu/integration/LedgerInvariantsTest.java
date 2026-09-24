package market.sisu.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Updates;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

/**
 * Exercises the data-model.md cross-entity invariants against a real MongoDB replica set
 * (Quarkus Dev Services / Testcontainers).
 */
@QuarkusTest
class LedgerInvariantsTest {

    @Inject
    MongoTransactions tx;

    @Inject
    BalanceLedger ledger;

    @Inject
    UserRepository users;

    @Inject
    PredictionRepository predictions;

    @Inject
    TransactionRepository transactions;

    @Test
    void creditAndDebitKeepBalanceEqualToLedgerSum() {
        ObjectId userId = newUserWithGrant(1000);

        tx.run(s -> ledger.debit(s, userId, Sisu.of(300), TransactionReason.WAGER_PLACED, Related.NONE));
        tx.run(s -> ledger.credit(s, userId, Sisu.of(50), TransactionReason.PAYOUT, Related.NONE));

        assertEquals(750, users.findById(userId).balanceSisu);
        assertEquals(750, transactions.sumForUser(userId));
        assertEquals(3, transactions.findByUser(userId).size());
    }

    @Test
    void debitBeyondBalanceIsRejectedWithoutWritingAnything() {
        ObjectId userId = newUserWithGrant(100);

        ApiException e = assertThrows(
                ApiException.class,
                () -> tx.run(s -> ledger.debit(s, userId, Sisu.of(101), TransactionReason.WAGER_PLACED, Related.NONE)));

        assertEquals("INSUFFICIENT_BALANCE", e.code());
        assertEquals(422, e.status());
        assertEquals(100, users.findById(userId).balanceSisu);
        assertEquals(1, transactions.findByUser(userId).size());
    }

    @Test
    void failureLaterInTheTransactionRollsBackEarlierWrites() {
        ObjectId userId = newUserWithGrant(100);

        assertThrows(
                IllegalStateException.class,
                () -> tx.run(s -> {
                    ledger.debit(s, userId, Sisu.of(40), TransactionReason.WAGER_PLACED, Related.NONE);
                    throw new IllegalStateException("simulated failure after the debit");
                }));

        assertEquals(100, users.findById(userId).balanceSisu);
        assertEquals(100, transactions.sumForUser(userId));
    }

    @Test
    void concurrentDebitsNeverOverdrawTheBalance() throws Exception {
        ObjectId userId = newUserWithGrant(100);
        int attempts = 8;
        List<Callable<Boolean>> debits = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            debits.add(() -> {
                try {
                    tx.run(s -> ledger.debit(s, userId, Sisu.of(30), TransactionReason.WAGER_PLACED, Related.NONE));
                    return true;
                } catch (ApiException insufficient) {
                    return false;
                }
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        long succeeded;
        try {
            succeeded = pool.invokeAll(debits).stream()
                    .filter(LedgerInvariantsTest::succeeded)
                    .count();
        } finally {
            pool.shutdownNow();
        }

        assertEquals(3, succeeded, "only three 30-sisu debits fit into 100");
        assertEquals(10, users.findById(userId).balanceSisu);
        assertEquals(10, transactions.sumForUser(userId));
    }

    @Test
    void poolOnlyGrowsWhileOpenAndBeforeClosingTime() {
        Instant now = Instant.now();
        ObjectId open = newPrediction(PredictionStatus.OPEN, now.plus(1, ChronoUnit.HOURS));
        ObjectId pastClosingButStillOpen = newPrediction(PredictionStatus.OPEN, now.minusSeconds(1));
        ObjectId closed = newPrediction(PredictionStatus.CLOSED, now.plus(1, ChronoUnit.HOURS));

        boolean intoOpen = tx.inTransaction(s -> predictions.addToPool(s, open, Outcome.YES, Sisu.of(25), now));
        boolean intoExpired =
                tx.inTransaction(s -> predictions.addToPool(s, pastClosingButStillOpen, Outcome.YES, Sisu.of(25), now));
        boolean intoClosed = tx.inTransaction(s -> predictions.addToPool(s, closed, Outcome.NO, Sisu.of(25), now));

        assertTrue(intoOpen);
        assertFalse(intoExpired, "closingAt passed: rejected even though status still reads OPEN (FR-025)");
        assertFalse(intoClosed);

        assertEquals(25, predictions.findById(open).yesPoolSisu);
        assertEquals(0, predictions.findById(pastClosingButStillOpen).yesPoolSisu);
        assertEquals(0, predictions.findById(closed).noPoolSisu);
    }

    @Test
    void statusTransitionSucceedsOnlyOnce() {
        ObjectId id = newPrediction(PredictionStatus.CLOSED, Instant.now().minusSeconds(60));
        Set<PredictionStatus> fromClosed = Set.of(PredictionStatus.CLOSED);

        boolean first = tx.inTransaction(s -> predictions.compareAndSetStatus(
                s, id, fromClosed, PredictionStatus.RESOLVED, Updates.set("winningOutcome", "YES")));
        boolean second = tx.inTransaction(s -> predictions.compareAndSetStatus(
                s, id, fromClosed, PredictionStatus.RESOLVED, Updates.set("winningOutcome", "NO")));

        assertTrue(first);
        assertFalse(second);
        Prediction stored = predictions.findById(id);
        assertEquals(PredictionStatus.RESOLVED, stored.status);
        assertEquals(Outcome.YES, stored.winningOutcome);
    }

    @Test
    void usernamesAreUniqueIgnoringCase() {
        String name = "Matti-" + UUID.randomUUID();
        insertUser(name);

        assertThrows(MongoWriteException.class, () -> insertUser(name.toLowerCase()));
        assertTrue(users.findByUsername(name.toUpperCase()).isPresent());
    }

    private ObjectId newUserWithGrant(long grant) {
        User user = insertUser("user-" + UUID.randomUUID());
        tx.run(s -> ledger.credit(s, user.id, Sisu.of(grant), TransactionReason.STARTING_GRANT, Related.NONE));
        return user.id;
    }

    private User insertUser(String username) {
        User user = new User();
        user.id = new ObjectId();
        user.username = username;
        user.passwordHash = "not-a-real-hash";
        user.createdAt = Instant.now();
        users.persist(user);
        return user;
    }

    private ObjectId newPrediction(PredictionStatus status, Instant closingAt) {
        Prediction p = new Prediction();
        p.id = new ObjectId();
        p.creatorId = new ObjectId();
        p.question = "Sataako huomenna?";
        p.resolutionCriteria = "Ilmatieteen laitoksen mittaus";
        p.closingAt = closingAt;
        p.status = status;
        p.createdAt = Instant.now();
        predictions.persist(p);
        return p.id;
    }

    private static boolean succeeded(Future<Boolean> result) {
        try {
            return result.get();
        } catch (Exception e) {
            throw new AssertionError("debit attempt failed unexpectedly", e);
        }
    }
}
