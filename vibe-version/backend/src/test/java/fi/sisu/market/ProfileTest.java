package fi.sisu.market;

import fi.sisu.market.wallet.TransactionType;
import fi.sisu.market.wallet.WalletService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ProfileTest {

    @Inject
    WalletService wallet;

    private record Account(ObjectId id, String name, String token) {
    }

    private static Account register() {
        String name = "p" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var r = given().contentType(ContentType.JSON)
                .body(Map.of("username", name, "email", name + "@example.fi", "password", "salasana123"))
                .post("/api/auth/register").then().statusCode(201).extract();
        return new Account(new ObjectId((String) r.path("user.id")), name, r.path("token"));
    }

    private static String createPrediction(Account by, String question) {
        return given().auth().oauth2(by.token()).contentType(ContentType.JSON)
                .body(Map.of("question", question, "description", "Profile test",
                        "resolutionCriteria", "Resolves however the test likes.", "category", "ECONOMY",
                        "closesAt", Instant.now().plus(Duration.ofDays(5)).toString()))
                .post("/api/predictions").then().statusCode(201).extract().path("id");
    }

    @Test
    void publicProfileShowsBalanceRankAndCreatedPredictions() {
        Account alice = register();
        String question = "Will Alice's profile list this? " + UUID.randomUUID();
        String id = createPrediction(alice, question);

        given().when().get("/api/profiles/" + alice.name().toUpperCase())
                .then().statusCode(200)
                .body("username", equalTo(alice.name()))
                .body("balance", equalTo(1000))
                .body("rank", greaterThan(0))
                .body("joinedAt", notNullValue())
                .body("predictionsCreated", equalTo(1))
                .body("admin", equalTo(false));

        given().when().get("/api/profiles/" + alice.name() + "/predictions")
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(id))
                .body("[0].createdBy", equalTo(alice.name()));

        given().when().get("/api/profiles/nobody_here_xyz").then().statusCode(404);
    }

    @Test
    void wagerHistoryIsPrivateAndShowsPredictionContext() {
        Account alice = register();
        String question = "Will this wager show in history? " + UUID.randomUUID();
        String id = createPrediction(register(), question);
        for (int amount : new int[] {10, 20, 30}) {
            given().auth().oauth2(alice.token()).contentType(ContentType.JSON)
                    .body(Map.of("side", "YES", "amount", amount))
                    .post("/api/predictions/" + id + "/wagers").then().statusCode(201);
        }

        String cursor = given().auth().oauth2(alice.token()).queryParam("limit", 2)
                .when().get("/api/users/me/wagers")
                .then().statusCode(200)
                .body("items", hasSize(2))
                .body("items[0].wager.amount", equalTo(30))
                .body("items[0].question", equalTo(question))
                .body("items[0].predictionStatus", equalTo("OPEN"))
                .body("items[0].wager.status", equalTo("OPEN"))
                .body("nextCursor", notNullValue())
                .extract().path("nextCursor");
        given().auth().oauth2(alice.token()).queryParam("limit", 2).queryParam("before", cursor)
                .when().get("/api/users/me/wagers")
                .then().body("items.wager.amount", equalTo(List.of(10))).body("nextCursor", nullValue());

        given().when().get("/api/users/me/wagers").then().statusCode(401);
    }

    @Test
    void leaderboardRanksByBalanceWithSharedRanksForTies() {
        // Far above anyone else in the test database.
        Account first = register();
        Account tied = register();
        Account third = register();
        wallet.apply(first.id(), 1_000_000_000L, TransactionType.SIGNUP_BONUS, "test boost", null);
        wallet.apply(tied.id(), 1_000_000_000L, TransactionType.SIGNUP_BONUS, "test boost", null);
        wallet.apply(third.id(), 999_999_999L, TransactionType.SIGNUP_BONUS, "test boost", null);

        var board = given().queryParam("limit", 5).when().get("/api/leaderboard")
                .then().statusCode(200)
                .body("entries[0].username", equalTo(first.name())) // earlier sign-up first among ties
                .body("entries[0].rank", equalTo(1))
                .body("entries[1].username", equalTo(tied.name()))
                .body("entries[1].rank", equalTo(1))
                .body("entries[2].username", equalTo(third.name()))
                .body("entries[2].rank", equalTo(3))
                .body("me", nullValue())
                .extract();

        List<Integer> balances = board.path("entries.balance");
        for (int i = 1; i < balances.size(); i++) {
            assertTrue(balances.get(i - 1) >= balances.get(i), "sorted by balance: " + balances);
        }
        given().get("/api/profiles/" + third.name()).then().body("rank", equalTo(3));
    }

    @Test
    void leaderboardIncludesCallerWhenOutsideTop() {
        Account nobody = register();
        int rank = given().auth().oauth2(nobody.token()).queryParam("limit", 1)
                .when().get("/api/leaderboard")
                .then().statusCode(200)
                .body("entries", hasSize(1))
                .body("me.username", equalTo(nobody.name()))
                .body("me.balance", equalTo(1000))
                .extract().path("me.rank");
        assertEquals(rank, (int) given().get("/api/profiles/" + nobody.name()).then().extract().path("rank"));

        given().queryParam("limit", 0).get("/api/leaderboard").then().statusCode(400);
    }
}
