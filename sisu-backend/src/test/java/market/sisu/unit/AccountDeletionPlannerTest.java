package market.sisu.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import market.sisu.prediction.Outcome;
import market.sisu.prediction.Prediction;
import market.sisu.prediction.PredictionStatus;
import market.sisu.user.AccountDeletionPlanner;
import market.sisu.user.AccountDeletionPlanner.OwnedPrediction;
import market.sisu.user.AccountDeletionPlanner.Plan;
import market.sisu.wager.Wager;
import market.sisu.wager.WagerStatus;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

/** Which wagers refund and which predictions cancel on account deletion (FR-036), no database. */
class AccountDeletionPlannerTest {

    private final ObjectId userId = new ObjectId();

    private Prediction prediction(PredictionStatus status) {
        Prediction prediction = new Prediction();
        prediction.id = new ObjectId();
        prediction.creatorId = userId;
        prediction.status = status;
        return prediction;
    }

    private Wager wager(ObjectId owner, ObjectId predictionId, long amount, WagerStatus status) {
        Wager wager = new Wager();
        wager.id = new ObjectId();
        wager.userId = owner;
        wager.predictionId = predictionId;
        wager.outcome = Outcome.YES;
        wager.amountSisu = amount;
        wager.status = status;
        return wager;
    }

    @Test
    void nothingOwnedAndNoWagersPlansNothing() {
        Plan plan = AccountDeletionPlanner.plan(userId, List.of(), List.of());

        assertTrue(plan.cancellations().isEmpty());
        assertTrue(plan.ownRefunds().isEmpty());
    }

    @Test
    void cancelsOwnPredictionAndRefundsEveryOpenWagerOnItIncludingOthers() {
        Prediction mine = prediction(PredictionStatus.OPEN);
        Wager byOther = wager(new ObjectId(), mine.id, 300, WagerStatus.OPEN);
        Wager byMe = wager(userId, mine.id, 50, WagerStatus.OPEN);

        Plan plan = AccountDeletionPlanner.plan(
                userId, List.of(new OwnedPrediction(mine, List.of(byOther, byMe))), List.of(byMe));

        assertEquals(1, plan.cancellations().size());
        assertEquals(mine.id, plan.cancellations().get(0).predictionId());
        assertEquals(2, plan.cancellations().get(0).refunds().size());
        assertEquals(350, plan.cancellations().get(0).refundedSisu());
        // The user's own wager on their own prediction is refunded once, via the cancellation.
        assertTrue(plan.ownRefunds().isEmpty());
    }

    @Test
    void ownOpenWagerOnSomeoneElsesPredictionIsRefundedWithPoolAdjustment() {
        Wager elsewhere = wager(userId, new ObjectId(), 200, WagerStatus.OPEN);

        Plan plan = AccountDeletionPlanner.plan(userId, List.of(), List.of(elsewhere));

        assertTrue(plan.cancellations().isEmpty());
        assertEquals(List.of(elsewhere), plan.ownRefunds());
    }

    @Test
    void resolvedAndCancelledPredictionsAreLeftUntouched() {
        Prediction resolved = prediction(PredictionStatus.RESOLVED);
        Prediction cancelled = prediction(PredictionStatus.CANCELLED);

        Plan plan = AccountDeletionPlanner.plan(
                userId,
                List.of(
                        new OwnedPrediction(resolved, List.of(wager(new ObjectId(), resolved.id, 10, WagerStatus.WON))),
                        new OwnedPrediction(cancelled, List.of())),
                List.of());

        assertTrue(plan.cancellations().isEmpty());
    }

    @Test
    void settledWagersAreNeverRefundedAgain() {
        Prediction mine = prediction(PredictionStatus.CLOSED);
        Wager open = wager(new ObjectId(), mine.id, 100, WagerStatus.OPEN);
        Wager alreadyRefunded = wager(new ObjectId(), mine.id, 40, WagerStatus.REFUNDED);
        Wager lost = wager(userId, new ObjectId(), 70, WagerStatus.LOST);

        Plan plan = AccountDeletionPlanner.plan(
                userId, List.of(new OwnedPrediction(mine, List.of(open, alreadyRefunded))), List.of(lost));

        assertEquals(List.of(open), plan.cancellations().get(0).refunds());
        assertTrue(plan.ownRefunds().isEmpty());
    }
}
