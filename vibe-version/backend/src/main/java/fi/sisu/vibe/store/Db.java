package fi.sisu.vibe.store;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.function.Function;
import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Collections, indexes and the transaction helper. All sisu movements go through {@link #tx}. */
@ApplicationScoped
public class Db {

    private final MongoClient client;
    private final MongoDatabase database;

    public Db(MongoClient client, @ConfigProperty(name = "quarkus.mongodb.database") String name) {
        this.client = client;
        this.database = client.getDatabase(name);
    }

    public MongoCollection<Document> users() {
        return database.getCollection("users");
    }

    public MongoCollection<Document> predictions() {
        return database.getCollection("predictions");
    }

    public MongoCollection<Document> wagers() {
        return database.getCollection("wagers");
    }

    public MongoCollection<Document> transactions() {
        return database.getCollection("transactions");
    }

    /** Runs {@code work} in a multi-document transaction; transient conflicts are retried. */
    public <T> T tx(Function<ClientSession, T> work) {
        try (ClientSession session = client.startSession()) {
            return session.withTransaction(() -> work.apply(session));
        }
    }

    void createIndexes(@Observes StartupEvent event) {
        users().createIndex(Indexes.ascending("usernameKey"), new IndexOptions().unique(true));
        users().createIndex(Indexes.descending("balance"));
        predictions().createIndex(Indexes.ascending("status", "closesAt"));
        predictions().createIndex(Indexes.ascending("creatorId"));
        wagers().createIndex(Indexes.ascending("predictionId"));
        wagers().createIndex(Indexes.ascending("userId", "createdAt"));
        transactions().createIndex(Indexes.ascending("userId", "createdAt"));
    }
}
