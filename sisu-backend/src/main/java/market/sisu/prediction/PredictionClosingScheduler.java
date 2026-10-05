package market.sisu.prediction;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class PredictionClosingScheduler {

    private final PredictionRepository predictions;
    private final Clock clock;

    public PredictionClosingScheduler(PredictionRepository predictions, Clock clock) {
        this.predictions = predictions;
        this.clock = clock;
    }

    @Scheduled(every = "60s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void closeDuePredictions() {
        Instant now = clock.instant();
        List<Prediction> due = predictions.list("status = ?1 and closingAt <= ?2", PredictionStatus.OPEN, now);
        for (Prediction prediction : due) {
            predictions.compareAndSetStatus(
                    null, prediction.id, Set.of(PredictionStatus.OPEN), PredictionStatus.CLOSED);
        }
    }
}
