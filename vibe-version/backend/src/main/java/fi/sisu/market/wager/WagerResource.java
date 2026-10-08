package fi.sisu.market.wager;

import fi.sisu.market.common.ApiException;
import fi.sisu.market.user.CurrentUser;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.bson.types.ObjectId;
import org.jboss.resteasy.reactive.ResponseStatus;

@Path("/api/predictions/{predictionId}/wagers")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class WagerResource {

    @Inject
    WagerService service;

    @Inject
    CurrentUser currentUser;

    @POST
    @ResponseStatus(201)
    public WagerResult place(@PathParam("predictionId") String predictionId,
                             @NotNull(message = "Request body is required") @Valid PlaceWagerRequest request) {
        if (!ObjectId.isValid(predictionId)) {
            throw ApiException.notFound("Prediction not found");
        }
        // @Digits(fraction = 0) and @Positive guarantee a positive integer; longValueExact guards the range.
        long amount;
        try {
            amount = request.amount().longValueExact();
        } catch (ArithmeticException e) {
            throw ApiException.badRequest("Amount must be a whole number of Sisu");
        }
        return service.place(currentUser.require().id, new ObjectId(predictionId), request.side(), amount);
    }
}
