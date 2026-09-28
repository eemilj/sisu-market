package market.sisu.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;
import com.mongodb.client.ClientSession;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Function;
import market.sisu.common.ApiException;
import market.sisu.common.MongoTransactions;
import market.sisu.common.Sisu;
import market.sisu.transaction.BalanceLedger;
import market.sisu.transaction.BalanceLedger.Related;
import market.sisu.transaction.TransactionReason;
import market.sisu.user.Role;
import market.sisu.user.User;
import market.sisu.user.UserRepository;
import market.sisu.user.UserService;
import org.bson.BsonDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Starting-balance grant and username uniqueness (FR-001, FR-007, FR-010) without a database. */
class UserServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

    private UserRepository users;
    private MongoTransactions tx;
    private BalanceLedger ledger;
    private UserService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        users = mock(UserRepository.class);
        tx = mock(MongoTransactions.class);
        ledger = mock(BalanceLedger.class);
        service = new UserService(users, tx, ledger, Clock.fixed(NOW, ZoneOffset.UTC), 1000);
        // Run transactional work inline with a null session: nothing here touches MongoDB itself.
        when(tx.inTransaction(any(Function.class)))
                .thenAnswer(invocation -> ((Function<ClientSession, User>) invocation.getArgument(0)).apply(null));
        when(ledger.credit(any(), any(), any(), any(), any())).thenReturn(Sisu.of(1000));
    }

    @Test
    void registerGrantsStartingBalanceAndRecordsItInTheLedger() {
        when(users.findByUsername("matti")).thenReturn(Optional.empty());

        User user = service.register("matti", "correct horse battery staple");

        assertEquals("matti", user.username);
        assertEquals(Role.USER, user.role);
        assertEquals(1000, user.balanceSisu);
        assertEquals(NOW, user.createdAt);
        assertNotEquals("correct horse battery staple", user.passwordHash);

        ArgumentCaptor<User> inserted = ArgumentCaptor.forClass(User.class);
        verify(users).insert(any(), inserted.capture());
        assertEquals(inserted.getValue().id, user.id);
        verify(ledger)
                .credit(any(), eq(user.id), eq(Sisu.of(1000)), eq(TransactionReason.STARTING_GRANT), eq(Related.NONE));
    }

    @Test
    void registerRejectsAnAlreadyTakenUsername() {
        User existing = new User();
        existing.username = "Matti";
        // The real lookup is case-insensitive via the collection collation; the unique index is
        // the final arbiter (see the duplicate-key test below and LedgerInvariantsTest).
        when(users.findByUsername("Matti")).thenReturn(Optional.of(existing));

        ApiException e =
                assertThrows(ApiException.class, () -> service.register("Matti", "correct horse battery staple"));

        assertEquals(409, e.status());
        assertEquals("USERNAME_TAKEN", e.code());
        verify(tx, never()).inTransaction(any(Function.class));
    }

    @Test
    void registerMapsADuplicateKeyRaceToConflict() {
        when(users.findByUsername("matti")).thenReturn(Optional.empty());
        MongoWriteException duplicate = new MongoWriteException(
                new WriteError(11000, "E11000 duplicate key", new BsonDocument()), new ServerAddress());
        org.mockito.Mockito.doThrow(duplicate).when(users).insert(any(), any(User.class));

        ApiException e =
                assertThrows(ApiException.class, () -> service.register("matti", "correct horse battery staple"));

        assertEquals(409, e.status());
        assertEquals("USERNAME_TAKEN", e.code());
    }

    @Test
    void registerPropagatesNonDuplicateWriteErrors() {
        when(users.findByUsername("matti")).thenReturn(Optional.empty());
        MongoWriteException other =
                new MongoWriteException(new WriteError(1, "internal error", new BsonDocument()), new ServerAddress());
        org.mockito.Mockito.doThrow(other).when(users).insert(any(), any(User.class));

        assertThrows(MongoWriteException.class, () -> service.register("matti", "correct horse battery staple"));
    }

    @Test
    void registerRejectsEmptyUsernamesAndShortPasswords() {
        assertValidationFails("   ", "correct horse battery staple");
        assertValidationFails("matti", "lyhyt");
        assertValidationFails("matti", null);
    }

    private void assertValidationFails(String username, String password) {
        ApiException e = assertThrows(ApiException.class, () -> service.register(username, password));
        assertEquals(422, e.status());
        assertEquals("VALIDATION_FAILED", e.code());
    }
}
