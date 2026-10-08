package fi.sisu.market.wager;

public enum WagerStatus {
    /** Placed; waiting for the prediction to resolve. */
    OPEN,
    /** Prediction resolved on this wager's side; {@code payoutIfWin} was paid. */
    WON,
    /** Prediction resolved against this wager's side; nothing paid. */
    LOST,
    /** Stake returned (e.g. a wager placed before the payout model existed). */
    REFUNDED
}
