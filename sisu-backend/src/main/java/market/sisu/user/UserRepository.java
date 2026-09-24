package market.sisu.user;

import com.mongodb.client.ClientSession;
import com.mongodb.client.model.Collation;
import com.mongodb.client.model.CollationStrength;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.Optional;
import org.bson.types.ObjectId;

@ApplicationScoped
public class UserRepository implements PanacheMongoRepository<User> {

    /** Case-insensitive comparison so "Matti" and "matti" are the same username. */
    static final Collation USERNAME_COLLATION = Collation.builder()
            .locale("fi")
            .collationStrength(CollationStrength.SECONDARY)
            .build();

    void createIndexes(@Observes StartupEvent event) {
        // Unique among live accounts only: deleted accounts have their username scrubbed to null.
        mongoCollection()
                .createIndex(
                        Indexes.ascending("username"),
                        new IndexOptions()
                                .name("username_unique_ci")
                                .unique(true)
                                .collation(USERNAME_COLLATION)
                                .partialFilterExpression(Filters.type("username", "string")));
    }

    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(mongoCollection()
                .find(Filters.eq("username", username))
                .collation(USERNAME_COLLATION)
                .first());
    }

    public void insert(ClientSession session, User user) {
        mongoCollection().insertOne(session, user);
    }

    public Optional<User> findById(ClientSession session, ObjectId id) {
        return Optional.ofNullable(
                mongoCollection().find(session, Filters.eq("_id", id)).first());
    }
}
