package fi.sisu.market.prediction;

import fi.sisu.market.pricing.Cpmm;
import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.types.ObjectId;

import java.math.BigDecimal;
import java.time.Instant;

@MongoEntity(collection = "predictions")
public class Prediction {

    public ObjectId id;
    public String question;
    public String description;
    /** How the outcome will be decided: source, cut-off and edge cases. */
    public String resolutionCriteria;
    public Category category;
    public Instant closesAt;
    /** Stored status. OPEN predictions past {@code closesAt} are reported as CLOSED; see {@link #effectiveStatus}. */
    public PredictionStatus status;
    /** Set only when RESOLVED. */
    public Outcome outcome;
    public Instant resolvedAt;
    public ObjectId resolvedById;
    /** Market maker's share pool; determines price and payouts. See {@link Cpmm}. */
    public BigDecimal poolYesShares;
    public BigDecimal poolNoShares;
    /** Sum of all real wagers placed on this prediction, in Sisu. */
    public long totalWagered;
    /** Creator; null for seeded demo predictions. */
    public ObjectId createdById;
    /** Creator's username at creation time, denormalised for listing. */
    public String createdBy;
    public Instant createdAt;
    /** Incremented with every change to pool or totals, so clients can order updates. */
    public long version;

    public Cpmm.Pool pool() {
        return new Cpmm.Pool(poolYesShares, poolNoShares);
    }

    public void updatePool(Cpmm.Pool pool) {
        poolYesShares = pool.yesShares();
        poolNoShares = pool.noShares();
    }

    public PredictionStatus effectiveStatus(Instant now) {
        if (status == PredictionStatus.OPEN && !now.isBefore(closesAt)) {
            return PredictionStatus.CLOSED;
        }
        return status;
    }
}
