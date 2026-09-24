package market.sisu.integration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import market.sisu.common.ApiException;

/** Test-only endpoints that trigger each failure type the global exception mappers handle. */
@Path("/test-probe")
public class ErrorProbeResource {

    public record Body(@NotBlank String name) {}

    @GET
    @Path("/api-exception/{status}")
    public String apiException(@PathParam("status") int status) {
        if (status == 429) {
            throw ApiException.tooManyRequests("TOO_MANY_ATTEMPTS", "Slow down", 900);
        }
        throw new ApiException(status, "PROBE_" + status, "probe message");
    }

    @POST
    @Path("/validated")
    @Consumes(MediaType.APPLICATION_JSON)
    public String validated(@Valid Body body) {
        return body.name();
    }

    @GET
    @Path("/boom")
    public String boom() {
        throw new IllegalStateException("secret internal detail");
    }
}
