package fi.sisu.market.resolution;

import fi.sisu.market.prediction.PredictionDto;

/**
 * @param winningWagers number of wagers that were paid
 * @param totalPaidOut  Sisu paid to winners in total
 */
public record ResolutionResult(PredictionDto prediction, int winningWagers, int losingWagers, long totalPaidOut) {
}
