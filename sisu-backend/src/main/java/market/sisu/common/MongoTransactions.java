package market.sisu.common;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Runs work inside one MongoDB multi-document transaction (data-model.md, "Cross-entity
 * invariants"). Every balance/wager/pool/ledger write that must be atomic takes the
 * {@link ClientSession} handed to the callback and passes it to each repository call, so they all
 * commit or roll back together.
 *
 * <p>The driver re-runs the callback on transient errors (e.g. a write conflict between two
 * concurrent wagers on the same balance), so the callback must not cause side effects outside
 * MongoDB: publish SSE events only after this method returns. An exception thrown by the callback
 * aborts the transaction and propagates unchanged.
 */
@ApplicationScoped
public class MongoTransactions {

    private static final TransactionOptions OPTIONS = TransactionOptions.builder()
            .readConcern(ReadConcern.SNAPSHOT)
            .writeConcern(WriteConcern.MAJORITY)
            .readPreference(ReadPreference.primary())
            .build();

    private final MongoClient client;

    MongoTransactions(MongoClient client) {
        this.client = client;
    }

    public <T> T inTransaction(Function<ClientSession, T> work) {
        try (ClientSession session = client.startSession()) {
            return session.withTransaction(() -> work.apply(session), OPTIONS);
        }
    }

    public void run(Consumer<ClientSession> work) {
        inTransaction(session -> {
            work.accept(session);
            return null;
        });
    }
}
