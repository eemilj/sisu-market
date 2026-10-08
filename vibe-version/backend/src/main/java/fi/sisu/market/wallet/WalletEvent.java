package fi.sisu.market.wallet;

/**
 * Pushed to a user whenever their balance changes.
 *
 * @param balanceVersion increases by one with every change to this user's balance; clients ignore events
 *                       with a version lower than the one they already show (events can arrive out of order)
 */
public record WalletEvent(LedgerEntryDto transaction, long balance, long balanceVersion) {
}
