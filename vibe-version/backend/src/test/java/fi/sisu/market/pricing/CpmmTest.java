package fi.sisu.market.pricing;

import fi.sisu.market.prediction.Outcome;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plain unit tests of the pricing math; no Quarkus needed. */
class CpmmTest {

    private static final long L = 1000;

    @Test
    void initialPoolHitsRequestedProbabilityWithSameK() {
        for (String q : new String[] {"0.5", "0.26", "0.61", "0.9"}) {
            Cpmm.Pool pool = Cpmm.initialPool(L, new BigDecimal(q));
            assertEquals(Double.parseDouble(q), pool.yesProbability(), 1e-6, "q=" + q);
            BigDecimal k = pool.yesShares().multiply(pool.noShares());
            assertEquals(1_000_000.0, k.doubleValue(), 1.0, "k for q=" + q);
        }
    }

    @Test
    void extremeStartingProbabilitiesAreClamped() {
        assertEquals(0.02, Cpmm.initialPool(L, BigDecimal.ZERO).yesProbability(), 1e-6);
        assertEquals(0.98, Cpmm.initialPool(L, BigDecimal.ONE).yesProbability(), 1e-6);
    }

    @Test
    void buyingYesAtEvenOddsMatchesHandCalculation() {
        Cpmm.Trade t = Cpmm.buy(Cpmm.initialPool(L, new BigDecimal("0.5")), Outcome.YES, 100);
        // no' = 1100, yes' = 1e6 / 1100 = 909.090909..., shares = 1100 - 909.090909... = 190.909090
        assertEquals(new BigDecimal("190.909090"), t.shares());
        assertEquals(190, t.payoutIfWin());
        assertEquals(1100.0 / (1100.0 + 909.090910), t.poolAfter().yesProbability(), 1e-6);
        assertTrue(t.averagePrice() > 0.5 && t.averagePrice() < t.poolAfter().yesProbability());
    }

    @Test
    void yesAndNoAreSymmetric() {
        Cpmm.Pool even = Cpmm.initialPool(L, new BigDecimal("0.5"));
        Cpmm.Trade yes = Cpmm.buy(even, Outcome.YES, 250);
        Cpmm.Trade no = Cpmm.buy(even, Outcome.NO, 250);
        assertEquals(yes.shares(), no.shares());
        assertEquals(yes.poolAfter().yesProbability(), 1 - no.poolAfter().yesProbability(), 1e-9);
    }

    @Test
    void cheaperSidePaysMore() {
        Cpmm.Pool pool = Cpmm.initialPool(L, new BigDecimal("0.25"));
        long yesPayout = Cpmm.buy(pool, Outcome.YES, 100).payoutIfWin();
        long noPayout = Cpmm.buy(pool, Outcome.NO, 100).payoutIfWin();
        assertTrue(yesPayout > 350, "YES at ~25% should pay ~4x, got " + yesPayout);
        assertTrue(noPayout > 100 && noPayout < 140, "NO at ~75% should pay ~1.3x, got " + noPayout);
    }

    @Test
    void payoutNeverBelowStakeAndPriceStaysInBounds() {
        Cpmm.Pool pool = Cpmm.initialPool(L, new BigDecimal("0.5"));
        for (long amount : new long[] {1, 7, 100, 10_000, 1_000_000_000L}) {
            Cpmm.Trade t = Cpmm.buy(pool, Outcome.YES, amount);
            assertTrue(t.payoutIfWin() >= amount, "amount " + amount);
            double p = t.poolAfter().yesProbability();
            assertTrue(p > 0 && p < 1, "probability " + p);
        }
    }

    @Test
    void marketMakerStaysSolventAndKNeverShrinks() {
        Random random = new Random(42);
        Cpmm.Pool start = Cpmm.initialPool(L, new BigDecimal("0.37"));
        Cpmm.Pool pool = start;
        BigDecimal k0 = start.yesShares().multiply(start.noShares());
        long stakes = 0;
        long yesPayouts = 0;
        long noPayouts = 0;

        for (int i = 0; i < 2_000; i++) {
            Outcome side = random.nextBoolean() ? Outcome.YES : Outcome.NO;
            long amount = 1 + random.nextInt(500);
            Cpmm.Trade t = Cpmm.buy(pool, side, amount);
            BigDecimal kBefore = pool.yesShares().multiply(pool.noShares());
            pool = t.poolAfter();
            assertTrue(pool.yesShares().multiply(pool.noShares()).compareTo(kBefore) >= 0, "k shrank at trade " + i);
            stakes += amount;
            if (side == Outcome.YES) {
                yesPayouts += t.payoutIfWin();
            } else {
                noPayouts += t.payoutIfWin();
            }
        }
        // Whichever side wins, the payouts are covered by the stakes plus the initial liquidity.
        long subsidy = start.yesShares().max(start.noShares()).round(MathContext.DECIMAL64).longValue() + 1;
        assertTrue(yesPayouts <= stakes + subsidy, "YES payouts " + yesPayouts + " vs " + (stakes + subsidy));
        assertTrue(noPayouts <= stakes + subsidy, "NO payouts " + noPayouts + " vs " + (stakes + subsidy));
        assertTrue(pool.yesShares().multiply(pool.noShares()).compareTo(k0) >= 0);
    }
}
