package fi.sisu.market.wallet;

import com.mongodb.client.model.Filters;
import fi.sisu.market.user.User;
import fi.sisu.market.user.UserRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;
import org.jboss.logging.Logger;

/**
 * Creates ledger indexes and, for accounts registered before the ledger existed, records their current
 * balance as an opening entry so their history sums to their balance. Idempotent.
 */
@ApplicationScoped
public class LedgerBootstrap {

    private static final Logger LOG = Logger.getLogger(LedgerBootstrap.class);

    @Inject
    LedgerRepository ledger;

    @Inject
    UserRepository users;

    void onStart(@Observes StartupEvent event) {
        ledger.ensureIndexes();

        int backfilled = 0;
        for (User user : users.mongoCollection().find(Filters.ne("balance", 0L))) {
            if (ledger.hasEntries(user.id)) {
                continue;
            }
            LedgerEntry entry = new LedgerEntry();
            entry.id = new ObjectId();
            entry.userId = user.id;
            entry.type = TransactionType.OPENING_BALANCE;
            entry.amount = user.balance;
            entry.balanceAfter = user.balance;
            entry.description = "Opening balance";
            entry.createdAt = user.createdAt;
            ledger.persist(entry);
            backfilled++;
        }
        if (backfilled > 0) {
            LOG.infof("Recorded opening balances for %d existing users", backfilled);
        }
    }
}
