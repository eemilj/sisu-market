package fi.sisu.market.prediction;

import fi.sisu.market.common.ApiException;
import fi.sisu.market.user.CurrentUser;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.bson.types.ObjectId;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Browsing predictions is public; creating one requires an account. */
@Path("/api/predictions")
@Produces(MediaType.APPLICATION_JSON)
public class PredictionResource {

    @Inject
    PredictionRepository predictions;

    @Inject
    PredictionService service;

    @Inject
    CurrentUser currentUser;

    @GET
    public List<PredictionDto> list(@QueryParam("category") String category,
                                    @QueryParam("status") String status) {
        Instant now = Instant.now();
        return predictions.search(parse(Category.class, category), parse(PredictionStatus.class, status), now)
                .stream()
                .map(p -> PredictionDto.from(p, now))
                .toList();
    }

    @GET
    @Path("/{id}")
    public PredictionDto get(@PathParam("id") String id) {
        if (!ObjectId.isValid(id)) {
            throw ApiException.notFound("Prediction not found");
        }
        return predictions.findByIdOptional(new ObjectId(id))
                .map(p -> PredictionDto.from(p, Instant.now()))
                .orElseThrow(() -> ApiException.notFound("Prediction not found"));
    }

    @POST
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    @ResponseStatus(201)
    public PredictionDto create(@NotNull(message = "Request body is required") @Valid CreatePredictionRequest request) {
        Prediction created = service.create(request, currentUser.require());
        return PredictionDto.from(created, Instant.now());
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("ALL")) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown " + type.getSimpleName() + ": " + value);
        }
    }
}
