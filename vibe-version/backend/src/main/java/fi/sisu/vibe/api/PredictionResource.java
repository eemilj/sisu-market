package fi.sisu.vibe.api;

import fi.sisu.vibe.api.Views.NewPrediction;
import fi.sisu.vibe.api.Views.NewWager;
import fi.sisu.vibe.api.Views.PredictionDetail;
import fi.sisu.vibe.api.Views.PredictionView;
import fi.sisu.vibe.api.Views.Resolution;
import fi.sisu.vibe.api.Views.WagerResult;
import fi.sisu.vibe.domain.PredictionStatus;
import fi.sisu.vibe.service.ApiException;
import fi.sisu.vibe.service.PredictionService;
import fi.sisu.vibe.service.WagerService;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import java.util.List;
import java.util.Locale;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.ResponseStatus;

@Path("/predictions")
public class PredictionResource {

    private final PredictionService predictions;
    private final WagerService wagers;
    private final JsonWebToken jwt;

    public PredictionResource(PredictionService predictions, WagerService wagers, JsonWebToken jwt) {
        this.predictions = predictions;
        this.wagers = wagers;
        this.jwt = jwt;
    }

    @GET
    @PermitAll
    public List<PredictionView> list(@QueryParam("status") String status) {
        return predictions.list(parseStatus(status));
    }

    @GET
    @Path("/{id}")
    @PermitAll
    public PredictionDetail get(@PathParam("id") String id) {
        return predictions.detail(id);
    }

    @POST
    @RolesAllowed("USER")
    @ResponseStatus(201)
    public PredictionView create(@Valid @NotNull NewPrediction body) {
        return predictions.create(body, jwt.getSubject(), jwt.getName());
    }

    @POST
    @Path("/{id}/wagers")
    @RolesAllowed("USER")
    @ResponseStatus(201)
    public WagerResult wager(@PathParam("id") String id, @Valid @NotNull NewWager body) {
        return wagers.place(id, body, jwt.getSubject(), jwt.getName());
    }

    @POST
    @Path("/{id}/resolve")
    @RolesAllowed("USER")
    public PredictionView resolve(@PathParam("id") String id, @Valid @NotNull Resolution body) {
        return predictions.resolve(id, body.outcome(), jwt.getSubject(), jwt.getGroups().contains("ADMIN"));
    }

    private static PredictionStatus parseStatus(String status) {
        if (status == null || status.isBlank() || status.equalsIgnoreCase("all")) {
            return null;
        }
        try {
            return PredictionStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("VALIDATION", "Unknown status filter");
        }
    }
}
