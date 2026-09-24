package market.sisu.integration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.NewCookie;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import market.sisu.auth.JwtService;
import market.sisu.auth.SessionCookieFilter;
import market.sisu.prediction.PredictionStatus;
import market.sisu.realtime.LiveEvents;
import market.sisu.realtime.SseBroadcaster;
import market.sisu.user.Role;
import org.awaitility.Awaitility;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SessionAndRealtimeTest {

    @Inject
    JwtService jwtService;

    @Inject
    SessionCookieFilter cookies;

    @Inject
    SseBroadcaster broadcaster;

    @TestHTTPResource("/api/realtime/updates")
    URI updatesUri;

    @Test
    void sessionCookieIsHardened() {
        NewCookie cookie = cookies.sessionCookie("token");

        assertEquals("sisu_session", cookie.getName());
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.isSecure());
        assertEquals(NewCookie.SameSite.STRICT, cookie.getSameSite());
        assertEquals(Duration.ofHours(24).toSeconds(), cookie.getMaxAge());
        assertEquals(0, cookies.clearedCookie().getMaxAge());
    }

    @Test
    void streamRequiresSession() {
        given().when().get("/api/realtime/updates").then().statusCode(401).body("code", equalTo("UNAUTHENTICATED"));
    }

    @Test
    void streamRejectsForgedToken() {
        given().cookie("sisu_session", "not-a-jwt")
                .when()
                .get("/api/realtime/updates")
                .then()
                .statusCode(401);
    }

    @Test
    void streamDeliversOwnBalanceAndBroadcastEvents() throws Exception {
        ObjectId userId = new ObjectId();
        String jwt = jwtService.issue(userId, "matti", Role.USER);
        HttpRequest request = HttpRequest.newBuilder(updatesUri)
                .header("Cookie", "sisu_session=" + jwt)
                .header("Accept", "text/event-stream")
                .build();
        HttpResponse<InputStream> response =
                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, response.statusCode());

        try (BufferedReader stream =
                new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> broadcaster.connectionCount() > 0);

            broadcaster.sendBalanceUpdated(new ObjectId().toHexString(), 1); // someone else's: not delivered
            broadcaster.sendBalanceUpdated(userId.toHexString(), 950);
            assertEquals("balance-updated", nextField(stream, "event"));
            assertEquals("{\"balanceSisu\":950}", nextField(stream, "data"));

            broadcaster.broadcastPredictionStatusChanged(
                    new LiveEvents.PredictionStatusChanged("p1", PredictionStatus.CLOSED, null));
            assertEquals("prediction-status-changed", nextField(stream, "event"));
            assertEquals(
                    "{\"predictionId\":\"p1\",\"status\":\"CLOSED\",\"winningOutcome\":null}",
                    nextField(stream, "data"));
        }
    }

    /** Reads up to the next "name:value" line; per the SSE spec one space after the colon is optional. */
    private static String nextField(BufferedReader stream, String name) throws Exception {
        String line;
        while ((line = stream.readLine()) != null) {
            if (line.startsWith(name + ":")) {
                String value = line.substring(name.length() + 1);
                return value.startsWith(" ") ? value.substring(1) : value;
            }
        }
        throw new AssertionError("stream ended before a '" + name + "' field arrived");
    }
}
