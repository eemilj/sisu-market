package fi.sisu.market.resolution;

import fi.sisu.market.prediction.Outcome;
import fi.sisu.market.wager.Wager;
import fi.sisu.market.wager.WagerStatus;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettlementTest {

    private static Wager wager(Outcome side, long amount, long payoutIfWin) {
        Wager w = new Wager();
        w.id = new ObjectId();
        w.userId = new ObjectId();
        w.side = side;
        w.amount = amount;
        w.payoutIfWin = payoutIfWin;
        w.status = WagerStatus.OPEN;
        return w;
    }

    @Test
    void winnersGetTheirLockedPayoutLosersNothing() {
        Wager yes1 = wager(Outcome.YES, 100, 343);
        Wager yes2 = wager(Outcome.YES, 50, 120);
        Wager no = wager(Outcome.NO, 200, 260);

        Settlement.Result r = Settlement.settle(List.of(yes1, yes2, no), Outcome.YES);

        assertEquals(2, r.winners());
        assertEquals(1, r.losers());
        assertEquals(463, r.totalPaid());
        assertEquals(new Settlement.Line(yes1, WagerStatus.WON, 343), r.lines().get(0));
        assertEquals(new Settlement.Line(yes2, WagerStatus.WON, 120), r.lines().get(1));
        assertEquals(new Settlement.Line(no, WagerStatus.LOST, 0), r.lines().get(2));
    }

    @Test
    void theOtherOutcomeMirrorsIt() {
        Settlement.Result r = Settlement.settle(
                List.of(wager(Outcome.YES, 100, 343), wager(Outcome.NO, 200, 260)), Outcome.NO);
        assertEquals(1, r.winners());
        assertEquals(260, r.totalPaid());
    }

    @Test
    void oneSidedMarkets() {
        Settlement.Result allLose = Settlement.settle(List.of(wager(Outcome.YES, 10, 19), wager(Outcome.YES, 5, 9)), Outcome.NO);
        assertEquals(0, allLose.winners());
        assertEquals(0, allLose.totalPaid());

        Settlement.Result nobody = Settlement.settle(List.of(), Outcome.YES);
        assertEquals(0, nobody.lines().size());
        assertEquals(0, nobody.totalPaid());
    }

    @Test
    void refusesToSettleAWagerTwice() {
        Wager settled = wager(Outcome.YES, 10, 19);
        settled.status = WagerStatus.WON;
        assertThrows(IllegalArgumentException.class, () -> Settlement.settle(List.of(settled), Outcome.YES));
        Wager refunded = wager(Outcome.YES, 10, 19);
        refunded.status = WagerStatus.REFUNDED;
        assertThrows(IllegalArgumentException.class, () -> Settlement.settle(List.of(refunded), Outcome.NO));
    }

    @Test
    void rejectsCorruptInput() {
        assertThrows(IllegalArgumentException.class, () -> Settlement.settle(List.of(wager(Outcome.YES, 10, 19)), null));
        assertThrows(IllegalArgumentException.class, () -> Settlement.settle(List.of(wager(Outcome.YES, 10, -1)), Outcome.YES));
        // A total beyond what a long can hold must fail loudly, not wrap around.
        assertThrows(ArithmeticException.class, () -> Settlement.settle(
                List.of(wager(Outcome.YES, 1, Long.MAX_VALUE), wager(Outcome.YES, 1, 1)), Outcome.YES));
    }
}
