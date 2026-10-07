package market.sisu.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import market.sisu.prediction.OddsCalculator;
import market.sisu.prediction.OddsCalculator.Odds;
import org.junit.jupiter.api.Test;

/**
 * Pari-mutuel display odds derived from the stored pools, how they move after each wager, and the
 * potential payout shown for an open wager (FR-023, FR-024, FR-034).
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

    @Test
    void aWagerMovesTheOddsTowardTheSideItBacks() {
        // 600 yes / 400 no, then 400 wagered on no.
        Odds before = OddsCalculator.odds(600, 400);
        Odds after = OddsCalculator.odds(600, 400 + 400);

        assertEquals(new Odds(60.0, 40.0), before);
        assertEquals(new Odds(42.9, 57.1), after);
    }

    @Test
    void theFirstWagerOnAnEmptyPredictionTakesAllTheOdds() {
        assertEquals(new Odds(100.0, 0.0), OddsCalculator.odds(25, 0));
        assertEquals(new Odds(0.0, 100.0), OddsCalculator.odds(0, 25));
    }

    @Test
    void potentialPayoutIsTheStakePlusItsShareOfTheOtherPool() {
        // 400 of the 800 on "no" would win half of the 600 on "yes".
        assertEquals(700, OddsCalculator.potentialPayout(400, 800, 600));
        // The only wager on its side takes the whole other pool.
        assertEquals(1100, OddsCalculator.potentialPayout(100, 100, 1000));
    }

    @Test
    void potentialPayoutWithNothingOnTheOtherSideIsJustTheStake() {
        assertEquals(300, OddsCalculator.potentialPayout(300, 300, 0));
    }

    @Test
    void potentialPayoutRoundsTheShareDownToWholeSisu() {
        // 1/3 of 100 = 33.33… and 2/3 of 100 = 66.66…, never paying out sisu that is not in the pool.
        assertEquals(34, OddsCalculator.potentialPayout(1, 3, 100));
        assertEquals(68, OddsCalculator.potentialPayout(2, 3, 100));
    }

    @Test
    void potentialPayoutRejectsAStakeLargerThanItsOwnPool() {
        assertThrows(IllegalArgumentException.class, () -> OddsCalculator.potentialPayout(0, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> OddsCalculator.potentialPayout(20, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> OddsCalculator.potentialPayout(5, 10, -1));
    }
}
