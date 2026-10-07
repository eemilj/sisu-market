package fi.sisu.vibe.service;

import fi.sisu.vibe.api.Views.LiveEvent;
import fi.sisu.vibe.api.Views.PredictionView;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;

/** Fan-out of prediction changes to every connected SSE client. */
@ApplicationScoped
public class LiveFeed {

    private final BroadcastProcessor<LiveEvent> processor = BroadcastProcessor.create();

    public synchronized void publish(PredictionView prediction) {
        processor.onNext(new LiveEvent("prediction", prediction));
    }

    /** Hot stream of changes, with a ping every 20 s so proxies keep the connection open. */
    public Multi<LiveEvent> stream() {
        Multi<LiveEvent> pings = Multi.createFrom().ticks().every(Duration.ofSeconds(20))
                .map(t -> new LiveEvent("ping", null));
        return Multi.createBy().merging().streams(processor, pings);
    }
}
