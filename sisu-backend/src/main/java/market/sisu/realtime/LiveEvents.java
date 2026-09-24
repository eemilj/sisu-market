package market.sisu.realtime;

import market.sisu.prediction.Outcome;
import market.sisu.prediction.PredictionStatus;

/** SSE event names and JSON payloads, exactly as specified in contracts/realtime.md. */
public final class LiveEvents {

    public static final String BALANCE_UPDATED = "balance-updated";
    public static final String ODDS_UPDATED = "odds-updated";
    public static final String PREDICTION_STATUS_CHANGED = "prediction-status-changed";

    private LiveEvents() {}

    /** Sent only to the affected user's own connections. */
    public record BalanceUpdated(long balanceSisu) {}

    /** Broadcast after any wager on the prediction (FR-023). Percentages are for display only. */
    public record OddsUpdated(
            String predictionId, double oddsYesPercent, double oddsNoPercent, long totalSisuWagered) {}

    /** Broadcast on automatic closing, resolution (FR-026) or cancellation (FR-036). */
    public record PredictionStatusChanged(String predictionId, PredictionStatus status, Outcome winningOutcome) {}
}
