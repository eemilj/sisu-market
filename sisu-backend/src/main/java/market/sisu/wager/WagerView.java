package market.sisu.wager;

import java.time.Instant;
import market.sisu.prediction.OddsCalculator;
import market.sisu.prediction.Outcome;
import market.sisu.prediction.Prediction;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * A wager as the API shows it to its owner (contracts/openapi.yaml {@code Wager}; FR-024).
 *
 * @param predictionQuestion the wagered-on question, so a list of wagers reads on its own;
 *     {@code null} if the prediction cannot be found
 * @param potentialPayoutSisu while {@code OPEN}, what the wager would pay if its side won at the
 *     current pools; once settled, the final payout ({@code 0} for a lost wager)
 */
@Schema(name = "Wager")
public record WagerView(
        String id,
        String predictionId,
        String predictionQuestion,
        Outcome outcome,
        long amountSisu,
        WagerStatus status,
        long potentialPayoutSisu,
        Instant placedAt) {

    /** {@code prediction} supplies the question and, for an open wager, the current pools. */
    public static WagerView of(Wager wager, Prediction prediction) {
        return new WagerView(
                wager.id.toHexString(),
                wager.predictionId.toHexString(),
                prediction == null ? null : prediction.question,
                wager.outcome,
                wager.amountSisu,
                wager.status,
                payout(wager, prediction),
                wager.placedAt);
    }

    private static long payout(Wager wager, Prediction prediction) {
        if (wager.status != WagerStatus.OPEN) {
            return wager.payoutSisu == null ? 0 : wager.payoutSisu;
        }
        if (prediction == null) {
            return wager.amountSisu;
        }
        boolean yes = wager.outcome == Outcome.YES;
        return OddsCalculator.potentialPayout(
                wager.amountSisu,
                yes ? prediction.yesPoolSisu : prediction.noPoolSisu,
                yes ? prediction.noPoolSisu : prediction.yesPoolSisu);
    }
}
