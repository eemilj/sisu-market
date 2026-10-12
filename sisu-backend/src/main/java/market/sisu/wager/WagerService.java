package market.sisu.wager;

import com.mongodb.client.ClientSession;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import market.sisu.common.ApiException;
import market.sisu.common.MongoTransactions;
import market.sisu.common.Sisu;
import market.sisu.prediction.OddsCalculator;
import market.sisu.prediction.OddsCalculator.Odds;
import market.sisu.prediction.Outcome;
import market.sisu.prediction.Prediction;
import market.sisu.prediction.PredictionRepository;
import market.sisu.prediction.PredictionStatus;
import market.sisu.realtime.LiveEvents;
import market.sisu.realtime.SseBroadcaster;
import market.sisu.transaction.BalanceLedger;
import market.sisu.transaction.BalanceLedger.Related;
import market.sisu.transaction.TransactionReason;
import org.bson.types.ObjectId;

/**
 * Wagering for User Story 3 (FR-019 through FR-025): a logged-in user stakes sisu on one side of an
 * open prediction, and their open wagers show what they would pay out.
 */
@ApplicationScoped
public class WagerService {

    private final WagerRepository wagers;
    private final PredictionRepository predictions;
    private final BalanceLedger ledger;
    private final MongoTransactions tx;
    private final SseBroadcaster live;
    private final Clock clock;

    public WagerService(
            WagerRepository wagers,
            PredictionRepository predictions,
            BalanceLedger ledger,
            MongoTransactions tx,
            SseBroadcaster live,
            Clock clock) {
        this.wagers = wagers;
        this.predictions = predictions;
        this.ledger = ledger;
        this.tx = tx;
        this.live = live;
        this.clock = clock;
    }

    /**
     * Places a wager. The balance debit and its ledger entry, the pool increment and the wager
     * itself are written in one MongoDB transaction, so they all happen or none do (FR-022,
     * data-model.md invariants 1, 3 and 5). Once it has committed, the new balance goes to the
     * user's own live connections and the new odds to everyone (FR-023, contracts/realtime.md).
     *
     * @throws ApiException {@code 422 VALIDATION_FAILED} without an outcome,
     *     {@code 422 INVALID_WAGER_AMOUNT} unless the amount is a positive whole number (FR-019),
     *     {@code 404 PREDICTION_NOT_FOUND} for an unknown prediction,
     *     {@code 409 PREDICTION_NOT_OPEN} if the prediction is closed, resolved or cancelled or its
     *     closing time has passed (FR-021, FR-025), {@code 422 INSUFFICIENT_BALANCE} if the amount
     *     exceeds the balance (FR-020), {@code 401 UNAUTHENTICATED} if the account no longer exists
     */
    public WagerView placeWager(ObjectId userId, String predictionId, PlaceWagerRequest request) {
        if (request == null || request.outcome() == null) {
            throw ApiException.unprocessable("VALIDATION_FAILED", "Outcome must be YES or NO");
        }
        Sisu amount = wholePositiveSisu(request.amountSisu());
        if (!ObjectId.isValid(predictionId)) {
            throw predictionNotFound();
        }
        ObjectId id = new ObjectId(predictionId);
        Instant now = clock.instant();

        Placed placed;
        try {
            placed = tx.inTransaction(session -> place(session, userId, id, request.outcome(), amount, now));
        } catch (ApiException e) {
            if ("USER_NOT_FOUND".equals(e.code())) {
                throw ApiException.unauthorized("UNAUTHENTICATED", "Login required");
            }
            throw e;
        }

        // Only now: the transaction callback may run more than once, but it has committed exactly once.
        Prediction prediction = placed.prediction();
        Odds odds = OddsCalculator.odds(prediction.yesPoolSisu, prediction.noPoolSisu);
        live.sendBalanceUpdated(userId.toHexString(), placed.balance().amount());
        live.broadcastOddsUpdated(new LiveEvents.OddsUpdated(
                prediction.id.toHexString(),
                odds.yesPercent(),
                odds.noPercent(),
                Math.addExact(prediction.yesPoolSisu, prediction.noPoolSisu)));
        return placed.wager();
    }

    /** The user's own open wagers, most recent first, with potential payouts at today's pools (FR-024). */
    public List<WagerView> openWagers(ObjectId userId) {
        List<Wager> open = wagers.findOpenByUser(userId);
        if (open.isEmpty()) {
            return List.of();
        }
        Map<ObjectId, Prediction> byId =
                predictions
                        .findByIds(open.stream()
                                .map(w -> w.predictionId)
                                .distinct()
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(p -> p.id, Function.identity()));
        return open.stream()
                .map(wager -> WagerView.of(wager, byId.get(wager.predictionId)))
                .toList();
    }

    private Placed place(
            ClientSession session, ObjectId userId, ObjectId predictionId, Outcome outcome, Sisu amount, Instant now) {
        Prediction prediction =
                predictions.findById(session, predictionId).orElseThrow(WagerService::predictionNotFound);
        // FR-025: judged by the clock, not only the cached status the closing job updates.
        if (prediction.statusAt(now) != PredictionStatus.OPEN) {
            throw predictionNotOpen();
        }

        Wager wager = new Wager();
        wager.id = new ObjectId();
        wager.userId = userId;
        wager.predictionId = predictionId;
        wager.outcome = outcome;
        wager.amountSisu = amount.amount();
        wager.status = WagerStatus.OPEN;
        wager.placedAt = now;

        Sisu balance = ledger.debit(
                session, userId, amount, TransactionReason.WAGER_PLACED, new Related(wager.id, predictionId));
        // Re-checks status and closing time in the write itself; throwing rolls back the debit too.
        if (!predictions.addToPool(session, predictionId, outcome, amount, now)) {
            throw predictionNotOpen();
        }
        wagers.insert(session, wager);

        // Snapshot isolation: no other transaction changed these pools between the read and our write.
        if (outcome == Outcome.YES) {
            prediction.yesPoolSisu = Math.addExact(prediction.yesPoolSisu, amount.amount());
        } else {
            prediction.noPoolSisu = Math.addExact(prediction.noPoolSisu, amount.amount());
        }
        return new Placed(WagerView.of(wager, prediction), balance, prediction);
    }

    private static Sisu wholePositiveSisu(BigDecimal amount) {
        if (amount == null
                || amount.signum() <= 0
                || amount.stripTrailingZeros().scale() > 0) {
            throw invalidAmount();
        }
        try {
            return Sisu.of(amount.longValueExact());
        } catch (ArithmeticException tooLarge) {
            throw invalidAmount();
        }
    }

    private static ApiException invalidAmount() {
        return ApiException.unprocessable("INVALID_WAGER_AMOUNT", "Amount must be a positive whole number of sisu");
    }

    private static ApiException predictionNotFound() {
        return ApiException.notFound("PREDICTION_NOT_FOUND", "No such prediction");
    }

    private static ApiException predictionNotOpen() {
        return ApiException.conflict("PREDICTION_NOT_OPEN", "Prediction is not open for wagers");
    }

    /** What one committed transaction produced: the wager, the balance after it and the pools after it. */
    private record Placed(WagerView wager, Sisu balance, Prediction prediction) {}
}
