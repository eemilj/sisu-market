package fi.sisu.vibe.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import fi.sisu.vibe.api.Views.ApiError;
import fi.sisu.vibe.service.ApiException;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/** Every error leaves the API as {@code {code, message}}, never with a stack trace. */
public class ErrorMappers {

    private static final Logger LOG = Logger.getLogger(ErrorMappers.class);

    @ServerExceptionMapper
    public Response api(ApiException e) {
        return error(e.status(), e.code(), e.getMessage());
    }

    @ServerExceptionMapper
    public Response validation(ConstraintViolationException e) {
        String fields = e.getConstraintViolations().stream()
                .map(v -> {
                    String path = v.getPropertyPath().toString();
                    return path.substring(path.lastIndexOf('.') + 1);
                })
                .distinct()
                .collect(Collectors.joining(","));
        return error(400, "VALIDATION", fields);
    }

    @ServerExceptionMapper({JsonProcessingException.class, MismatchedInputException.class, InvalidFormatException.class})
    public Response malformed(Exception e) {
        return error(400, "VALIDATION", "Malformed request body");
    }

    @ServerExceptionMapper({UnauthorizedException.class, AuthenticationFailedException.class})
    public Response unauthorized(Exception e) {
        return error(401, "UNAUTHORIZED", "Login required");
    }

    @ServerExceptionMapper
    public Response forbidden(ForbiddenException e) {
        return error(403, "NOT_ALLOWED", "Not allowed");
    }

    @ServerExceptionMapper
    public Response web(WebApplicationException e) {
        int status = e.getResponse().getStatus();
        return error(status, status == 404 ? "NOT_FOUND" : "BAD_REQUEST", "Request could not be processed");
    }

    @ServerExceptionMapper
    public Response unexpected(RuntimeException e) {
        LOG.error("Unexpected error", e);
        return error(500, "INTERNAL", "Something went wrong");
    }

    private static Response error(int status, String code, String message) {
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(new ApiError(code, message)).build();
    }
}
