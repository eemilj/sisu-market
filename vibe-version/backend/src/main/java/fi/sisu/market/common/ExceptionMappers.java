package fi.sisu.market.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class ExceptionMappers {

    private static final Logger LOG = Logger.getLogger(ExceptionMappers.class);

    /** Constraint attributes worth passing to clients (limits they can show the user). */
    private static final Set<String> RULE_PARAMS = Set.of("min", "max", "value", "integer", "fraction");

    @ServerExceptionMapper
    public Response mapApiException(ApiException e) {
        int status = e.status().getStatusCode();
        Response.ResponseBuilder response = Response.status(status).type(MediaType.APPLICATION_JSON)
                .entity(new ApiError(status, e.code().name(), e.getMessage(), e.params(), e.fieldErrors()));
        if (e.params().get("retryAfterSeconds") instanceof Number seconds) {
            response.header("Retry-After", seconds.longValue());
        }
        return response.build();
    }

    /**
     * Anything not handled above is a bug or an infrastructure failure. Its details (exception type, message,
     * stack trace: possibly hostnames, queries or other internals) go only to the server log, under a random id
     * that the client receives so a report can be matched to the log entry.
     *
     * <p>More specific mappers, including Quarkus' own for authentication (401) and authorization (403),
     * still take precedence over this one.
     */
    @ServerExceptionMapper
    public Response mapUnexpected(Throwable e) {
        String errorId = UUID.randomUUID().toString();
        LOG.errorf(e, "Unhandled error %s", errorId);
        return json(500, new ApiError(500, ErrorCode.INTERNAL_ERROR.name(), "Internal server error",
                Map.of("errorId", errorId), null));
    }

    /** Bean-validation failures: one {@link ApiError.FieldError} per field problem. */
    @ServerExceptionMapper
    public Response mapValidation(ConstraintViolationException e) {
        List<ApiError.FieldError> fields = e.getConstraintViolations().stream()
                .map(ExceptionMappers::fieldError)
                .sorted(Comparator.comparing(ApiError.FieldError::field).thenComparing(ApiError.FieldError::rule))
                .toList();
        String message = fields.stream().map(ApiError.FieldError::message).sorted().collect(Collectors.joining("; "));
        return json(400, new ApiError(400, ErrorCode.VALIDATION_FAILED.name(), message, null, fields));
    }

    private static ApiError.FieldError fieldError(ConstraintViolation<?> v) {
        String field = null;
        for (Path.Node node : v.getPropertyPath()) {
            field = node.getName(); // last node: "question" in "create.request.question"
        }
        String rule = v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
        Map<String, Object> params = new LinkedHashMap<>();
        v.getConstraintDescriptor().getAttributes().forEach((k, val) -> {
            if (RULE_PARAMS.contains(k)) {
                params.put(k, val);
            }
        });
        return new ApiError.FieldError(field, rule, v.getMessage(), params);
    }

    /** Unparseable JSON. */
    @ServerExceptionMapper
    public Response mapBadJson(JsonProcessingException e) {
        return json(400, ApiError.of(400, ErrorCode.MALFORMED_BODY, "Malformed request body"));
    }

    /** A value of the wrong type, such as an unknown category. Overrides Quarkus' built-in mapper for this type. */
    @ServerExceptionMapper
    public Response mapMismatchedJson(MismatchedInputException e) {
        return json(400, ApiError.of(400, ErrorCode.MALFORMED_BODY, "Malformed request body"));
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
        ApiError body = switch (status) {
            case 400 -> ApiError.of(400, ErrorCode.MALFORMED_BODY, "Malformed request body");
            case 413 -> ApiError.of(413, ErrorCode.PAYLOAD_TOO_LARGE, "Request body is too large");
            default -> ApiError.of(status, ErrorCode.HTTP_ERROR, original.getStatusInfo().getReasonPhrase());
        };
        return Response.fromResponse(original).type(MediaType.APPLICATION_JSON).entity(body).build();
    }

    private static Response json(int status, ApiError body) {
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(body).build();
    }
}
