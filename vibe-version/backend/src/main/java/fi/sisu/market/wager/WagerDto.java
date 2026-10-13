package fi.sisu.market.wager;

import fi.sisu.market.prediction.Outcome;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;

public record WagerDto(String id, String predictionId, Outcome side, long amount, BigDecimal shares,
                       long payoutIfWin, double averagePrice, WagerStatus status, Instant createdAt,
                       /** Sisu paid at settlement; null until the prediction resolves. */
                       Long payout, Instant settledAt) {

    public static WagerDto from(Wager w) {
        // Refunded legacy wagers have no shares.
        double averagePrice = w.shares.signum() == 0
                ? 0
                : BigDecimal.valueOf(w.amount).divide(w.shares, MathContext.DECIMAL64).doubleValue();
        return new WagerDto(w.id.toHexString(), w.predictionId.toHexString(), w.side, w.amount, w.shares,
                w.payoutIfWin, averagePrice, w.status, w.createdAt, w.payout, w.settledAt);
    }
}
