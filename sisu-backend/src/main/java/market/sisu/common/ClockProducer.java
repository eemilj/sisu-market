package market.sisu.common;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.Clock;

/**
 * Exposes the system clock as an injectable bean so time-dependent business rules (closing time,
 * login cooldown) can be unit-tested with a fixed clock (Constitution III).
 */
public class ClockProducer {

    @Produces
    @Singleton
    Clock clock() {
        return Clock.systemUTC();
    }
}
