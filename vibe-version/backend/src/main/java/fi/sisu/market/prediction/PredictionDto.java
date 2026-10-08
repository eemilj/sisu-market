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
        /** Current price of a YES share = implied chance of YES, 0-1. NO is 1 - this. */
        double yesProbability,
        /** {@code yesProbability} as a whole percentage, for display. */
        int yesPercent,
        /** Total Sisu wagered by users on both sides. */
        long totalWagered,
        /** Creator's username, or null for seeded predictions. */
        String createdBy,
        Instant createdAt,
        Instant resolvedAt,
        /** Higher = newer; clients keep the highest version they have seen. */
        long version) {

    public static PredictionDto from(Prediction p, Instant now) {
        double yes = p.pool().yesProbability();
        return new PredictionDto(
                p.id.toHexString(),
                p.question,
                p.description,
                p.resolutionCriteria,
                p.category,
                p.effectiveStatus(now),
                p.outcome,
                p.closesAt,
                yes,
                (int) Math.round(100 * yes),
                p.totalWagered,
                p.createdBy,
                p.createdAt,
                p.resolvedAt,
                p.version);
    }
}
