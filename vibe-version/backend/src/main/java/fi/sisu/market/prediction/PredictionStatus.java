package fi.sisu.market.prediction;

public enum PredictionStatus {
    /** Accepting positions until {@code closesAt}. */
    OPEN,
    /** Past its close time, waiting for an outcome. */
    CLOSED,
    /** Outcome decided. */
    RESOLVED
}
