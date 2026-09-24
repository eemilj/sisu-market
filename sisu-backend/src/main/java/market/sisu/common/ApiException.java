package market.sisu.common;

import jakarta.ws.rs.core.Response.Status;

/**
 * A business-rule or precondition failure that maps to a specific HTTP status and a stable
 * {@link ApiError#code()} (Constitution V: fail fast with a clear, user-actionable error).
 */
public class ApiException extends RuntimeException {

    private static final int UNPROCESSABLE_ENTITY = 422;

    private final int status;
    private final String code;
    private final Long retryAfterSeconds;

    public ApiException(int status, String code, String message) {
        this(status, code, message, null);
    }

    private ApiException(int status, String code, String message, Long retryAfterSeconds) {
        super(message, null, false, false);
        this.status = status;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static ApiException notFound(String code, String message) {
        return new ApiException(Status.NOT_FOUND.getStatusCode(), code, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(Status.CONFLICT.getStatusCode(), code, message);
    }

    public static ApiException unprocessable(String code, String message) {
        return new ApiException(UNPROCESSABLE_ENTITY, code, message);
    }

    public static ApiException unauthorized(String code, String message) {
        return new ApiException(Status.UNAUTHORIZED.getStatusCode(), code, message);
    }

    public static ApiException forbidden(String code, String message) {
        return new ApiException(Status.FORBIDDEN.getStatusCode(), code, message);
    }

    public static ApiException tooManyRequests(String code, String message, long retryAfterSeconds) {
        return new ApiException(Status.TOO_MANY_REQUESTS.getStatusCode(), code, message, retryAfterSeconds);
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    /** Seconds for the {@code Retry-After} header, or {@code null} when not applicable. */
    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
