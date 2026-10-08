package fi.sisu.market.wager;

import fi.sisu.market.prediction.Outcome;

import java.time.Instant;

public record WagerDto(String id, String predictionId, Outcome side, long amount, Instant createdAt) {

    public static WagerDto from(Wager w) {
        return new WagerDto(w.id.toHexString(), w.predictionId.toHexString(), w.side, w.amount, w.createdAt);
    }
}
