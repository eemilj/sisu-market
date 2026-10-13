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
import market.sisu.common.AuditLog;
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
import market.sisu.user.AccountDeletionPlanner.Cancellation;
import market.sisu.user.AccountDeletionPlanner.OwnedPrediction;
import market.sisu.user.AccountDeletionPlanner.Plan;
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
    private final AuditLog audit;

    public AccountDeletionService(
            UserRepository users,
            PredictionRepository predictions,
            WagerRepository wagers,
            BalanceLedger ledger,
            MongoTransactions tx,
            SseBroadcaster broadcaster,
            Clock clock,
            AuditLog audit) {
        this.users = users;
        this.predictions = predictions;
        this.wagers = wagers;
        this.ledger = ledger;
        this.tx = tx;
        this.broadcaster = broadcaster;
        this.clock = clock;
        this.audit = audit;
    }

    /** What a cancelled prediction cost, kept for logging and broadcasting after commit. */
    private record Cancelled(ObjectId predictionId, int refundedWagers, long refundedSisu) {}

    /** Side effects to publish only after the transaction has committed. */
    private record Changes(
            List<Cancelled> cancelled,
            Set<ObjectId> refundedUsers,
            Map<ObjectId, Prediction> adjustedPredictions,
            int ownWagersRefunded) {}

    /**
     * @throws ApiException {@code 404 USER_NOT_FOUND} if the account does not exist or is already deleted
     */
    public void delete(ObjectId userId) {
        Instant now = clock.instant();
        Changes changes = tx.inTransaction(session -> {
            User user = users.findById(session, userId)
                    .filter(u -> u.deletedAt == null)
                    .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "No such user"));

            // Load what the decision needs, then let the pure planner decide.
            List<Prediction> created = predictions
                    .mongoCollection()
                    .find(
                            session,
                            Filters.and(
                                    Filters.eq("creatorId", user.id),
                                    Filters.in("status", PredictionStatus.OPEN.name(), PredictionStatus.CLOSED.name())))
                    .into(new ArrayList<>());
            List<OwnedPrediction> owned = new ArrayList<>();
            for (Prediction prediction : created) {
                owned.add(new OwnedPrediction(prediction, openWagersOn(session, prediction.id)));
            }
            List<Wager> ownWagers = wagers.mongoCollection()
                    .find(
                            session,
                            Filters.and(Filters.eq("userId", user.id), Filters.eq("status", WagerStatus.OPEN.name())))
                    .into(new ArrayList<>());
            Plan plan = AccountDeletionPlanner.plan(user.id, owned, ownWagers);

            List<Cancelled> cancelled = new ArrayList<>();
            Set<ObjectId> refundedUsers = new LinkedHashSet<>();
            Map<ObjectId, Prediction> adjusted = new LinkedHashMap<>();
            int ownRefunded = 0;

            // (b) Cancel own unresolved predictions and refund every wager on them.
            for (Cancellation cancellation : plan.cancellations()) {
                boolean moved = predictions.compareAndSetStatus(
                        session,
                        cancellation.predictionId(),
                        Set.of(PredictionStatus.OPEN, PredictionStatus.CLOSED),
                        PredictionStatus.CANCELLED);
                if (!moved) {
                    continue;
                }
                for (Wager wager : cancellation.refunds()) {
                    refund(session, wager, now);
                    refundedUsers.add(wager.userId);
                    if (wager.userId.equals(user.id)) {
                        ownRefunded++;
                    }
                }
                cancelled.add(new Cancelled(
                        cancellation.predictionId(), cancellation.refunds().size(), cancellation.refundedSisu()));
            }

            // (a) Refund the user's own remaining open wagers on other predictions.
            for (Wager wager : plan.ownRefunds()) {
                refund(session, wager, now);
                predictions.removeFromPool(session, wager.predictionId, wager.outcome, Sisu.of(wager.amountSisu));
                adjusted.put(wager.predictionId, null);
                ownRefunded++;
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
            return new Changes(cancelled, refundedUsers, adjusted, ownRefunded);
        });

        for (Cancelled c : changes.cancelled()) {
            audit.predictionCancelled(
                    c.predictionId().toHexString(),
                    userId.toHexString(),
                    "ACCOUNT_DELETED",
                    c.refundedWagers(),
                    c.refundedSisu());
        }
        audit.accountDeleted(userId.toHexString(), changes.ownWagersRefunded());
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
        for (Cancelled c : changes.cancelled()) {
            broadcaster.broadcastPredictionStatusChanged(new LiveEvents.PredictionStatusChanged(
                    c.predictionId().toHexString(), PredictionStatus.CANCELLED, null));
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
