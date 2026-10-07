package fi.sisu.vibe.api;

import fi.sisu.vibe.api.Views.LiveEvent;
import fi.sisu.vibe.service.LiveFeed;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestStreamElementType;

/** Server-sent events: every prediction change (new, wager, close, resolve) is pushed here. */
@Path("/live")
@PermitAll
public class LiveResource {

    private final LiveFeed feed;

    public LiveResource(LiveFeed feed) {
        this.feed = feed;
    }

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<LiveEvent> stream() {
        return feed.stream();
    }
}
