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
        @Schema(minLength = 1) String question,
        String description,
        @Schema(minLength = 1) String resolutionCriteria,
        Instant closingAt) {}
