package market.sisu.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import market.sisu.transaction.Transaction;
import market.sisu.transaction.TransactionDirection;
import market.sisu.transaction.TransactionReason;
import market.sisu.transaction.TransactionRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests asserting a user's transaction-log sum always equals balanceSisu (SC-006). */
class TransactionLedgerTest {

    private TransactionRepository transactionRepository;
    private ObjectId userId;

    @BeforeEach
    void setUp() {
        transactionRepository = mock(TransactionRepository.class);
        userId = new ObjectId();
    }

    @Test
    void signedAmountIsPositiveForCreditAndNegativeForDebit() {
        Transaction credit = new Transaction();
        credit.direction = TransactionDirection.CREDIT;
        credit.amountSisu = 500;
        assertEquals(500, credit.signedAmount());

        Transaction debit = new Transaction();
        debit.direction = TransactionDirection.DEBIT;
        debit.amountSisu = 200;
        assertEquals(-200, debit.signedAmount());
    }

    @Test
    void startingGrantAndWagerAndPayoutSumMatchesBalance() {
        Transaction grant = transaction(TransactionDirection.CREDIT, 1000, TransactionReason.STARTING_GRANT);
        Transaction wager = transaction(TransactionDirection.DEBIT, 300, TransactionReason.WAGER_PLACED);
        Transaction payout = transaction(TransactionDirection.CREDIT, 450, TransactionReason.PAYOUT);
        Transaction refund = transaction(TransactionDirection.CREDIT, 100, TransactionReason.REFUND);

        List<Transaction> history = List.of(grant, wager, payout, refund);
        when(transactionRepository.findByUser(userId)).thenReturn(history);
        when(transactionRepository.sumForUser(userId)).thenCallRealMethod();

        long balanceSisu = 1000 - 300 + 450 + 100; // 1250
        assertEquals(balanceSisu, transactionRepository.sumForUser(userId));
    }

    @Test
    void emptyTransactionHistorySumIsZero() {
        when(transactionRepository.findByUser(userId)).thenReturn(List.of());
        when(transactionRepository.sumForUser(userId)).thenCallRealMethod();

        assertEquals(0, transactionRepository.sumForUser(userId));
    }

    @Test
    void singleStartingGrantSumMatchesBalance() {
        Transaction grant = transaction(TransactionDirection.CREDIT, 1000, TransactionReason.STARTING_GRANT);

        List<Transaction> history = List.of(grant);
        when(transactionRepository.findByUser(userId)).thenReturn(history);
        when(transactionRepository.sumForUser(userId)).thenCallRealMethod();

        assertEquals(1000, transactionRepository.sumForUser(userId));
    }

    private Transaction transaction(TransactionDirection direction, long amount, TransactionReason reason) {
        Transaction tx = new Transaction();
        tx.id = new ObjectId();
        tx.userId = userId;
        tx.direction = direction;
        tx.amountSisu = amount;
        tx.reason = reason;
        tx.createdAt = Instant.now();
        return tx;
    }
}
