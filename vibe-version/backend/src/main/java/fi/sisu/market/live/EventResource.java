package fi.sisu.market.live;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Map;

/**
 * Server-Sent Events stream. Anyone can listen to public events (predictions); a request carrying a valid
 * bearer token additionally receives that user's private events (wallet).
 *
 * <p>The first event is always {@code ready}: clients should (re)load their data when they get it, which
 * covers anything that happened while they were disconnected.
 */
@Path("/api/events")
public class EventResource {

    static final Duration HEARTBEAT = Duration.ofSeconds(20);
    /** A client this far behind is disconnected; it will reconnect and reload. */
    private static final int MAX_BUFFERED_EVENTS = 1000;

    @Inject
    EventHub hub;

    @Inject
    JsonWebToken jwt;

    @Inject
    ObjectMapper json;

    @Context
    Sse sse;

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<OutboundSseEvent> stream() {
        String subject = jwt.getSubject();
        ObjectId userId = subject != null && ObjectId.isValid(subject) ? new ObjectId(subject) : null;

        Multi<OutboundSseEvent> ready = Multi.createFrom().item(
                event("ready", Map.of("authenticated", userId != null)));
        Multi<OutboundSseEvent> events = hub.stream()
                .filter(e -> e.visibleTo(userId))
                .onOverflow().buffer(MAX_BUFFERED_EVENTS)
                .map(e -> event(e.type(), e.payload()));
        // Keeps proxies from closing an idle connection.
        Multi<OutboundSseEvent> heartbeats = Multi.createFrom().ticks().every(HEARTBEAT)
                .map(tick -> sse.newEventBuilder().comment("heartbeat").build());

        return Multi.createBy().merging().streams(ready, events, heartbeats);
    }

    private OutboundSseEvent event(String name, Object payload) {
        try {
            return sse.newEventBuilder()
                    .name(name)
                    .mediaType(MediaType.APPLICATION_JSON_TYPE)
                    .data(String.class, json.writeValueAsString(payload))
                    .build();
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
