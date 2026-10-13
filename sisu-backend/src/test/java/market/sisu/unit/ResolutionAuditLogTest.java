package market.sisu.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import market.sisu.common.ApiException;
import market.sisu.common.AuditLog;
import market.sisu.common.MongoTransactions;
import market.sisu.common.Sisu;
import market.sisu.prediction.Outcome;
import market.sisu.prediction.Prediction;
import market.sisu.prediction.PredictionRepository;
import market.sisu.prediction.PredictionStatus;
import market.sisu.prediction.ResolutionService;
import market.sisu.transaction.BalanceLedger;
import market.sisu.user.Role;
import market.sisu.user.User;
import market.sisu.user.UserRepository;
import market.sisu.wager.Wager;
import market.sisu.wager.WagerRepository;
import market.sisu.wager.WagerStatus;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Resolutions are logged with enough context to trace them (Constitution IV, research.md section 9). */
class ResolutionAuditLogTest {

    private static final Instant NOW = Instant.parse("2026-10-13T12:00:00Z");

    private final List<String> lines = new ArrayList<>();
    private PredictionRepository predictions;
    private WagerRepository wagers;
    private UserRepository users;
    private MongoTransactions tx;
    private ResolutionService service;
    private ObjectId resolverId;
    private Prediction prediction;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        predictions = mock(PredictionRepository.class);
        wagers = mock(WagerRepository.class);
        users = mock(UserRepository.class);
        tx = mock(MongoTransactions.class);
        BalanceLedger ledger = mock(BalanceLedger.class);
        when(ledger.credit(any(), any(), any(), any(), any())).thenReturn(Sisu.of(1));
        service = new ResolutionService(
                predictions,
                wagers,
                users,
                ledger,
                tx,
                null,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new AuditLog(lines::add, lines::add));

        resolverId = new ObjectId();
        User resolver = new User();
        resolver.id = resolverId;
        resolver.role = Role.RESOLVER;
        when(users.findById(resolverId)).thenReturn(resolver);

        prediction = new Prediction();
        prediction.id = new ObjectId();
        prediction.creatorId = new ObjectId();
        prediction.status = PredictionStatus.CLOSED;
        prediction.yesPoolSisu = 200;
        prediction.noPoolSisu = 100;
        when(predictions.findById(nullable(ClientSession.class), any())).thenReturn(Optional.of(prediction));
        when(predictions.compareAndSetStatus(nullable(ClientSession.class), any(), any(), any(), any(Bson[].class)))
                .thenReturn(true);

        Wager yes = wager(Outcome.YES, 200);
        Wager no = wager(Outcome.NO, 100);
        MongoCollection<Wager> collection = mock(MongoCollection.class);
        FindIterable<Wager> found = mock(FindIterable.class);
        when(wagers.mongoCollection()).thenReturn(collection);
        when(collection.find(nullable(ClientSession.class), any(Bson.class))).thenReturn(found);
        when(collection.find(any(Bson.class))).thenReturn(found);
        when(found.into(any())).thenAnswer(invocation -> {
            List<Wager> out = invocation.getArgument(0);
            out.add(yes);
            out.add(no);
            return out;
        });
        when(tx.inTransaction(any(Function.class)))
                .thenAnswer(invocation -> ((Function<ClientSession, Object>) invocation.getArgument(0)).apply(null));
    }

    private Wager wager(Outcome outcome, long amount) {
        Wager wager = new Wager();
        wager.id = new ObjectId();
        wager.userId = new ObjectId();
        wager.predictionId = prediction == null ? new ObjectId() : prediction.id;
        wager.outcome = outcome;
        wager.amountSisu = amount;
        wager.status = WagerStatus.OPEN;
        return wager;
    }

    @Test
    @SuppressWarnings("unchecked")
    void successfulResolutionLogsExactlyOneResolvedEventWithContext() {
        when(users.findById(any(ObjectId.class))).thenAnswer(invocation -> {
            ObjectId id = invocation.getArgument(0);
            if (id.equals(resolverId)) {
                User resolver = new User();
                resolver.id = resolverId;
                resolver.role = Role.RESOLVER;
                return resolver;
            }
            return null;
        });

        service.resolve(resolverId, prediction.id.toHexString(), Outcome.YES);

        List<String> resolved = lines.stream()
                .filter(l -> l.startsWith("event=prediction-resolved"))
                .toList();
        assertEquals(1, resolved.size());
        String line = resolved.get(0);
        assertTrue(line.contains("predictionId=" + prediction.id.toHexString()), line);
        assertTrue(line.contains("resolverId=" + resolverId.toHexString()), line);
        assertTrue(line.contains("outcome=YES"), line);
        assertTrue(line.contains("winners=1"), line);
        // The only winning wager staked 200 of the 200 winning pool, so it takes the 100 losing pool too.
        assertTrue(line.contains("totalPaidSisu=300"), line);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rolledBackResolutionLogsNoResolvedEvent() {
        when(tx.inTransaction(any(Function.class)))
                .thenThrow(ApiException.conflict("PREDICTION_NOT_CLOSED", "not closed"));

        assertThrows(ApiException.class, () -> service.resolve(resolverId, prediction.id.toHexString(), Outcome.YES));

        assertTrue(lines.stream().noneMatch(l -> l.startsWith("event=prediction-resolved")), lines.toString());
    }
}
