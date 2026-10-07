package fi.sisu.vibe.domain;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The single pricing model: a parimutuel pool.
 *
 * <p>Every wager goes into the YES or NO pool. The implied probability of YES is its share of the
 * total pool. When a prediction resolves, the whole pool is split between the winning wagers in
 * proportion to their stakes. If nobody backed the winning side, every wager is refunded.
 *
 * <p>All amounts are whole sisu. Payouts are floored, and the few sisu lost to rounding go to the
 * largest winning stake, so the pool is always paid out exactly and no sisu is created or lost.
 */
public final class Parimutuel {

    private Parimutuel() {}

    /** A stake in a pool, identified by an opaque key (the wager id). */
    public record Stake(String key, Side side, long amount) {}

    /** Probability of YES in [0, 1]. An empty market is a coin flip. */
    public static double yesProbability(long yesPool, long noPool) {
        long total = yesPool + noPool;
        return total == 0 ? 0.5 : (double) yesPool / total;
    }

    /**
     * What a stake would pay if its side won and the pools stayed as they are. {@code sidePool} and
     * {@code totalPool} must already include the stake.
     */
    public static long payoutFor(long stake, long sidePool, long totalPool) {
        if (stake <= 0 || sidePool <= 0) {
            return 0;
        }
        return BigInteger.valueOf(stake)
                .multiply(BigInteger.valueOf(totalPool))
                .divide(BigInteger.valueOf(sidePool))
                .longValueExact();
    }

    /** Payout for a hypothetical new stake placed on top of the current pools. */
    public static long quote(long stake, Side side, long yesPool, long noPool) {
        long sidePool = (side == Side.YES ? yesPool : noPool) + stake;
        return payoutFor(stake, sidePool, yesPool + noPool + stake);
    }

    /**
     * Settles a resolved market. Returns the amount to credit for every stake key (0 for losers).
     * If the winning side is empty, every stake is refunded in full.
     */
    public static Map<String, Long> settle(List<Stake> stakes, Side outcome) {
        Map<String, Long> result = new HashMap<>();
        long total = 0;
        long winningPool = 0;
        Stake largestWinner = null;
        for (Stake s : stakes) {
            if (s.amount() <= 0) {
                throw new IllegalArgumentException("stake must be positive: " + s);
            }
            total += s.amount();
            if (s.side() == outcome) {
                winningPool += s.amount();
                if (largestWinner == null || s.amount() > largestWinner.amount()) {
                    largestWinner = s;
                }
            }
        }

        if (winningPool == 0) {
            stakes.forEach(s -> result.put(s.key(), s.amount()));
            return result;
        }

        long paid = 0;
        for (Stake s : stakes) {
            long payout = s.side() == outcome ? payoutFor(s.amount(), winningPool, total) : 0;
            result.put(s.key(), payout);
            paid += payout;
        }
        result.merge(largestWinner.key(), total - paid, Long::sum);
        return result;
    }

    /** True when every stake was on the losing side and the market will be refunded. */
    public static boolean isRefund(List<Stake> stakes, Side outcome) {
        return stakes.stream().noneMatch(s -> s.side() == outcome);
    }
}
