package market.sisu.user;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import market.sisu.prediction.Prediction;
import market.sisu.prediction.PredictionStatus;
import market.sisu.wager.Wager;
import market.sisu.wager.WagerStatus;
import org.bson.types.ObjectId;

/**
 * The pure decision behind account deletion (FR-036): which of the user's predictions are cancelled,
 * which wagers are refunded, and which of those refunds must also shrink a pool. It touches no
 * database and no clock, so it is unit-testable in isolation (Constitution III); the service loads
 * the inputs, calls this, and persists the result inside one transaction.
 */
public final class AccountDeletionPlanner {

    private AccountDeletionPlanner() {}

    /** A prediction the user created, with its wagers as currently stored. */
    public record OwnedPrediction(Prediction prediction, List<Wager> wagers) {}

    /** One prediction to cancel and the open wagers on it (any user's) to refund. */
    public record Cancellation(ObjectId predictionId, List<Wager> refunds) {

        public long refundedSisu() {
            return refunds.stream().mapToLong(w -> w.amountSisu).sum();
        }
    }

    /**
     * @param cancellations predictions to cancel; each wager on them is refunded and the pool is not
     *     adjusted (a cancelled prediction's pools no longer matter)
     * @param ownRefunds the user's own open wagers on other predictions; refunded and removed from
     *     the pool they were placed in
     */
    public record Plan(List<Cancellation> cancellations, List<Wager> ownRefunds) {}

    /**
     * @param ownedPredictions every prediction the user created, with its wagers
     * @param ownWagers the user's own wagers on any prediction
     */
    public static Plan plan(ObjectId userId, List<OwnedPrediction> ownedPredictions, List<Wager> ownWagers) {
        List<Cancellation> cancellations = new ArrayList<>();
        Set<ObjectId> cancelledIds = new HashSet<>();

        for (OwnedPrediction owned : ownedPredictions) {
            PredictionStatus status = owned.prediction().status;
            boolean unresolved = status == PredictionStatus.OPEN || status == PredictionStatus.CLOSED;
            if (!unresolved) {
                continue;
            }
            List<Wager> open = owned.wagers().stream()
                    .filter(w -> w.status == WagerStatus.OPEN)
                    .toList();
            cancellations.add(new Cancellation(owned.prediction().id, open));
            cancelledIds.add(owned.prediction().id);
        }

        List<Wager> ownRefunds = ownWagers.stream()
                .filter(w -> w.userId.equals(userId))
                .filter(w -> w.status == WagerStatus.OPEN)
                .filter(w -> !cancelledIds.contains(w.predictionId))
                .toList();

        return new Plan(List.copyOf(cancellations), ownRefunds);
    }
}
