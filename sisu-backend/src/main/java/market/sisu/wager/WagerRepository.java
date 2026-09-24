package market.sisu.wager;

import com.mongodb.client.ClientSession;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

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
}
