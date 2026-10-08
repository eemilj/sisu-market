package fi.sisu.market.pricing;

import fi.sisu.market.prediction.Outcome;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Constant-product market maker: the single pricing and payout model for every prediction.
 *
 * <p>Each prediction holds a pool of YES and NO shares with {@code yes * no = k}. A share pays exactly
 * 1 Sisu if its side wins. The YES probability (= price of a YES share) is {@code no / (yes + no)}.
 *
 * <p>Buying YES with M Sisu: the M Sisu mint M YES + M NO shares into the pool (one pair always pays out
 * exactly 1 Sisu), then the buyer takes YES shares out until {@code yes * no = k} again. Buying NO is
 * symmetric. The buyer's payout if their side wins is fixed at that moment: {@code floor(shares)} Sisu.
 *
 * <p>Solvency: every staked Sisu mints exactly one share pair, so the shares of either outcome can never
 * exceed (initial pool + total stakes). The market maker's worst case is losing its initial liquidity.
 *
 * <p>All arithmetic is decimal and rounds in the market maker's favour, so results are deterministic and
 * identical between a quote and the wager placed from it.
 */
public final class Cpmm {

    /** Share quantities are kept to this many decimal places. */
    public static final int SHARE_SCALE = 6;
    private static final MathContext MC = MathContext.DECIMAL128;
    /** Starting probabilities are clamped to this range so neither side starts out (nearly) worthless. */
    private static final BigDecimal MIN_PROBABILITY = new BigDecimal("0.02");
    private static final BigDecimal MAX_PROBABILITY = new BigDecimal("0.98");

    private Cpmm() {
    }

    /** The market maker's share pool for one prediction. */
    public record Pool(BigDecimal yesShares, BigDecimal noShares) {

        public Pool {
            if (yesShares.signum() <= 0 || noShares.signum() <= 0) {
                throw new IllegalArgumentException("Pool shares must be positive");
            }
        }

        public double yesProbability() {
            return noShares.divide(yesShares.add(noShares), MC).doubleValue();
        }
    }

    /**
     * Result of buying {@code amount} Sisu of one side.
     *
     * @param shares       shares received, each worth 1 Sisu if the side wins
     * @param payoutIfWin  whole Sisu paid out if the side wins: {@code floor(shares)}
     * @param poolAfter    pool once the trade is applied
     */
    public record Trade(Outcome side, long amount, BigDecimal shares, long payoutIfWin, Pool poolAfter) {

        /** Average price paid per share, 0-1; equivalently the probability the trade was effectively bought at. */
        public double averagePrice() {
            return BigDecimal.valueOf(amount).divide(shares, MC).doubleValue();
        }
    }

    /**
     * Opening pool for a market of the given liquidity starting at {@code yesProbability}.
     * Every pool has {@code k = liquidity²}, so all predictions move the same way for the same wager size.
     */
    public static Pool initialPool(long liquidity, BigDecimal yesProbability) {
        if (liquidity <= 0) {
            throw new IllegalArgumentException("Liquidity must be positive");
        }
        BigDecimal q = yesProbability.max(MIN_PROBABILITY).min(MAX_PROBABILITY);
        // With r = sqrt(q / (1 - q)): yes = L / r, no = L * r  =>  no / (yes + no) = q and yes * no = L².
        BigDecimal r = q.divide(BigDecimal.ONE.subtract(q), MC).sqrt(MC);
        BigDecimal l = BigDecimal.valueOf(liquidity);
        return new Pool(
                l.divide(r, MC).setScale(SHARE_SCALE, RoundingMode.HALF_EVEN),
                l.multiply(r, MC).setScale(SHARE_SCALE, RoundingMode.HALF_EVEN));
    }

    public static Trade buy(Pool pool, Outcome side, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        BigDecimal m = BigDecimal.valueOf(amount);
        BigDecimal bought = side == Outcome.YES ? pool.yesShares() : pool.noShares();
        BigDecimal other = side == Outcome.YES ? pool.noShares() : pool.yesShares();

        BigDecimal otherAfter = other.add(m);
        // Pool must keep k: boughtAfter = bought * other / otherAfter. The buyer takes the rest of (bought + m).
        BigDecimal exactRemaining = bought.multiply(other, MC).divide(otherAfter, MC);
        BigDecimal shares = bought.add(m).subtract(exactRemaining).setScale(SHARE_SCALE, RoundingMode.DOWN);
        // Rounding the buyer's shares down leaves the dust in the pool (k never decreases).
        BigDecimal boughtAfter = bought.add(m).subtract(shares);

        Pool after = side == Outcome.YES ? new Pool(boughtAfter, otherAfter) : new Pool(otherAfter, boughtAfter);
        long payout = shares.setScale(0, RoundingMode.DOWN).longValueExact();
        return new Trade(side, amount, shares, payout, after);
    }
}
