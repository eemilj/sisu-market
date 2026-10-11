package market.sisu.prediction;

import com.mongodb.client.model.Updates;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import market.sisu.realtime.LiveEvents;
import market.sisu.realtime.SseBroadcaster;

/**
 * DB-driven scheduled job flipping OPEN predictions to CLOSED once closingAt has passed (FR-025).
 * Runs at a <=1-minute interval (every 30 seconds) and self-heals after a restart.
 */
@ApplicationScoped
public class PredictionClosingScheduler {

    private final PredictionRepository predictions;
    private final Clock clock;
    private final SseBroadcaster broadcaster;

    public PredictionClosingScheduler(PredictionRepository predictions, Clock clock, SseBroadcaster broadcaster) {
        this.predictions = predictions;
        this.clock = clock;
        this.broadcaster = broadcaster;
    }

    public PredictionClosingScheduler(PredictionRepository predictions, Clock clock) {
        this(predictions, clock, null);
    }

    @Scheduled(every = "30s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void closeExpiredPredictions() {
        Instant now = clock.instant();
        List<Prediction> expired = predictions.list("status = ?1 and closingAt <= ?2", PredictionStatus.OPEN, now);
        for (Prediction p : expired) {
            boolean closed = predictions.compareAndSetStatus(
                    null, p.id, Set.of(PredictionStatus.OPEN), PredictionStatus.CLOSED, Updates.set("closedAt", now));
            if (closed) {
                p.status = PredictionStatus.CLOSED;
                if (broadcaster != null) {
                    broadcaster.broadcastPredictionStatusChanged(
                            new LiveEvents.PredictionStatusChanged(p.id.toHexString(), PredictionStatus.CLOSED, null));
                }
            }
        }
    }
}
