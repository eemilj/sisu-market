package fi.sisu.market.resolution;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import fi.sisu.market.common.ApiException;
import fi.sisu.market.common.ErrorCode;
import fi.sisu.market.live.EventHub;
import fi.sisu.market.live.LiveEvent;
import fi.sisu.market.prediction.Outcome;
import fi.sisu.market.prediction.Prediction;
import fi.sisu.market.prediction.PredictionDto;
import fi.sisu.market.prediction.PredictionRepository;
import fi.sisu.market.prediction.PredictionStatus;
import fi.sisu.market.user.AdminPolicy;
import fi.sisu.market.user.User;
import fi.sisu.market.wager.Wager;
import fi.sisu.market.wager.WagerRepository;
import fi.sisu.market.wallet.TransactionType;
import fi.sisu.market.wallet.WalletService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;

import java.time.Instant;

@ApplicationScoped
public class ResolutionService {

    @Inject
    WalletService wallet;

    @Inject
    PredictionRepository predictions;

    @Inject
    WagerRepository wagers;

    @Inject
    AdminPolicy adminPolicy;

    @Inject
    EventHub events;

    /**
     * Who may resolve: admins, or the prediction's creator as long as they have no stake in it
     * (so nobody decides a market they would profit from).
     */
    public boolean mayResolve(User user, Prediction p) {
        if (adminPolicy.isAdmin(user)) {
            return true;
        }
        return user.id.equals(p.createdById) && !wagers.hasActiveWager(user.id, p.id);
    }

    /**
     * Resolves a closed prediction and settles every wager on it, all in one transaction: the prediction moves
     * to RESOLVED only if it is not resolved yet, each winning wager is paid its locked {@code payoutIfWin}
     * (with a ledger entry), and every wager is marked WON or LOST. If anything fails, nothing is paid and
     * the prediction stays unresolved. A second resolution can never match the status filter, and two
     * concurrent ones conflict on the prediction document so only one commits.
     */
    public ResolutionResult resolve(ObjectId predictionId, Outcome outcome, User resolver) {
        ResolutionResult result = wallet.inTransaction(session -> {
            Instant now = Instant.now();
            Prediction p = predictions.mongoCollection().find(session, Filters.eq("_id", predictionId)).first();
            if (p == null) {
                throw ApiException.notFound(ErrorCode.PREDICTION_NOT_FOUND, "Prediction not found");
            }
            if (!mayResolve(resolver, p)) {
                throw resolver.id.equals(p.createdById)
                        ? ApiException.forbidden(ErrorCode.RESOLVE_NOT_ALLOWED_HAS_STAKE,
                                "You have wagered on this prediction, so an admin must resolve it")
                        : ApiException.forbidden(ErrorCode.RESOLVE_NOT_ALLOWED,
                                "Only an admin or the prediction's creator can resolve it");
            }
            switch (p.effectiveStatus(now)) {
                case RESOLVED -> throw ApiException.conflict(ErrorCode.ALREADY_RESOLVED,
                        "This prediction has already been resolved " + p.outcome).with("outcome", p.outcome);
                case OPEN -> throw ApiException.conflict(ErrorCode.PREDICTION_STILL_OPEN,
                        "This prediction is still open; it can be resolved after it closes").with("closesAt", p.closesAt);
                case CLOSED -> { /* ok */ }
            }

            long claimed = predictions.mongoCollection().updateOne(session,
                    Filters.and(
                            Filters.eq("_id", predictionId),
                            Filters.in("status", PredictionStatus.OPEN.name(), PredictionStatus.CLOSED.name()),
                            Filters.lte("closesAt", now)),
                    Updates.combine(
                            Updates.set("status", PredictionStatus.RESOLVED.name()),
                            Updates.set("outcome", outcome.name()),
                            Updates.set("resolvedAt", now),
                            Updates.set("resolvedById", resolver.id),
                            Updates.inc("version", 1))).getModifiedCount();
            if (claimed == 0) {
                throw ApiException.conflict(ErrorCode.ALREADY_RESOLVED, "This prediction has already been resolved");
            }

            Settlement.Result settlement = Settlement.settle(wagers.openWagersFor(session, predictionId), outcome);
            for (Settlement.Line line : settlement.lines()) {
                Wager w = line.wager();
                if (line.payout() > 0) {
                    wallet.apply(session, w.userId, line.payout(), TransactionType.PAYOUT,
                            "Payout: " + w.side + " won on " + p.question, predictionId);
                }
                if (!wagers.settle(session, w.id, line.status(), line.payout(), now)) {
                    throw new IllegalStateException("Wager " + w.id + " was settled concurrently");
                }
            }

            p.status = PredictionStatus.RESOLVED;
            p.outcome = outcome;
            p.resolvedAt = now;
            p.resolvedById = resolver.id;
            p.version++;
            return new ResolutionResult(PredictionDto.from(p, now), settlement.winners(), settlement.losers(),
                    settlement.totalPaid());
        });
        // Winners' balances are announced by WalletService; this tells everyone the outcome.
        events.publish(LiveEvent.toEveryone(LiveEvent.PREDICTION_UPDATED, result.prediction()));
        return result;
    }
}
