package market.sisu.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import market.sisu.user.ResolverBootstrap;
import market.sisu.user.Role;
import market.sisu.user.User;
import market.sisu.user.UserRepository;
import market.sisu.user.UserService;
import market.sisu.user.UserService.Provisioning;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Bootstrap resolver provisioning rules (FR-039, FR-040) without a database. */
class ResolverBootstrapTest {

    private static final Instant NOW = Instant.parse("2026-10-13T12:00:00Z");
    private static final String PASSWORD = "a long resolver passphrase";

    private UserRepository users;
    private BalanceLedger ledger;
    private UserService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        users = mock(UserRepository.class);
        MongoTransactions tx = mock(MongoTransactions.class);
        ledger = mock(BalanceLedger.class);
        service = new UserService(users, tx, ledger, Clock.fixed(NOW, ZoneOffset.UTC), 1000);
        when(tx.inTransaction(any(Function.class)))
                .thenAnswer(invocation -> ((Function<ClientSession, User>) invocation.getArgument(0)).apply(null));
        when(ledger.credit(any(), any(), any(), any(), any())).thenReturn(Sisu.of(1000));
    }

    private static User existing(Role role) {
        User user = new User();
        user.id = new ObjectId();
        user.username = "resolver";
        user.passwordHash = "existing-hash";
        user.role = role;
        user.balanceSisu = 42;
        return user;
    }

    @Test
    void absentAccountIsCreatedAsResolverWithStartingGrant() {
        when(users.findByUsername("resolver")).thenReturn(Optional.empty());

        assertEquals(Provisioning.CREATED, service.provisionResolver("resolver", PASSWORD));

        ArgumentCaptor<User> inserted = ArgumentCaptor.forClass(User.class);
        verify(users).insert(any(), inserted.capture());
        User created = inserted.getValue();
        assertEquals(Role.RESOLVER, created.role);
        assertNotEquals(PASSWORD, created.passwordHash);
        assertEquals(1000, created.balanceSisu);
        verify(ledger)
                .credit(
                        any(),
                        eq(created.id),
                        eq(Sisu.of(1000)),
                        eq(TransactionReason.STARTING_GRANT),
                        eq(Related.NONE));
    }

    @Test
    void existingResolverIsLeftUntouched() {
        User resolver = existing(Role.RESOLVER);
        when(users.findByUsername("resolver")).thenReturn(Optional.of(resolver));

        assertEquals(Provisioning.UNCHANGED, service.provisionResolver("resolver", PASSWORD));

        verify(users, never()).insert(any(), any());
        verify(users, never()).setRole(any(), any());
        assertEquals("existing-hash", resolver.passwordHash);
        assertEquals(42, resolver.balanceSisu);
    }

    @Test
    void existingRegularUserIsPromotedWithoutTouchingPasswordOrBalance() {
        User regular = existing(Role.USER);
        when(users.findByUsername("resolver")).thenReturn(Optional.of(regular));

        assertEquals(Provisioning.PROMOTED, service.provisionResolver("resolver", PASSWORD));

        verify(users).setRole(regular.id, Role.RESOLVER);
        verify(users, never()).insert(any(), any());
        verify(ledger, never()).credit(any(), any(), any(), any(), any());
        assertEquals("existing-hash", regular.passwordHash);
        assertEquals(42, regular.balanceSisu);
    }

    @Test
    void duplicateKeyRaceFallsThroughToTheRoleCheck() {
        User winner = existing(Role.RESOLVER);
        // Absent on the first look, present once a concurrent start has inserted it.
        when(users.findByUsername("resolver")).thenReturn(Optional.empty(), Optional.empty(), Optional.of(winner));
        doThrow(ApiException.conflict("USERNAME_TAKEN", "taken")).when(users).insert(any(), any());

        assertEquals(Provisioning.UNCHANGED, service.provisionResolver("resolver", PASSWORD));
    }

    @Test
    void shortPasswordIsRejectedForANewAccount() {
        when(users.findByUsername("resolver")).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> service.provisionResolver("resolver", "short"));
        verify(users, never()).insert(any(), any());
    }

    @Test
    void bootstrapWithoutPasswordSkipsAndDoesNotFail() {
        UserService userService = mock(UserService.class);

        new ResolverBootstrap(userService, Optional.of("resolver"), Optional.empty()).provision();

        verify(userService, never()).provisionResolver(any(), any());
    }

    @Test
    void bootstrapSwallowsValidationFailureSoTheApplicationStillStarts() {
        UserService userService = mock(UserService.class);
        when(userService.provisionResolver("resolver", "short"))
                .thenThrow(ApiException.unprocessable("VALIDATION_FAILED", "too short"));

        new ResolverBootstrap(userService, Optional.of("resolver"), Optional.of("short")).provision();

        verify(userService, times(1)).provisionResolver("resolver", "short");
    }
}
