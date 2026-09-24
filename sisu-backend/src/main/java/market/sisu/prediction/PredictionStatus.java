package market.sisu.prediction;

/**
 * Lifecycle of a prediction (data-model.md, "State transitions"):
 * {@code OPEN -> CLOSED -> RESOLVED}, or {@code OPEN|CLOSED -> CANCELLED}. RESOLVED and CANCELLED
 * are terminal.
 */
public enum PredictionStatus {
    OPEN,
    CLOSED,
    RESOLVED,
    CANCELLED
}
