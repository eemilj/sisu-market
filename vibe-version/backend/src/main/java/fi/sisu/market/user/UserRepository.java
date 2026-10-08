package fi.sisu.market.user;

import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Locale;
import java.util.Optional;

@ApplicationScoped
public class UserRepository implements PanacheMongoRepository<User> {

    public Optional<User> findByUsername(String username) {
        return find("usernameKey", normalize(username)).firstResultOptional();
    }

    public boolean usernameTaken(String username) {
        return count("usernameKey", normalize(username)) > 0;
    }

    public boolean emailTaken(String email) {
        return count("email", normalize(email)) > 0;
    }

    public void ensureIndexes() {
        IndexOptions unique = new IndexOptions().unique(true);
        mongoCollection().createIndex(Indexes.ascending("usernameKey"), unique);
        mongoCollection().createIndex(Indexes.ascending("email"), unique);
    }

    public static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
