package fi.sisu.market.common;

/** Uniform JSON error body: {@code {"status": 409, "message": "..."}}. */
public record ApiError(int status, String message) {
}
