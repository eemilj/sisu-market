package market.sisu.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import market.sisu.prediction.OddsCalculator;
import market.sisu.prediction.OddsCalculator.Odds;
import org.junit.jupiter.api.Test;

/**
 * Pari-mutuel display odds derived from the stored pools (FR-034). User Story 3 (T044) extends this
 * with the odds movement after each wager.
 */
class OddsCalculatorTest {

    @Test
    void oddsAreEachSidesShareOfTheTotalPool() {
        assertEquals(new Odds(60.0, 40.0), OddsCalculator.odds(600, 400));
        assertEquals(new Odds(100.0, 0.0), OddsCalculator.odds(10, 0));
        assertEquals(new Odds(0.0, 100.0), OddsCalculator.odds(0, 10));
    }

    @Test
    void emptyPoolsShowEvenOdds() {
        assertEquals(new Odds(50.0, 50.0), OddsCalculator.odds(0, 0));
    }

    @Test
    void oddsRoundHalfUpToOneDecimalAndAlwaysSumToOneHundred() {
        assertEquals(new Odds(33.3, 66.7), OddsCalculator.odds(1, 2));
        assertEquals(new Odds(66.7, 33.3), OddsCalculator.odds(2, 1));
        // 1/2000 = 0.05% rounds half-up to 0.1%.
        assertEquals(new Odds(0.1, 99.9), OddsCalculator.odds(1, 1999));
        assertEquals(new Odds(14.3, 85.7), OddsCalculator.odds(1, 6));
        Odds uneven = OddsCalculator.odds(1, 3);
        assertEquals(100.0, uneven.yesPercent() + uneven.noPercent(), 1e-9);
    }

    @Test
    void negativePoolsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> OddsCalculator.odds(-1, 5));
        assertThrows(IllegalArgumentException.class, () -> OddsCalculator.odds(5, -1));
    }
}
