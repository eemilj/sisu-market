package market.sisu.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mongodb.client.ClientSession;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import market.sisu.common.ApiException;
import market.sisu.common.MongoTransactions;
import market.sisu.common.Sisu;
import market.sisu.prediction.Outcome;
import market.sisu.prediction.Prediction;
import market.sisu.prediction.PredictionRepository;
import market.sisu.prediction.PredictionStatus;
import market.sisu.realtime.LiveEvents;
import market.sisu.realtime.SseBroadcaster;
import market.sisu.transaction.BalanceLedger;
import market.sisu.transaction.BalanceLedger.Related;
import market.sisu.transaction.TransactionReason;
import market.sisu.wager.PlaceWagerRequest;
import market.sisu.wager.Wager;
import market.sisu.wager.WagerRepository;
import market.sisu.wager.WagerService;
import market.sisu.wager.WagerStatus;
import market.sisu.wager.WagerView;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * Placing a wager (User Story 3; FR-019 through FR-025) without a database: every write goes
 * through one transaction's session, rejections happen before anything is written, and live
 * updates go out only after the transaction has committed. The real-MongoDB counterpart, with
 * concurrent wagers racing for one balance, is {@code integration/WagerConcurrencyTest}.
 */
class WagerServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    private WagerRepository wagers;
    private PredictionRepository predictions;
    private BalanceLedger ledger;
    private MongoTransactions tx;
    private SseBroadcaster live;
    private ClientSession session;
    private WagerService service;
    private ObjectId userId;
    private Prediction prediction;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        wagers = mock(WagerRepository.class);
        predictions = mock(PredictionRepository.class);
        ledger = mock(BalanceLedger.class);
        tx = mock(MongoTransactions.class);
        live = mock(SseBroadcaster.class);
        session = mock(ClientSession.class);
        when(tx.inTransaction(any()))
                .thenAnswer(call -> call.getArgument(0, Function.class).apply(session));
        service = new WagerService(wagers, predictions, ledger, tx, live, Clock.fixed(NOW, ZoneOffset.UTC));

        userId = new ObjectId();
        prediction = new Prediction();
        prediction.id = new ObjectId();
        prediction.question = "Sataako huomenna?";
        prediction.status = PredictionStatus.OPEN;
        prediction.closingAt = NOW.plusSeconds(3600);
        prediction.yesPoolSisu = 600;
        prediction.noPoolSisu = 400;
        when(predictions.findById(session, prediction.id)).thenReturn(Optional.of(prediction));
        when(predictions.addToPool(eq(session), eq(prediction.id), any(), any(), eq(NOW)))
                .thenReturn(true);
        when(ledger.debit(eq(session), eq(userId), any(), eq(TransactionReason.WAGER_PLACED), any()))
                .thenReturn(Sisu.of(600));
    }

    @Test
    void debitsTheBalanceAddsToThePoolAndRecordsTheWagerInOneTransaction() {
        WagerView placed = place(Outcome.NO, "400");

        ArgumentCaptor<Wager> saved = ArgumentCaptor.forClass(Wager.class);
        verify(wagers).insert(eq(session), saved.capture());
        Wager wager = saved.getValue();
        assertEquals(userId, wager.userId);
        assertEquals(prediction.id, wager.predictionId);
        assertEquals(Outcome.NO, wager.outcome);
        assertEquals(400, wager.amountSisu);
        assertEquals(WagerStatus.OPEN, wager.status);
        assertEquals(NOW, wager.placedAt);
        assertNull(wager.payoutSisu);

        // The ledger entry points at this very wager, and the pool write re-checks the clock.
        verify(ledger)
                .debit(
                        session,
                        userId,
                        Sisu.of(400),
                        TransactionReason.WAGER_PLACED,
                        new Related(wager.id, prediction.id));
        verify(predictions).addToPool(session, prediction.id, Outcome.NO, Sisu.of(400), NOW);

        assertEquals(wager.id.toHexString(), placed.id());
        assertEquals(prediction.id.toHexString(), placed.predictionId());
        assertEquals("Sataako huomenna?", placed.predictionQuestion());
        assertEquals(Outcome.NO, placed.outcome());
        assertEquals(400, placed.amountSisu());
        assertEquals(WagerStatus.OPEN, placed.status());
        // Pools after the wager: 600 yes / 800 no; 400 of the 800 would win half of the 600.
        assertEquals(700, placed.potentialPayoutSisu());
        assertEquals(NOW, placed.placedAt());
    }

    @Test
    void publishesTheNewBalanceAndOddsOnlyAfterTheTransactionCommits() {
        place(Outcome.NO, "400");

        InOrder order = inOrder(tx, live);
        order.verify(tx).inTransaction(any());
        order.verify(live).sendBalanceUpdated(userId.toHexString(), 600);
        order.verify(live)
                .broadcastOddsUpdated(new LiveEvents.OddsUpdated(prediction.id.toHexString(), 42.9, 57.1, 1400));
    }

    @Test
    void rejectsAWagerOnceClosingTimeHasPassedEvenWhileTheCachedStatusIsStillOpen() {
        // FR-025: the closing job has not flipped the status yet, but the clock says it is closed.
        prediction.status = PredictionStatus.OPEN;
        prediction.closingAt = NOW;

        assertRejected(409, "PREDICTION_NOT_OPEN", () -> place(Outcome.YES, "10"));
        assertNothingWritten();
    }

    @Test
    void rejectsAWagerOnAClosedResolvedOrCancelledPrediction() {
        for (PredictionStatus status :
                List.of(PredictionStatus.CLOSED, PredictionStatus.RESOLVED, PredictionStatus.CANCELLED)) {
            prediction.status = status;
            assertRejected(409, "PREDICTION_NOT_OPEN", () -> place(Outcome.YES, "10"));
        }
        assertNothingWritten();
    }

    @Test
    void rejectsAWagerWhenThePredictionClosesBetweenTheCheckAndThePoolWrite() {
        when(predictions.addToPool(eq(session), eq(prediction.id), any(), any(), eq(NOW)))
                .thenReturn(false);

        assertRejected(409, "PREDICTION_NOT_OPEN", () -> place(Outcome.YES, "10"));
        // Throwing aborts the transaction, which also rolls back the debit already made in it.
        verify(wagers, never()).insert(any(), any());
        verifyNoInteractions(live);
    }

    @Test
    void rejectsAWagerOverTheBalanceWithoutRecordingIt() {
        when(ledger.debit(eq(session), eq(userId), any(), eq(TransactionReason.WAGER_PLACED), any()))
                .thenThrow(ApiException.unprocessable("INSUFFICIENT_BALANCE", "Amount exceeds the current balance"));

        assertRejected(422, "INSUFFICIENT_BALANCE", () -> place(Outcome.YES, "5000"));
        verify(predictions, never()).addToPool(any(), any(), any(), any(), any());
        verify(wagers, never()).insert(any(), any());
        verifyNoInteractions(live);
    }

    @Test
    void rejectsAmountsThatAreNotPositiveWholeNumbersBeforeTouchingTheDatabase() {
        for (String amount : List.of("0", "-5", "1.5", "0.0001", "99999999999999999999")) {
            assertRejected(422, "INVALID_WAGER_AMOUNT", () -> place(Outcome.YES, amount));
        }
        assertRejected(
                422,
                "INVALID_WAGER_AMOUNT",
                () -> service.placeWager(
                        userId, prediction.id.toHexString(), new PlaceWagerRequest(Outcome.YES, null)));
        verifyNoInteractions(tx);
    }

    @Test
    void acceptsAWholeAmountWrittenWithADecimalPoint() {
        WagerView placed = place(Outcome.YES, "25.00");
        assertEquals(25, placed.amountSisu());
    }

    @Test
    void requiresAnOutcome() {
        assertRejected(
                422,
                "VALIDATION_FAILED",
                () -> service.placeWager(
                        userId, prediction.id.toHexString(), new PlaceWagerRequest(null, BigDecimal.TEN)));
        assertRejected(422, "VALIDATION_FAILED", () -> service.placeWager(userId, prediction.id.toHexString(), null));
        verifyNoInteractions(tx);
    }

    @Test
    void rejectsAnUnknownOrMalformedPrediction() {
        ObjectId unknown = new ObjectId();
        when(predictions.findById(session, unknown)).thenReturn(Optional.empty());

        assertRejected(
                404,
                "PREDICTION_NOT_FOUND",
                () -> service.placeWager(
                        userId, unknown.toHexString(), new PlaceWagerRequest(Outcome.YES, BigDecimal.ONE)));
        assertRejected(
                404,
                "PREDICTION_NOT_FOUND",
                () -> service.placeWager(userId, "not-an-id", new PlaceWagerRequest(Outcome.YES, BigDecimal.ONE)));
        assertNothingWritten();
    }

    @Test
    void aDeletedAccountCannotWager() {
        when(ledger.debit(eq(session), eq(userId), any(), eq(TransactionReason.WAGER_PLACED), any()))
                .thenThrow(ApiException.notFound("USER_NOT_FOUND", "No such user"));

        assertRejected(401, "UNAUTHENTICATED", () -> place(Outcome.YES, "10"));
        verifyNoInteractions(live);
    }

    @Test
    void listsOpenWagersWithTheirPotentialPayoutAtCurrentPools() {
        Prediction other = new Prediction();
        other.id = new ObjectId();
        other.question = "Voittaako Suomi?";
        other.yesPoolSisu = 100;
        other.noPoolSisu = 1000;
        Wager onFirst = openWager(prediction.id, Outcome.NO, 400);
        Wager onOther = openWager(other.id, Outcome.YES, 100);
        when(wagers.findOpenByUser(userId)).thenReturn(List.of(onOther, onFirst));
        when(predictions.findByIds(any())).thenReturn(List.of(prediction, other));

        List<WagerView> open = service.openWagers(userId);

        assertEquals(2, open.size());
        assertEquals("Voittaako Suomi?", open.get(0).predictionQuestion());
        assertEquals(1100, open.get(0).potentialPayoutSisu());
        assertEquals("Sataako huomenna?", open.get(1).predictionQuestion());
        // 400 of the 400 on "no" would take all 600 on "yes".
        assertEquals(1000, open.get(1).potentialPayoutSisu());
    }

    @Test
    void listingNoOpenWagersSkipsThePredictionLookup() {
        when(wagers.findOpenByUser(userId)).thenReturn(List.of());

        assertEquals(List.of(), service.openWagers(userId));
        verify(predictions, never()).findByIds(any());
    }

    private WagerView place(Outcome outcome, String amount) {
        return service.placeWager(
                userId, prediction.id.toHexString(), new PlaceWagerRequest(outcome, new BigDecimal(amount)));
    }

    private Wager openWager(ObjectId predictionId, Outcome outcome, long amount) {
        Wager wager = new Wager();
        wager.id = new ObjectId();
        wager.userId = userId;
        wager.predictionId = predictionId;
        wager.outcome = outcome;
        wager.amountSisu = amount;
        wager.status = WagerStatus.OPEN;
        wager.placedAt = NOW;
        return wager;
    }

    private void assertNothingWritten() {
        verify(ledger, never()).debit(any(), any(), any(), any(), any());
        verify(predictions, never()).addToPool(any(), any(), any(), any(), any());
        verify(wagers, never()).insert(any(), any());
        verify(live, never()).sendBalanceUpdated(anyString(), anyLong());
        verify(live, never()).broadcastOddsUpdated(any());
    }

    private static void assertRejected(int status, String code, Runnable call) {
        ApiException e = assertThrows(ApiException.class, call::run);
        assertEquals(status, e.status());
        assertEquals(code, e.code());
    }
}
