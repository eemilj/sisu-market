package market.sisu.realtime;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import market.sisu.common.ApiError;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/** {@code GET /api/realtime/updates}: the per-tab live update stream (contracts/realtime.md). */
@Path("/realtime")
public class RealtimeResource {

    private final SseBroadcaster broadcaster;
    private final JsonWebToken jwt;

    RealtimeResource(SseBroadcaster broadcaster, JsonWebToken jwt) {
        this.broadcaster = broadcaster;
        this.jwt = jwt;
    }

    @GET
    @Path("/updates")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Authenticated
    @Operation(summary = "Live balance/odds/status updates as Server-Sent Events")
    @APIResponse(responseCode = "200", description = "Event stream (see contracts/realtime.md)")
    @APIResponse(
            responseCode = "401",
            description = "Not logged in",
            content =
                    @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ApiError.class)))
    public void updates(@Context SseEventSink sink, @Context Sse sse) {
        broadcaster.register(jwt.getSubject(), sink, sse);
    }
}
