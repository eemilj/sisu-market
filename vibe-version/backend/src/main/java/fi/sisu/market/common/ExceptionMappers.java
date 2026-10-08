package fi.sisu.market.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.stream.Collectors;

public class ExceptionMappers {

    @ServerExceptionMapper
    public Response mapApiException(ApiException e) {
        return error(e.status().getStatusCode(), e.getMessage());
    }

    @ServerExceptionMapper
    public Response mapValidation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .sorted()
                .collect(Collectors.joining("; "));
        return error(400, message);
    }

    /** Unparseable JSON. */
    @ServerExceptionMapper
    public Response mapBadJson(JsonProcessingException e) {
        return error(400, "Malformed request body");
    }

    /** A value of the wrong type, such as an unknown category. Overrides Quarkus' built-in mapper for this type. */
    @ServerExceptionMapper
    public Response mapMismatchedJson(MismatchedInputException e) {
        return error(400, "Malformed request body");
    }

    /**
     * Framework-raised errors (unreadable body, unknown route, wrong method...) come without a body;
     * give them the standard error shape while keeping status and headers.
     */
    @ServerExceptionMapper
    public Response mapWebApplication(WebApplicationException e) {
        Response original = e.getResponse();
        if (original.hasEntity()) {
            return original;
        }
        int status = original.getStatus();
        String message = status == 400 ? "Malformed request body" : original.getStatusInfo().getReasonPhrase();
        return Response.fromResponse(original)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ApiError(status, message))
                .build();
    }

    private static Response error(int status, String message) {
        return Response.status(status).entity(new ApiError(status, message)).build();
    }
}
