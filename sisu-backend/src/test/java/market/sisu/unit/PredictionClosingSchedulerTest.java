package market.sisu.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import market.sisu.prediction.Prediction;
import market.sisu.prediction.PredictionClosingScheduler;
import market.sisu.prediction.PredictionRepository;
import market.sisu.prediction.PredictionStatus;
import market.sisu.realtime.LiveEvents;
import market.sisu.realtime.SseBroadcaster;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit test for the automatic OPEN->CLOSED transition at closingAt (FR-025). */
class PredictionClosingSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

    private PredictionRepository predictions;
    private SseBroadcaster broadcaster;
    private PredictionClosingScheduler scheduler;

    @BeforeEach
    void setUp() {
        predictions = mock(PredictionRepository.class);
        broadcaster = mock(SseBroadcaster.class);
        scheduler = new PredictionClosingScheduler(predictions, Clock.fixed(NOW, ZoneOffset.UTC), broadcaster);
    }

    @Test
    void transitionsOpenPredictionsPastClosingAtToClosed() {
        Prediction p1 = new Prediction();
        p1.id = new ObjectId();
        p1.status = PredictionStatus.OPEN;
        p1.closingAt = NOW.minusSeconds(30);

        when(predictions.list("status = ?1 and closingAt <= ?2", PredictionStatus.OPEN, NOW))
                .thenReturn(List.of(p1));
        when(predictions.compareAndSetStatus(
                        eq(null), eq(p1.id), eq(Set.of(PredictionStatus.OPEN)), eq(PredictionStatus.CLOSED), any()))
                .thenReturn(true);

        scheduler.closeExpiredPredictions();

        assertEquals(PredictionStatus.CLOSED, p1.status);
        verify(predictions)
                .compareAndSetStatus(
                        eq(null), eq(p1.id), eq(Set.of(PredictionStatus.OPEN)), eq(PredictionStatus.CLOSED), any());
        verify(broadcaster).broadcastPredictionStatusChanged(any(LiveEvents.PredictionStatusChanged.class));
    }

    @Test
    void skipsClosingWhenConcurrentStatusChangeFailsCompareAndSet() {
        Prediction p1 = new Prediction();
        p1.id = new ObjectId();
        p1.status = PredictionStatus.OPEN;
        p1.closingAt = NOW.minusSeconds(30);

        when(predictions.list("status = ?1 and closingAt <= ?2", PredictionStatus.OPEN, NOW))
                .thenReturn(List.of(p1));
        when(predictions.compareAndSetStatus(
                        eq(null), eq(p1.id), eq(Set.of(PredictionStatus.OPEN)), eq(PredictionStatus.CLOSED), any()))
                .thenReturn(false);

        scheduler.closeExpiredPredictions();

        // Status remains OPEN because compareAndSetStatus failed due to concurrent modification
        assertEquals(PredictionStatus.OPEN, p1.status);
        verify(broadcaster, never()).broadcastPredictionStatusChanged(any(LiveEvents.PredictionStatusChanged.class));
    }

    @Test
    void selfHealsAfterRestartClosingExpiredPredictionsOnSubsequentInvocation() {
        // Simulates a restart where a prediction expired while offline/restarting (FR-025).
        Prediction p1 = new Prediction();
        p1.id = new ObjectId();
        p1.status = PredictionStatus.OPEN;
        p1.closingAt = NOW.minusSeconds(300); // expired 5 minutes ago during downtime

        when(predictions.list("status = ?1 and closingAt <= ?2", PredictionStatus.OPEN, NOW))
                .thenReturn(List.of(p1));
        when(predictions.compareAndSetStatus(
                        eq(null), eq(p1.id), eq(Set.of(PredictionStatus.OPEN)), eq(PredictionStatus.CLOSED), any()))
                .thenReturn(true);

        // First invocation post-restart
        scheduler.closeExpiredPredictions();

        assertEquals(PredictionStatus.CLOSED, p1.status);
        verify(broadcaster).broadcastPredictionStatusChanged(any(LiveEvents.PredictionStatusChanged.class));
    }
}
