package market.sisu.prediction;

import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * The full prediction view (contracts/openapi.yaml {@code PredictionDetail}; FR-015, FR-017): the
 * {@link PredictionSummary} fields plus description, resolution criteria, creator and outcome.
 *
 * @param creatorUsername {@code null} once the creator's account has been deleted (FR-033)
 * @param winningOutcome {@code null} until the prediction is resolved
 */
@Schema(name = "PredictionDetail")
public record PredictionDetail(
        String id,
        String question,
        PredictionStatus status,
        Instant closingAt,
        long totalSisuWagered,
        double oddsYesPercent,
        double oddsNoPercent,
        String description,
        String resolutionCriteria,
        String creatorUsername,
        Outcome winningOutcome) {}
