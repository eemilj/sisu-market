package fi.sisu.market.wager;

import fi.sisu.market.prediction.Outcome;
import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.types.ObjectId;

import java.math.BigDecimal;
import java.time.Instant;

/** A user's stake on one side of a prediction. The terms are fixed when it is placed. */
@MongoEntity(collection = "wagers")
public class Wager {

    public ObjectId id;
    public ObjectId userId;
    public ObjectId predictionId;
    public Outcome side;
    /** Whole Sisu staked, always positive. */
    public long amount;
    /** Shares bought; each pays 1 Sisu if {@link #side} wins. */
    public BigDecimal shares;
    /** Sisu paid out if {@link #side} wins: floor(shares). Locked at placement. */
    public long payoutIfWin;
    public WagerStatus status;
    /** Client-supplied idempotency key; unique per user when present. */
    public String requestId;
    /** The ledger entry that debited the stake. */
    public ObjectId ledgerEntryId;
    public Instant createdAt;
    /** Sisu actually paid out at settlement (WON: payoutIfWin, LOST: 0). Null while OPEN. */
    public Long payout;
    public Instant settledAt;
}
