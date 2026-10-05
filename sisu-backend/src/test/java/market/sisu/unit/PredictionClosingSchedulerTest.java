package market.sisu.unit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.scheduler.Scheduled;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import market.sisu.prediction.Prediction;
import market.sisu.prediction.PredictionClosingScheduler;
import market.sisu.prediction.PredictionRepository;
import market.sisu.prediction.PredictionStatus;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PredictionClosingSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

    private PredictionRepository predictions;
    private PredictionClosingScheduler scheduler;
    private Clock clock;

    @BeforeEach
    void setUp() {
        predictions = mock(PredictionRepository.class);
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        scheduler = new PredictionClosingScheduler(predictions, clock);
    }

    @Test
    void closesOverdueOpenPredictions() {
        Prediction overdue = prediction(PredictionStatus.OPEN, NOW.minusSeconds(10));
        when(predictions.list("status = ?1 and closingAt <= ?2", PredictionStatus.OPEN, NOW))
                .thenReturn(List.of(overdue));

        scheduler.closeDuePredictions();

        verify(predictions).compareAndSetStatus(any(), eq(overdue.id), any(), eq(PredictionStatus.CLOSED));
    }

    @Test
    void leavesFutureOpenPredictionsOpen() {
        Prediction future = prediction(PredictionStatus.OPEN, NOW.plusSeconds(60));
        when(predictions.list("status = ?1 and closingAt <= ?2", PredictionStatus.OPEN, NOW))
                .thenReturn(List.of());

        scheduler.closeDuePredictions();

        verify(predictions, never()).compareAndSetStatus(any(), any(), any(), eq(PredictionStatus.CLOSED));
    }

    @Test
    void selfHealsOverduePredictionsAfterRestart() {
        Prediction persistedOverdue = prediction(PredictionStatus.OPEN, NOW.minusSeconds(300));
        when(predictions.list("status = ?1 and closingAt <= ?2", PredictionStatus.OPEN, NOW))
                .thenReturn(List.of(persistedOverdue));

        scheduler.closeDuePredictions();

        verify(predictions).compareAndSetStatus(any(), eq(persistedOverdue.id), any(), eq(PredictionStatus.CLOSED));
    }

    @Test
    void scheduledIntervalIsAtMostOneMinute() throws Exception {
        Scheduled scheduled = PredictionClosingScheduler.class
                .getMethod("closeDuePredictions")
                .getAnnotation(Scheduled.class);

        assertNotNull(scheduled, "closeDuePredictions must be annotated with @Scheduled");
        String every = scheduled.every();
        assertNotNull(every);
        assertTrue(
                every.endsWith("s") || every.endsWith("m"),
                "Scheduled interval format should be seconds or minutes: " + every);

        long seconds = parseIntervalToSeconds(every);
        assertTrue(seconds > 0 && seconds <= 60, "Scheduled interval must be <= 1 minute (60s), got: " + every);
    }

    private long parseIntervalToSeconds(String every) {
        if (every.endsWith("s")) {
            return Long.parseLong(every.substring(0, every.length() - 1));
        } else if (every.endsWith("m")) {
            return Long.parseLong(every.substring(0, every.length() - 1)) * 60;
        }
        return Long.MAX_VALUE;
    }

    private static Prediction prediction(PredictionStatus status, Instant closingAt) {
        Prediction prediction = new Prediction();
        prediction.id = new ObjectId();
        prediction.question = "Sataako huomenna?";
        prediction.status = status;
        prediction.closingAt = closingAt;
        return prediction;
    }
}
