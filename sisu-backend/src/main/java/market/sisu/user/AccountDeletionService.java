package market.sisu.user;

import com.mongodb.client.ClientSession;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import market.sisu.common.ApiException;
import market.sisu.common.MongoTransactions;
import market.sisu.common.Sisu;
import market.sisu.prediction.OddsCalculator;
import market.sisu.prediction.Prediction;
import market.sisu.prediction.PredictionRepository;
import market.sisu.prediction.PredictionStatus;
import market.sisu.realtime.LiveEvents;
import market.sisu.realtime.SseBroadcaster;
import market.sisu.transaction.BalanceLedger;
import market.sisu.transaction.BalanceLedger.Related;
import market.sisu.transaction.TransactionReason;
import market.sisu.wager.Wager;
import market.sisu.wager.WagerRepository;
import market.sisu.wager.WagerStatus;
import org.bson.types.ObjectId;

/**
 * Account deletion (FR-033, FR-036): in one transaction, cancels the user's unresolved predictions
 * and refunds every wager on them (by any user), refunds the user's own remaining open wagers, and
 * scrubs the personal fields. Resolved history is kept de-identified (spec.md Assumptions).
 */
@ApplicationScoped
public class AccountDeletionService {

    private final UserRepository users;
    private final PredictionRepository predictions;
    private final WagerRepository wagers;
    private final BalanceLedger ledger;
    private final MongoTransactions tx;
    private final SseBroadcaster broadcaster;
    private final Clock clock;

    public AccountDeletionService(
            UserRepository users,
            PredictionRepository predictions,
            WagerRepository wagers,
            BalanceLedger ledger,
            MongoTransactions tx,
            SseBroadcaster broadcaster,
            Clock clock) {
        this.users = users;
        this.predictions = predictions;
        this.wagers = wagers;
        this.ledger = ledger;
        this.tx = tx;
        this.broadcaster = broadcaster;
        this.clock = clock;
    }

    /** Side effects to publish only after the transaction has committed. */
    private record Changes(
            Set<ObjectId> cancelledPredictions,
            Set<ObjectId> refundedUsers,
            Map<ObjectId, Prediction> adjustedPredictions) {}

    /**
     * @throws ApiException {@code 404 USER_NOT_FOUND} if the account does not exist or is already deleted
     */
    public void delete(ObjectId userId) {
        Instant now = clock.instant();
        Changes changes = tx.inTransaction(session -> {
            User user = users.findById(session, userId)
                    .filter(u -> u.deletedAt == null)
                    .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "No such user"));

            Set<ObjectId> cancelled = new LinkedHashSet<>();
            Set<ObjectId> refundedUsers = new LinkedHashSet<>();
            Map<ObjectId, Prediction> adjusted = new LinkedHashMap<>();

            // (b) Cancel own unresolved predictions and refund every wager on them.
            List<Prediction> owned = predictions
                    .mongoCollection()
                    .find(
                            session,
                            Filters.and(
                                    Filters.eq("creatorId", user.id),
                                    Filters.in("status", PredictionStatus.OPEN.name(), PredictionStatus.CLOSED.name())))
                    .into(new ArrayList<>());
            for (Prediction prediction : owned) {
                boolean moved = predictions.compareAndSetStatus(
                        session,
                        prediction.id,
                        Set.of(PredictionStatus.OPEN, PredictionStatus.CLOSED),
                        PredictionStatus.CANCELLED);
                if (!moved) {
                    continue;
                }
                cancelled.add(prediction.id);
                for (Wager wager : openWagersOn(session, prediction.id)) {
                    refund(session, wager, now);
                    refundedUsers.add(wager.userId);
                }
            }

            // (a) Refund the user's own remaining open wagers on other predictions.
            List<Wager> own = wagers.mongoCollection()
                    .find(
                            session,
                            Filters.and(Filters.eq("userId", user.id), Filters.eq("status", WagerStatus.OPEN.name())))
                    .into(new ArrayList<>());
            for (Wager wager : own) {
                refund(session, wager, now);
                predictions.removeFromPool(session, wager.predictionId, wager.outcome, Sisu.of(wager.amountSisu));
                adjusted.put(wager.predictionId, null);
            }
            for (ObjectId predictionId : new ArrayList<>(adjusted.keySet())) {
                adjusted.put(
                        predictionId,
                        predictions.findById(session, predictionId).orElse(null));
            }

            // Scrub personal data; the id and the de-identified history remain.
            users.mongoCollection()
                    .updateOne(
                            session,
                            Filters.eq("_id", user.id),
                            Updates.combine(
                                    Updates.set("username", null),
                                    Updates.set("passwordHash", null),
                                    Updates.set("deletedAt", now)));
            refundedUsers.remove(user.id);
            return new Changes(cancelled, refundedUsers, adjusted);
        });

        publish(changes);
    }

    private List<Wager> openWagersOn(ClientSession session, ObjectId predictionId) {
        return wagers.mongoCollection()
                .find(
                        session,
                        Filters.and(
                                Filters.eq("predictionId", predictionId),
                                Filters.eq("status", WagerStatus.OPEN.name())))
                .into(new ArrayList<>());
    }

    private void refund(ClientSession session, Wager wager, Instant now) {
        wager.status = WagerStatus.REFUNDED;
        wager.payoutSisu = wager.amountSisu;
        wager.settledAt = now;
        wagers.mongoCollection().replaceOne(session, Filters.eq("_id", wager.id), wager);
        ledger.credit(
                session,
                wager.userId,
                Sisu.of(wager.amountSisu),
                TransactionReason.REFUND,
                new Related(wager.id, wager.predictionId));
    }

    private void publish(Changes changes) {
        if (broadcaster == null) {
            return;
        }
        for (ObjectId predictionId : changes.cancelledPredictions()) {
            broadcaster.broadcastPredictionStatusChanged(new LiveEvents.PredictionStatusChanged(
                    predictionId.toHexString(), PredictionStatus.CANCELLED, null));
        }
        for (Prediction prediction : changes.adjustedPredictions().values()) {
            if (prediction == null) {
                continue;
            }
            OddsCalculator.Odds odds = OddsCalculator.odds(prediction.yesPoolSisu, prediction.noPoolSisu);
            broadcaster.broadcastOddsUpdated(new LiveEvents.OddsUpdated(
                    prediction.id.toHexString(),
                    odds.yesPercent(),
                    odds.noPercent(),
                    Math.addExact(prediction.yesPoolSisu, prediction.noPoolSisu)));
        }
        for (ObjectId refunded : changes.refundedUsers()) {
            User u = users.findById(refunded);
            if (u != null && u.deletedAt == null) {
                broadcaster.sendBalanceUpdated(refunded.toHexString(), u.balanceSisu);
            }
        }
    }
}
