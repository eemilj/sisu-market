package fi.sisu.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@QuarkusTest
class LiveEventsTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TestHTTPResource("/api/events")
    URL eventsUrl;

    // ---- helpers ----

    private record Account(String id, String token) {
    }

    private static Account register() {
        String name = "l" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var r = given().contentType(ContentType.JSON)
                .body(Map.of("username", name, "email", name + "@example.fi", "password", "salasana123"))
                .post("/api/auth/register").then().statusCode(201).extract();
        return new Account(r.path("user.id"), r.path("token"));
    }

    private static String createPrediction(Account by, String question) {
        return given().auth().oauth2(by.token()).contentType(ContentType.JSON)
                .body(Map.of("question", question, "description", "Live test",
                        "resolutionCriteria", "Resolves YES if the event arrives.", "category", "CULTURE",
                        "closesAt", Instant.now().plus(Duration.ofDays(10)).toString()))
                .post("/api/predictions").then().statusCode(201).extract().path("id");
    }

    private static io.restassured.response.ValidatableResponse wager(Account by, String predictionId, String side, int amount) {
        return given().auth().oauth2(by.token()).contentType(ContentType.JSON)
                .body(Map.of("side", side, "amount", amount))
                .post("/api/predictions/" + predictionId + "/wagers").then();
    }

    /** Minimal SSE client: collects named events in the background. */
    private final class Stream implements AutoCloseable {
        private record Event(String name, JsonNode data) {
        }

        private final HttpClient http = HttpClient.newHttpClient();
        private final List<Event> received = new ArrayList<>();

        Stream(String token) throws Exception {
            HttpRequest.Builder request = HttpRequest.newBuilder(eventsUrl.toURI()).header("Accept", "text/event-stream");
            if (token != null) {
                request.header("Authorization", "Bearer " + token);
            }
            http.sendAsync(request.build(), HttpResponse.BodyHandlers.ofLines()).thenAccept(response -> {
                String name = null;
                StringBuilder data = new StringBuilder();
                for (String line : (Iterable<String>) response.body()::iterator) {
                    if (line.startsWith("event:")) {
                        name = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        data.append(line.substring(5).trim());
                    } else if (line.isEmpty() && name != null) {
                        try {
                            Event event = new Event(name, JSON.readTree(data.toString()));
                            synchronized (received) {
                                received.add(event);
                            }
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                        name = null;
                        data.setLength(0);
                    }
                }
            });
            JsonNode ready = await("ready", d -> true);
            assertEquals(token != null, ready.get("authenticated").asBoolean());
        }

        JsonNode await(String name, Predicate<JsonNode> match) throws InterruptedException {
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (System.nanoTime() < deadline) {
                synchronized (received) {
                    for (Event e : received) {
                        if (e.name().equals(name) && match.test(e.data())) {
                            return e.data();
                        }
                    }
                }
                Thread.sleep(20);
            }
            fail("No '" + name + "' event matched; got " + names());
            return null;
        }

        void assertNone(String name, Predicate<JsonNode> match) throws InterruptedException {
            Thread.sleep(500); // give a wrongly published event time to arrive
            synchronized (received) {
                for (Event e : received) {
                    assertTrue(!(e.name().equals(name) && match.test(e.data())), "Unexpected '" + name + "': " + e.data());
                }
            }
        }

        private List<String> names() {
            synchronized (received) {
                return received.stream().map(Event::name).toList();
            }
        }

        @Override
        public void close() {
            http.shutdownNow();
        }
    }

    // ---- tests ----

    @Test
    void anonymousViewersSeeNewPredictionsAndOddsButNoWallets() throws Exception {
        Account alice = register();
        try (Stream viewer = new Stream(null)) {
            String question = "Will live events reach anonymous viewers? " + UUID.randomUUID();
            String id = createPrediction(alice, question);
            JsonNode created = viewer.await("prediction.created", d -> d.get("id").asText().equals(id));
            assertEquals(question, created.get("question").asText());
            assertEquals(0, created.get("version").asLong());

            wager(alice, id, "YES", 100).statusCode(201);
            JsonNode updated = viewer.await("prediction.updated", d -> d.get("id").asText().equals(id));
            assertEquals(1, updated.get("version").asLong());
            assertEquals(100, updated.get("totalWagered").asLong());
            assertTrue(updated.get("yesProbability").asDouble() > 0.5);

            viewer.assertNone("wallet.transaction", d -> true);
        }
    }

    @Test
    void balanceUpdatesGoOnlyToTheirOwner() throws Exception {
        Account alice = register();
        Account bob = register();
        String id = createPrediction(bob, "Will only Alice hear about her balance? " + UUID.randomUUID());

        try (Stream aliceStream = new Stream(alice.token()); Stream bobStream = new Stream(bob.token())) {
            wager(alice, id, "NO", 250).statusCode(201);

            JsonNode wallet = aliceStream.await("wallet.transaction", d -> true);
            assertEquals(750, wallet.get("balance").asLong());
            assertEquals(2, wallet.get("balanceVersion").asLong()); // 1 = welcome bonus
            assertEquals("WAGER", wallet.get("transaction").get("type").asText());
            assertEquals(-250, wallet.get("transaction").get("amount").asLong());

            // Bob sees the market move, but not Alice's wallet.
            bobStream.await("prediction.updated", d -> d.get("id").asText().equals(id));
            bobStream.assertNone("wallet.transaction", d -> true);
        }

        given().auth().oauth2(alice.token()).get("/api/users/me").then()
                .body("balance", org.hamcrest.Matchers.equalTo(750))
                .body("balanceVersion", org.hamcrest.Matchers.equalTo(2));
    }

    @Test
    void rejectedWagerAnnouncesNothing() throws Exception {
        Account alice = register();
        String id = createPrediction(alice, "Will a rolled-back wager stay silent? " + UUID.randomUUID());

        try (Stream aliceStream = new Stream(alice.token())) {
            wager(alice, id, "YES", 5000).statusCode(409); // more than her balance: rolled back
            aliceStream.assertNone("prediction.updated", d -> d.get("id").asText().equals(id));
            aliceStream.assertNone("wallet.transaction", d -> true);
        }
    }

    @Test
    void versionsOrderConsecutiveUpdates() throws Exception {
        Account alice = register();
        String id = createPrediction(alice, "Do versions increase per wager? " + UUID.randomUUID());
        try (Stream viewer = new Stream(null)) {
            wager(alice, id, "YES", 10).statusCode(201);
            wager(alice, id, "NO", 20).statusCode(201);
            JsonNode second = viewer.await("prediction.updated",
                    d -> d.get("id").asText().equals(id) && d.get("version").asLong() == 2);
            assertEquals(30, second.get("totalWagered").asLong());
        }
        given().get("/api/predictions/" + id).then().body("version", org.hamcrest.Matchers.equalTo(2));
    }
}
