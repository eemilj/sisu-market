package market.sisu.realtime;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;

/**
 * Registry of open SSE connections and the only way services push live updates
 * (contracts/realtime.md). Connections are grouped by user id so {@code balance-updated} reaches
 * only its owner; the other events go to everyone.
 *
 * <p>Call these methods only after the MongoDB transaction behind the change has committed. The
 * stream is a notification channel, not the system of record: a dropped event is repaired by the
 * frontend refetching over REST on reconnect.
 */
@ApplicationScoped
public class SseBroadcaster {

    private static final Logger LOG = Logger.getLogger(SseBroadcaster.class);

    private final Map<String, Set<SseEventSink>> sinksByUser = new ConcurrentHashMap<>();
    private volatile Sse sse;

    /** Adds a connection for {@code userId}; it is dropped automatically once it fails or closes. */
    public void register(String userId, SseEventSink sink, Sse sse) {
        this.sse = sse;
        sinksByUser.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet()).add(sink);
        // Flushes the response headers so the browser's EventSource fires `open` right away.
        send(userId, sink, sse.newEventBuilder().comment("connected").build());
    }

    public void sendBalanceUpdated(String userId, long balanceSisu) {
        Set<SseEventSink> sinks = sinksByUser.get(userId);
        if (sinks == null || sse == null) {
            return;
        }
        OutboundSseEvent event = json(LiveEvents.BALANCE_UPDATED, new LiveEvents.BalanceUpdated(balanceSisu));
        sinks.forEach(sink -> send(userId, sink, event));
    }

    public void broadcastOddsUpdated(LiveEvents.OddsUpdated odds) {
        broadcast(LiveEvents.ODDS_UPDATED, odds);
    }

    public void broadcastPredictionStatusChanged(LiveEvents.PredictionStatusChanged change) {
        broadcast(LiveEvents.PREDICTION_STATUS_CHANGED, change);
    }

    /** Number of open connections; for tests and diagnostics. */
    public int connectionCount() {
        return sinksByUser.values().stream().mapToInt(Set::size).sum();
    }

    // Keeps idle connections from being cut by proxies and detects dead ones.
    @Scheduled(every = "25s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void heartbeat() {
        if (sse == null) {
            return;
        }
        OutboundSseEvent ping = sse.newEventBuilder().comment("keep-alive").build();
        sinksByUser.forEach((userId, sinks) -> sinks.forEach(sink -> send(userId, sink, ping)));
    }

    private void broadcast(String name, Object payload) {
        if (sse == null) {
            return;
        }
        OutboundSseEvent event = json(name, payload);
        sinksByUser.forEach((userId, sinks) -> sinks.forEach(sink -> send(userId, sink, event)));
    }

    private OutboundSseEvent json(String name, Object payload) {
        return sse.newEventBuilder()
                .name(name)
                .mediaType(MediaType.APPLICATION_JSON_TYPE)
                .data(payload.getClass(), payload)
                .build();
    }

    private void send(String userId, SseEventSink sink, OutboundSseEvent event) {
        if (sink.isClosed()) {
            remove(userId, sink);
            return;
        }
        sink.send(event).whenComplete((ignored, failure) -> {
            if (failure != null) {
                LOG.debugf("Dropping SSE connection for user %s: %s", userId, failure.getMessage());
                remove(userId, sink);
            }
        });
    }

    private void remove(String userId, SseEventSink sink) {
        sinksByUser.computeIfPresent(userId, (id, sinks) -> {
            sinks.remove(sink);
            return sinks.isEmpty() ? null : sinks;
        });
    }
}
