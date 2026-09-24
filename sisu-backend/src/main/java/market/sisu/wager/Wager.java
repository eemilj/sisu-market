package market.sisu.wager;

import io.quarkus.mongodb.panache.common.MongoEntity;
import java.time.Instant;
import market.sisu.prediction.Outcome;
import org.bson.types.ObjectId;

/** A stake placed by one user on one side of a prediction ({@code wagers} collection). */
@MongoEntity(collection = "wagers")
public class Wager {

    public ObjectId id;

    public ObjectId userId;

    public ObjectId predictionId;

    public Outcome outcome;

    /** Positive whole number (FR-019); immutable once placed. */
    public long amountSisu;

    public WagerStatus status = WagerStatus.OPEN;

    /** Set when the wager is WON (stake + pari-mutuel share) or REFUNDED (stake only). */
    public Long payoutSisu;

    public Instant placedAt;

    public Instant settledAt;
}
