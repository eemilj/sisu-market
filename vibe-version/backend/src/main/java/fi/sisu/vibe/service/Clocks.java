package fi.sisu.vibe.service;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.Clock;

/** Time is injected so business rules can be tested with a fixed clock. */
public class Clocks {

    @Produces
    @Singleton
    Clock clock() {
        return Clock.systemUTC();
    }
}
