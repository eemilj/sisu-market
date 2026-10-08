package fi.sisu.market.common;

import jakarta.ws.rs.core.Response.Status;

/** A business error that maps directly to an HTTP status and a user-facing message. */
public class ApiException extends RuntimeException {

    private final Status status;

    public ApiException(Status status, String message) {
        super(message);
        this.status = status;
    }

    public Status status() {
        return status;
    }

    public static ApiException conflict(String message) {
        return new ApiException(Status.CONFLICT, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(Status.UNAUTHORIZED, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(Status.FORBIDDEN, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(Status.NOT_FOUND, message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(Status.BAD_REQUEST, message);
    }
}
