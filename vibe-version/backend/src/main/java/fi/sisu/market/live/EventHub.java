package fi.sisu.market.live;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.Flow.Processor;

/**
 * In-process fan-out of {@link LiveEvent}s to every open event stream.
 *
 * <p>Publishers must only publish <em>after</em> their transaction committed, so clients never see a change
 * that was rolled back. Being in-process, this only reaches clients of the same backend instance; running
 * several instances would need a shared source such as MongoDB change streams.
 */
@ApplicationScoped
public class EventHub {

    /** Serialised because requests on different threads publish concurrently. */
    private final Processor<LiveEvent, LiveEvent> processor = BroadcastProcessor.<LiveEvent>create().serialized();

    public void publish(LiveEvent event) {
        processor.onNext(event);
    }

    /** Events published from now on. Each subscriber gets its own stream. */
    public Multi<LiveEvent> stream() {
        return Multi.createFrom().publisher(processor);
    }
}
