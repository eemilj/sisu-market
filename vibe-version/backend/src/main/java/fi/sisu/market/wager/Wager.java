package fi.sisu.market.wager;

import fi.sisu.market.prediction.Outcome;
import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.types.ObjectId;

import java.time.Instant;

/** A user's stake on one side of a prediction. Immutable once placed. */
@MongoEntity(collection = "wagers")
public class Wager {

    public ObjectId id;
    public ObjectId userId;
    public ObjectId predictionId;
    public Outcome side;
    /** Whole Sisu, always positive. */
    public long amount;
    /** The ledger entry that debited the stake. */
    public ObjectId ledgerEntryId;
    public Instant createdAt;
}
