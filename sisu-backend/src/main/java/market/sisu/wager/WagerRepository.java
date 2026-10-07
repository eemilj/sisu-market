package market.sisu.wager;

import com.mongodb.client.ClientSession;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.ArrayList;
import java.util.List;
import org.bson.types.ObjectId;

@ApplicationScoped
public class WagerRepository implements PanacheMongoRepository<Wager> {

    void createIndexes(@Observes StartupEvent event) {
        mongoCollection().createIndex(Indexes.ascending("userId"), new IndexOptions().name("userId"));
        // Settlement (payout/refund) walks every wager on one prediction.
        mongoCollection().createIndex(Indexes.ascending("predictionId"), new IndexOptions().name("predictionId"));
    }

    public void insert(ClientSession session, Wager wager) {
        mongoCollection().insertOne(session, wager);
    }

    /** A user's wagers that are still {@code OPEN} (FR-024), most recently placed first. */
    public List<Wager> findOpenByUser(ObjectId userId) {
        return mongoCollection()
                .find(Filters.and(Filters.eq("userId", userId), Filters.eq("status", WagerStatus.OPEN.name())))
                .sort(Sorts.orderBy(Sorts.descending("placedAt"), Sorts.descending("_id")))
                .into(new ArrayList<>());
    }
}
