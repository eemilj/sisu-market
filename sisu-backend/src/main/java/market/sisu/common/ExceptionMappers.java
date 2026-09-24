package market.sisu.common;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Global exception mappers: every failure leaves the API as an {@link ApiError} body, and none
 * exposes a stack trace or other internal detail (Constitution V).
 */
public class ExceptionMappers {

    private static final Logger LOG = Logger.getLogger(ExceptionMappers.class);
    private static final int UNPROCESSABLE_ENTITY = 422;

    @ServerExceptionMapper
    public Response apiException(ApiException e) {
        Response.ResponseBuilder response = error(e.status(), e.code(), e.getMessage());
        if (e.retryAfterSeconds() != null) {
            response.header(HttpHeaders.RETRY_AFTER, e.retryAfterSeconds());
        }
        return response.build();
    }

    // Takes precedence over Quarkus's built-in mapper, which answers 400 instead of the 422 the
    // contract specifies for validation failures.
    @ServerExceptionMapper(priority = Priorities.USER - 1)
    public Response validation(ValidationException e) {
        String message = e instanceof ConstraintViolationException cve
                ? cve.getConstraintViolations().stream()
                        .map(ExceptionMappers::describe)
                        .sorted()
                        .collect(Collectors.joining("; "))
                : "Request validation failed";
        return error(UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", message).build();
    }

    @ServerExceptionMapper({UnauthorizedException.class, AuthenticationFailedException.class})
    public Response unauthenticated(RuntimeException e) {
        return error(401, "UNAUTHENTICATED", "Login required").build();
    }

    @ServerExceptionMapper
    public Response forbidden(ForbiddenException e) {
        return error(403, "FORBIDDEN", "Not allowed").build();
    }

    @ServerExceptionMapper
    public Response webApplication(WebApplicationException e) {
        int status = e.getResponse().getStatus();
        Response.Status known = Response.Status.fromStatusCode(status);
        if (status >= 500) {
            LOG.error("Request failed with a server error", e);
        }
        return error(
                        status,
                        known != null ? known.name() : "HTTP_" + status,
                        known != null ? known.getReasonPhrase() : "Request failed")
                .build();
    }

    @ServerExceptionMapper
    public Response unexpected(Exception e) {
        LOG.error("Unhandled exception", e);
        return error(500, "INTERNAL_ERROR", "Unexpected server error").build();
    }

    private static Response.ResponseBuilder error(int status, String code, String message) {
        return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE).entity(new ApiError(code, message));
    }

    private static String describe(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        // Drop the "method.argN." prefix Hibernate Validator adds for resource parameters.
        int lastDot = path.lastIndexOf('.');
        String field = lastDot >= 0 ? path.substring(lastDot + 1) : path;
        return field + ": " + violation.getMessage();
    }
}
