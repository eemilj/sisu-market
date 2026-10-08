package fi.sisu.market.wager;

import com.mongodb.client.model.Indexes;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class WagerRepository implements PanacheMongoRepository<Wager> {

    public void ensureIndexes() {
        mongoCollection().createIndex(Indexes.ascending("predictionId"));
        mongoCollection().createIndex(Indexes.compoundIndex(Indexes.ascending("userId"), Indexes.descending("_id")));
    }
}
