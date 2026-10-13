package fi.sisu.market.user;

import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
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

    /** Competition rank by balance: 1 + number of users with strictly more Sisu (ties share a rank). */
    public long rankOf(long balance) {
        return mongoCollection().countDocuments(Filters.gt("balance", balance)) + 1;
    }

    /** Richest first; earlier sign-up first among equal balances. */
    public List<User> topByBalance(int limit) {
        return mongoCollection().find()
                .sort(Sorts.orderBy(Sorts.descending("balance"), Sorts.ascending("createdAt")))
                .limit(limit)
                .into(new ArrayList<>());
    }

    /** Newest sign-ups first. */
    public List<User> newest(int limit) {
        return mongoCollection().find().sort(Sorts.descending("createdAt")).limit(limit).into(new ArrayList<>());
    }

    public void ensureIndexes() {
        mongoCollection().createIndex(Indexes.compoundIndex(Indexes.descending("balance"), Indexes.ascending("createdAt")));
        IndexOptions unique = new IndexOptions().unique(true);
        mongoCollection().createIndex(Indexes.ascending("usernameKey"), unique);
        mongoCollection().createIndex(Indexes.ascending("email"), unique);
    }

    public static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
