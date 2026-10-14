package fi.sisu.market;

import fi.sisu.market.user.UserRepository;
import fi.sisu.market.wallet.InsufficientBalanceException;
import fi.sisu.market.wallet.LedgerEntry;
import fi.sisu.market.wallet.LedgerRepository;
import fi.sisu.market.wallet.TransactionType;
import fi.sisu.market.wallet.WalletService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class WalletTest {

    @Inject
    WalletService wallet;

    @Inject
    UserRepository users;

    @Inject
    LedgerRepository ledger;

    private String token;
    private ObjectId userId;

    @BeforeEach
    void registerUser() {
        String name = "w" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var response = given().contentType(ContentType.JSON)
                .body(Map.of("username", name, "email", name + "@example.fi", "password", "salasana123"))
                .post("/api/auth/register")
                .then().statusCode(201).extract();
        token = response.path("token");
        userId = new ObjectId((String) response.path("user.id"));
    }

    private long storedBalance() {
        return users.findById(userId).balance;
    }

    private long ledgerSum() {
        return ledger.list("userId", userId).stream().mapToLong(e -> e.amount).sum();
    }

    @Test
    void registrationRecordsWelcomeBonus() {
        given().auth().oauth2(token).when().get("/api/users/me/transactions")
                .then().statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].type", equalTo("SIGNUP_BONUS"))
                .body("items[0].amount", equalTo(1000))
                .body("items[0].balanceAfter", equalTo(1000))
                .body("items[0].description", equalTo("Welcome bonus"))
                .body("nextCursor", nullValue());
    }

    @Test
    void historyRequiresLogin() {
        given().when().get("/api/users/me/transactions").then().statusCode(401);
    }

    @Test
    void creditsAndDebitsKeepBalanceEqualToLedger() {
        wallet.apply(userId, -300, TransactionType.SIGNUP_BONUS, "debit", null);
        LedgerEntry last = wallet.apply(userId, 50, TransactionType.SIGNUP_BONUS, "credit", null);

        assertEquals(750, last.balanceAfter);
        assertEquals(750, storedBalance());
        assertEquals(storedBalance(), ledgerSum());
        given().auth().oauth2(token).get("/api/users/me").then().body("balance", equalTo(750));
    }

    @Test
    void overdraftIsRejectedAndLeavesNoTrace() {
        assertThrows(InsufficientBalanceException.class,
                () -> wallet.apply(userId, -1001, TransactionType.SIGNUP_BONUS, "too much", null));

        assertEquals(1000, storedBalance());
        assertEquals(1, ledger.count("userId", userId));

        // Spending exactly everything is allowed.
        assertEquals(0, wallet.apply(userId, -1000, TransactionType.SIGNUP_BONUS, "all in", null).balanceAfter);
    }

    @Test
    void failureLaterInTransactionRollsBackBalanceChange() {
        assertThrows(IllegalStateException.class, () -> wallet.inTransaction(session -> {
            wallet.apply(session, userId, 500, TransactionType.SIGNUP_BONUS, "should vanish", null);
            throw new IllegalStateException("boom");
        }));

        assertEquals(1000, storedBalance());
        assertEquals(1, ledger.count("userId", userId));
    }

    @Test
    void historyIsNewestFirstAndPaginates() {
        for (int i = 1; i <= 4; i++) {
            wallet.apply(userId, i, TransactionType.SIGNUP_BONUS, "credit " + i, null);
        }
        // 5 entries total: bonus + 4 credits.
        String cursor = given().auth().oauth2(token).queryParam("limit", 2)
                .when().get("/api/users/me/transactions")
                .then().statusCode(200)
                .body("items.description", equalTo(List.of("credit 4", "credit 3")))
                .body("nextCursor", notNullValue())
                .extract().path("nextCursor");

        cursor = given().auth().oauth2(token).queryParam("limit", 2).queryParam("before", cursor)
                .when().get("/api/users/me/transactions")
                .then().body("items.description", equalTo(List.of("credit 2", "credit 1")))
                .extract().path("nextCursor");

        given().auth().oauth2(token).queryParam("limit", 2).queryParam("before", cursor)
                .when().get("/api/users/me/transactions")
                .then().body("items.description", equalTo(List.of("Welcome bonus")))
                .body("nextCursor", nullValue());
    }

    @Test
    void rejectsBadPagingParameters() {
        given().auth().oauth2(token).queryParam("limit", 0).get("/api/users/me/transactions").then().statusCode(400);
        given().auth().oauth2(token).queryParam("limit", 101).get("/api/users/me/transactions").then().statusCode(400);
        given().auth().oauth2(token).queryParam("before", "nope").get("/api/users/me/transactions").then().statusCode(400);
    }
}
