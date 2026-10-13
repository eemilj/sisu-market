package market.sisu.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mongodb.client.ClientSession;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import market.sisu.common.AuditLog;
import market.sisu.common.MongoTransactions;
import market.sisu.common.Sisu;
import market.sisu.prediction.Outcome;
import market.sisu.prediction.Prediction;
import market.sisu.prediction.PredictionRepository;
import market.sisu.prediction.PredictionStatus;
import market.sisu.transaction.BalanceLedger;
import market.sisu.user.AccountDeletionService;
import market.sisu.user.User;
import market.sisu.user.UserRepository;
import market.sisu.wager.Wager;
import market.sisu.wager.WagerRepository;
import market.sisu.wager.WagerStatus;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Cancellations and deletions are logged with context (Constitution IV, FR-036). */
class AccountDeletionAuditLogTest {

    private final List<String> lines = new ArrayList<>();
    private final ObjectId userId = new ObjectId();
    private final ObjectId mineId = new ObjectId();
    private AccountDeletionService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        UserRepository users = mock(UserRepository.class);
        PredictionRepository predictions = mock(PredictionRepository.class);
        WagerRepository wagers = mock(WagerRepository.class);
        BalanceLedger ledger = mock(BalanceLedger.class);
        MongoTransactions tx = mock(MongoTransactions.class);
        when(ledger.credit(any(), any(), any(), any(), any())).thenReturn(Sisu.of(1));

        User user = new User();
        user.id = userId;
        when(users.findById(nullable(ClientSession.class), any())).thenReturn(Optional.of(user));
        when(users.mongoCollection()).thenReturn(mock(MongoCollection.class));
        when(users.findById(any(ObjectId.class))).thenReturn(null);

        Prediction mine = new Prediction();
        mine.id = mineId;
        mine.creatorId = userId;
        mine.status = PredictionStatus.OPEN;
        MongoCollection<Prediction> predictionCollection = mock(MongoCollection.class);
        FindIterable<Prediction> ownedFound = mock(FindIterable.class);
        when(predictions.mongoCollection()).thenReturn(predictionCollection);
        when(predictionCollection.find(nullable(ClientSession.class), any(Bson.class)))
                .thenReturn(ownedFound);
        when(ownedFound.into(any())).thenAnswer(invocation -> {
            List<Prediction> out = invocation.getArgument(0);
            out.add(mine);
            return out;
        });
        when(predictions.compareAndSetStatus(nullable(ClientSession.class), any(), any(), any(), any(Bson[].class)))
                .thenReturn(true);

        Wager byOther = wager(new ObjectId(), mineId, 300);
        Wager byMe = wager(userId, mineId, 50);
        MongoCollection<Wager> wagerCollection = mock(MongoCollection.class);
        FindIterable<Wager> wagersFound = mock(FindIterable.class);
        when(wagers.mongoCollection()).thenReturn(wagerCollection);
        when(wagerCollection.find(nullable(ClientSession.class), any(Bson.class)))
                .thenReturn(wagersFound);
        // Both the per-prediction query and the user's own-wagers query return these two.
        when(wagersFound.into(any())).thenAnswer(invocation -> {
            List<Wager> out = invocation.getArgument(0);
            out.add(byOther);
            out.add(byMe);
            return out;
        });
        when(tx.inTransaction(any(Function.class)))
                .thenAnswer(invocation -> ((Function<ClientSession, Object>) invocation.getArgument(0)).apply(null));

        service = new AccountDeletionService(
                users,
                predictions,
                wagers,
                ledger,
                tx,
                null,
                Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC),
                new AuditLog(lines::add, lines::add));
    }

    private static Wager wager(ObjectId owner, ObjectId predictionId, long amount) {
        Wager wager = new Wager();
        wager.id = new ObjectId();
        wager.userId = owner;
        wager.predictionId = predictionId;
        wager.outcome = Outcome.YES;
        wager.amountSisu = amount;
        wager.status = WagerStatus.OPEN;
        return wager;
    }

    @Test
    void logsOneCancellationPerCancelledPredictionAndOneAccountDeletion() {
        service.delete(userId);

        List<String> cancelled = lines.stream()
                .filter(l -> l.startsWith("event=prediction-cancelled"))
                .toList();
        assertEquals(1, cancelled.size());
        String line = cancelled.get(0);
        assertTrue(line.contains("predictionId=" + mineId.toHexString()), line);
        assertTrue(line.contains("creatorId=" + userId.toHexString()), line);
        assertTrue(line.contains("reason=ACCOUNT_DELETED"), line);
        assertTrue(line.contains("refundedWagers=2"), line);
        assertTrue(line.contains("refundedSisu=350"), line);

        List<String> deleted = lines.stream()
                .filter(l -> l.startsWith("event=account-deleted"))
                .toList();
        assertEquals(1, deleted.size());
        assertTrue(deleted.get(0).contains("userId=" + userId.toHexString()), deleted.get(0));
    }

    @Test
    void neverLogsPersonalData() {
        service.delete(userId);

        assertTrue(lines.stream().noneMatch(l -> l.contains("password") || l.contains("username")), lines.toString());
    }
}
