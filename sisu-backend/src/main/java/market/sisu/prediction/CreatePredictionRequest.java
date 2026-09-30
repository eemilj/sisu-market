package market.sisu.prediction;

import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * {@code POST /api/predictions} body (contracts/openapi.yaml {@code CreatePredictionRequest}).
 * Validated by {@link PredictionService#create} rather than bean validation, so each rule fails
 * with its own error code the frontend can explain (SC-007).
 */
@Schema(
        name = "CreatePredictionRequest",
        requiredProperties = {"question", "description", "resolutionCriteria", "closingAt"})
public record CreatePredictionRequest(
        @Schema(minLength = 1, maxLength = PredictionService.QUESTION_MAX_LENGTH)
        String question,

        @Schema(maxLength = PredictionService.DESCRIPTION_MAX_LENGTH)
        String description,

        @Schema(minLength = 1, maxLength = PredictionService.RESOLUTION_CRITERIA_MAX_LENGTH)
        String resolutionCriteria,

        Instant closingAt) {}
