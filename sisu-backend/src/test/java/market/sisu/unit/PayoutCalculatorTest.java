package market.sisu.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import market.sisu.prediction.OddsCalculator;
import org.junit.jupiter.api.Test;

/** Unit test for pari-mutuel payout calculation (no winners, single wager, multiple wagers) (FR-034). */
class PayoutCalculatorTest {

    @Test
    void singleWagerWinningTakesAllPools() {
        // Stake 100, sidePool 100, otherPool 500 (losing pool)
        long payout = OddsCalculator.potentialPayout(100, 100, 500);
        assertEquals(600, payout);
    }

    @Test
    void multipleWagersProportionalShareOfLosingPool() {
        // Wager 1: 100 out of 400 total yes pool (25%), losing pool (no) is 600.
        // Payout = 100 + (100 * 600) / 400 = 100 + 150 = 250.
        long payout1 = OddsCalculator.potentialPayout(100, 400, 600);
        assertEquals(250, payout1);

        // Wager 2: 300 out of 400 total yes pool (75%), losing pool (no) is 600.
        // Payout = 300 + (300 * 600) / 400 = 300 + 450 = 750.
        long payout2 = OddsCalculator.potentialPayout(300, 400, 600);
        assertEquals(750, payout2);
    }

    @Test
    void noLosingPoolReturnsJustStake() {
        long payout = OddsCalculator.potentialPayout(200, 200, 0);
        assertEquals(200, payout);
    }
}
