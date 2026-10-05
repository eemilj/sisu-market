package market.sisu.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import market.sisu.prediction.PayoutCalculator;
import org.junit.jupiter.api.Test;

/**
 * Pari-mutuel payout calculations covering no winners, single winning wager,
 * and multiple winning wagers (FR-028, FR-034).
 */
class PayoutCalculatorTest {

    @Test
    void noWinnersResultInZeroPayout() {
        assertEquals(0L, PayoutCalculator.payout(100L, 0L, 500L));
    }

    @Test
    void singleWinningWagerReceivesStakePlusEntireLosingPool() {
        // stake = 100, winningPool = 100, losingPool = 200
        // expected: 100 + (100 * 200 / 100) = 300
        assertEquals(300L, PayoutCalculator.payout(100L, 100L, 200L));
    }

    @Test
    void multipleWinningWagersReceiveProportionalShares() {
        // Winning stakes: 100 and 300 -> winningPool = 400
        // Losing pool = 800
        // Winner 1 (stake 100): 100 + (100 * 800 / 400) = 100 + 200 = 300
        // Winner 2 (stake 300): 300 + (300 * 800 / 400) = 300 + 600 = 900
        assertEquals(300L, PayoutCalculator.payout(100L, 400L, 800L));
        assertEquals(900L, PayoutCalculator.payout(300L, 400L, 800L));
    }
}
