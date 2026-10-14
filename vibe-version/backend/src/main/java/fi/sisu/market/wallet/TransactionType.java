package fi.sisu.market.wallet;

public enum TransactionType {
    /** Starting Sisu granted on registration. */
    SIGNUP_BONUS,
    /** Balance that existed before the ledger was introduced; recorded once so history adds up. */
    OPENING_BALANCE,
    /** Sisu staked on a prediction (negative amount). */
    WAGER,
    /** Stake returned for a cancelled wager (positive amount). */
    WAGER_REFUND,
    /** Winnings from a resolved prediction (positive amount). */
    PAYOUT
}
