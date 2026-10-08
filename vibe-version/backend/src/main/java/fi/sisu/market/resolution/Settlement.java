package fi.sisu.market.resolution;

import fi.sisu.market.prediction.Outcome;
import fi.sisu.market.wager.Wager;
import fi.sisu.market.wager.WagerStatus;

import java.util.List;

/**
 * Who gets what when a prediction resolves. Pure; {@link ResolutionService} applies the result in one
 * transaction. Winners receive exactly the {@code payoutIfWin} locked when they wagered; losers receive nothing.
 */
public final class Settlement {

    /** The decision for one wager. */
    public record Line(Wager wager, WagerStatus status, long payout) {
    }

    public record Result(List<Line> lines, int winners, int losers, long totalPaid) {
    }

    private Settlement() {
    }

    /** @param openWagers wagers still OPEN on the prediction; anything else is a bug and rejected */
    public static Result settle(List<Wager> openWagers, Outcome outcome) {
        if (outcome == null) {
            throw new IllegalArgumentException("outcome is required");
        }
        int winners = 0;
        int losers = 0;
        long paid = 0;
        List<Line> lines = new java.util.ArrayList<>(openWagers.size());
        for (Wager w : openWagers) {
            if (w.status != WagerStatus.OPEN) {
                throw new IllegalArgumentException("Wager " + w.id + " is already " + w.status);
            }
            if (w.side == outcome) {
                if (w.payoutIfWin < 0) {
                    throw new IllegalArgumentException("Wager " + w.id + " has a negative payout");
                }
                lines.add(new Line(w, WagerStatus.WON, w.payoutIfWin));
                winners++;
                paid = Math.addExact(paid, w.payoutIfWin);
            } else {
                lines.add(new Line(w, WagerStatus.LOST, 0));
                losers++;
            }
        }
        return new Result(List.copyOf(lines), winners, losers, paid);
    }
}
