package fi.sisu.market.resolution;

import fi.sisu.market.prediction.Outcome;
import jakarta.validation.constraints.NotNull;

public record ResolveRequest(@NotNull(message = "Outcome is required (YES or NO)") Outcome outcome) {
}
