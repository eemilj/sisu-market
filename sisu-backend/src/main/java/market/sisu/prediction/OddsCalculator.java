package market.sisu.prediction;

/**
 * Pari-mutuel odds (FR-034): each outcome's share of the total sisu wagered on a prediction,
 * derived from the two pool totals stored on the prediction (never by summing wagers).
 */
public final class OddsCalculator {

    private static final long TENTHS_OF_PERCENT = 1000;

    private OddsCalculator() {}

    /**
     * Display odds for the given pools, as percentages rounded half-up to one decimal that always
     * sum to exactly 100. A prediction nobody has wagered on yet has no information either way, so
     * it shows even 50 / 50 odds.
     *
     * @throws IllegalArgumentException if either pool is negative
     */
    public static Odds odds(long yesPoolSisu, long noPoolSisu) {
        if (yesPoolSisu < 0 || noPoolSisu < 0) {
            throw new IllegalArgumentException("Pools must not be negative: " + yesPoolSisu + " / " + noPoolSisu);
        }
        long total = Math.addExact(yesPoolSisu, noPoolSisu);
        if (total == 0) {
            return new Odds(50.0, 50.0);
        }
        // Integer arithmetic (Constitution II: no floating point in money math); half-up rounding.
        long yesTenths = Math.addExact(Math.multiplyExact(yesPoolSisu, 2 * TENTHS_OF_PERCENT), total)
                / Math.multiplyExact(2, total);
        long noTenths = TENTHS_OF_PERCENT - yesTenths;
        return new Odds(yesTenths / 10.0, noTenths / 10.0);
    }

    /** One prediction's odds split, in percent. */
    public record Odds(double yesPercent, double noPercent) {}
}
