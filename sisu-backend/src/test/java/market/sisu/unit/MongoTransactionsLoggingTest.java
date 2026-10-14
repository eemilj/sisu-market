package market.sisu.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mongodb.TransactionOptions;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.TransactionBody;
import java.util.ArrayList;
import java.util.List;
import market.sisu.common.ApiException;
import market.sisu.common.AuditLog;
import market.sisu.common.MongoTransactions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Failed transactions are logged and the original exception is rethrown unchanged (Constitution IV). */
class MongoTransactionsLoggingTest {

    private final List<String> lines = new ArrayList<>();
    private MongoTransactions transactions;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        MongoClient client = mock(MongoClient.class);
        ClientSession session = mock(ClientSession.class);
        when(client.startSession()).thenReturn(session);
        when(session.withTransaction(any(TransactionBody.class), any(TransactionOptions.class)))
                .thenAnswer(invocation -> ((TransactionBody<?>) invocation.getArgument(0)).execute());
        transactions = new MongoTransactions(client, new AuditLog(lines::add, lines::add));
    }

    @Test
    void logsFailureWithErrorCodeAndRethrowsTheSameException() {
        ApiException failure = ApiException.unprocessable("INSUFFICIENT_BALANCE", "Amount exceeds the balance");

        ApiException thrown = assertThrows(
                ApiException.class,
                () -> transactions.inTransaction(session -> {
                    throw failure;
                }));

        assertSame(failure, thrown);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).startsWith("event=transaction-failed"), lines.get(0));
        assertTrue(lines.get(0).contains("errorCode=INSUFFICIENT_BALANCE"), lines.get(0));
    }

    @Test
    void logsTheExceptionTypeForUnexpectedFailures() {
        IllegalStateException failure = new IllegalStateException("database exploded with secret detail");

        assertThrows(
                IllegalStateException.class,
                () -> transactions.inTransaction(session -> {
                    throw failure;
                }));

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("errorCode=INTERNAL"), lines.get(0));
        assertTrue(lines.get(0).contains("exception=IllegalStateException"), lines.get(0));
        // The message may carry internals, so it must not be written to the audit line.
        assertTrue(!lines.get(0).contains("secret"), lines.get(0));
    }

    @Test
    void successfulTransactionsLogNothing() {
        String result = transactions.inTransaction(session -> "done");

        assertEquals("done", result);
        assertTrue(lines.isEmpty());
    }
}
