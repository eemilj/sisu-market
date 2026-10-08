package fi.sisu.market.wager;

import fi.sisu.market.common.ApiException;
import fi.sisu.market.prediction.Outcome;
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
import org.jboss.resteasy.reactive.RestResponse;

import java.math.BigDecimal;
import java.util.Locale;

@Path("/api/predictions/{predictionId}")
@Produces(MediaType.APPLICATION_JSON)
public class WagerResource {

    @Inject
    WagerService service;

    @Inject
    CurrentUser currentUser;

    /** Public: what a wager of {@code amount} on {@code side} would pay right now. Places nothing. */
    @GET
    @Path("/quote")
    public QuoteDto quote(@PathParam("predictionId") String predictionId,
                          @QueryParam("side") String side,
                          @QueryParam("amount") String amount) {
        if (side == null || amount == null) {
            throw ApiException.badRequest("side and amount are required");
        }
        Outcome outcome;
        try {
            outcome = Outcome.valueOf(side.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("side must be YES or NO");
        }
        long parsed;
        try {
            parsed = new BigDecimal(amount.trim()).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw ApiException.badRequest("Amount must be a whole number of Sisu");
        }
        return service.quote(parseId(predictionId), outcome, parsed);
    }

    /** 201 with the new wager, or 200 with the original one when {@code requestId} was already used. */
    @POST
    @Path("/wagers")
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    public RestResponse<WagerResult> place(@PathParam("predictionId") String predictionId,
                                           @NotNull(message = "Request body is required") @Valid PlaceWagerRequest request) {
        ObjectId id = parseId(predictionId);
        // @Digits(fraction = 0) and @Positive guarantee a positive integer; longValueExact guards the range.
        long amount;
        try {
            amount = request.amount().longValueExact();
        } catch (ArithmeticException e) {
            throw ApiException.badRequest("Amount must be a whole number of Sisu");
        }
        WagerService.Placement placement = service.place(currentUser.require().id, id, request.side(), amount,
                request.minPayout(), request.requestId());
        return placement.replayed()
                ? RestResponse.ok(placement.result())
                : RestResponse.status(RestResponse.Status.CREATED, placement.result());
    }

    private static ObjectId parseId(String id) {
        if (!ObjectId.isValid(id)) {
            throw ApiException.notFound("Prediction not found");
        }
        return new ObjectId(id);
    }
}
