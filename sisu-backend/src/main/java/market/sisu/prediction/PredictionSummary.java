package market.sisu.prediction;

import java.time.Instant;
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
        double oddsNoPercent) {}
