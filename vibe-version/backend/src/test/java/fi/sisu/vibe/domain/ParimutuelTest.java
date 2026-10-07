package fi.sisu.vibe.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fi.sisu.vibe.domain.Parimutuel.Stake;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParimutuelTest {

    @Test
    void emptyMarketIsFiftyFifty() {
        assertEquals(0.5, Parimutuel.yesProbability(0, 0));
    }

    @Test
    void probabilityIsPoolShare() {
        assertEquals(0.75, Parimutuel.yesProbability(300, 100));
        assertEquals(1.0, Parimutuel.yesProbability(50, 0));
    }

    @Test
    void quoteIncludesTheNewStake() {
        // 100 YES, 300 NO; adding 100 YES -> pool 500, YES side 200 -> 100 * 500 / 200 = 250
        assertEquals(250, Parimutuel.quote(100, Side.YES, 100, 300));
        // Only stake in the market gets its money back
        assertEquals(40, Parimutuel.quote(40, Side.NO, 0, 0));
    }

    @Test
    void payoutForZeroSidePoolIsZero() {
        assertEquals(0, Parimutuel.payoutFor(10, 0, 100));
    }

    @Test
    void singleWagerGetsStakeBack() {
        Map<String, Long> p = Parimutuel.settle(List.of(new Stake("a", Side.YES, 100)), Side.YES);
        assertEquals(Map.of("a", 100L), p);
    }

    @Test
    void winnersSplitWholePoolProportionally() {
        List<Stake> stakes = List.of(
                new Stake("a", Side.YES, 100), new Stake("b", Side.YES, 300), new Stake("c", Side.NO, 400));
        Map<String, Long> p = Parimutuel.settle(stakes, Side.YES);
        assertEquals(200L, p.get("a"));
        assertEquals(600L, p.get("b"));
        assertEquals(0L, p.get("c"));
        assertTrue(!Parimutuel.isRefund(stakes, Side.YES));
    }

    @Test
    void roundingDustGoesToLargestWinnerSoPoolIsConserved() {
        List<Stake> stakes = List.of(
                new Stake("a", Side.NO, 1), new Stake("b", Side.NO, 1), new Stake("c", Side.NO, 2), new Stake("d", Side.YES, 3));
        Map<String, Long> p = Parimutuel.settle(stakes, Side.NO);
        // pool 7, winning pool 4: a=floor(7/4)=1, b=1, c=floor(14/4)=3, dust 2 -> c
        assertEquals(1L, p.get("a"));
        assertEquals(1L, p.get("b"));
        assertEquals(5L, p.get("c"));
        assertEquals(7L, p.values().stream().mapToLong(Long::longValue).sum());
    }

    @Test
    void noWinnersMeansEveryoneIsRefunded() {
        List<Stake> stakes = List.of(new Stake("a", Side.NO, 50), new Stake("b", Side.NO, 70));
        assertTrue(Parimutuel.isRefund(stakes, Side.YES));
        assertEquals(Map.of("a", 50L, "b", 70L), Parimutuel.settle(stakes, Side.YES));
    }

    @Test
    void noStakesSettlesToNothing() {
        assertEquals(Map.of(), Parimutuel.settle(List.of(), Side.YES));
    }

    @Test
    void nonPositiveStakeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Parimutuel.settle(List.of(new Stake("a", Side.YES, 0)), Side.YES));
    }

    @Test
    void largeAmountsDoNotOverflow() {
        long big = 4_000_000_000_000_000_000L;
        assertEquals(big * 2, Parimutuel.payoutFor(big, big, big * 2));
    }
}
