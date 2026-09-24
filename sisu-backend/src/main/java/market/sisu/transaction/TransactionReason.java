package market.sisu.transaction;

/** Why a balance changed (FR-010). The only documented ways sisu is created or moved. */
public enum TransactionReason {
    STARTING_GRANT,
    WAGER_PLACED,
    PAYOUT,
    REFUND
}
