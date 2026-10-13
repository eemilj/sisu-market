package fi.sisu.market.wallet;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class LedgerRepository implements PanacheMongoRepository<LedgerEntry> {

    /** Newest first. {@code before} is an exclusive cursor: the id of the last entry of the previous page. */
    public List<LedgerEntry> page(ObjectId userId, ObjectId before, int limit) {
        Bson filter = before == null
                ? Filters.eq("userId", userId)
                : Filters.and(Filters.eq("userId", userId), Filters.lt("_id", before));
        return mongoCollection().find(filter)
                .sort(Sorts.descending("_id"))
                .limit(limit)
                .into(new ArrayList<>());
    }

    public boolean hasEntries(ObjectId userId) {
        return count("userId", userId) > 0;
    }

    public void ensureIndexes() {
        mongoCollection().createIndex(Indexes.compoundIndex(Indexes.ascending("userId"), Indexes.descending("_id")));
    }
}
