package fi.sisu.market.prediction;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The open/closed boundary that wagering and resolution both rely on. */
class PredictionStatusTest {

    private static final Instant CLOSES = Instant.parse("2026-12-01T00:00:00Z");

    private static Prediction withStatus(PredictionStatus status) {
        Prediction p = new Prediction();
        p.status = status;
        p.closesAt = CLOSES;
        return p;
    }

    @Test
    void openUntilTheClosingInstantExclusive() {
        Prediction p = withStatus(PredictionStatus.OPEN);
        assertEquals(PredictionStatus.OPEN, p.effectiveStatus(CLOSES.minusMillis(1)));
        assertEquals(PredictionStatus.CLOSED, p.effectiveStatus(CLOSES), "closed at exactly closesAt");
        assertEquals(PredictionStatus.CLOSED, p.effectiveStatus(CLOSES.plusSeconds(3600)));
    }

    @Test
    void storedClosedOrResolvedStatusWins() {
        assertEquals(PredictionStatus.CLOSED, withStatus(PredictionStatus.CLOSED).effectiveStatus(CLOSES.minusSeconds(60)));
        assertEquals(PredictionStatus.RESOLVED, withStatus(PredictionStatus.RESOLVED).effectiveStatus(CLOSES.minusSeconds(60)));
        assertEquals(PredictionStatus.RESOLVED, withStatus(PredictionStatus.RESOLVED).effectiveStatus(CLOSES.plusSeconds(60)));
    }
}
