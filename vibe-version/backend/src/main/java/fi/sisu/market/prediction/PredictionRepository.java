package fi.sisu.market.prediction;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.conversions.Bson;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class PredictionRepository implements PanacheMongoRepository<Prediction> {

    /**
     * Lists predictions soonest-closing first, optionally filtered. Status filtering uses the
     * effective status, so an OPEN prediction whose close time has passed counts as CLOSED.
     */
    public List<Prediction> search(Category category, PredictionStatus status, Instant now) {
        List<Bson> filters = new ArrayList<>();
        if (category != null) {
            filters.add(Filters.eq("category", category.name()));
        }
        if (status != null) {
            filters.add(switch (status) {
                case OPEN -> Filters.and(
                        Filters.eq("status", PredictionStatus.OPEN.name()),
                        Filters.gt("closesAt", now));
                case CLOSED -> Filters.or(
                        Filters.eq("status", PredictionStatus.CLOSED.name()),
                        Filters.and(
                                Filters.eq("status", PredictionStatus.OPEN.name()),
                                Filters.lte("closesAt", now)));
                case RESOLVED -> Filters.eq("status", PredictionStatus.RESOLVED.name());
            });
        }
        Bson filter = filters.isEmpty() ? Filters.empty() : Filters.and(filters);
        return mongoCollection().find(filter).sort(Sorts.ascending("closesAt")).into(new ArrayList<>());
    }

    public void ensureIndexes() {
        mongoCollection().createIndex(Indexes.ascending("status", "closesAt"));
        mongoCollection().createIndex(Indexes.ascending("category"));
    }
}
