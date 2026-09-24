package market.sisu.transaction;

import com.mongodb.client.ClientSession;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.ArrayList;
import java.util.List;
import org.bson.types.ObjectId;

/**
 * The append-only balance ledger (FR-010, FR-011, Constitution II). Deliberately offers no update
 * or delete operation; corrections are made with new compensating entries.
 */
@ApplicationScoped
public class TransactionRepository {

    private final TransactionStore store;

    TransactionRepository(TransactionStore store) {
        this.store = store;
    }

    void createIndexes(@Observes StartupEvent event) {
        store.mongoCollection()
                .createIndex(Indexes.ascending("userId", "createdAt"), new IndexOptions().name("userId_createdAt"));
    }

    /** Appends one entry; callers use {@code BalanceLedger} so the balance moves in the same transaction. */
    void append(ClientSession session, Transaction transaction) {
        store.mongoCollection().insertOne(session, transaction);
    }

    /** A user's full history, newest first (FR-011). */
    public List<Transaction> findByUser(ObjectId userId) {
        return store.mongoCollection()
                .find(Filters.eq("userId", userId))
                .sort(Sorts.orderBy(Sorts.descending("createdAt"), Sorts.descending("_id")))
                .into(new ArrayList<>());
    }

    /** Net effect of all a user's entries; always equals their {@code balanceSisu} (SC-006). */
    public long sumForUser(ObjectId userId) {
        return findByUser(userId).stream().mapToLong(Transaction::signedAmount).sum();
    }
}
