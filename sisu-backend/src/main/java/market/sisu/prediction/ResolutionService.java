package market.sisu.prediction;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import market.sisu.common.ApiException;
import market.sisu.common.AuditLog;
import market.sisu.common.MongoTransactions;
import market.sisu.common.Sisu;
import market.sisu.realtime.LiveEvents;
import market.sisu.realtime.SseBroadcaster;
import market.sisu.transaction.BalanceLedger;
import market.sisu.transaction.TransactionReason;
import market.sisu.user.Role;
import market.sisu.user.User;
import market.sisu.user.UserRepository;
import market.sisu.wager.Wager;
import market.sisu.wager.WagerRepository;
import market.sisu.wager.WagerStatus;
import org.bson.types.ObjectId;

/**
 * Resolution service for User Story 5 (FR-026, FR-027, FR-034, FR-035): settles a CLOSED prediction
 * to a winning outcome, calculates pari-mutuel payouts for winning wagers, credits balances and
 * records payout transactions atomically, and broadcasts updates via SSE.
 */
@ApplicationScoped
public class ResolutionService {

    private final PredictionRepository predictions;
    private final WagerRepository wagers;
    private final UserRepository users;
    private final BalanceLedger ledger;
    private final MongoTransactions tx;
    private final SseBroadcaster broadcaster;
    private final Clock clock;
    private final AuditLog audit;

    public ResolutionService(
            PredictionRepository predictions,
            WagerRepository wagers,
            UserRepository users,
            BalanceLedger ledger,
            MongoTransactions tx,
            SseBroadcaster broadcaster,
            Clock clock,
            AuditLog audit) {
        this.predictions = predictions;
        this.wagers = wagers;
        this.users = users;
        this.ledger = ledger;
        this.tx = tx;
        this.broadcaster = broadcaster;
        this.clock = clock;
        this.audit = audit;
    }

    /** What a committed resolution did, carried out of the transaction for logging. */
    private record Resolved(Prediction prediction, int winners, long totalPaidSisu) {}

    public PredictionDetail resolve(ObjectId resolverId, String predictionIdStr, Outcome winningOutcome) {
        if (!ObjectId.isValid(predictionIdStr)) {
            throw ApiException.notFound("PREDICTION_NOT_FOUND", "No such prediction");
        }
        ObjectId predictionId = new ObjectId(predictionIdStr);

        User resolver = users.findById(resolverId);
        if (resolver == null || resolver.deletedAt != null || resolver.role != Role.RESOLVER) {
            throw ApiException.forbidden("FORBIDDEN", "Not allowed");
        }

        Instant now = clock.instant();

        // Perform transactional resolution, payout, and status update
        Resolved resolved = tx.inTransaction(session -> {
            Prediction p = predictions
                    .findById(session, predictionId)
                    .orElseThrow(() -> ApiException.notFound("PREDICTION_NOT_FOUND", "No such prediction"));

            if (p.status != PredictionStatus.CLOSED) {
                throw ApiException.conflict("PREDICTION_NOT_CLOSED", "Prediction must be CLOSED to be resolved");
            }

            boolean updated = predictions.compareAndSetStatus(
                    session,
                    predictionId,
                    Set.of(PredictionStatus.CLOSED),
                    PredictionStatus.RESOLVED,
                    Updates.set("winningOutcome", winningOutcome.name()),
                    Updates.set("resolvedAt", now),
                    Updates.set("resolvedBy", resolverId));

            if (!updated) {
                throw ApiException.conflict("PREDICTION_NOT_CLOSED", "Prediction must be CLOSED to be resolved");
            }

            // Fetch all wagers for this prediction
            List<Wager> predictionWagers = wagers.mongoCollection()
                    .find(session, Filters.eq("predictionId", predictionId))
                    .into(new ArrayList<>());

            long winningPool = winningOutcome == Outcome.YES ? p.yesPoolSisu : p.noPoolSisu;
            long losingPool = winningOutcome == Outcome.YES ? p.noPoolSisu : p.yesPoolSisu;

            int winners = 0;
            long totalPaid = 0;
            for (Wager wager : predictionWagers) {
                if (wager.status != WagerStatus.OPEN) {
                    continue;
                }
                if (wager.outcome == winningOutcome && winningPool > 0) {
                    long payout = OddsCalculator.potentialPayout(wager.amountSisu, winningPool, losingPool);
                    wager.status = WagerStatus.WON;
                    wager.payoutSisu = payout;
                    winners++;
                    totalPaid += payout;
                    wager.settledAt = now;
                    wagers.mongoCollection().replaceOne(session, Filters.eq("_id", wager.id), wager);

                    ledger.credit(
                            session,
                            wager.userId,
                            Sisu.of(payout),
                            TransactionReason.PAYOUT,
                            new BalanceLedger.Related(wager.id, predictionId));
                } else {
                    wager.status = WagerStatus.LOST;
                    wager.payoutSisu = 0L;
                    wager.settledAt = now;
                    wagers.mongoCollection().replaceOne(session, Filters.eq("_id", wager.id), wager);
                }
            }

            p.status = PredictionStatus.RESOLVED;
            p.winningOutcome = winningOutcome;
            p.resolvedAt = now;
            p.resolvedBy = resolverId;
            return new Resolved(p, winners, totalPaid);
        });
        Prediction prediction = resolved.prediction();
        audit.predictionResolved(
                predictionIdStr,
                resolverId.toHexString(),
                winningOutcome.name(),
                resolved.winners(),
                resolved.totalPaidSisu());

        // Broadcast SSE updates after successful transaction commit
        if (broadcaster != null) {
            broadcaster.broadcastPredictionStatusChanged(
                    new LiveEvents.PredictionStatusChanged(predictionIdStr, PredictionStatus.RESOLVED, winningOutcome));
            // Also broadcast updated balances for affected wager users
            List<Wager> updatedWagers = wagers.mongoCollection()
                    .find(Filters.eq("predictionId", predictionId))
                    .into(new ArrayList<>());
            Set<ObjectId> affectedUsers = new HashSet<>();
            for (Wager w : updatedWagers) {
                if (w.outcome == winningOutcome && w.status == WagerStatus.WON) {
                    affectedUsers.add(w.userId);
                }
            }
            for (ObjectId userId : affectedUsers) {
                User u = users.findById(userId);
                if (u != null) {
                    broadcaster.sendBalanceUpdated(userId.toHexString(), u.balanceSisu);
                }
            }
        }

        User creator = prediction.creatorId != null ? users.findById(prediction.creatorId) : null;
        String creatorUsername = creator != null ? creator.username : null;
        return PredictionDetail.of(prediction, now, creatorUsername);
    }
}
