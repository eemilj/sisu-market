package fi.sisu.market.prediction;

import java.time.Instant;

public record PredictionDto(
        String id,
        String question,
        String description,
        String resolutionCriteria,
        Category category,
        PredictionStatus status,
        Outcome outcome,
        Instant closesAt,
        /** Implied chance of YES, 0-100. 50 when nothing has been staked yet. */
        int yesPercent,
        /** Total Sisu staked on both sides. */
        long volume,
        /** Creator's username, or null for seeded predictions. */
        String createdBy,
        Instant createdAt) {

    public static PredictionDto from(Prediction p, Instant now) {
        long volume = p.yesPool + p.noPool;
        int yesPercent = volume == 0 ? 50 : (int) Math.round(100.0 * p.yesPool / volume);
        return new PredictionDto(
                p.id.toHexString(),
                p.question,
                p.description,
                p.resolutionCriteria,
                p.category,
                p.effectiveStatus(now),
                p.outcome,
                p.closesAt,
                yesPercent,
                volume,
                p.createdBy,
                p.createdAt);
    }
}
