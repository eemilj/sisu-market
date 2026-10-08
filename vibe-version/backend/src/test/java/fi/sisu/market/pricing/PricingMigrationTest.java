package fi.sisu.market.pricing;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import fi.sisu.market.prediction.Prediction;
import fi.sisu.market.prediction.PredictionRepository;
import fi.sisu.market.user.UserRepository;
import fi.sisu.market.wallet.LedgerRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@QuarkusTest
class PricingMigrationTest {

    @Inject
    PricingMigration migration;

    @Inject
    PredictionRepository predictions;

    @Inject
    UserRepository users;

    @Inject
    LedgerRepository ledger;

    private MongoCollection<Document> raw(String name) {
        return predictions.mongoDatabase().getCollection(name);
    }

    @Test
    void refundsLegacyWagersAndConvertsPredictionsIdempotently() {
        String name = "m" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        ObjectId userId = new ObjectId((String) given().contentType(ContentType.JSON)
                .body(Map.of("username", name, "email", name + "@example.fi", "password", "salasana123"))
                .post("/api/auth/register").then().statusCode(201).extract().path("user.id"));

        // Recreate the pre-CPMM shape: seeded pools 200/700 plus a 100 Sisu YES wager on top.
        ObjectId predictionId = new ObjectId();
        raw("predictions").insertOne(new Document("_id", predictionId)
                .append("question", "Legacy question?").append("description", "d").append("category", "SPORTS")
                .append("status", "OPEN").append("closesAt", Date.from(Instant.now().plus(Duration.ofDays(5))))
                .append("yesPool", 300L).append("noPool", 700L).append("createdAt", new Date()));
        raw("wagers").insertOne(new Document("_id", new ObjectId()).append("userId", userId)
                .append("predictionId", predictionId).append("side", "YES").append("amount", 100L)
                .append("createdAt", new Date()));
        raw("users").updateOne(Filters.eq("_id", userId), Updates.inc("balance", -100L));
        raw("ledger").insertOne(new Document("_id", new ObjectId()).append("userId", userId).append("type", "WAGER")
                .append("amount", -100L).append("balanceAfter", 900L).append("description", "legacy")
                .append("predictionId", predictionId).append("createdAt", new Date()));

        int[] first = migration.run();
        assertEquals(1, first[0], "refunded");

        assertEquals(1000, users.findById(userId).balance);
        long ledgerSum = ledger.list("userId", userId).stream().mapToLong(e -> e.amount).sum();
        assertEquals(1000, ledgerSum);
        Document wager = raw("wagers").find(Filters.eq("predictionId", predictionId)).first();
        assertEquals("REFUNDED", wager.getString("status"));

        Prediction converted = predictions.findById(predictionId);
        assertEquals(200.0 / 900.0, converted.pool().yesProbability(), 1e-6, "probability from seeded pools only");
        assertEquals(0, converted.totalWagered);
        Document rawPrediction = raw("predictions").find(Filters.eq("_id", predictionId)).first();
        assertFalse(rawPrediction.containsKey("yesPool"));

        // Running again changes nothing.
        assertArrayEquals(new int[] {0, 0}, migration.run());
        assertEquals(1000, users.findById(userId).balance);
    }
}
