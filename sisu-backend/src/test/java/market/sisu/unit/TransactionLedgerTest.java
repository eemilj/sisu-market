package market.sisu.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.UUID;
import market.sisu.common.MongoTransactions;
import market.sisu.common.Sisu;
import market.sisu.transaction.BalanceLedger;
import market.sisu.transaction.BalanceLedger.Related;
import market.sisu.transaction.TransactionReason;
import market.sisu.transaction.TransactionRepository;
import market.sisu.user.Role;
import market.sisu.user.User;
import market.sisu.user.UserRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

/**
 * Unit test asserting a user's transaction-log sum always equals {@code balanceSisu} (SC-006).
 */
@QuarkusTest
class TransactionLedgerTest {

    @Inject
    MongoTransactions tx;

    @Inject
    BalanceLedger ledger;

    @Inject
    UserRepository users;

    @Inject
    TransactionRepository transactions;

    @Test
    void transactionLogSumAlwaysEqualsBalance() {
        User user = new User();
        user.id = new ObjectId();
        user.username = "ledger-test-" + UUID.randomUUID();
        user.passwordHash = "hash";
        user.balanceSisu = 0;
        user.role = Role.USER;
        user.createdAt = Instant.now();
        users.persist(user);

        tx.run(s -> ledger.credit(s, user.id, Sisu.of(1000), TransactionReason.STARTING_GRANT, Related.NONE));
        tx.run(s -> ledger.debit(s, user.id, Sisu.of(250), TransactionReason.WAGER_PLACED, Related.NONE));
        tx.run(s -> ledger.credit(s, user.id, Sisu.of(500), TransactionReason.PAYOUT, Related.NONE));

        User reloaded = users.findById(user.id);
        long ledgerSum = transactions.sumForUser(user.id);

        assertEquals(reloaded.balanceSisu, ledgerSum);
        assertEquals(1250, ledgerSum);
    }
}
