package market.sisu.prediction;

import java.time.Instant;
import market.sisu.prediction.OddsCalculator.Odds;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * One row of the prediction list (contracts/openapi.yaml {@code PredictionSummary}; FR-016):
 * enough to decide whether to open the prediction.
 */
@Schema(name = "PredictionSummary")
public record PredictionSummary(
        String id,
        String question,
        PredictionStatus status,
        Instant closingAt,
        long totalSisuWagered,
        double oddsYesPercent,
        double oddsNoPercent) {

    /** The prediction as seen at {@code now}, with odds derived from its stored pools. */
    public static PredictionSummary of(Prediction prediction, Instant now) {
        Odds odds = OddsCalculator.odds(prediction.yesPoolSisu, prediction.noPoolSisu);
        return new PredictionSummary(
                prediction.id.toHexString(),
                prediction.question,
                prediction.statusAt(now),
                prediction.closingAt,
                Math.addExact(prediction.yesPoolSisu, prediction.noPoolSisu),
                odds.yesPercent(),
                odds.noPercent());
    }
}
