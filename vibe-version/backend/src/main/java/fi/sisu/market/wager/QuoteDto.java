package fi.sisu.market.wager;

import fi.sisu.market.prediction.Outcome;
import fi.sisu.market.pricing.Cpmm;

import java.math.BigDecimal;

/**
 * What a wager would get right now. Placing the same wager immediately afterwards yields exactly this,
 * unless someone else trades in between; send {@code payoutIfWin} as {@code minPayout} to guard against that.
 */
public record QuoteDto(Outcome side, long amount, BigDecimal shares, long payoutIfWin, double averagePrice,
                       double yesProbabilityBefore, double yesProbabilityAfter) {

    static QuoteDto from(Cpmm.Pool before, Cpmm.Trade trade) {
        return new QuoteDto(trade.side(), trade.amount(), trade.shares(), trade.payoutIfWin(), trade.averagePrice(),
                before.yesProbability(), trade.poolAfter().yesProbability());
    }
}
