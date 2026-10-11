package market.sisu.prediction;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(
        description = "Body for POST /api/predictions/{id}/resolve",
        requiredProperties = {"outcome"})
public record ResolvePredictionRequest(
        @Schema(description = "The winning outcome") Outcome outcome) {}
