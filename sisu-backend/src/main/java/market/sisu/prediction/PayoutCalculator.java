package market.sisu.prediction;

/**
 * Pari-mutuel payout calculation (FR-034, FR-028): each winning wager's payout as its stake
 * plus its proportional share of the losing side's pool.
 */
public final class PayoutCalculator {

    private PayoutCalculator() {}

    /**
     * Calculates the payout for a winning wager in a pari-mutuel market.
     *
     * @param stakeSisu the amount of sisu wagered by the winning wager
     * @param winningPoolSisu total sisu wagered on the winning outcome
     * @param losingPoolSisu total sisu wagered on the losing outcome
     * @return total payout in sisu (stake + proportional share of losing pool), or 0 if no winners
     * @throws IllegalArgumentException if any input is negative
     */
    public static long payout(long stakeSisu, long winningPoolSisu, long losingPoolSisu) {
        if (stakeSisu < 0 || winningPoolSisu < 0 || losingPoolSisu < 0) {
            throw new IllegalArgumentException("Amounts must not be negative");
        }
        if (winningPoolSisu == 0) {
            return 0L;
        }
        long losingShare = Math.multiplyExact(stakeSisu, losingPoolSisu) / winningPoolSisu;
        return Math.addExact(stakeSisu, losingShare);
    }
}
