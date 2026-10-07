package fi.sisu.vibe.domain;

/** Why a user's balance changed. Every balance change writes exactly one transaction. */
public enum TxReason {
    STARTING_GRANT,
    WAGER,
    PAYOUT,
    REFUND
}
