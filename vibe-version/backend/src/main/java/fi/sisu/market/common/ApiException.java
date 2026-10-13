package fi.sisu.market.common;

import jakarta.ws.rs.core.Response.Status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A business error that maps to an HTTP status, a stable {@link ErrorCode} with optional parameters (for the
 * client to build its own message from), and an English developer message.
 */
public class ApiException extends RuntimeException {

    private final Status status;
    private final ErrorCode code;
    private final Map<String, Object> params = new LinkedHashMap<>();
    private List<ApiError.FieldError> fieldErrors = List.of();

    public ApiException(Status status, ErrorCode code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public Status status() {
        return status;
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> params() {
        return params;
    }

    public List<ApiError.FieldError> fieldErrors() {
        return fieldErrors;
    }

    /** A 400 VALIDATION_FAILED for rules bean validation can't express (e.g. comparing two fields). */
    public static ApiException validation(List<ApiError.FieldError> fieldErrors) {
        ApiException e = new ApiException(Status.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                fieldErrors.stream().map(ApiError.FieldError::message).reduce((a, b) -> a + "; " + b).orElse("Invalid input"));
        e.fieldErrors = List.copyOf(fieldErrors);
        return e;
    }

    /** 429 with the number of seconds to wait; also sent as a Retry-After header. */
    public static ApiException tooManyRequests(ErrorCode code, String message, long retryAfterSeconds) {
        return new ApiException(Status.TOO_MANY_REQUESTS, code, message).with("retryAfterSeconds", retryAfterSeconds);
    }

    /** Adds a parameter the client can use in its message, e.g. {@code balance}. */
    public ApiException with(String key, Object value) {
        params.put(key, value);
        return this;
    }

    public static ApiException conflict(ErrorCode code, String message) {
        return new ApiException(Status.CONFLICT, code, message);
    }

    public static ApiException unauthorized(ErrorCode code, String message) {
        return new ApiException(Status.UNAUTHORIZED, code, message);
    }

    public static ApiException forbidden(ErrorCode code, String message) {
        return new ApiException(Status.FORBIDDEN, code, message);
    }

    public static ApiException notFound(ErrorCode code, String message) {
        return new ApiException(Status.NOT_FOUND, code, message);
    }

    public static ApiException badRequest(ErrorCode code, String message) {
        return new ApiException(Status.BAD_REQUEST, code, message);
    }
}
